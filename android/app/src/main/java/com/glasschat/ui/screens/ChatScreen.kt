package com.glasschat.ui.screens

import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Groups
import com.glasschat.ui.glass.menuGlass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.CompositionLocalProvider
import com.glasschat.media.PickedContact
import com.glasschat.media.Contacts
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import com.glasschat.engine.Sticker
import com.glasschat.media.CallHandoff
import com.glasschat.media.MediaFiles
import com.glasschat.media.MediaPrep
import com.glasschat.media.MediaState
import com.glasschat.media.PendingMedia
import com.glasschat.media.VoicePlayer
import com.glasschat.media.VoiceRecorder
import com.glasschat.media.rememberImage
import com.glasschat.media.rememberMediaPickers
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.glass.liquidGlass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Chat
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.engine.previewFor
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.ActionSheet
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.SheetAction
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.Wallpaper
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCapsule
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.GlassPill
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import com.glasschat.ui.theme.senderColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private sealed interface ChatItem {
    val key: String

    data class Msg(val spec: BubbleSpec) : ChatItem { override val key = "m:" + spec.msg.id }
    data class Day(val ts: Long) : ChatItem { override val key = "d:" + Fmt.dayHeader(ts) + ts / 86_400_000L }
    data class Unread(val count: Int) : ChatItem { override val key = "unread" }
    data object Typing : ChatItem { override val key = "typing" }
    data object Notice : ChatItem { override val key = "notice" }
}

private fun runBreak(a: Message, b: Message): Boolean =
    a.fromMe != b.fromMe || a.sender != b.sender || !Fmt.sameDay(a.ts, b.ts) || kotlin.math.abs(b.ts - a.ts) > 5 * 60_000L

private fun albumable(m: Message) =
    !m.revoked && (m.type == "image" || m.type == "video") &&
        m.extra?.optBoolean("viewOnce") != true && m.extra?.optBoolean("round") != true && m.quotedId.isEmpty()

/**
 * Photos/videos that belong together: the same WhatsApp album, or (for
 * older messages without one) four or more sent in a row by the same
 * person within a minute, which WhatsApp also shows as a grid.
 * Returns, for each index, the first index of its group.
 */
private fun albumStarts(msgs: List<Message>): IntArray {
    val start = IntArray(msgs.size) { it }
    var i = 0
    while (i < msgs.size) {
        val m = msgs[i]
        if (!albumable(m)) { i++; continue }
        val album = m.extra?.optString("album").orEmpty()
        var j = i + 1
        while (j < msgs.size) {
            val n = msgs[j]
            if (!albumable(n) || n.sender != m.sender || n.fromMe != m.fromMe) break
            val nAlbum = n.extra?.optString("album").orEmpty()
            val same = if (album.isNotEmpty()) nAlbum == album
            else nAlbum.isEmpty() && kotlin.math.abs(n.ts - msgs[j - 1].ts) <= 60_000L && n.text.isBlank()
            if (!same) break
            j++
        }
        val size = j - i
        if ((album.isNotEmpty() && size >= 2) || (album.isEmpty() && size >= 4)) {
            for (k in i until j) start[k] = i
        }
        i = j
    }
    return start
}

private fun buildItems(msgs: List<Message>, isGroup: Boolean, unreadAnchor: String?, unreadCount: Int, typing: Boolean, reachedStart: Boolean): List<ChatItem> {
    val out = ArrayList<ChatItem>(msgs.size + 16)
    if (typing) out.add(ChatItem.Typing)
    val starts = albumStarts(msgs)
    var i = msgs.size - 1
    while (i >= 0) {
        val s = starts[i]
        val m = msgs[i]
        val older = msgs.getOrNull(s - 1)
        val newer = msgs.getOrNull(i + 1)
        val head = msgs[s]
        val first = older == null || runBreak(older, head)
        val last = newer == null || runBreak(m, newer)
        val album = if (s < i) msgs.subList(s, i + 1).toList() else emptyList()
        out.add(ChatItem.Msg(BubbleSpec(m, first, last, isGroup && !m.fromMe && first, album)))
        if (unreadAnchor != null && (s..i).any { msgs[it].id == unreadAnchor }) out.add(ChatItem.Unread(unreadCount))
        if (older == null || !Fmt.sameDay(older.ts, head.ts)) out.add(ChatItem.Day(head.ts))
        i = s - 1
    }
    if (reachedStart) out.add(ChatItem.Notice)
    return out
}

@Composable
fun ChatScreen(jid: String, nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val typingMap by Engine.typing.collectAsState()
    val presenceMap by Engine.presence.collectAsState()

    var chat by remember { mutableStateOf(Engine.chats.value.find { it.jid == jid }) }
    LaunchedEffect(jid) { Engine.chat(jid)?.let { c -> if (chat == null || c.name.isNotBlank()) chat = chat?.copy(name = c.name, participants = c.participants) ?: c } }
    LaunchedEffect(chats) { chats.find { it.jid == jid }?.let { chat = it } }
    val isGroup = chat?.isGroup ?: jid.endsWith("@g.us")

    val messages = remember { mutableStateListOf<Message>() }
    var loading by remember { mutableStateOf(false) }
    var reachedStart by remember { mutableStateOf(false) }
    val initialUnread = remember { chat?.unread ?: 0 }
    var unreadAnchor by remember { mutableStateOf<String?>(null) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var menuFor by remember { mutableStateOf<Message?>(null) }
    var deleteFor by remember { mutableStateOf<Message?>(null) }
    // Pins, multi-select and the long-press extras
    var pinned by remember { mutableStateOf<List<Message>>(emptyList()) }
    var pinIndex by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    var forwardIds by remember { mutableStateOf<List<String>?>(null) }
    var infoFor by remember { mutableStateOf<Message?>(null) }
    var emojiFor by remember { mutableStateOf<Message?>(null) }
    var pinFor by remember { mutableStateOf<Message?>(null) }
    var moreFor by remember { mutableStateOf<Message?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    var unblockAsk by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    // Text shared from another app lands here, ready to send.
    LaunchedEffect(jid) {
        com.glasschat.ui.Drafts.pending.remove(jid)?.let { t -> if (t.isNotBlank()) input = TextFieldValue(t, TextRange(t.length)) }
            ?: com.glasschat.ui.ChatDrafts.get(ctx, jid).takeIf { it.isNotBlank() }?.let { t -> input = TextFieldValue(t, TextRange(t.length)) }
    }
    // Keep what you typed as a draft (not while editing a sent message).
    LaunchedEffect(jid) {
        androidx.compose.runtime.snapshotFlow { if (editing == null) input.text else null }
            .collect { t -> if (t != null) { kotlinx.coroutines.delay(400); com.glasschat.ui.ChatDrafts.set(ctx, jid, t) } }
    }
    val draftNow by androidx.compose.runtime.rememberUpdatedState(if (editing == null) input.text else null)
    androidx.compose.runtime.DisposableEffect(jid) {
        onDispose { draftNow?.let { com.glasschat.ui.ChatDrafts.set(ctx, jid, it) } }
    }
    var inputHeight by remember { mutableIntStateOf(0) }
    var newBelow by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    // Media, voice notes, stickers and calls
    var viewer by remember { mutableStateOf<Message?>(null) }
    var attachSheet by remember { mutableStateOf(false) }
    var locationSheet by remember { mutableStateOf(false) }
    var pollComposer by remember { mutableStateOf(false) }
    var pendingContact by remember { mutableStateOf<PickedContact?>(null) }
    var contactFor by remember { mutableStateOf<Message?>(null) }
    var callSheet by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<PendingMedia>?>(null) }
    var camera by remember { mutableStateOf(false) }
    var instantVideo by remember { mutableStateOf(false) }
    var addDismissed by rememberSaveable(jid) { mutableStateOf(false) }
    // @mentions in groups: the members, and who's been picked into the text.
    var members by remember { mutableStateOf<List<com.glasschat.engine.GroupMember>>(emptyList()) }
    val picked = remember { mutableStateListOf<Pair<String, String>>() } // "@Label" to jid
    // Scheduled messages ("send later").
    var scheduleText by remember { mutableStateOf(false) }
    var scheduleMedia by remember { mutableStateOf<Pair<String, List<PendingMedia>>?>(null) }
    var showScheduled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { com.glasschat.engine.Scheduled.load(ctx) }
    LaunchedEffect(jid) {
        if (jid.endsWith("@g.us")) Engine.groupInfo(jid, refresh = false).onSuccess { members = it.members }
    }
    val allScheduled by com.glasschat.engine.Scheduled.items.collectAsState()
    val scheduledHere = allScheduled.count { it.chat == jid }
    var pendingDoc by remember { mutableStateOf<Uri?>(null) }
    var stickerPanel by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recordCancel by remember { mutableStateOf(false) }
    var recordMs by remember { mutableLongStateOf(0L) }
    val cancelPx = with(density) { 110.dp.toPx() }
    val pickers = rememberMediaPickers(onMedia = { preview = it }, onDocument = { pendingDoc = it })
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val picked = Contacts.fromPhoneUri(ctx, uri)
        if (picked != null) pendingContact = picked else toast = "Couldn't read that contact"
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val quoted = replyTo?.id
            replyTo = null
            scope.launch {
                if (!MediaPrep.sendAudio(ctx, jid, uri, quoted)) toast = "That audio file couldn't be read"
                listState.animateScrollToItem(0)
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast = if (granted) "Hold the microphone to record" else "Allow the microphone to record voice messages"
    }

    val typing = typingMap[jid]
    val showTyping = typing != null && !isGroup
    val items by remember(isGroup, showTyping) {
        derivedStateOf { buildItems(messages.toList(), isGroup, unreadAnchor, initialUnread, showTyping, reachedStart) }
    }
    // The typing bubble is added below the newest message; the list keeps its
    // place by item, so it would appear hidden behind the input bar. When
    // you're at the bottom, bring it into view.
    LaunchedEffect(showTyping) {
        if (showTyping && listState.firstVisibleItemIndex <= 1) {
            kotlinx.coroutines.delay(30)
            listState.animateScrollToItem(0)
        }
    }

    // Initial load
    LaunchedEffect(jid) {
        val list = Engine.messages(jid, 0, maxOf(80, initialUnread + 20))
        messages.clear()
        messages.addAll(list)
        if (list.size < 60) reachedStart = true
        if (initialUnread > 0) unreadAnchor = list.filter { !it.fromMe }.takeLast(initialUnread).firstOrNull()?.id
        Engine.markRead(jid)
        Engine.subscribePresence(jid)
        pinned = Engine.pinned(jid)
        if (unreadAnchor != null) {
            val idx = items.indexOfFirst { it is ChatItem.Unread }
            if (idx > 3) listState.scrollToItem(idx, -with(density) { 140.dp.roundToPx() })
        }
    }
    DisposableEffect(jid) {
        Engine.openChat = jid
        onDispose {
            if (Engine.openChat == jid) Engine.openChat = null
            Engine.typing(jid, false)
            recorder.stop(keep = false)
            VoicePlayer.stop()
        }
    }
    // Live updates
    LaunchedEffect(jid) {
        Engine.events.collect { ev ->
            when (ev) {
                is Engine.Event.NewMessage -> if (ev.msg.chat == jid) {
                    val atBottom = listState.firstVisibleItemIndex <= 1
                    upsert(messages, ev.msg)
                    if (atBottom || ev.msg.fromMe) {
                        scope.launch { listState.animateScrollToItem(0) }
                    } else {
                        newBelow++
                    }
                }
                is Engine.Event.Updated -> if (ev.msg.chat == jid) upsert(messages, ev.msg)
                is Engine.Event.Removed -> if (ev.chat == jid) messages.removeAll { it.id == ev.id }
                is Engine.Event.Receipt -> if (ev.chat == jid) {
                    val ids = ev.ids.toSet()
                    for (i in messages.indices) {
                        val m = messages[i]
                        if (m.id in ids && m.status < ev.status && m.status >= 0) messages[i] = m.copy(status = ev.status)
                    }
                }
                is Engine.Event.Cleared -> if (ev.chat == jid) {
                    // Starred messages survive a clear; reload what's left.
                    messages.clear()
                    messages.addAll(Engine.messages(jid, 0, 80))
                    pinned = Engine.pinned(jid)
                }
                is Engine.Event.PinsChanged -> if (ev.chat == jid) pinned = Engine.pinned(jid)
            }
        }
    }
    // Older pages
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (!loading && !reachedStart && messages.isNotEmpty() && last >= items.size - 10) {
                    loading = true
                    val older = Engine.messages(jid, messages.first().ts, 80)
                    if (older.size < 80) reachedStart = true
                    val known = messages.map { it.id }.toHashSet()
                    messages.addAll(0, older.filter { it.id !in known })
                    loading = false
                }
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { if (it <= 1) newBelow = 0 }
    }
    // Typing indicator to the other side
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var lastTypingSent by remember { mutableStateOf(0L) }
    fun onTyped() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent > 4000) {
            Engine.typing(jid, true)
            lastTypingSent = now
        }
        typingJob?.cancel()
        typingJob = scope.launch {
            delay(3500)
            Engine.typing(jid, false)
            lastTypingSent = 0
        }
    }

    fun send() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        val e = editing
        if (e != null) {
            Engine.edit(jid, e.id, text)
            editing = null
        } else {
            // "@Name" → "@<number>" with the person listed as mentioned, like WhatsApp.
            var out = text
            val mentioned = mutableListOf<String>()
            picked.sortedByDescending { it.first.length }.forEach { (label, who) ->
                if (out.contains(label)) {
                    if (who == "all") {
                        members.filter { !it.isMe }.forEach { m -> if (m.jid !in mentioned) mentioned.add(m.jid) }
                    } else {
                        out = out.replace(label, "@" + who.substringBefore('@'))
                        if (who !in mentioned) mentioned.add(who)
                    }
                }
            }
            Engine.sendText(jid, out, replyTo?.id, mentioned)
            replyTo = null
        }
        picked.clear()
        input = TextFieldValue("")
        typingJob?.cancel()
        Engine.typing(jid, false)
        lastTypingSent = 0
    }

    fun jumpTo(id: String) {
        val idx = items.indexOfFirst { it is ChatItem.Msg && it.spec.msg.id == id }
        if (idx >= 0) {
            scope.launch {
                // Land on the message (just above the input bar) and flash it.
                listState.scrollToItem(idx)
                highlight = id
                delay(1800)
                highlight = null
            }
        } else {
            toast = "That message is further back in the chat"
        }
    }

    // A search result to show: load older messages until it's in the list, then scroll to it.
    val jumpRequest by Engine.jump.collectAsState()
    LaunchedEffect(jumpRequest) {
        val (c, target) = jumpRequest ?: return@LaunchedEffect
        if (c != jid) return@LaunchedEffect
        Engine.consumeJump()
        // Wait for the first page to load.
        repeat(50) { if (messages.isEmpty()) delay(100) }
        var pages = 0
        while (messages.none { it.id == target.id } && !reachedStart && pages < 60) {
            val first = messages.firstOrNull() ?: break
            loading = true
            val older = Engine.messages(jid, first.ts, 200)
            if (older.size < 200) reachedStart = true
            val known = messages.map { it.id }.toHashSet()
            messages.addAll(0, older.filter { it.id !in known })
            loading = false
            pages++
        }
        delay(60) // let the list pick up the new items
        jumpTo(target.id)
    }

    fun openMedia(m: Message) {
        when (m.type) {
            "image", "video", "gif" -> {
                keyboard?.hide()
                if (m.type == "video" && m.extra?.optBoolean("round") == true) {
                    // Instant video: plays in its circle, tap again to stop.
                    RoundPlayer.toggle(m)
                    return
                }
                if (m.extra?.optBoolean("viewOnce") == true && !m.fromMe) {
                    // View once: one look, then it's gone (like on the phone).
                    if (com.glasschat.service.Prefs.flag(ctx, viewOnceKey(m), false)) {
                        toast = "You already opened this view once ${if (m.type == "video") "video" else "photo"}"
                        return
                    }
                    com.glasschat.service.Prefs.setFlag(ctx, viewOnceKey(m), true)
                }
                viewer = m
            }
            "document" -> scope.launch {
                MediaState.fetch(m.chat, m.id)
                    .onSuccess { MediaFiles.open(ctx, it, m.extra?.optString("mime").orEmpty()) }
                    .onFailure { toast = it.message ?: "Couldn't download the file" }
            }
            "location" -> m.extra?.let { ex -> MediaFiles.openMap(ctx, ex.optDouble("lat"), ex.optDouble("lng"), m.text) }
            "contact" -> contactFor = m
        }
    }

    fun onAttach(a: AttachAction) {
        when (a) {
            AttachAction.Gallery -> pickers.pickGallery()
            AttachAction.Camera -> camera = true
            AttachAction.Document -> pickers.pickDocument()
            AttachAction.Location -> locationSheet = true
            AttachAction.Contact -> try {
                contactPicker.launch(contactPickIntent())
            } catch (e: ActivityNotFoundException) {
                toast = "No contacts app found"
            }
            AttachAction.Audio -> runCatching { audioPicker.launch(arrayOf("audio/*")) }
            AttachAction.Poll -> {
                keyboard?.hide()
                pollComposer = true
            }
            AttachAction.InstantVideo -> {
                keyboard?.hide()
                instantVideo = true
            }
            AttachAction.Sticker -> {
                keyboard?.hide()
                focusManager.clearFocus()
                stickerPanel = true
            }
        }
    }

    fun sendPicked(items: List<PendingMedia>, caption: String, grouped: Boolean = true, asFiles: Boolean = false) {
        val quoted = replyTo?.id
        replyTo = null
        scope.launch {
            if (!MediaPrep.sendPicked(ctx, jid, items, caption, quoted, grouped, asFiles)) toast = "Some files couldn't be read"
            listState.animateScrollToItem(0)
        }
    }

    fun sendDocument(uri: Uri) {
        val quoted = replyTo?.id
        replyTo = null
        scope.launch {
            if (!MediaPrep.sendDocument(ctx, jid, uri, quoted)) toast = "That file couldn't be read"
            listState.animateScrollToItem(0)
        }
    }

    fun startRecording(): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        VoicePlayer.stop()
        if (!recorder.start()) {
            toast = "Couldn't start recording"
            return false
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        recordCancel = false
        recordMs = 0L
        recording = true
        return true
    }

    fun finishRecording() {
        val cancelled = recordCancel
        val rec = recorder.stop(keep = !cancelled)
        recording = false
        recordCancel = false
        if (rec != null) {
            val spec = MediaPrep.voice(rec)
            replyTo?.id?.let { spec.put("quotedId", it) }
            replyTo = null
            Engine.sendMedia(jid, spec)
            scope.launch { listState.animateScrollToItem(0) }
        } else if (!cancelled) {
            toast = "Hold to record, release to send"
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            recordMs = recorder.elapsedMs()
            delay(200)
        }
    }
    BackHandler(enabled = stickerPanel) { stickerPanel = false }
    BackHandler(enabled = selecting) { selected.clear() }

    val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    val otherUnread = chats.count { it.jid != jid && it.hasUnread && !it.archived }

    Box(Modifier.fillMaxSize().background(p.wallpaper)) {
        // ---------- content (feeds the glass) ----------
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .then(if (menuFor != null && Build.VERSION.SDK_INT >= 31) Modifier.blur(16.dp) else Modifier),
        ) {
            Wallpaper(Modifier.fillMaxSize(), chat = jid)
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 66.dp + if (pinned.isNotEmpty() && !selecting) 56.dp else 0.dp,
                    bottom = with(density) { inputHeight.toDp() } + 6.dp,
                ),
            ) {
                items(items, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is ChatItem.Msg -> MessageRow(
                            spec = item.spec,
                            highlighted = highlight == item.spec.msg.id,
                            onReply = { replyTo = it; editing = null; focus.requestFocus() },
                            // An album: hold to select all its photos/videos (forward, delete…).
                            onLongPress = { m ->
                                if (item.spec.album.size > 1) item.spec.album.forEach { a -> if (a.id !in selected) selected.add(a.id) }
                                else menuFor = m
                            },
                            onQuoteClick = ::jumpTo,
                            onRetry = { Engine.retry(jid, it.id) },
                            onMediaTap = ::openMedia,
                            selecting = selecting,
                            selected = item.spec.msg.id in selected,
                            onSelect = { m ->
                                val ids = item.spec.album.ifEmpty { listOf(m) }.map { it.id }
                                if (m.id in selected) selected.removeAll(ids) else ids.forEach { if (it !in selected) selected.add(it) }
                            },
                        )
                        is ChatItem.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            DayPill(Fmt.dayHeader(item.ts))
                        }
                        is ChatItem.Unread -> Box(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp).background(p.pill.copy(alpha = 0.55f)).padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${item.count} unread message${if (item.count == 1) "" else "s"}", style = Type.footnote, color = p.label.copy(alpha = 0.75f))
                        }
                        ChatItem.Typing -> TypingBubble()
                        ChatItem.Notice -> EncryptionNotice()
                    }
                }
            }
            // Soft shade behind the header so it reads over any wallpaper or photo.
            val shade = if (p.dark) Color.Black else Color.White
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 104.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to shade.copy(alpha = if (p.dark) 0.88f else 0.82f),
                            0.55f to shade.copy(alpha = if (p.dark) 0.55f else 0.45f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }

        // ---------- header ----------
        if (selecting) {
            SelectionHeader(backdrop, selected.size, onCancel = { selected.clear() })
        } else if (pinned.isEmpty() && chat?.let { !it.isGroup && !it.saved } == true && !addDismissed) {
            val unsaved = chat!!
            // Not in your contacts: offer to save the number, like WhatsApp.
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 12.dp, top = 66.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .liquidGlass(backdrop, RoundedCornerShape(18.dp), p.glassStrong)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Not in your contacts", style = Type.subhead, color = p.label, modifier = Modifier.weight(1f))
                Text(
                    "Add",
                    style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).clickable {
                        com.glasschat.ui.components.addToContacts(ctx, unsaved.phone.ifBlank { jid.substringBefore('@') }, unsaved.name.removePrefix("~").trim().takeIf { !it.startsWith("+") }.orEmpty())
                    }.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { addDismissed = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Dismiss", tint = p.secondaryLabel, modifier = Modifier.size(18.dp))
                }
            }
        } else if (pinned.isNotEmpty()) {
            PinnedBanner(
                pinned,
                pinIndex,
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 12.dp, top = 62.dp),
                onTap = {
                    val m = pinned[pinIndex % pinned.size]
                    Engine.requestJump(jid, m)
                    pinIndex = (pinIndex + 1) % pinned.size
                },
            )
        }
        // Header: back · photo + name in a glass capsule · calls (like before),
        // on dark glass so it stays readable over any wallpaper.
        if (!selecting) Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCapsule(backdrop, Modifier.height(50.dp).clickable(remember { MutableInteractionSource() }, indication = null) { nav.pop() }, radius = 25.dp, style = headerButtonGlass(), contentPadding = PaddingValues(start = 6.dp, end = if (otherUnread > 0) 14.dp else 6.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", tint = p.label, modifier = Modifier.size(34.dp))
                if (otherUnread > 0) Text("$otherUnread", style = Type.body.copy(fontWeight = FontWeight.Medium), color = p.label)
            }
            Spacer(Modifier.width(8.dp))
            GlassCapsule(
                backdrop,
                Modifier.weight(1f).height(50.dp).clickable(remember { MutableInteractionSource() }, indication = null) { nav.push(Route.ChatInfo(jid)) },
                radius = 25.dp,
                style = headerButtonGlass(),
                contentPadding = PaddingValues(start = 4.dp, end = 14.dp),
            ) {
                // Round photo, clipped so it never shows square corners.
                Box(Modifier.size(42.dp).clip(CircleShape)) { Avatar(jid, isGroup, 42.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat?.name ?: "", style = Type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val presence = presenceMap[jid]
                    val members = chat?.participants ?: 0
                    val sub = when {
                        typing != null && isGroup -> "${typing.sender.removePrefix("~ ")} is ${if (typing.recording) "recording…" else "typing…"}"
                        typing != null -> if (typing.recording) "recording audio…" else "typing…"
                        isGroup -> if (members > 0) "$members member${if (members == 1) "" else "s"}" else "tap here for group info"
                        presence?.online == true -> "online"
                        presence != null && presence.lastSeen > 0 -> Fmt.lastSeen(ctx, presence.lastSeen)
                        else -> "tap here for contact info"
                    }
                    Text(
                        sub,
                        style = Type.caption.copy(fontSize = 12.5.sp),
                        color = if (typing != null) p.accentText else p.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassCapsule(backdrop, Modifier.height(50.dp), radius = 25.dp, style = headerButtonGlass(), contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Outlined.Videocam, "Video call", tint = p.label, modifier = Modifier.size(25.dp).clickable { callSheet = true })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Outlined.Phone, "Voice call", tint = p.label, modifier = Modifier.size(22.dp).clickable { callSheet = true })
            }
        }

        // ---------- scroll to bottom ----------
        val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
        AnimatedVisibility(
            showDown,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = with(density) { inputHeight.toDp() } + 12.dp),
        ) {
            Box {
                GlassCircleButton(backdrop, Icons.Rounded.KeyboardArrowDown, "Latest", size = 42.dp) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
                if (newBelow > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(p.accent),
                        contentAlignment = Alignment.Center,
                    ) { Text("$newBelow", style = Type.caption.copy(fontSize = 10.sp), color = Color.White) }
                }
            }
        }

        // ---------- input ----------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { inputHeight = it.height }
                .windowInsetsPadding(bottomInsets)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (scheduledHere > 0 && !selecting) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ScheduledBanner(scheduledHere) { showScheduled = true }
                }
            }
            if (!selecting && preview == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RecentShotPill { uri -> preview = listOf(PendingMedia(uri, false)) }
                }
            }
            // Typing "@" in a group: pick who to mention.
            val mentionQuery = remember(input) {
                val before = input.text.substring(0, input.selection.start.coerceIn(0, input.text.length))
                Regex("(?:^|\\s)@([^\\s@]{0,30})$").find(before)?.groupValues?.get(1)
            }
            if (mentionQuery != null && members.isNotEmpty() && editing == null) {
                val q = mentionQuery.lowercase()
                val options = remember(members, q) {
                    members.filter { !it.isMe && (q.isEmpty() || it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q)) }.take(30)
                }
                MentionPopup(
                    showAll = q.isEmpty() || "all".startsWith(q) || "everyone".startsWith(q),
                    options = options,
                    onPick = { label, who ->
                        val cur = input.selection.start.coerceIn(0, input.text.length)
                        val start = input.text.lastIndexOf('@', cur - 1).coerceAtLeast(0)
                        val token = "@$label"
                        val newText = input.text.substring(0, start) + token + " " + input.text.substring(cur)
                        input = TextFieldValue(newText, TextRange(start + token.length + 1))
                        picked.add(token to who)
                    },
                )
            }
            val banner = editing ?: replyTo
            AnimatedVisibility(banner != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                banner?.let { b ->
                    ReplyBanner(
                        backdrop,
                        title = if (editing != null) "Edit message" else if (b.fromMe) "You" else b.senderName.removePrefix("~ "),
                        text = previewFor(b.type, b.text, b.revoked),
                        color = if (editing != null || b.fromMe) p.accentText else senderColor(b.sender),
                        onClose = {
                            if (editing != null) input = TextFieldValue("")
                            editing = null
                            replyTo = null
                        },
                    )
                }
            }
            if (selecting) {
                val chosen = messages.filter { it.id in selected }
                SelectionBar(
                    backdrop,
                    allStarred = chosen.isNotEmpty() && chosen.all { it.starred },
                    canCopy = chosen.any { it.text.isNotBlank() },
                    canForward = chosen.isNotEmpty() && chosen.all { canForward(it) },
                    onStar = {
                        val star = !chosen.all { it.starred }
                        chosen.forEach { Engine.star(jid, it.id, star) }
                        toast = if (star) "Starred" else "Unstarred"
                        selected.clear()
                    },
                    onCopy = {
                        val text = chosen.sortedBy { it.ts }.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("messages", text))
                        toast = "Copied"
                        selected.clear()
                    },
                    onForward = { forwardIds = chosen.sortedBy { it.ts }.map { it.id } },
                    onDelete = { bulkDelete = true },
                )
            } else if (chat?.blocked == true) {
                GlassCapsule(
                    backdrop,
                    Modifier.fillMaxWidth().height(48.dp).clickable { unblockAsk = true },
                    radius = 24.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(
                        "You blocked this contact. Tap to unblock.",
                        style = Type.subhead,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else Row(verticalAlignment = Alignment.Bottom) {
                if (recording) {
                    RecordingBar(backdrop, recordMs, recordCancel, Modifier.weight(1f))
                } else {
                    GlassCircleButton(backdrop, Icons.Rounded.Add, "Attach", size = 44.dp) {
                        stickerPanel = false
                        attachSheet = true
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassCapsule(
                        backdrop,
                        Modifier.weight(1f).heightIn(min = 44.dp),
                        radius = 22.dp,
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Box(Modifier.weight(1f).padding(vertical = 7.dp)) {
                            BasicTextField(
                                value = input,
                                onValueChange = {
                                    if (it.text != input.text && it.text.isNotEmpty()) onTyped()
                                    input = it
                                },
                                textStyle = Type.body.copy(color = p.label),
                                cursorBrush = SolidColor(p.accent),
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focus)
                                    .onFocusChanged { if (it.isFocused) stickerPanel = false },
                            )
                        }
                        Icon(
                            if (stickerPanel) Icons.Rounded.Keyboard else Icons.Outlined.EmojiEmotions,
                            if (stickerPanel) "Keyboard" else "Emoji and stickers",
                            tint = p.secondaryLabel,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .size(24.dp)
                                .align(Alignment.Bottom)
                                .clickable {
                                    if (stickerPanel) {
                                        stickerPanel = false
                                        focus.requestFocus()
                                        keyboard?.show()
                                    } else {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        stickerPanel = true
                                    }
                                },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val canSend = input.text.isNotBlank() && !recording
                if (canSend) {
                    GlassCircleButton(
                        backdrop,
                        if (editing != null) Icons.Rounded.Check else Icons.Rounded.ArrowUpward,
                        "Send",
                        size = 44.dp,
                        tint = Color.White,
                        filled = p.accent,
                        // Hold to send later.
                        onLongClick = if (editing == null) ({
                            keyboard?.hide()
                            scheduleText = true
                        }) else null,
                        onClick = ::send,
                    )
                } else {
                    if (!recording) {
                        // Tap: camera. Hold: instant (round) video message.
                        GlassCircleButton(
                            backdrop, Icons.Outlined.PhotoCamera, "Camera", size = 44.dp,
                            onLongClick = {
                                keyboard?.hide()
                                instantVideo = true
                            },
                        ) {
                            keyboard?.hide()
                            camera = true
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    MicButton(
                        backdrop = backdrop,
                        recording = recording,
                        onStart = ::startRecording,
                        onDrag = { dx -> recordCancel = dx < -cancelPx },
                        onEnd = ::finishRecording,
                    )
                }
            }
            if (stickerPanel) {
                EmojiStickerPanel(
                    onEmoji = { e -> input = input.insert(e) },
                    onBackspace = { input = input.backspace() },
                    onSticker = { id ->
                        Engine.sendSticker(jid, id, replyTo?.id)
                        replyTo = null
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onStickerFile = { path ->
                        Engine.sendStickerFile(jid, path, replyTo?.id)
                        replyTo = null
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                )
            }
        }

        // ---------- long-press menu ----------
        menuFor?.let { m ->
            val close: () -> Unit = { menuFor = null }
            MessageMenu(
                m = m,
                isGroup = isGroup,
                disappearing = (chat?.ephemeral ?: 0) > 0,
                pinned = pinned.any { it.id == m.id },
                onDismiss = close,
                actions = MessageActions(
                    onReact = { emoji ->
                        val mine = m.reactions.firstOrNull { it.mine }?.emoji
                        Engine.react(jid, m.id, if (mine == emoji) "" else emoji)
                        close()
                    },
                    onMoreEmoji = { emojiFor = m; close() },
                    onReply = { replyTo = m; editing = null; close(); focus.requestFocus() },
                    onForward = { forwardIds = listOf(m.id); close() },
                    onCopy = {
                        close()
                        val visual = m.type == "image" || m.type == "video" || m.type == "gif"
                        if (!visual) {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", m.text))
                            toast = "Copied"
                        } else {
                            // Photos and videos: the file itself, so you can paste it into another app.
                            scope.launch {
                                val path = MediaState.fetch(m.chat, m.id).getOrNull()
                                toast = if (path == null) {
                                    "Couldn't download it"
                                } else {
                                    runCatching {
                                        val uri = MediaFiles.uriFor(ctx, java.io.File(path))
                                        ctx.getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(ClipData.newUri(ctx.contentResolver, if (m.type == "image") "Photo" else "Video", uri))
                                        "Copied"
                                    }.getOrDefault("Couldn't copy it")
                                }
                            }
                        }
                    },
                    onInfo = { keyboard?.hide(); infoFor = m; close() },
                    onStar = {
                        Engine.star(jid, m.id, !m.starred)
                        close()
                        toast = if (m.starred) "Unstarred" else "Starred"
                    },
                    onKeep = {
                        Engine.keep(jid, m.id, !m.kept)
                        close()
                        toast = if (m.kept) "No longer kept" else "Kept in chat"
                    },
                    onPin = {
                        if (pinned.any { it.id == m.id }) {
                            Engine.pin(jid, m.id, false)
                            toast = "Unpinned"
                        } else {
                            pinFor = m
                        }
                        close()
                    },
                    onEdit = {
                        editing = m
                        replyTo = null
                        input = TextFieldValue(m.text, TextRange(m.text.length))
                        close()
                        focus.requestFocus()
                    },
                    onDelete = { deleteFor = m; close() },
                    onMore = { moreFor = m; close() },
                    onSave = {
                        close()
                        scope.launch {
                            val path = MediaState.fetch(m.chat, m.id).getOrNull()
                            toast = when {
                                path == null -> "Couldn't download it"
                                m.type == "image" || m.type == "video" || m.type == "gif" || m.type == "sticker" ->
                                    if (MediaFiles.saveToGallery(ctx, path, m.type != "image" && m.type != "sticker")) "Saved to gallery" else "Couldn't save"
                                else -> if (MediaFiles.saveToDownloads(ctx, path, m.extra?.optString("fileName").orEmpty())) "Saved to Downloads" else "Couldn't save"
                            }
                        }
                    },
                ),
            )
        }
        emojiFor?.let { m ->
            EmojiPicker(
                onPick = { e ->
                    Engine.react(jid, m.id, e)
                    emojiFor = null
                },
                onDismiss = { emojiFor = null },
            )
        }
        // Tapped phone number: Chat / Call / Add to contacts / Copy, like WhatsApp.
        PhoneTap.number?.let { digits ->
            val shown = "+" + digits.let { d -> if (d.length == 12 && d.startsWith("91")) "91 ${d.substring(2, 7)} ${d.substring(7)}" else d }
            var waJid by remember(digits) { mutableStateOf<String?>(null) }
            var checked by remember(digits) { mutableStateOf(false) }
            LaunchedEffect(digits) {
                waJid = Engine.checkNumber(digits).getOrNull()
                checked = true
            }
            val close = { PhoneTap.number = null }
            ActionSheet(
                title = when {
                    !checked -> shown
                    waJid == null -> "$shown isn't on WhatsApp"
                    else -> shown
                },
                actions = buildList {
                    waJid?.let { j -> add(SheetAction("Chat with $shown", com.glasschat.ui.glass.LineIcons.Chat) { close(); nav.push(Route.Chat(j)) }) }
                    add(SheetAction("Call $shown", Icons.Outlined.Call) {
                        close()
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+$digits")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                    add(SheetAction("Add to contacts", Icons.Outlined.PersonAddAlt) { close(); com.glasschat.ui.components.addToContacts(ctx, digits, "") })
                    add(SheetAction("Copy number", Icons.Outlined.ContentCopy) {
                        close()
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Phone", "+$digits"))
                        toast = "Number copied"
                    })
                    if (checked && waJid == null) add(SheetAction("Invite to WhatsApp", Icons.Outlined.Share) {
                        close()
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:+$digits"))
                                    .putExtra("sms_body", "Let's chat on WhatsApp! It's a fast, simple and secure app we can use to message and call each other for free. Get it at https://whatsapp.com/dl/")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    })
                },
                onDismiss = close,
            )
        }
        pinFor?.let { m ->
            ActionSheet(
                title = "Choose how long your pin lasts. You can unpin at any time.",
                actions = listOf(
                    SheetAction("24 hours") { Engine.pin(jid, m.id, true, 86_400); toast = "Pinned for 24 hours" },
                    SheetAction("7 days") { Engine.pin(jid, m.id, true, 7 * 86_400); toast = "Pinned for 7 days" },
                    SheetAction("30 days") { Engine.pin(jid, m.id, true, 30 * 86_400); toast = "Pinned for 30 days" },
                ),
                onDismiss = { pinFor = null },
            )
        }
        moreFor?.let { m ->
            val disappearing = (chat?.ephemeral ?: 0) > 0
            ActionSheet(
                title = null,
                actions = buildList {
                    val usable = !m.revoked && m.type != "waiting"
                    val canEdit = m.fromMe && m.type == "text" && usable && System.currentTimeMillis() - m.ts < 15 * 60_000L && m.status >= Message.SENT
                    if (canEdit) add(SheetAction("Edit", Icons.Outlined.Edit) {
                        editing = m
                        replyTo = null
                        input = TextFieldValue(m.text, TextRange(m.text.length))
                        focus.requestFocus()
                    })
                    if (m.fromMe && usable) add(SheetAction("Info", Icons.Outlined.Info) { keyboard?.hide(); infoFor = m })
                    if (usable) {
                        if (disappearing) {
                            add(SheetAction(if (m.kept) "Unkeep" else "Keep", Icons.Outlined.BookmarkBorder) { Engine.keep(jid, m.id, !m.kept) })
                        } else {
                            add(SheetAction(if (m.starred) "Unstar" else "Star", Icons.Outlined.StarBorder) { Engine.star(jid, m.id, !m.starred); toast = if (m.starred) "Unstarred" else "Starred" })
                        }
                        val isPinned = pinned.any { it.id == m.id }
                        add(SheetAction(if (isPinned) "Unpin" else "Pin", Icons.Outlined.PushPin) {
                            if (isPinned) { Engine.pin(jid, m.id, false); toast = "Unpinned" } else pinFor = m
                        })
                    }
                    add(SheetAction("Select", Icons.Outlined.CheckCircle) { selected.clear(); selected.add(m.id); keyboard?.hide() })
                    if (!m.revoked && m.type != "waiting") {
                        add(SheetAction("Share", Icons.Outlined.Share) { scope.launch { shareMessage(ctx, m)?.let { toast = it } } })
                    }

                    if (m.text.isNotBlank() && !m.revoked) {
                        add(SheetAction("Search in chat", Icons.Outlined.Search) { nav.push(Route.ChatSearch(jid)) })
                    }
                },
                onDismiss = { moreFor = null },
            )
        }
        if (bulkDelete) {
            val chosen = messages.filter { it.id in selected }
            val now = System.currentTimeMillis()
            val everyone = chosen.isNotEmpty() && chosen.all { it.fromMe && !it.revoked && now - it.ts < 2 * 86_400_000L && it.status >= Message.SENT }
            ActionSheet(
                title = "Delete ${chosen.size} message${if (chosen.size == 1) "" else "s"}?",
                actions = buildList {
                    if (everyone) add(SheetAction("Delete for everyone", destructive = true) { chosen.forEach { Engine.deleteForEveryone(jid, it.id) }; selected.clear() })
                    add(SheetAction("Delete for me", destructive = true) { chosen.forEach { Engine.deleteForMe(jid, it.id) }; selected.clear() })
                },
                onDismiss = { bulkDelete = false },
            )
        }
        if (unblockAsk) {
            val cancel: () -> Unit = { unblockAsk = false }
            val go: () -> Unit = {
                unblockAsk = false
                scope.launch {
                    Engine.setBlocked(jid, false)
                        .onSuccess { toast = "Unblocked" }
                        .onFailure { toast = it.message ?: "Couldn't unblock" }
                }
            }
            GlassAlert(
                title = "Unblock ${chat?.name ?: "this contact"}?",
                message = "They'll be able to message and call you again.",
                onDismiss = cancel,
                buttons = listOf("Cancel" to cancel, "Unblock" to go),
            )
        }
        deleteFor?.let { m ->
            val canEveryone = m.fromMe && !m.revoked && System.currentTimeMillis() - m.ts < 2 * 86_400_000L && m.status >= Message.SENT
            ActionSheet(
                title = "Delete message?",
                actions = buildList {
                    if (canEveryone) add(SheetAction("Delete for everyone", destructive = true) { Engine.deleteForEveryone(jid, m.id) })
                    add(SheetAction("Delete for me", destructive = true) { Engine.deleteForMe(jid, m.id) })
                },
                onDismiss = { deleteFor = null },
            )
        }
        if (attachSheet) {
            AttachPanel(
                onDismiss = { attachSheet = false },
                onAction = ::onAttach,
                onSendRecent = { preview = it },
            )
        }
        if (locationSheet) {
            LocationSheet(
                onDismiss = { locationSheet = false },
                onSend = { lat, lng, name, address ->
                    Engine.sendLocation(jid, lat, lng, name, address, replyTo?.id)
                    replyTo = null
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
        }
        pendingContact?.let { c ->
            val cancelContact: () -> Unit = { pendingContact = null }
            val sendContact: () -> Unit = {
                pendingContact = null
                Engine.sendContact(jid, c.name, c.vcard, replyTo?.id)
                replyTo = null
                scope.launch { listState.animateScrollToItem(0) }
            }
            val number = Contacts.numberIn(c.vcard)
            GlassAlert(
                title = "Send contact?",
                message = c.name + (if (number != null) "\n+$number" else "") + "\nto ${chat?.name ?: "this chat"}",
                onDismiss = cancelContact,
                buttons = listOf("Cancel" to cancelContact, "Send" to sendContact),
            )
        }
        contactFor?.let { m ->
            val vcard = m.extra?.optString("vcard").orEmpty()
            val number = Contacts.numberIn(vcard)
            val name = m.text.ifBlank { "Contact" }
            ActionSheet(
                title = if (number != null) "$name\n+$number" else name,
                actions = buildList {
                    if (number != null) {
                        add(
                            SheetAction("Message") {
                                scope.launch {
                                    Engine.checkNumber("+$number")
                                        .onSuccess { nav.push(Route.Chat(it)) }
                                        .onFailure { toast = "+$number isn't on WhatsApp" }
                                }
                            },
                        )
                        add(SheetAction("Save contact") { saveContact(ctx, name, "+$number") })
                        add(
                            SheetAction("Phone call") {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                        )
                    } else {
                        add(SheetAction("Save contact") { saveContact(ctx, name, "") })
                    }
                },
                onDismiss = { contactFor = null },
            )
        }
        pendingDoc?.let { uri ->
            val name = remember(uri) { MediaPrep.displayName(ctx, uri) }
            val cancelDoc: () -> Unit = { pendingDoc = null }
            val sendDoc: () -> Unit = {
                pendingDoc = null
                sendDocument(uri)
            }
            GlassAlert(
                title = "Send document?",
                message = "$name\nto ${chat?.name ?: "this chat"}",
                onDismiss = cancelDoc,
                buttons = listOf("Cancel" to cancelDoc, "Send" to sendDoc),
            )
        }
        if (callSheet) {
            CallSheet(jid, isGroup, onDismiss = { callSheet = false }, onMessage = { toast = it })
        }
        if (scheduleText) {
            ScheduleSheet(
                onPick = { at ->
                    val text = input.text
                    if (text.isNotBlank()) {
                        com.glasschat.engine.Scheduled.add(ctx, jid, chat?.name ?: "", text, at)
                        input = TextFieldValue("")
                        replyTo = null
                        toast = "Scheduled for ${whenLabel(ctx, at)}"
                    }
                },
                onDismiss = { scheduleText = false },
            )
        }
        scheduleMedia?.let { (cap, items) ->
            ScheduleSheet(
                onPick = { at ->
                    scope.launch {
                        var ok = true
                        items.forEachIndexed { i, item ->
                            val spec = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    if (item.video) MediaPrep.video(ctx, item.uri, "", item.trimStartMs, item.trimEndMs) else MediaPrep.image(ctx, item.uri, "")
                                }.getOrNull()
                            }
                            if (spec == null) ok = false
                            else com.glasschat.engine.Scheduled.add(ctx, jid, chat?.name ?: "", if (i == 0) cap else "", at, spec)
                        }
                        toast = if (ok) "Scheduled for ${whenLabel(ctx, at)}" else "Some files couldn't be read"
                    }
                },
                onDismiss = { scheduleMedia = null },
            )
        }
        if (showScheduled) ScheduledList(jid, onDismiss = { showScheduled = false })
        if (instantVideo) {
            InstantVideoRecorder(
                onRecorded = { file, secs ->
                    instantVideo = false
                    val quoted = replyTo?.id
                    replyTo = null
                    scope.launch {
                        val spec = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { instantVideoSpec(ctx, file, secs) }
                        if (spec == null) {
                            toast = "Couldn't prepare the video"
                        } else {
                            if (quoted != null) spec.put("quotedId", quoted)
                            Engine.sendMedia(jid, spec)
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                onClose = { instantVideo = false },
            )
        }
        if (camera) {
            CameraCapture(
                onMedia = { picked ->
                    camera = false
                    preview = picked
                },
                onClose = { camera = false },
            )
        }
        preview?.let { items ->
            SendPreview(
                items,
                target = chat?.name ?: "",
                onSend = { cap, edited ->
                    preview = null
                    sendPicked(edited, cap)
                },
                onSchedule = { cap, edited ->
                    preview = null
                    scheduleMedia = cap to edited
                },
                allowViewOnce = true,
                onSendAsFiles = { cap, edited ->
                    preview = null
                    sendPicked(edited, cap, asFiles = true)
                },
                onSendUngrouped = { cap, edited ->
                    preview = null
                    sendPicked(edited, cap, grouped = false)
                },
                onCancel = { preview = null },
            )
        }
        if (pollComposer) {
            PollComposer(
                onSend = { question, options, multi ->
                    pollComposer = false
                    Engine.sendPoll(jid, question, options, multi)
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onClose = { pollComposer = false },
            )
        }
        viewer?.let { MediaViewer(jid, it, onClose = { viewer = null }) }
        forwardIds?.let { ids ->
            val first = remember(ids) { messages.find { it.id == ids.first() } }
            ForwardPicker(
                fromChat = jid,
                ids = ids,
                previewText = first?.let { m -> m.text.ifBlank { previewFor(m.type, "", false) } }.orEmpty(),
                previewThumb = remember(first?.id) { first?.let { messageThumb(it) } },
                onClose = { forwardIds = null },
                onDone = { msg ->
                    forwardIds = null
                    selected.clear()
                    toast = msg
                },
            )
        }
        infoFor?.let { m -> MessageInfoScreen(m, isGroup, onClose = { infoFor = null }) }
        Toast(toast) { toast = null }
    }
}

/** Offers WhatsApp or a normal phone call, since linked devices can't call. */
@Composable
fun CallSheet(jid: String, isGroup: Boolean, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val hasWhatsApp = remember { CallHandoff.hasWhatsApp(ctx) }
    ActionSheet(
        title = "WhatsApp only allows calls in its own app. Continue there, or make a normal phone call.",
        actions = buildList {
            add(
                SheetAction(if (hasWhatsApp) "Call in WhatsApp" else "Open in WhatsApp") {
                    if (!CallHandoff.openInWhatsApp(ctx, jid)) onMessage("WhatsApp isn't installed on this phone")
                },
            )
            if (!isGroup && CallHandoff.canDial(jid)) {
                add(SheetAction("Phone call") { CallHandoff.dial(ctx, jid) })
            }
        },
        onDismiss = onDismiss,
    )
}

/** Hold to record a voice note; slide left to cancel; release to send. */
@Composable
private fun MicButton(backdrop: Backdrop, recording: Boolean, onStart: () -> Boolean, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val p = LocalPalette.current
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                val s = if (recording) 1.3f else 1f
                scaleX = s
                scaleY = s
            }
            .glassShadow(CircleShape, 12.dp, Color.Black.copy(alpha = if (p.dark) 0.35f else 0.12f), 3.dp)
            .then(if (recording) Modifier.clip(CircleShape).background(p.accent) else Modifier.liquidGlass(backdrop, CircleShape, p.glass))
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!start()) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        drag(change.position.x - down.position.x)
                        change.consume()
                        if (!change.pressed) break
                    }
                    end()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Mic, "Hold to record", tint = if (recording) Color.White else p.label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RecordingBar(backdrop: Backdrop, ms: Long, cancelling: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "rec")
    val blink by t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "blink")
    GlassCapsule(backdrop, modifier.height(44.dp), radius = 22.dp, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Box(Modifier.size(10.dp).alpha(blink).clip(CircleShape).background(p.danger))
        Spacer(Modifier.width(10.dp))
        Text(Fmt.duration((ms / 1000).toInt()), style = Type.body, color = p.label)
        Spacer(Modifier.weight(1f))
        Text(
            if (cancelling) "Release to cancel" else "‹ Slide to cancel",
            style = Type.subhead,
            color = if (cancelling) p.danger else p.secondaryLabel,
        )
    }
}

/** Stickers you've sent or received (and your recent ones from the phone). */
@Composable
private fun StickerPanel(onPick: (String) -> Unit) {
    val p = LocalPalette.current
    var stickers by remember { mutableStateOf<List<Sticker>?>(null) }
    LaunchedEffect(Unit) { stickers = Engine.stickers() }
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(280.dp)
            .clip(shape)
            .frosted(shape, if (p.dark) Color(0xF01C1C1E) else Color(0xF5F7F7F7), 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        val list = stickers
        when {
            list == null -> CircularProgressIndicator(color = p.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            list.isEmpty() -> Text(
                "Stickers you send or receive will show up here.\nFor emoji, use your keyboard.",
                style = Type.subhead,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list.size, key = { list[it].id }) { i -> StickerCell(list[i], onPick) }
            }
        }
    }
}

@Composable
private fun StickerCell(s: Sticker, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    var path by remember(s.id) { mutableStateOf<String?>(null) }
    var failed by remember(s.id) { mutableStateOf(false) }
    LaunchedEffect(s.id) {
        Engine.downloadSticker(s.id).onSuccess { path = it }.onFailure { failed = true }
    }
    val img = rememberImage(path, 256)
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = path != null) { onPick(s.id) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            img != null -> Image(img, "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            failed -> Icon(Icons.Outlined.EmojiEmotions, null, tint = p.tertiaryLabel, modifier = Modifier.size(22.dp))
            else -> CircularProgressIndicator(color = p.tertiaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
    }
}

private fun upsert(list: MutableList<Message>, m: Message) {
    val i = list.indexOfFirst { it.id == m.id }
    if (i >= 0) {
        list[i] = m
        return
    }
    // Usually the newest; otherwise insert in time order.
    if (list.isEmpty() || list.last().ts <= m.ts) {
        list.add(m)
    } else {
        val at = list.indexOfFirst { it.ts > m.ts }.let { if (it < 0) list.size else it }
        list.add(at, m)
    }
}

@Composable
private fun ReplyBanner(backdrop: Backdrop, title: String, text: String, color: Color, onClose: () -> Unit) {
    val p = LocalPalette.current
    GlassCapsule(backdrop, Modifier.fillMaxWidth().padding(bottom = 8.dp), radius = 20.dp, style = p.glassStrong, contentPadding = PaddingValues(10.dp)) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
            Text(text, style = Type.footnote.copy(fontSize = 14.sp), color = p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Close, "Cancel", tint = p.secondaryLabel, modifier = Modifier.size(22.dp).clickable(onClick = onClose))
    }
}

@Composable
private fun TypingBubble() {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Row(Modifier.fillMaxWidth().padding(start = 15.dp, top = 6.dp, bottom = 4.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(p.bubbleIn).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val d = ((phase - i + 3f) % 3f)
                val lift = if (d < 1f) kotlin.math.sin(d * Math.PI).toFloat() else 0f
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { translationY = -lift * 5f }
                        .alpha(0.4f + 0.6f * lift)
                        .clip(CircleShape)
                        .background(p.secondaryLabel),
                )
            }
        }
    }
}

@Composable
private fun EncryptionNotice() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (p.dark) Color(0xFF2A2A1E) else Color(0xFFFFF5C4))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = p.label.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Messages are end-to-end encrypted. Only people in this chat can read them.",
                style = Type.caption.copy(fontSize = 12.sp),
                color = p.label.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayPill(text: String) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = Type.footnote.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
        color = p.label.copy(alpha = 0.75f),
        modifier = Modifier
            .glassShadow(shape, 8.dp, Color.Black.copy(alpha = 0.08f), 1.dp)
            .clip(shape)
            .frosted(shape, if (p.dark) Color(0xE61C1C1E) else Color(0xEBFFFFFF), 0.7f)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}



/** Lighter glass for the buttons sitting on the dark header bar. */
@Composable
private fun headerButtonGlass(): com.glasschat.ui.glass.GlassStyle {
    val p = LocalPalette.current
    // Dark, frosted capsules: the text and icons stay clear over bright wallpapers.
    return p.glass.copy(
        blur = 20.dp,
        tint = if (p.dark) Color(0xFF151517).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.78f),
    )
}


/** The list of group members shown while you type "@", like WhatsApp. */
@Composable
private fun MentionPopup(showAll: Boolean, options: List<com.glasschat.engine.GroupMember>, onPick: (label: String, jid: String) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    // A plain scrolling column (not a lazy list): safe in any layout, and the list is short.
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .mentionGlass(shape)
            .verticalScroll(rememberScrollState()),
    ) {
        if (showAll) {
            run {
                Row(
                    Modifier.fillMaxWidth().clickable { onPick("all", "all") }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Groups, null, tint = p.label, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("all", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label)
                        Text("Mention all members in this chat", style = Type.footnote, color = p.secondaryLabel)
                    }
                }
            }
        }
        options.forEach { m ->
            val label = m.name.removePrefix("~").trim().ifBlank { m.phone }
            Row(
                Modifier.fillMaxWidth().clickable { onPick(label, m.jid) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(m.jid, false, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, style = Type.body, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.phone.isNotBlank() && m.phone != label) Text(m.phone, style = Type.footnote, color = p.secondaryLabel, maxLines = 1)
                }
            }
        }
    }
}

private fun Modifier.mentionGlass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.menuGlass(shape)).clip(shape)
