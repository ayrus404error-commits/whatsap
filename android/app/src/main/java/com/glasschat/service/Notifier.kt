package com.glasschat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.glasschat.R
import com.glasschat.engine.Chat
import com.glasschat.engine.ChatLists
import com.glasschat.engine.Engine
import com.glasschat.engine.previewFor
import com.glasschat.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object Notifier {
    const val SERVICE_ID = 1
    // v2: these channels show on the lock screen (a channel's lock-screen
    // setting can't be changed after it's created, so they're new ones).
    const val CH_MESSAGES = "messages_v2"
    const val CH_GROUPS = "groups_v2"
    // A visible (low, silent) channel: phones treat apps with a visible
    // ongoing notification as in use and are less likely to stop them.
    private const val CH_SERVICE = "keep_connected"
    private const val CH_CALLS = "calls"
    private const val CH_REACTIONS = "reactions"
    const val KEY_REPLY = "reply_text"
    const val EXTRA_CHAT = "chat"
    private const val GROUP_KEY = "glasschat.messages"

    private data class Line(val text: String, val sender: String, val senderKey: String, val ts: Long, val fromMe: Boolean)

    private val history = ConcurrentHashMap<String, MutableList<Line>>()
    /** Newest message time shown per chat, so a stale chat list can't clear it. */
    private val notifiedTs = ConcurrentHashMap<String, Long>()
    private val titles = ConcurrentHashMap<String, Pair<String, Boolean>>()
    /** Latest voice message per chat, for the notification's Play button. */
    private val lastVoice = ConcurrentHashMap<String, String>()
    const val EXTRA_MSG = "msg"
    const val CH_SERVICE_QUIET = "service_quiet"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New messages from people"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                },
                NotificationChannel(CH_GROUPS, "Group messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New messages in groups"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                },
                NotificationChannel(CH_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shows when someone calls your WhatsApp number, so you can answer on your phone"
                },
                NotificationChannel(CH_REACTIONS, "Reactions and status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reactions to your messages, and new status updates if you turn them on"
                },
                NotificationChannel(CH_SERVICE_QUIET, "Stay connected (hidden)", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Used when you hide the \"Connected\" notification: it stays out of the status bar and lock screen"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                },
                NotificationChannel(CH_SERVICE, "Stay connected", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps WhatsApp connected in the background so messages arrive instantly"
                    setShowBadge(false)
                },
            ),
        )
        // Channels from earlier builds.
        runCatching { nm.deleteNotificationChannel("connection") }
        runCatching { nm.deleteNotificationChannel("messages") }
        runCatching { nm.deleteNotificationChannel("groups") }
    }

    private fun canPost(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun serviceNotification(ctx: Context, conn: Engine.Conn, history: Int? = null): Notification {
        val text = when {
            history != null && history < 100 -> "Syncing chats from your phone… $history%"
            conn == Engine.Conn.Connected -> "Connected to WhatsApp"
            conn == Engine.Conn.Connecting -> "Connecting…"
            conn == Engine.Conn.NeedsLink -> "Not linked"
            else -> "Waiting for network"
        }
        // Hidden: a minimised, silent notification (Android requires one for the
        // background connection, but this keeps it out of sight).
        val quiet = Prefs.flag(ctx, "hide_service_notif", false)
        return NotificationCompat.Builder(ctx, if (quiet) CH_SERVICE_QUIET else CH_SERVICE)
            .setSmallIcon(NotifIcon.small(ctx))
            .setLargeIcon(NotifIcon.large(ctx))
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .setVisibility(if (quiet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent(ctx, null))
            .build()
    }

    fun updateService(ctx: Context, conn: Engine.Conn, history: Int?) {
        if (!canPost(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(SERVICE_ID, serviceNotification(ctx, conn, history)) }
    }

    fun onMessage(ctx: Context, ev: Engine.Event.NewMessage) {
        val tag = DeliveryLog.tag(ev.msg.chat)
        if (ev.mutedUntil < 0 || ev.mutedUntil > System.currentTimeMillis()) {
            DeliveryLog.add("no notification: $tag is muted")
            return
        }
        if (!canPost(ctx)) {
            DeliveryLog.add("no notification: notifications are turned off for the app")
            return
        }
        if (ev.isGroup && !Prefs.notifyGroups(ctx)) {
            DeliveryLog.add("no notification: group notifications are off ($tag)")
            return
        }
        if (!ev.isGroup && !Prefs.notifyPrivate(ctx)) {
            DeliveryLog.add("no notification: private chat notifications are off ($tag)")
            return
        }
        val m = ev.msg
        notifiedTs.merge(m.chat, m.ts) { a, b -> maxOf(a, b) }
        if (m.type == "voice") lastVoice[m.chat] = m.id
        // Locked chats never show who wrote or what.
        val locked = ChatLists.isLocked(m.chat)
        val showPreview = if (ev.isGroup) Prefs.groupPreviews(ctx) else Prefs.previews(ctx)
        val text = if (showPreview && !locked) previewFor(m.type, m.text, m.revoked) else "New message"
        val lines = history.getOrPut(m.chat) { mutableListOf() }
        synchronized(lines) {
            lines.add(Line(text, if (locked) "Locked chat" else m.senderName, if (locked) "locked" else m.sender, m.ts, false))
            while (lines.size > 7) lines.removeAt(0)
        }
        titles[m.chat] = (if (locked) "Locked chat" else ev.chatName) to (ev.isGroup && !locked)
        // Not on Engine.io: that pool can be busy loading profile photos.
        Engine.scope.launch(Dispatchers.IO) {
            // Their photo for the notification: from the phone if we have it,
            // otherwise fetch it (but never hold the notification for long).
            if (!locked && Engine.cachedAvatarPath(m.chat) == null) {
                kotlinx.coroutines.withTimeoutOrNull(4_000) {
                    kotlinx.coroutines.runInterruptible { Engine.avatarPath(m.chat) }
                }
            }
            post(ctx, m.chat, alert = true)
        }
    }

    /** Redraws a chat's notification quietly (e.g. Play / Pause). */
    fun refresh(ctx: Context, chat: String) {
        if (history[chat] != null && canPost(ctx)) post(ctx, chat, alert = false)
    }

    private fun post(ctx: Context, chat: String, alert: Boolean) {
        val lines = history[chat] ?: return
        val (title, isGroup) = titles[chat] ?: (chat to false)
        // Only a photo already on the phone: never wait on the network here.
        val locked = ChatLists.isLocked(chat)
        val avatar = if (locked) null else Engine.cachedAvatarPath(chat)?.let { circle(BitmapFactory.decodeFile(it)) }
        val me = Person.Builder().setName("You").setKey("me").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (isGroup) {
            style.setConversationTitle(title)
            style.setGroupConversation(true)
        }
        synchronized(lines) {
            for (l in lines) {
                val person = if (l.fromMe) null else Person.Builder()
                    .setName(if (isGroup) l.sender else title)
                    .setKey(l.senderKey)
                    .apply { if (!isGroup && avatar != null) setIcon(IconCompat.createWithBitmap(avatar)) }
                    .build()
                style.addMessage(l.text, l.ts, person)
            }
        }
        val id = chat.hashCode()
        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "Reply",
            PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReplyReceiver::class.java).putExtra(EXTRA_CHAT, chat),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(
            RemoteInput.Builder(KEY_REPLY)
                .setLabel("Reply")
                .apply {
                    // Smart replies: one-tap answers under the notification.
                    if (Prefs.smartReplies(ctx) && !locked) {
                        val last = synchronized(lines) { lines.lastOrNull { !it.fromMe }?.text }
                        setChoices(smartReplies(last).toTypedArray())
                    }
                }
                .build(),
        )
            .setAllowGeneratedReplies(Prefs.smartReplies(ctx))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val read = NotificationCompat.Action.Builder(
            R.drawable.ic_check, "Mark as read",
            PendingIntent.getBroadcast(
                ctx, id + 1, Intent(ctx, MarkReadReceiver::class.java).putExtra(EXTRA_CHAT, chat),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val n = NotificationCompat.Builder(ctx, if (isGroup) CH_GROUPS else CH_MESSAGES)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setStyle(style)
            .setLargeIcon(avatar)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Shown on the lock screen; locked chats only as "New message".
            .setVisibility(if (locked) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, if (isGroup) CH_GROUPS else CH_MESSAGES)
                    .setSmallIcon(NotifIcon.small(ctx))
                    .setContentTitle(ctx.getString(R.string.app_name))
                    .setContentText("New message")
                    .build(),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setGroup(GROUP_KEY)
            .setShortcutId(chat)
            .setAllowSystemGeneratedContextualActions(Prefs.smartReplies(ctx))
            .setBadgeIconType(if (Prefs.showBadge(ctx)) NotificationCompat.BADGE_ICON_SMALL else NotificationCompat.BADGE_ICON_NONE)
            .setNumber(if (Prefs.showBadge(ctx)) badgeCount(ctx) else 0)
            .setContentIntent(openIntent(ctx, chat))
            .apply {
                if (!locked) {
                    // Voice message: play it right from the notification.
                    lastVoice[chat]?.let { voiceId ->
                        val ps = com.glasschat.media.VoicePlayer.state.value
                        val playing = ps.playing && ps.key == com.glasschat.media.MediaState.key(chat, voiceId)
                        addAction(
                            NotificationCompat.Action.Builder(
                                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                                if (playing) "Pause" else "Play voice",
                                PendingIntent.getBroadcast(
                                    ctx, id + 2,
                                    Intent(ctx, VoicePlayReceiver::class.java).putExtra(EXTRA_CHAT, chat).putExtra(EXTRA_MSG, voiceId),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                ),
                            ).setShowsUserInterface(false).build(),
                        )
                    }
                    addAction(reply)
                    addAction(read)
                }
            }
            .build()
        runCatching {
            val nm = NotificationManagerCompat.from(ctx)
            nm.notify(id, n)
            if (alert && !Engine.foreground) DeliveryLog.add("notification shown (${DeliveryLog.tag(chat)})")
            nm.notify(
                GROUP_KEY.hashCode(),
                NotificationCompat.Builder(ctx, CH_MESSAGES)
                    .setSmallIcon(NotifIcon.small(ctx))
                    .setColor(0xFF1DAA61.toInt())
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setContentIntent(openIntent(ctx, null))
                    .build(),
            )
        }
    }

    /** Suggested one-tap replies for the newest message. */
    private fun smartReplies(text: String?): List<String> {
        val t = text?.trim()?.lowercase() ?: ""
        return when {
            t.isEmpty() -> listOf("Ok", "👍", "Will call you")
            Regex("\\b(thanks|thank you|thx|dhanyavad|shukriya)\\b").containsMatchIn(t) -> listOf("You're welcome", "👍", "😊")
            Regex("^(hi+|hey+|hello+|hii+|bhai|bro|good (morning|night|evening))\\b").containsMatchIn(t) -> listOf("Hi!", "Hey 👋", "Haan bolo")
            t.endsWith("?") || Regex("\\b(kya|kab|kahan|kaise|where|when|what|why|how|can you|are you)\\b").containsMatchIn(t) ->
                listOf("Yes", "No", "I'll check and tell you")
            Regex("\\b(call|phone)\\b").containsMatchIn(t) -> listOf("Calling you now", "Busy, will call later", "Ok")
            else -> listOf("Ok", "👍", "Will reply soon")
        }
    }

    /** The number on the app icon badge, as set in Notifications. */
    private fun badgeCount(ctx: Context): Int {
        val chats = Engine.chats.value.filter { !it.archived && (Prefs.badgeIncludeMuted(ctx) || !it.isMuted) }
        return if (Prefs.badgeCountMessages(ctx)) chats.sumOf { maxOf(it.unread, if (it.hasUnread) 1 else 0) } else chats.count { it.hasUnread }
    }

    /** Someone reacted to your message. */
    fun onReaction(ctx: Context, o: JSONObject) {
        if (!canPost(ctx) || !Prefs.notifyReactions(ctx)) return
        val chat = o.optString("chat")
        if (ChatLists.isLocked(chat)) return
        val c = Engine.chats.value.firstOrNull { it.jid == chat }
        if (c != null && c.isMuted) return
        val who = if (Prefs.reactionSender(ctx)) o.optString("sender").removePrefix("~ ").ifBlank { c?.name ?: "Someone" } else "Someone"
        val what = o.optString("text").take(60).ifBlank { "your message" }
        val n = NotificationCompat.Builder(ctx, CH_REACTIONS)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setContentTitle(if (c?.isGroup == true) "$who in ${c.name}" else who)
            .setContentText("Reacted ${o.optString("emoji")} to: $what")
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openIntent(ctx, chat))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify("react:$chat:${o.optString("id")}".hashCode(), n) }
    }

    /** A contact posted a status update (only when turned on in Notifications). */
    fun onStatusPosted(ctx: Context, name: String) {
        if (!canPost(ctx) || !Prefs.notifyStatus(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_REACTIONS)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setContentTitle(name)
            .setContentText("Posted a status update")
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify("status:$name".hashCode(), n) }
    }

    /** A call is ringing on WhatsApp: linked devices can't answer, so point to the phone. */
    fun onIncomingCall(ctx: Context, o: JSONObject) {
        if (!canPost(ctx)) return
        val id = o.optString("id")
        val name = o.optString("name").ifBlank { "Someone" }
        val kind = (if (o.optBoolean("group")) "Group " else "") + (if (o.optBoolean("video")) "video call" else "voice call")
        val launch = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        val content = if (launch != null) {
            PendingIntent.getActivity(
                ctx, callNotificationId(id), launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            openIntent(ctx, o.optString("from").ifBlank { null })
        }
        val n = NotificationCompat.Builder(ctx, CH_CALLS)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setContentTitle(name)
            .setContentText("Incoming ${kind.replaceFirstChar { it.lowercase() }} · answer on your phone")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(content)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(callNotificationId(id), n) }
    }

    fun cancelCall(ctx: Context, id: String) {
        NotificationManagerCompat.from(ctx).cancel(callNotificationId(id))
    }

    private fun callNotificationId(id: String) = "call:$id".hashCode()

    /** After an inline reply, show it in the notification instead of alerting again. */
    fun addOwnReply(ctx: Context, chat: String, text: String) {
        val lines = history[chat] ?: return
        synchronized(lines) { lines.add(Line(text, "You", "me", System.currentTimeMillis(), true)) }
        Engine.scope.launch(Dispatchers.IO) { post(ctx, chat, alert = false) }
    }

    fun cancel(ctx: Context, chat: String) {
        history.remove(chat)
        notifiedTs.remove(chat)
        NotificationManagerCompat.from(ctx).cancel(chat.hashCode())
        if (history.isEmpty()) NotificationManagerCompat.from(ctx).cancel(GROUP_KEY.hashCode())
    }

    fun cancelAll(ctx: Context) {
        notifiedTs.clear()
        val nm = NotificationManagerCompat.from(ctx)
        history.keys.toList().forEach { nm.cancel(it.hashCode()) }
        history.clear()
        nm.cancel(GROUP_KEY.hashCode())
    }

    /**
     * Clear notifications for chats that were read elsewhere (e.g. on the
     * phone). Only when the list already includes the newest notified
     * message, so a list read a moment too early can't clear a fresh one.
     */
    fun syncWithChats(ctx: Context, chats: List<Chat>) {
        val byJid = chats.associateBy { it.jid }
        for (jid in history.keys.toList()) {
            val c = byJid[jid] ?: continue
            val shown = notifiedTs[jid] ?: 0L
            if (c.unread == 0 && !c.hasUnread && c.lastTs >= shown) {
                if (!Engine.foreground) DeliveryLog.add("notification cleared: ${DeliveryLog.tag(jid)} was read on the phone")
                cancel(ctx, jid)
            }
        }
    }

    /** A simple one-off notification (test results and the like). */
    fun plain(ctx: Context, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    /** A sample notification, to check this phone shows them. */
    fun test(ctx: Context) {
        if (!canPost(ctx)) {
            DeliveryLog.add("test notification not shown: notifications are turned off for the app")
            return
        }
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(NotifIcon.small(ctx))
            .setColor(0xFF1DAA61.toInt())
            .setContentTitle("Test notification")
            .setContentText("Notifications work on this phone.")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4242, n) }
        DeliveryLog.add("test notification shown")
    }

    fun openIntent(ctx: Context, chat: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (chat != null) putExtra(EXTRA_CHAT, chat)
            action = "open:" + (chat ?: "")
        }
        return PendingIntent.getActivity(ctx, chat?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun circle(src: Bitmap?): Bitmap? {
        src ?: return null
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
        val left = (src.width - size) / 2
        val top = (src.height - size) / 2
        c.drawBitmap(src, Rect(left, top, left + size, top + size), Rect(0, 0, size, size), paint)
        return out
    }
}
