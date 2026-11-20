package com.glasschat.ui.screens

import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.glasschat.engine.Chat
import com.glasschat.engine.ChatLists
import com.glasschat.engine.ChatLock
import com.glasschat.engine.Engine
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.glass.menuGlass
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Long-press menu for a chat: read/unread, pin, archive, mute, lock,
 * favourites, lists, block (or exit group), clear and delete.
 */
@Composable
fun ChatContextMenu(chat: Chat, onDismiss: () -> Unit, onMessage: (String) -> Unit, onOpenInfo: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val org by ChatLists.state.collectAsState()
    var muteSheet by remember { mutableStateOf(false) }
    var listSheet by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var moreSheet by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f)) }
    val favorite = chat.jid in org.favorites
    val locked = chat.jid in org.locked
    val shortName = chat.name.substringBefore(" ~").take(24)

    fun done(msg: String? = null) {
        onDismiss()
        if (msg != null) onMessage(msg)
    }

    if (!moreSheet && !muteSheet && !listSheet && confirm == null) com.glasschat.ui.glass.GlassOverlay {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (p.dark) 0.35f else 0.12f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            ) {
                val shape = RoundedCornerShape(24.dp)
                Box(Modifier.fillMaxWidth().menuGlass(shape).clip(shape)) {
                    ChatRow(chat, null, onClick = {}, onLongClick = {})
                }
                Spacer(Modifier.height(10.dp))
                val menuShape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .width(270.dp)
                        .menuGlass(menuShape)
                        .clip(menuShape),
                ) {
                    if (onOpenInfo != null) {
                        MenuItem(if (chat.isGroup) "Group info" else "View contact", com.glasschat.ui.glass.LineIcons.Person) {
                            onDismiss()
                            onOpenInfo()
                        }
                        MenuDivider()
                    }
                    MenuItem("Open in WhatsApp", com.glasschat.ui.glass.LineIcons.OpenOut) {
                        val msg = openInWhatsApp(ctx, chat.jid, chat.isGroup)
                        if (msg != null) done(msg) else onDismiss()
                    }
                    MenuDivider()
                    MenuItem(if (chat.pinned) "Unpin" else "Pin", com.glasschat.ui.glass.LineIcons.Pin) {
                        Engine.setPinned(chat.jid, !chat.pinned)
                        done()
                    }
                    MenuDivider()
                    MenuItem(if (chat.isMuted) "Unmute" else "Mute", if (chat.isMuted) com.glasschat.ui.glass.LineIcons.Bell else com.glasschat.ui.glass.LineIcons.BellOff) {
                        if (chat.isMuted) {
                            Engine.setMutedUntil(chat.jid, 0)
                            done("Chat unmuted")
                        } else {
                            muteSheet = true
                        }
                    }
                    MenuDivider()
                    MenuItem(if (locked) "Unlock chat" else "Lock chat", if (locked) Icons.Outlined.LockOpen else com.glasschat.ui.glass.LineIcons.Lock) {
                        ChatLock.authenticate(
                            ctx,
                            if (locked) "Unlock this chat" else "Lock this chat",
                            onSuccess = {
                                ChatLists.setLocked(chat.jid, !locked)
                                done(if (locked) "Chat unlocked" else "Chat locked — find it under Locked chats")
                            },
                            onFailure = { done(it) },
                        )
                    }
                    MenuDivider()
                    MenuItem("More…", Icons.Rounded.MoreHoriz) { moreSheet = true }
                }
            }
        }
    }
    if (moreSheet) {
        com.glasschat.ui.components.ActionSheet(
            title = chat.name,
            actions = buildList {
                add(com.glasschat.ui.components.SheetAction(if (chat.hasUnread) "Mark as read" else "Mark as unread", if (chat.hasUnread) Icons.Outlined.MarkChatRead else Icons.Outlined.MarkChatUnread) {
                    if (chat.hasUnread) Engine.markRead(chat.jid) else Engine.markUnread(chat.jid)
                    done()
                })
                add(com.glasschat.ui.components.SheetAction(if (chat.archived) "Unarchive" else "Archive", if (chat.archived) Icons.Outlined.Unarchive else com.glasschat.ui.glass.LineIcons.Archive) {
                    Engine.setArchived(chat.jid, !chat.archived)
                    done(if (chat.archived) "Chat unarchived" else "Chat archived")
                })
                add(com.glasschat.ui.components.SheetAction(if (favorite) "Remove from Favorites" else "Add to Favorites", if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder) {
                    ChatLists.setFavorite(chat.jid, !favorite)
                    done(if (favorite) "Removed from Favorites" else "Added to Favorites")
                })
                add(com.glasschat.ui.components.SheetAction("Add to list", Icons.AutoMirrored.Outlined.PlaylistAdd) { listSheet = true })
                if (chat.isGroup) {
                    add(com.glasschat.ui.components.SheetAction("Exit group", Icons.AutoMirrored.Outlined.ExitToApp, destructive = true) { confirm = "exit" })
                } else {
                    add(com.glasschat.ui.components.SheetAction(if (chat.blocked) "Unblock $shortName" else "Block $shortName", Icons.Outlined.Block, destructive = !chat.blocked) {
                        confirm = if (chat.blocked) "unblock" else "block"
                    })
                }
                add(com.glasschat.ui.components.SheetAction("Clear chat", Icons.Outlined.DeleteSweep, destructive = true) { confirm = "clear" })
                add(com.glasschat.ui.components.SheetAction("Delete chat", Icons.Outlined.Delete, destructive = true) { confirm = "delete" })
            },
            // Closing it goes back to the menu; actions close everything themselves.
            onDismiss = { moreSheet = false },
        )
    }
    if (muteSheet) MuteSheet(chat.jid) { muteSheet = false; done() }
    if (listSheet) AddToListSheet(chat.jid, onDismiss = { listSheet = false; done() })
    confirm?.let { what ->
        val cancel: () -> Unit = { confirm = null }
        when (what) {
            "block", "unblock" -> {
                val block = what == "block"
                val go: () -> Unit = {
                    confirm = null
                    scope.launch {
                        Engine.setBlocked(chat.jid, block)
                            .onSuccess { done(if (block) "$shortName blocked" else "$shortName unblocked") }
                            .onFailure { done(it.message ?: "Couldn't ${if (block) "block" else "unblock"}") }
                    }
                }
                GlassAlert(
                    title = if (block) "Block $shortName?" else "Unblock $shortName?",
                    message = if (block) "Blocked contacts can't call you or send you messages. This applies to all your devices." else "They'll be able to message and call you again.",
                    onDismiss = cancel,
                    buttons = listOf("Cancel" to cancel, (if (block) "Block" else "Unblock") to go),
                    destructiveIndex = if (block) 1 else -1,
                )
            }
            "exit" -> {
                val go: () -> Unit = {
                    confirm = null
                    scope.launch {
                        Engine.leaveGroup(chat.jid)
                            .onSuccess { done("You left ${chat.name}") }
                            .onFailure { done(it.message ?: "Couldn't leave the group") }
                    }
                }
                GlassAlert(
                    title = "Exit ${chat.name}?",
                    message = "You'll stop getting messages from this group. Only admins are notified.",
                    onDismiss = cancel,
                    buttons = listOf("Cancel" to cancel, "Exit" to go),
                    destructiveIndex = 1,
                )
            }
            "clear" -> {
                val go: () -> Unit = {
                    confirm = null
                    Engine.clearChat(chat.jid)
                    done("Chat cleared")
                }
                GlassAlert(
                    title = "Clear this chat?",
                    message = "All messages in this chat are removed from this phone. Starred messages stay.",
                    onDismiss = cancel,
                    buttons = listOf("Cancel" to cancel, "Clear chat" to go),
                    destructiveIndex = 1,
                )
            }
            else -> {
                val go: () -> Unit = {
                    confirm = null
                    ChatLists.forget(chat.jid)
                    Engine.deleteChat(chat.jid)
                    done("Chat deleted")
                }
                GlassAlert(
                    title = "Delete this chat?",
                    message = "The chat and its messages are deleted from this phone and your other devices.",
                    onDismiss = cancel,
                    buttons = listOf("Cancel" to cancel, "Delete" to go),
                    destructiveIndex = 1,
                )
            }
        }
    }
}

/** Put a chat in (or take it out of) your custom lists, or make a new list. */
@Composable
fun AddToListSheet(chat: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val org by ChatLists.state.collectAsState()
    var newName by remember { mutableStateOf("") }
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .imePadding()
                .padding(24.dp)
                .fillMaxWidth()
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 14.dp),
        ) {
            Text("Add to list", style = Type.title, color = p.label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            Text(
                "Lists show up as filters above your chats.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))
            org.lists.forEach { l ->
                val inList = chat in l.chats
                Row(
                    Modifier.fillMaxWidth().clickable { ChatLists.setInList(l.id, chat, !inList) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceMark(inList, square = true)
                    Spacer(Modifier.width(12.dp))
                    Text(l.name, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
                    Text("${l.chats.size}", style = Type.footnote, color = p.secondaryLabel)
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = p.separator, thickness = 0.5.dp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.fill).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (newName.isEmpty()) Text("New list name", style = Type.body, color = p.tertiaryLabel)
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it.take(30) },
                        singleLine = true,
                        textStyle = Type.body.copy(color = p.label),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Create",
                    style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (newName.isNotBlank()) p.link else p.tertiaryLabel,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = newName.isNotBlank()) {
                        ChatLists.createList(newName, chat)
                        newName = ""
                    }.padding(8.dp),
                )
            }
            Text(
                "Done",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.link,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(14.dp),
            )
        }
    }
}

/**
 * Shows [content] only after unlocking when the chat is locked (whatever
 * way it was opened: list, notification, search or forward).
 */
@Composable
fun LockGate(jid: String, onBack: () -> Unit, always: Boolean = false, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val org by ChatLists.state.collectAsState()
    val lockEpoch by ChatLock.epoch.collectAsState()
    var open by remember(jid, lockEpoch) { mutableStateOf(ChatLock.unlocked) }
    var error by remember { mutableStateOf<String?>(null) }
    // Away from the app for a while (not just picking a photo) locks it again.
    val lifecycle = (ctx as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        var stoppedAt = 0L
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > ChatLock.RELOCK_MS) open = false
                else -> {}
            }
        }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    if ((!always && jid !in org.locked) || open) {
        content()
        return
    }
    val lock by ChatLock.settings.collectAsState()
    var code by remember { mutableStateOf("") }
    fun unlock() {
        ChatLock.authenticate(ctx, "Unlock chat", onSuccess = { open = true }, onFailure = { error = it })
    }
    // With a secret code, the code is the way in (like WhatsApp on linked
    // devices); the phone's own check stays available as a second option.
    if (!lock.hasCode) LaunchedEffect(jid) { unlock() }
    Box(Modifier.fillMaxSize().background(p.groupedBackground).imePadding()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(p.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = p.secondaryLabel, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(if (always) "Locked chats" else "This chat is locked", style = Type.title2, color = p.label)
            Spacer(Modifier.height(6.dp))
            if (lock.hasCode) {
                Text(error ?: "Enter your secret code", style = Type.subhead, color = if (error != null) p.danger else p.secondaryLabel, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                SecretCodeField(code, { code = it; error = null }, onDone = {
                    if (ChatLock.checkCode(ctx, code)) open = true else { error = "Wrong secret code"; code = "" }
                })
                Spacer(Modifier.height(16.dp))
                Text(
                    "Unlock",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (code.isBlank()) p.tertiaryLabel else p.accent)
                        .clickable(enabled = code.isNotBlank()) {
                            if (ChatLock.checkCode(ctx, code)) open = true else { error = "Wrong secret code"; code = "" }
                        }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                )
                if (ChatLock.canLock(ctx)) {
                    Spacer(Modifier.height(6.dp))
                    Text("Use fingerprint or phone PIN", style = Type.body, color = p.link, modifier = Modifier.clickable { unlock() }.padding(10.dp))
                }
            } else {
                Text(error ?: "Unlock with your fingerprint, face or phone PIN.", style = Type.subhead, color = p.secondaryLabel, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Unlock",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).clickable { unlock() }.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Go back", style = Type.body, color = p.link, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }
    }
}

/** A masked input for the chat-lock secret code. */
@Composable
fun SecretCodeField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, placeholder: String = "Secret code", autoFocus: Boolean = true) {
    val p = LocalPalette.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.cell)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = Type.body, color = p.tertiaryLabel)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(32)) },
            singleLine = true,
            textStyle = Type.body.copy(color = p.label),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** Settings → Privacy → Chat lock: hide locked chats, and the secret code. */
@Composable
fun ChatLockSettingsScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val lock by ChatLock.settings.collectAsState()
    var codeSheet by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    SubPage("Chat lock", nav) {
        SectionCard {
            SettingRow(Icons.Rounded.Lock, Color(0xFF34C759), "Hide locked chats", trailing = {
                IosSwitch(lock.hidden) { on ->
                    if (on && !lock.hasCode) codeSheet = true else ChatLock.setHide(ctx, on)
                }
            })
        }
        FootNote(
            "Locked chats won't show on your chat list. To see them, enter your secret code into the search bar in your Chats tab. " +
                "Hiding needs a secret code.",
        )
        Spacer(Modifier.height(14.dp))
        SectionCard {
            SettingRow(Icons.Rounded.Lock, Color(0xFF5856D6), "Secret code", onClick = { codeSheet = true }, trailing = {
                Text(if (lock.hasCode) "On" else "Off", style = Type.body, color = p.secondaryLabel)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
            })
            if (lock.hasCode) {
                RowDivider()
                Box(Modifier.fillMaxWidth().clickable { confirmRemove = true }.padding(15.dp)) {
                    Text("Remove secret code", style = Type.body, color = p.danger)
                }
            }
        }
        FootNote(
            "Unlock your locked chats with a secret code instead of your phone's fingerprint or PIN. " +
                "Use something different from your phone's PIN. You can still use your fingerprint here too.",
        )
        Spacer(Modifier.height(14.dp))
        SectionCard {
            SettingRow(Icons.Rounded.Lock, Color(0xFF8E8E93), "Locked chats", onClick = { nav.push(Route.LockedChats) })
            RowDivider()
            Box(Modifier.fillMaxWidth().clickable { ChatLock.lockNow(); toast = "Locked chats locked" }.padding(15.dp)) {
                Text("Lock now", style = Type.body, color = p.link)
            }
        }
    }
    if (codeSheet) {
        SecretCodeDialog(
            hasCode = lock.hasCode,
            onDone = { code ->
                codeSheet = false
                if (code != null) {
                    ChatLock.setCode(ctx, code)
                    toast = "Secret code saved"
                }
            },
        )
    }
    if (confirmRemove) {
        // Removing the code needs the code itself.
        SecretCodeDialog(
            hasCode = true,
            verifyOnly = true,
            onDone = { ok ->
                confirmRemove = false
                if (ok != null) {
                    ChatLock.setCode(ctx, null)
                    toast = "Secret code removed"
                }
            },
        )
    }
    Toast(toast) { toast = null }
}

/** Create or change the secret code: current code (if any), new code, confirm. */
@Composable
private fun SecretCodeDialog(hasCode: Boolean, onDone: (String?) -> Unit, verifyOnly: Boolean = false) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(if (hasCode) 0 else 1) }
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone(null) }
    fun next() {
        when (step) {
            0 -> if (ChatLock.checkCode(ctx, current)) {
                if (verifyOnly) onDone("") else { step = 1; error = null }
            } else {
                error = "That's not your current code"
                current = ""
            }
            1 -> if (first.trim().length < 4) error = "Use at least 4 characters" else { step = 2; error = null }
            else -> if (second.trim() == first.trim()) onDone(first.trim()) else { error = "The codes don't match"; second = "" }
        }
    }
    com.glasschat.ui.glass.GlassOverlay {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, indication = null) { onDone(null) }.imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(28.dp)
                    .fillMaxWidth()
                    .menuGlass(RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (step) {
                        0 -> if (verifyOnly) "Enter your secret code" else "Enter current code"
                        1 -> "Create secret code"
                        else -> "Confirm secret code"
                    },
                    style = Type.title.copy(fontWeight = FontWeight.SemiBold),
                    color = p.label,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    error ?: if (step == 1) "Use at least 4 letters, numbers or emoji. Make sure you remember it." else " ",
                    style = Type.footnote,
                    color = if (error != null) p.danger else p.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                when (step) {
                    0 -> SecretCodeField(current, { current = it; error = null }, onDone = ::next, placeholder = "Current code")
                    1 -> SecretCodeField(first, { first = it; error = null }, onDone = ::next, placeholder = "New secret code")
                    else -> SecretCodeField(second, { second = it; error = null }, onDone = ::next, placeholder = "Repeat secret code")
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("Cancel", style = Type.body, color = p.link, modifier = Modifier.clickable { onDone(null) }.padding(10.dp))
                    Text(
                        if (step == 2) "Save" else if (verifyOnly) "Remove" else "Next",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.link,
                        modifier = Modifier.clickable(onClick = ::next).padding(10.dp),
                    )
                }
            }
        }
    }
}

/** Locked chats, behind your fingerprint / face / PIN. */
@Composable
fun LockedChatsScreen(nav: Navigator) {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val typing by Engine.typing.collectAsState()
    val org by ChatLists.state.collectAsState()
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val locked = remember(chats, org) { chats.filter { it.jid in org.locked } }
    LockGate("locked-chats", onBack = { nav.pop() }, always = true) {
        Box(Modifier.fillMaxSize().background(p.background)) {
            LazyColumn(Modifier.fillMaxSize().backdropSource(backdrop).background(p.background)) {
                item {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(Modifier.height(60.dp))
                    Text(
                        if (locked.isEmpty()) "No locked chats. Long-press a chat and choose Lock chat."
                        else "Locked chats don't show who wrote or what in notifications.",
                        style = Type.footnote,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                items(locked, key = { it.jid }) { c ->
                    ChatRow(c, typing[c.jid], onClick = { nav.push(Route.Chat(c.jid)) }, onLongClick = { menuChat = c })
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
            TopEdgeFade()
            TopControls(
                backdrop,
                title = "Locked chats",
                showTitle = true,
                left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
                right = {},
            )
            menuChat?.let { ChatContextMenu(it, onDismiss = { menuChat = null }, onMessage = { m -> toast = m }, onOpenInfo = { nav.push(Route.ChatInfo(it.jid)) }) }
            Toast(toast) { toast = null }
        }
    }
}

/**
 * Opens this person's chat in the WhatsApp app (or WhatsApp Business).
 * WhatsApp has no link that opens a group, or a contact whose number this
 * device doesn't know, so for those it just opens WhatsApp. Returns a
 * message to show, or null when nothing needs saying.
 */
fun openInWhatsApp(ctx: android.content.Context, jid: String, isGroup: Boolean): String? {
    val pm = ctx.packageManager
    val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        ?: return "WhatsApp isn't installed on this phone"
    val number = jid.substringBefore('@').substringBefore(':').takeIf { jid.endsWith("@s.whatsapp.net") && it.all(Char::isDigit) }
    if (!isGroup && number != null) {
        val chat = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$number"))
            .setPackage(pkg)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(chat) }.isSuccess) return null
    }
    val launch = pm.getLaunchIntentForPackage(pkg)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: return "Couldn't open WhatsApp"
    return if (runCatching { ctx.startActivity(launch) }.isSuccess) {
        if (isGroup) "WhatsApp can't be opened straight to a group — find it in your chats there" else null
    } else "Couldn't open WhatsApp"
}
