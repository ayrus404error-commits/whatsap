package com.glasschat.engine

import android.content.Context
import android.util.Log
import com.glasschat.go.wa.Client
import com.glasschat.go.wa.Listener
import com.glasschat.go.wa.Wa
import com.glasschat.service.ConnectionService
import com.glasschat.service.DeliveryLog
import com.glasschat.service.Notifier
import com.glasschat.service.Prefs
import com.glasschat.service.WakeHold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Kotlin face of the Go WhatsApp engine. Everything the screens show comes
 * from the state flows here; everything they do goes through these calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object Engine {
    private const val TAG = "Engine"

    /** The pseudo-chat statuses (stories) live under. */
    const val STATUS_CHAT = "status@broadcast"

    enum class Conn { Starting, NeedsLink, Connecting, Connected, Error }

    data class Me(val jid: String, val phone: String, val name: String)

    sealed interface Event {
        data class NewMessage(val msg: Message, val chatName: String, val isGroup: Boolean, val mutedUntil: Long) : Event
        data class Updated(val msg: Message) : Event
        data class Removed(val chat: String, val id: String) : Event
        data class Receipt(val chat: String, val ids: List<String>, val status: Int) : Event
        /** A chat's messages were cleared (or the chat deleted). */
        data class Cleared(val chat: String) : Event
        /** Messages were pinned or unpinned in a chat. */
        data class PinsChanged(val chat: String) : Event
    }

    private lateinit var app: Context
    @Volatile private var client: Client? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val io = Dispatchers.IO.limitedParallelism(4)

    private val _conn = MutableStateFlow(Conn.Starting)
    val conn = _conn.asStateFlow()
    private val _qr = MutableStateFlow<String?>(null)
    val qr = _qr.asStateFlow()
    private val _pairError = MutableStateFlow<String?>(null)
    val pairError = _pairError.asStateFlow()
    private val _alert = MutableStateFlow<String?>(null)
    val alert = _alert.asStateFlow()
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val _me = MutableStateFlow<Me?>(null)
    val me = _me.asStateFlow()
    private val _history = MutableStateFlow<Int?>(null)
    val history = _history.asStateFlow()
    private val _typing = MutableStateFlow<Map<String, Typing>>(emptyMap())
    val typing = _typing.asStateFlow()
    private val _presence = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val presence = _presence.asStateFlow()
    private val _avatarVersion = MutableStateFlow(0)
    val avatarVersion = _avatarVersion.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 512)
    val events = _events.asSharedFlow()
    /** Bumps whenever statuses change, so the Updates tab reloads. */
    private val _statusVersion = MutableStateFlow(0)
    val statusVersion = _statusVersion.asStateFlow()

    /** People with status updates you haven't seen yet (green ring on their photo). */
    private val _unseenStatus = MutableStateFlow<Set<String>>(emptySet())
    val unseenStatus = _unseenStatus.asStateFlow()

    /** Re-reads who has unseen updates (after new ones arrive or you view some). */
    fun refreshStatusRings() {
        scope.launch(io) {
            val groups = runCatching { StatusGroup.list(client?.getStatuses() ?: "[]") }.getOrDefault(emptyList())
            _unseenStatus.value = groups.filter { !it.mine && it.unviewed > 0 }.map { it.sender }.toSet()
        }
    }

    /** The chat currently on screen (no notifications for it). */
    @Volatile var openChat: String? = null
    @Volatile var foreground: Boolean = false
        private set

    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var syncNotifications = false
    @Volatile private var lastLoggedConn: Conn? = null

    /** A message the chat screen should scroll to (from search). */
    private val _jump = MutableStateFlow<Pair<String, Message>?>(null)
    val jump = _jump.asStateFlow()

    fun requestJump(chat: String, m: Message) {
        _jump.value = chat to m
    }

    fun consumeJump() {
        _jump.value = null
    }

    val isLoggedIn: Boolean get() = client?.isLoggedIn == true

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        scope.launch(io) {
            try {
                val dir = File(app.filesDir, "wa").apply { mkdirs() }
                val c = Wa.newClient(dir.absolutePath, listener)
                client = c
                c.setKeepDeleted(Prefs.flag(app, "keepDeleted", true))
                if (c.isLoggedIn) {
                    _conn.value = Conn.Connecting
                    refreshMe()
                    refreshChatsNow()
                    ConnectionService.start(app)
                } else {
                    _conn.value = Conn.NeedsLink
                }
            } catch (t: Throwable) {
                Log.e(TAG, "engine start failed", t)
                _alert.value = "Couldn't start: ${t.message}"
                _conn.value = Conn.Error
            }
        }
        scope.launch(io) {
            for (x in refreshSignal) {
                refreshChatsNow()
                // Clear notifications for chats read elsewhere — only with a
                // list read *after* the change, never a stale one (that used
                // to wipe fresh notifications right after they appeared).
                if (syncNotifications) {
                    syncNotifications = false
                    Notifier.syncWithChats(app, _chats.value)
                }
                delay(150)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection

    /** Opens the connection (starts QR pairing when not linked). */
    fun connect() {
        val c = client ?: return
        scope.launch(io) {
            try {
                if (!c.isConnected) {
                    if (c.isLoggedIn) _conn.value = Conn.Connecting
                    c.connect()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "connect", t)
                if (!c.isLoggedIn) _pairError.value = t.message
            }
        }
    }

    fun disconnect() {
        scope.launch(io) { client?.disconnect() }
    }

    /** Restart pairing (new QR codes). */
    fun restartLinking() {
        _pairError.value = null
        _qr.value = null
        scope.launch(io) {
            client?.disconnect()
            delay(300)
            connect()
        }
    }

    suspend fun pairWithPhone(phone: String): Result<String> = withContext(io) {
        runCatching { client!!.pairWithPhone(phone) }
    }

    suspend fun logout() = withContext(io) {
        runCatching { client?.logout() }
        _chats.value = emptyList()
        _me.value = null
        _conn.value = Conn.NeedsLink
        ConnectionService.stop(app)
        Notifier.cancelAll(app)
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        // Locked chats lock again after you've been away for a while.
        ChatLock.onForeground(fg)
        DeliveryLog.add(if (fg) "app opened (connection: ${_conn.value.name.lowercase()})" else "app minimised")
        scope.launch(io) { client?.setActive(fg) }
        // Coming back: make sure the connection is really alive, so new
        // messages show at once instead of after a dead socket times out.
        if (fg && isLoggedIn) checkConnection()
    }

    /** True once the Go engine has been created (it starts in the background). */
    val ready: Boolean get() = client != null

    /**
     * Pings WhatsApp and reconnects if the connection died silently while
     * the phone slept. Blocks up to ~12 s; call from a background thread.
     * Returns "connected", "reconnecting", "connecting" or "".
     */
    fun keepAliveNow(timeoutSec: Int = 12): String {
        val c = client ?: return ""
        return runCatching { c.keepAlive(timeoutSec.toLong()) }.getOrDefault("")
    }

    /** [keepAliveNow] on a background thread, with a short timeout. */
    fun checkConnection() {
        scope.launch(Dispatchers.IO) { keepAliveNow(6) }
    }

    fun dismissAlert() {
        _alert.value = null
    }

    // -------------------------------------------------------------------------
    // Chats and messages

    fun refreshChats() {
        refreshSignal.trySend(Unit)
    }

    private fun refreshChatsNow() {
        val c = client ?: return
        _chats.value = Chat.list(c.chats)
        // Rings also need re-reading once the engine is up and as updates expire.
        refreshStatusRings()
    }

    private fun refreshMe() {
        val c = client ?: return
        runCatching {
            val o = JSONObject(c.me())
            if (o.has("jid")) _me.value = Me(o.optString("jid"), o.optString("phone"), o.optString("name"))
        }
    }

    suspend fun chat(jid: String): Chat? = withContext(io) {
        runCatching { Chat.from(JSONObject(client!!.getChat(jid))) }.getOrNull()
    }

    suspend fun messages(chat: String, before: Long = 0, limit: Int = 60): List<Message> = withContext(io) {
        val c = client ?: return@withContext emptyList()
        Message.list(c.getMessages(chat, before, limit.toLong()))
    }

    fun sendText(chat: String, text: String, quotedId: String?, mentions: List<String> = emptyList()): Job? {
        val c = client ?: return null
        return scope.launch(io) {
            try {
                val json = if (mentions.isEmpty()) c.sendText(chat, text, quotedId ?: "")
                else c.sendTextMentions(chat, text, quotedId ?: "", JSONArray(mentions).toString())
                val msg = Message.from(JSONObject(json))
                _events.emit(Event.NewMessage(msg, "", chat.endsWith("@g.us"), 0))
                refreshChats()
            } catch (t: Throwable) {
                Log.w(TAG, "send", t)
                _alert.value = "Couldn't send: ${t.message}"
            }
        }
    }

    fun retry(chat: String, id: String) = fire { it.retrySend(chat, id) }
    fun react(chat: String, id: String, emoji: String) = fire { it.sendReaction(chat, id, emoji) }
    fun deleteForEveryone(chat: String, id: String) = fire { it.deleteForEveryone(chat, id) }
    fun deleteForMe(chat: String, id: String) = fire { it.deleteForMe(chat, id) }
    fun edit(chat: String, id: String, text: String) = fire { it.editMessage(chat, id, text) }
    fun markRead(chat: String) = fire { it.markChatRead(chat, Prefs.readReceipts(app)) }
    fun typing(chat: String, on: Boolean) = fire { it.sendTyping(chat, on) }
    fun subscribePresence(chat: String) = fire { it.subscribePresence(chat) }
    fun setPinned(chat: String, on: Boolean) = fire { it.setPinned(chat, on) }
    fun setArchived(chat: String, on: Boolean) = fire { it.setArchived(chat, on) }
    fun setMutedUntil(chat: String, until: Long) = fire { it.setMutedUntil(chat, until) }
    fun markUnread(chat: String) = fire { it.markUnread(chat) }

    fun markAllRead() {
        _chats.value.filter { it.hasUnread }.forEach { markRead(it.jid) }
    }

    private fun fire(block: (Client) -> Unit) {
        val c = client ?: return
        scope.launch(io) {
            try {
                block(c)
            } catch (t: Throwable) {
                Log.w(TAG, "call failed", t)
                _alert.value = t.message
            }
        }
    }

    // -------------------------------------------------------------------------
    // Media, stickers and statuses

    /** Local path of a message's attachment, downloading it if needed. */
    suspend fun downloadMedia(chat: String, id: String): Result<String> = withContext(io) {
        runCatching { client!!.downloadMedia(chat, id) }
    }

    /**
     * Sends a photo/video/document/voice note described by [spec] (see
     * MediaPrep). Use [STATUS_CHAT] to post a status.
     */
    fun sendMedia(chat: String, spec: JSONObject): Job? {
        val c = client ?: return null
        return scope.launch(io) {
            try {
                val msg = Message.from(JSONObject(c.sendMedia(chat, spec.toString())))
                if (chat == STATUS_CHAT) {
                    _statusVersion.update { it + 1 }
                    refreshStatusRings()
                } else {
                    _events.emit(Event.NewMessage(msg, "", chat.endsWith("@g.us"), 0))
                    refreshChats()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "send media", t)
                _alert.value = "Couldn't send: ${t.message}"
            }
        }
    }

    /** A chat's photos/videos ("media"), documents ("docs") or link messages ("links"), newest first. */
    suspend fun chatMedia(chat: String, kind: String, before: Long = 0, limit: Int = 500): List<Message> = withContext(io) {
        Message.list(client?.getChatMedia(chat, kind, before, limit.toLong()) ?: "[]")
    }

    /** How many photos/videos, links and documents a chat has. */
    suspend fun chatMediaCounts(chat: String): Map<String, Int> = withContext(io) {
        runCatching {
            val o = JSONObject(client?.chatMediaCounts(chat) ?: "{}")
            mapOf("media" to o.optInt("media"), "links" to o.optInt("links"), "docs" to o.optInt("docs"))
        }.getOrDefault(emptyMap())
    }

    private fun sendAndShow(chat: String, what: String, block: (Client) -> String) {
        val c = client ?: return
        scope.launch(io) {
            try {
                val msg = Message.from(JSONObject(block(c)))
                _events.emit(Event.NewMessage(msg, "", chat.endsWith("@g.us"), 0))
                refreshChats()
            } catch (t: Throwable) {
                Log.w(TAG, "send $what", t)
                _alert.value = "Couldn't send: ${t.message}"
            }
        }
    }

    fun sendLocation(chat: String, lat: Double, lng: Double, name: String, address: String, quotedId: String?) =
        sendAndShow(chat, "location") { it.sendLocation(chat, lat, lng, name, address, quotedId ?: "") }

    fun sendContact(chat: String, name: String, vcard: String, quotedId: String?) =
        sendAndShow(chat, "contact") { it.sendContact(chat, name, vcard, quotedId ?: "") }

    fun sendPoll(chat: String, question: String, options: List<String>, multi: Boolean) =
        sendAndShow(chat, "poll") { it.sendPoll(chat, question, JSONArray(options).toString(), multi) }

    fun votePoll(chat: String, pollId: String, options: List<String>) =
        fire { it.votePoll(chat, pollId, JSONArray(options).toString()) }

    suspend fun stickers(): List<Sticker> = withContext(io) {
        Sticker.list(client?.getStickers() ?: "[]")
    }

    suspend fun downloadSticker(id: String): Result<String> = withContext(io) {
        runCatching { client!!.downloadSticker(id) }
    }

    /** Send your own sticker (a 512×512 WebP file made on this phone). */
    fun sendStickerFile(chat: String, path: String, quotedId: String?) {
        val c = client ?: return
        scope.launch(io) {
            try {
                val msg = Message.from(JSONObject(c.sendStickerFile(chat, path, quotedId ?: "")))
                _events.emit(Event.NewMessage(msg, "", chat.endsWith("@g.us"), 0))
                refreshChats()
            } catch (t: Throwable) {
                _alert.value = "Couldn't send the sticker: ${t.message}"
            }
        }
    }

    /** Reply to someone's status update in your chat with them. */
    suspend fun replyToStatus(statusId: String, text: String): Result<Unit> = withContext(io) {
        runCatching {
            client!!.replyToStatus(statusId, text)
            refreshChats()
        }
    }

    /** Who has seen one of your status updates (names and times). */
    suspend fun statusViewers(id: String): List<ReceiptEntry> =
        messageInfo(STATUS_CHAT, id)?.let { info -> (info.read + info.played).distinctBy { it.jid }.sortedByDescending { it.ts } } ?: emptyList()

    /** Who can see your last seen, online, photo, about… as WhatsApp has it. */
    suspend fun privacySettings(): Result<Map<String, String>> = withContext(io) {
        runCatching {
            val o = JSONObject(client!!.privacySettings())
            o.keys().asSequence().associateWith { o.optString(it) }
        }
    }

    fun setKeepDeleted(on: Boolean) {
        Prefs.setFlag(app, "keepDeleted", on)
        client?.setKeepDeleted(on)
    }

    /** Makes a group with these people; returns its chat id. */
    suspend fun createGroup(name: String, members: List<String>): Result<String> = withContext(io) {
        runCatching { client!!.createGroup(name, JSONArray(members).toString()) }.also { refreshChats() }
    }

    /** Status privacy: who sees your updates, and the people on each list. */
    suspend fun statusPrivacyLists(): Pair<String, Map<String, List<String>>>? = withContext(io) {
        runCatching {
            val o = JSONObject(client!!.statusPrivacy())
            val lists = mutableMapOf<String, List<String>>()
            o.optJSONObject("lists")?.let { l ->
                l.keys().forEach { k -> lists[k] = l.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty() }
            }
            o.optString("type", "contacts") to lists
        }.getOrNull()
    }

    suspend fun setStatusPrivacy(kind: String, people: List<String>): Result<Unit> = withContext(io) {
        runCatching { client!!.setStatusPrivacy(kind, JSONArray(people).toString()) }
    }

    suspend fun userAbout(jid: String): String = withContext(io) { runCatching { client!!.userAbout(jid) }.getOrDefault("") }

    suspend fun setDisappearing(chat: String, seconds: Int): Result<Unit> = withContext(io) {
        runCatching { client!!.setDisappearing(chat, seconds.toLong()) }.also { refreshChats() }
    }

    suspend fun setPrivacy(name: String, value: String): Result<Unit> = withContext(io) {
        runCatching { client!!.setPrivacy(name, value) }
    }

    fun sendSticker(chat: String, id: String, quotedId: String?) {
        val c = client ?: return
        scope.launch(io) {
            try {
                val msg = Message.from(JSONObject(c.sendSticker(chat, id, quotedId ?: "")))
                _events.emit(Event.NewMessage(msg, "", chat.endsWith("@g.us"), 0))
                refreshChats()
            } catch (t: Throwable) {
                _alert.value = "Couldn't send: ${t.message}"
            }
        }
    }

    suspend fun statuses(): List<StatusGroup> = withContext(io) {
        StatusGroup.list(client?.getStatuses() ?: "[]")
    }

    /** In secret mode the update is marked seen here only: the sender isn't told. */
    fun markStatusViewed(id: String) {
        fire { it.markStatusViewed(id, Prefs.readReceipts(app) && !Prefs.secretStatus(app)) }
        scope.launch {
            kotlinx.coroutines.delay(300)
            refreshStatusRings()
        }
    }

    // ---- your profile ----

    suspend fun setProfilePhoto(path: String): Result<Unit> = withContext(io) {
        runCatching { client!!.setProfilePhoto(path) }.also { if (it.isSuccess) _avatarVersion.update { v -> v + 1 } }
    }

    suspend fun setPushName(name: String): Result<Unit> = withContext(io) {
        runCatching { client!!.setPushName(name) }.also { if (it.isSuccess) refreshMe() }
    }

    suspend fun setAbout(text: String): Result<Unit> = withContext(io) {
        runCatching { client!!.setAbout(text) }
    }

    /** Your "About" text, or null when it can't be fetched right now. */
    suspend fun myAbout(): String? = withContext(io) {
        runCatching { JSONObject(client!!.myProfile()).let { if (it.has("about")) it.optString("about") else null } }.getOrNull()
    }

    /** Full-size profile photo path, or null. Blocks on network; call from IO. */
    fun fullAvatarPath(jid: String): String? {
        val c = client ?: return null
        return runCatching { c.getAvatarFull(jid) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Who your status updates go to: ("contacts" | "blacklist" | "whitelist", number of people on the list). */
    suspend fun statusPrivacy(): Result<Pair<String, Int>> = withContext(io) {
        runCatching { JSONObject(client!!.statusPrivacy()).let { it.optString("type", "contacts") to it.optInt("count") } }
    }

    fun markPlayed(chat: String, id: String) = fire { it.markPlayed(chat, id, Prefs.readReceipts(app)) }

    fun postTextStatus(text: String, background: Long, font: Int) {
        val c = client ?: return
        scope.launch(io) {
            try {
                c.postTextStatus(text, background, font.toLong())
                _statusVersion.update { it + 1 }
            } catch (t: Throwable) {
                _alert.value = "Couldn't post: ${t.message}"
            }
        }
    }

    fun deleteStatus(id: String) {
        val c = client ?: return
        scope.launch(io) {
            try {
                c.deleteForEveryone(STATUS_CHAT, id)
            } catch (t: Throwable) {
                _alert.value = "Couldn't delete: ${t.message}"
            }
            _statusVersion.update { it + 1 }
        }
    }

    suspend fun mediaCacheSize(): Long = withContext(io) { client?.mediaCacheSize() ?: 0L }

    suspend fun clearMediaCache() = withContext(io) { client?.clearMediaCache() }

    suspend fun contacts(): List<Contact> = withContext(io) {
        val c = client ?: return@withContext emptyList()
        runCatching {
            val a = JSONArray(c.contacts)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Contact(o.optString("jid"), o.optString("name"), o.optString("phone"))
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    suspend fun checkNumber(phone: String): Result<String> = withContext(io) {
        runCatching { client!!.checkNumber(phone) }
    }

    private fun logConn(c: Conn, why: String = "") {
        if (lastLoggedConn == c) return
        lastLoggedConn = c
        DeliveryLog.add(
            when (c) {
                Conn.Connected -> "connected"
                else -> "connection lost ($why), reconnecting"
            },
        )
    }

    // -------------------------------------------------------------------------
    // Chat and message actions

    suspend fun setBlocked(chat: String, block: Boolean): Result<Unit> = withContext(io) {
        runCatching { client!!.setBlocked(chat, block) }.also { refreshChats() }
    }

    suspend fun leaveGroup(chat: String): Result<Unit> = withContext(io) {
        runCatching { client!!.leaveGroup(chat) }
    }

    fun clearChat(chat: String) = fire { it.clearChat(chat) }

    fun deleteChat(chat: String) {
        Notifier.cancel(app, chat)
        fire { it.deleteChat(chat) }
    }

    /** Forwards messages to up to 5 chats; the copies show up like sent messages. */
    suspend fun forward(fromChat: String, ids: List<String>, targets: List<String>): Result<Int> = withContext(io) {
        runCatching {
            val rows = Message.list(client!!.forwardMessages(fromChat, JSONArray(ids).toString(), JSONArray(targets).toString()))
            rows.forEach { _events.emit(Event.NewMessage(it, "", it.chat.endsWith("@g.us"), 0)) }
            refreshChats()
            rows.size
        }
    }

    fun star(chat: String, id: String, star: Boolean) = fire { it.starMessage(chat, id, star) }

    fun keep(chat: String, id: String, keep: Boolean) = fire { it.keepMessage(chat, id, keep) }

    /** Pins for [seconds] (24 h / 7 days / 30 days), or unpins. */
    fun pin(chat: String, id: String, pin: Boolean, seconds: Int = 7 * 86_400) = fire { it.pinMessage(chat, id, pin, seconds.toLong()) }

    /** Starred messages, newest first ("" = all chats). */
    suspend fun starred(chat: String): List<Message> = withContext(io) {
        Message.list(client?.getStarred(chat, 500) ?: "[]")
    }

    suspend fun pinned(chat: String): List<Message> = withContext(io) {
        Message.list(client?.getPinned(chat) ?: "[]")
    }

    suspend fun messageInfo(chat: String, id: String): MessageInfo? = withContext(io) {
        runCatching { MessageInfo.from(JSONObject(client!!.messageInfo(chat, id))) }.getOrNull()
    }

    /** Messages in one chat matching [query] (text, captions, file names), newest first. */
    suspend fun searchChat(chat: String, query: String, kind: String, limit: Int = 300): List<Message> = withContext(io) {
        Message.list(client?.searchChat(chat, query, kind, limit.toLong()) ?: "[]")
    }

    /** Group name, description and members; refresh asks WhatsApp (else the saved copy). */
    suspend fun groupInfo(jid: String, refresh: Boolean): Result<GroupInfo> = withContext(io) {
        runCatching { GroupInfo.from(JSONObject(client!!.getGroupInfo(jid, refresh))) }
    }

    /** A profile photo already on this device (no network), or null. */
    fun cachedAvatarPath(jid: String): String? {
        val c = client ?: return null
        return runCatching { c.cachedAvatar(jid) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Local path of a profile photo, or null. Blocks on network; call from IO. */
    fun avatarPath(jid: String): String? {
        val c = client ?: return null
        return runCatching { c.getAvatar(jid) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** QR matrix rows ('1' = dark) for a pairing code. */
    fun qrModules(code: String): List<String> = runCatching { Wa.qrModules(code).split('\n') }.getOrDefault(emptyList())

    fun logTail(): String = client?.logTail() ?: ""
    /** Starts an album; null if it couldn't (the items then go one by one). */
    suspend fun sendAlbum(chat: String, images: Int, videos: Int): String? = withContext(io) {
        runCatching { client?.sendAlbum(chat, images.toLong(), videos.toLong()) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun setContactName(phone: String, name: String) {
        scope.launch(Dispatchers.IO) { runCatching { client?.setContactName(phone, name) } }
    }
    fun statusCheck(): String = client?.statusCheck() ?: "Not connected yet."
    fun requestStatusSync(force: Boolean) {
        scope.launch(Dispatchers.IO) { runCatching { client?.requestStatusSync(force) } }
    }

    fun engineVersion(): String = runCatching { Wa.version() }.getOrDefault("?")

    // -------------------------------------------------------------------------
    // Events from Go (arrive on Go threads)

    private val listener = object : Listener {
        override fun onEvent(kind: String?, payload: String?) {
            try {
                handle(kind ?: return, payload ?: "")
            } catch (t: Throwable) {
                Log.e(TAG, "event $kind", t)
            }
        }
    }

    private fun handle(kind: String, payload: String) {
        when (kind) {
            "qr" -> {
                _qr.value = JSONObject(payload).optString("code")
                _pairError.value = null
                _conn.value = Conn.NeedsLink
            }
            "qr_timeout" -> {
                _qr.value = null
                _pairError.value = "The QR code expired."
            }
            "pair_error" -> _pairError.value = payload.ifBlank { "Linking failed." }
            "pair_success" -> {
                _qr.value = null
                _pairError.value = null
                _conn.value = Conn.Connecting
                _history.value = 0
                ConnectionService.start(app)
            }
            "connected" -> {
                logConn(Conn.Connected)
                _conn.value = Conn.Connected
                refreshMe()
                refreshChats()
                if (foreground) client?.setActive(true)
            }
            "connecting", "disconnected" -> if (isLoggedIn) {
                logConn(Conn.Connecting, kind)
                _conn.value = Conn.Connecting
            }
            "logged_out" -> {
                _chats.value = emptyList()
                _me.value = null
                _conn.value = Conn.NeedsLink
                _alert.value = "This device was unlinked from WhatsApp on your phone."
                ConnectionService.stop(app)
                Notifier.cancelAll(app)
            }
            "stream_replaced" -> _alert.value = "Another session took over this link. Reopen the app to reconnect."
            "banned" -> _alert.value = "WhatsApp has temporarily restricted this number ($payload)."
            "outdated" -> _alert.value = "WhatsApp says this client version is outdated — an app update is needed."
            "message" -> {
                val o = JSONObject(payload)
                val msg = Message.from(o.getJSONObject("message"))
                val ev = Event.NewMessage(msg, o.optString("chatName"), o.optBoolean("isGroup"), o.optLong("mutedUntil"))
                _events.tryEmit(ev)
                _typing.update { it - msg.chat }
                refreshChats()
                val visible = foreground && openChat == msg.chat
                if (!foreground && !msg.fromMe) DeliveryLog.add("message arrived in background (${DeliveryLog.tag(msg.chat)})")
                // Keep the CPU awake long enough to store and show it.
                if (!foreground) WakeHold.brief(app)
                if (visible && !msg.fromMe) markRead(msg.chat)
                if (!msg.fromMe && !visible) Notifier.onMessage(app, ev)
                if (msg.fromMe) Notifier.cancel(app, msg.chat)
            }
            "message_update" -> {
                val msg = Message.from(JSONObject(payload))
                if (msg.chat == STATUS_CHAT) _statusVersion.update { it + 1 } else _events.tryEmit(Event.Updated(msg))
            }
            "status" -> {
                _statusVersion.update { it + 1 }
                refreshStatusRings()
                val o = runCatching { JSONObject(payload) }.getOrNull()
                val sender = o?.optString("sender").orEmpty()
                if (sender.isNotEmpty() && sender != _me.value?.jid) {
                    val name = _chats.value.firstOrNull { it.jid == sender }?.name ?: sender.substringBefore('@')
                    Notifier.onStatusPosted(app, name)
                }
            }
            "reaction" -> {
                val o = JSONObject(payload)
                if (!(foreground && openChat == o.optString("chat"))) Notifier.onReaction(app, o)
            }
            "call" -> Notifier.onIncomingCall(app, JSONObject(payload))
            "call_end" -> Notifier.cancelCall(app, JSONObject(payload).optString("id"))
            "chat_cleared" -> _events.tryEmit(Event.Cleared(JSONObject(payload).optString("chat")))
            "pins_changed" -> _events.tryEmit(Event.PinsChanged(JSONObject(payload).optString("chat")))
            "message_removed" -> {
                val o = JSONObject(payload)
                _events.tryEmit(Event.Removed(o.optString("chat"), o.optString("id")))
            }
            "receipt" -> {
                val o = JSONObject(payload)
                val ids = o.optJSONArray("ids") ?: JSONArray()
                _events.tryEmit(Event.Receipt(o.optString("chat"), List(ids.length()) { ids.getString(it) }, o.optInt("status")))
            }
            "chats_changed" -> {
                syncNotifications = true
                refreshChats()
            }
            "history" -> {
                refreshStatusRings()
                val p = JSONObject(payload).optInt("progress")
                _history.value = p
                if (p >= 100) scope.launch { delay(1500); _history.value = null }
                refreshChats()
            }
            "typing" -> {
                val o = JSONObject(payload)
                val chat = o.optString("chat")
                if (o.optString("state") == "composing") {
                    val t = Typing(chat, o.optString("sender"), o.optString("media") == "audio", System.currentTimeMillis())
                    _typing.update { it + (chat to t) }
                    scope.launch {
                        delay(8000)
                        _typing.update { m -> if (m[chat]?.at == t.at) m - chat else m }
                    }
                } else {
                    _typing.update { it - chat }
                }
            }
            "presence" -> {
                val o = JSONObject(payload)
                _presence.update { it + (o.optString("jid") to Presence(o.optBoolean("online"), o.optLong("lastSeen"))) }
            }
            "avatar" -> _avatarVersion.update { it + 1 }
        }
    }
}
