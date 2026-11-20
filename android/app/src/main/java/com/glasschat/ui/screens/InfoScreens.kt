package com.glasschat.ui.screens

import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.glasschat.engine.ChatLock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Contact
import com.glasschat.engine.Engine
import androidx.compose.material.icons.outlined.Star
import com.glasschat.ui.components.GlassAlert
import com.glasschat.engine.ChatLists
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.glasschat.ui.components.SheetAction
import com.glasschat.ui.components.ActionSheet
import com.glasschat.engine.GroupMember
import com.glasschat.engine.GroupInfo
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun ChatInfoScreen(jid: String, nav: Navigator) {
    var showPhoto by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val chat = chats.find { it.jid == jid }
    val scroll = rememberScrollState()
    var muteSheet by remember { mutableStateOf(false) }
    var callSheet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val isGroup = chat?.isGroup ?: jid.endsWith("@g.us")
    var group by remember { mutableStateOf<GroupInfo?>(null) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var memberFor by remember { mutableStateOf<GroupMember?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var disappearSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (isGroup) {
        LaunchedEffect(jid) {
            // The saved copy first (instant), then a fresh one from WhatsApp.
            Engine.groupInfo(jid, refresh = false).onSuccess { group = it }
            Engine.groupInfo(jid, refresh = true)
                .onSuccess { group = it; groupError = null }
                .onFailure { if (group == null) groupError = "Couldn't load members (${it.message ?: "offline"})" }
        }
    }
    val title = chat?.name?.ifBlank { null } ?: group?.name?.ifBlank { null } ?: if (isGroup) "Group" else "+" + jid.substringBefore('@')

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            // Big photo header with the name over it, like your own profile page.
            val presenceMap by Engine.presence.collectAsState()
            val avatarVersion by Engine.avatarVersion.collectAsState()
            var photo by remember(jid) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(jid, avatarVersion) {
                photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    (Engine.fullAvatarPath(jid) ?: Engine.avatarPath(jid))?.let { android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap() }
                }
            }
            val members = group?.members?.size ?: chat?.participants ?: 0
            val presence = presenceMap[jid]
            val sub = when {
                isGroup && members > 0 -> "Group · $members members"
                isGroup -> "Group"
                presence?.online == true -> "online"
                presence != null && presence.lastSeen > 0 -> com.glasschat.ui.components.Fmt.lastSeen(LocalContext.current, presence.lastSeen)
                else -> chat?.let { c -> c.phone.takeIf { it != c.name } } ?: ""
            }
            var about by remember(jid) { mutableStateOf("") }
            LaunchedEffect(jid) { if (!isGroup) about = Engine.userAbout(jid) }
            val isMe = Engine.me.collectAsState().value?.jid?.substringBefore('@')?.substringBefore(':') == jid.substringBefore('@')
            ProfileHeader(
                jid = jid,
                isGroup = isGroup,
                photo = photo,
                name = if (isMe) "$title (You)" else title,
                lines = if (isGroup) {
                    listOf(sub)
                } else {
                    listOf(chat?.phone?.takeIf { it != title }.orEmpty(), about, if (presence?.online == true) "online" else "")
                },
                actions = when {
                    isMe -> listOf(
                        ProfileAction(com.glasschat.ui.glass.LineIcons.Chat, "Message") { nav.pop() },
                        ProfileAction(Icons.Outlined.Search, "Search") { nav.push(Route.ChatSearch(jid)) },
                    )
                    else -> listOf(
                        ProfileAction(Icons.Outlined.Phone, "Voice") { callSheet = true },
                        ProfileAction(Icons.Outlined.Videocam, "Video") { callSheet = true },
                        ProfileAction(Icons.Outlined.Search, "Search") { nav.push(Route.ChatSearch(jid)) },
                    )
                },
                onPhoto = if (photo != null) ({ showPhoto = true }) else null,
            )
            group?.topic?.takeIf { it.isNotBlank() }?.let { topic ->
                Spacer(Modifier.height(22.dp))
                SectionCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Description", style = Type.footnote, color = p.secondaryLabel)
                        Spacer(Modifier.height(4.dp))
                        Text(topic, style = Type.body, color = p.label)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            MediaRowsCard(jid, onMedia = { nav.push(Route.ChatMedia(jid)) }, onStarred = { nav.push(Route.Starred(jid)) }, onMessage = { toast = it })
            Spacer(Modifier.height(22.dp))
            if (chat != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val org by ChatLists.state.collectAsState()
                val locked = jid in org.locked
                if (!isGroup && !chat.saved) {
                    SectionCard {
                        SettingRow(com.glasschat.ui.glass.LineIcons.Person, Color.Unspecified, "Add to contacts", onClick = {
                            com.glasschat.ui.components.addToContacts(ctx, chat.phone.ifBlank { jid.substringBefore('@') }, chat.name.removePrefix("~").trim().takeIf { !it.startsWith("+") }.orEmpty())
                        })
                    }
                    Spacer(Modifier.height(22.dp))
                }
                SectionCard {
                    SettingRow(
                        if (chat.isMuted) com.glasschat.ui.glass.LineIcons.BellOff else com.glasschat.ui.glass.LineIcons.Bell,
                        Color.Unspecified,
                        "Notifications",
                        onClick = { if (chat.isMuted) Engine.setMutedUntil(jid, 0) else muteSheet = true },
                        trailing = { InfoValue(if (chat.isMuted) "Muted" else "On") },
                    )
                    RowDivider()
                    SettingRow(com.glasschat.ui.glass.LineIcons.Palette, Color.Unspecified, "Chat theme", onClick = { nav.push(Route.Wallpaper(jid)) })
                }
                Spacer(Modifier.height(22.dp))
                SectionCard {
                    SettingRow(
                        com.glasschat.ui.glass.LineIcons.Timer,
                        Color.Unspecified,
                        "Disappearing messages",
                        onClick = { disappearSheet = true },
                        trailing = { InfoValue(disappearLabel(chat.ephemeral)) },
                    )
                    RowDivider()
                    SettingRow(com.glasschat.ui.glass.LineIcons.ChatLock, Color.Unspecified, "Lock chat", trailing = {
                        IosSwitch(locked) { on ->
                            ChatLock.authenticate(
                                ctx,
                                if (on) "Lock this chat" else "Unlock this chat",
                                onSuccess = {
                                    ChatLists.setLocked(jid, on)
                                    toast = if (on) "Chat locked — find it under Locked chats" else "Chat unlocked"
                                },
                                onFailure = { toast = it },
                            )
                        }
                    })
                    RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            Icon(com.glasschat.ui.glass.LineIcons.Lock, null, tint = p.label, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Encryption", style = Type.body, color = p.label)
                            Text("Messages and calls are end-to-end encrypted.", style = Type.footnote, color = p.secondaryLabel)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                SectionCard {
                    SettingRow(com.glasschat.ui.glass.LineIcons.Pin, Color.Unspecified, if (chat.pinned) "Unpin chat" else "Pin chat", onClick = { Engine.setPinned(jid, !chat.pinned) })
                    RowDivider()
                    SettingRow(com.glasschat.ui.glass.LineIcons.Archive, Color.Unspecified, if (chat.archived) "Unarchive chat" else "Archive chat", onClick = {
                        Engine.setArchived(jid, !chat.archived)
                        if (!chat.archived) nav.resetTo(Route.Home)
                    })
                }
                Spacer(Modifier.height(22.dp))
            }
            if (isGroup) {
                GroupMembers(group, groupError, onMember = { memberFor = it })
                Spacer(Modifier.height(22.dp))
            }
            if (chat != null) {
                Spacer(Modifier.height(22.dp))
                SectionCard {
                    if (isGroup) {
                        DangerRow("Exit group") { confirm = "exit" }
                    } else {
                        DangerRow(if (chat.blocked) "Unblock ${chat.name}" else "Block ${chat.name}", red = !chat.blocked) {
                            confirm = if (chat.blocked) "unblock" else "block"
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = p.separator, thickness = 0.5.dp)
                    DangerRow("Clear chat") { confirm = "clear" }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = p.separator, thickness = 0.5.dp)
                    DangerRow("Delete chat") { confirm = "delete" }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = if (isGroup) "Group info" else "Contact info",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {
                if (!isGroup && chat != null) {
                    val ctxEdit = LocalContext.current
                    com.glasschat.ui.glass.GlassCapsule(
                        backdrop,
                        Modifier.height(44.dp).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            // Opens the phone's contact editor (add new or to an existing contact).
                            val phone = chat.phone.ifBlank { jid.substringBefore('@') }.filter { it.isDigit() || it == '+' }
                            val i = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT)
                                .setType(android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                .putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, if (phone.startsWith("+")) phone else "+$phone")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            com.glasschat.ui.components.ContactBook.pending = if (phone.startsWith("+")) phone else "+$phone"
                            runCatching { ctxEdit.startActivity(i) }
                        },
                        radius = 22.dp,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    ) {
                        Text("Edit", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label)
                    }
                }
            },
        )
        if (showPhoto) ProfilePhotoViewer(jid, isGroup, chat?.name ?: "", onClose = { showPhoto = false })
        Toast(toast) { toast = null }
    }
    if (muteSheet) MuteSheet(jid) { muteSheet = false }
    if (disappearSheet) {
        com.glasschat.ui.components.ActionSheet(
            title = "Disappearing messages",
            actions = listOf(0 to "Off", 86_400 to "24 hours", 604_800 to "7 days", 7_776_000 to "90 days").map { (secs, label) ->
                com.glasschat.ui.components.SheetAction(if (chat?.ephemeral == secs) "$label  ✓" else label) {
                    scope.launch {
                        Engine.setDisappearing(jid, secs)
                            .onSuccess { toast = if (secs == 0) "Disappearing messages off" else "New messages disappear after $label" }
                            .onFailure { toast = "Couldn't change it: ${it.message}" }
                    }
                }
            },
            onDismiss = { disappearSheet = false },
        )
    }
    confirm?.let { what ->
        val name = chat?.name ?: "this chat"
        val cancel: () -> Unit = { confirm = null }
        val (title, message, button) = when (what) {
            "block" -> Triple("Block $name?", "Blocked contacts can't call you or send you messages.", "Block")
            "unblock" -> Triple("Unblock $name?", "They'll be able to message and call you again.", "Unblock")
            "exit" -> Triple("Exit $name?", "You'll stop getting messages from this group.", "Exit")
            "clear" -> Triple("Clear this chat?", "All messages are removed from this phone. Starred messages stay.", "Clear chat")
            else -> Triple("Delete this chat?", "The chat and its messages are deleted from this phone and your other devices.", "Delete")
        }
        val go: () -> Unit = {
            confirm = null
            when (what) {
                "block", "unblock" -> scope.launch {
                    Engine.setBlocked(jid, what == "block")
                        .onSuccess { toast = if (what == "block") "Blocked" else "Unblocked" }
                        .onFailure { toast = it.message ?: "Couldn't change it" }
                }
                "exit" -> scope.launch {
                    Engine.leaveGroup(jid)
                        .onSuccess { toast = "You left the group" }
                        .onFailure { toast = it.message ?: "Couldn't leave the group" }
                }
                "clear" -> {
                    Engine.clearChat(jid)
                    toast = "Chat cleared"
                }
                else -> {
                    ChatLists.forget(jid)
                    Engine.deleteChat(jid)
                    nav.resetTo(Route.Home)
                }
            }
        }
        GlassAlert(title = title, message = message, onDismiss = cancel, buttons = listOf("Cancel" to cancel, button to go), destructiveIndex = if (what == "unblock") -1 else 1)
    }
    memberFor?.let { m ->
        val ctx = LocalContext.current
        ActionSheet(
            title = listOf(m.name, m.phone).filter { it.isNotBlank() }.distinct().joinToString("\n"),
            actions = buildList {
                add(SheetAction("Message ${m.name.substringBefore(" ~")}") { nav.push(Route.Chat(m.jid)) })
                add(SheetAction("View info") { nav.push(Route.ChatInfo(m.jid)) })
                if (!m.saved && m.phone.startsWith("+")) add(SheetAction("Save contact") { saveContact(ctx, m.name.substringAfter("~ ").trim(), m.phone) })
            },
            onDismiss = { memberFor = null },
        )
    }
    if (callSheet) CallSheet(jid, isGroup, onDismiss = { callSheet = false }, onMessage = { toast = it })
}

/** "N members" with everyone listed (you, admins, contacts, others). */
@Composable
private fun GroupMembers(group: GroupInfo?, error: String?, onMember: (GroupMember) -> Unit) {
    val p = LocalPalette.current
    var showAll by remember { mutableStateOf(false) }
    val members = group?.members.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(if (members.isEmpty()) "Members" else "${members.size} members")
        SectionCard {
            when {
                group == null && error == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                members.isEmpty() -> Text(
                    error ?: "No members found.",
                    style = Type.subhead,
                    color = p.secondaryLabel,
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    val shown = if (showAll || members.size <= 12) members else members.take(10)
                    shown.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = p.separator, thickness = 0.5.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !m.isMe) { onMember(m) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(m.jid, false, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.phone.isNotBlank() && m.phone != m.name && !m.isMe) {
                                    Text(m.phone, style = Type.footnote, color = p.secondaryLabel, maxLines = 1)
                                }
                            }
                            if (m.admin) {
                                Text(
                                    "Admin",
                                    style = Type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    color = p.accentText,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(p.accent.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    if (shown.size < members.size) {
                        HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                        Text(
                            "View all (${members.size - shown.size} more)",
                            style = Type.body,
                            color = p.link,
                            modifier = Modifier.fillMaxWidth().clickable { showAll = true }.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerRow(label: String, red: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label,
        style = Type.body,
        color = if (red) p.danger else p.link,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.cell)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = p.accentText, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.footnote, color = p.label)
    }
}

@Composable
fun NewChatScreen(nav: Navigator) {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var query by remember { mutableStateOf("") }
    var numberMode by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("+91 ") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { contacts = Engine.contacts() }

    val filtered = remember(contacts, query) {
        val q = query.trim().lowercase()
        contacts.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q.replace(" ", "")) }
    }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        LazyColumn(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).imePadding()) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(60.dp))
                SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Search name or number")
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    SettingRow(Icons.Outlined.Groups, p.accent, "New group", onClick = { nav.replaceTop(Route.NewGroup) })
                    HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                    SettingRow(Icons.Outlined.PersonAddAlt, p.accent, "New chat with a number", onClick = { numberMode = !numberMode; error = null })
                    if (numberMode) {
                        HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = number,
                                onValueChange = { v -> number = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = Type.body.copy(color = p.label, fontSize = 19.sp),
                                cursorBrush = SolidColor(p.accent),
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(p.accent)
                                    .clickable(enabled = !checking) {
                                        checking = true
                                        error = null
                                        scope.launch {
                                            Engine.checkNumber(number)
                                                .onSuccess { jid -> nav.replaceTop(Route.Chat(jid)) }
                                                .onFailure { error = it.message ?: "Couldn't check that number" }
                                            checking = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                if (checking) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                else Text("Chat", style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                            }
                        }
                        error?.let { Text(it, style = Type.footnote, color = p.danger, modifier = Modifier.padding(start = 14.dp, bottom = 12.dp)) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionHeader("Contacts on WhatsApp")
            }
            if (contacts == null) {
                item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) } }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        if (query.isEmpty()) "Your phone's contacts appear here after the first sync." else "No contacts match.",
                        style = Type.subhead,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(30.dp),
                    )
                }
            }
            items(filtered, key = { it.jid }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(p.cell)
                        .clickable { nav.replaceTop(Route.Chat(c.jid)) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.jid, false, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = p.label, maxLines = 1)
                        Text(c.phone, style = Type.footnote, color = p.secondaryLabel)
                    }
                }
                HorizontalDivider(Modifier.background(p.cell).padding(start = 70.dp), color = p.separator, thickness = 0.5.dp)
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "New chat",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.Rounded.Close, "Close") { nav.pop() } },
            right = {},
        )
    }
}


/** Someone's profile photo, full screen (tap the photo on their info page). */
@Composable
fun ProfilePhotoViewer(jid: String, isGroup: Boolean, name: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    var img by remember(jid) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember(jid) { mutableStateOf(true) }
    LaunchedEffect(jid) {
        img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            (Engine.fullAvatarPath(jid) ?: Engine.avatarPath(jid))?.let {
                android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap()
            }
        }
        loading = false
    }
    var scale by remember { mutableStateOf(1f) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) } },
        contentAlignment = Alignment.Center,
    ) {
        val b = img
        when {
            b != null -> androidx.compose.foundation.Image(
                b, name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
            )
            loading -> androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            else -> Text(if (isGroup) "No group photo" else "No profile photo, or it's only visible to their contacts", color = Color.White, style = Type.subhead)
        }
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        }
    }
}


@Composable
private fun InfoValue(text: String) {
    Text(text, style = Type.body, color = LocalPalette.current.secondaryLabel)
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = LocalPalette.current.tertiaryLabel, modifier = Modifier.size(24.dp))
}

private fun disappearLabel(seconds: Int): String = when {
    seconds <= 0 -> "Off"
    seconds <= 86_400 -> "24 hours"
    seconds <= 604_800 -> "7 days"
    else -> "90 days"
}
