package com.glasschat.ui.screens

import androidx.compose.material.icons.outlined.Restore
import com.glasschat.ui.glass.menuGlass
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.glasschat.R
import com.glasschat.engine.ChatLists
import com.glasschat.engine.ChatLock
import com.glasschat.engine.Engine
import com.glasschat.media.MediaState
import com.glasschat.service.Notifier
import com.glasschat.service.OemSettings
import com.glasschat.service.Prefs
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.ActionSheet
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.SheetAction
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// -----------------------------------------------------------------------------
// Building blocks

/** A pushed settings page: back button, centred title, grouped cards. */
@Composable
fun SubPage(title: String, nav: Navigator, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(70.dp))
            content()
            Spacer(Modifier.height(60.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = title,
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {},
        )
    }
}

@Composable
fun FootNote(text: String) {
    Text(
        text,
        style = Type.footnote,
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

/** Telegram-style settings row: coloured icon, title and a grey line under it. */
@Composable
fun SettingRow2(icon: ImageVector, iconBg: Color, title: String, subtitle: String?, onClick: () -> Unit, trailing: String? = null) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Plain black & white icons, like WhatsApp on iPhone (the colour is ignored).
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(monoIcon(icon), null, tint = p.label, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = p.label)
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = Type.body, color = p.secondaryLabel)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
    }
}

/** One choice in a list of options, with a tick when chosen. */
@Composable
private fun ChoiceRow(title: String, subtitle: String?, chosen: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = if (enabled) p.label else p.secondaryLabel)
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = p.secondaryLabel)
        }
        if (chosen) Icon(Icons.Rounded.Check, null, tint = p.accentText, modifier = Modifier.size(22.dp))
    }
}

// -----------------------------------------------------------------------------
// Settings tab

@Composable
fun SettingsTab(nav: Navigator, backdrop: Backdrop) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val me by Engine.me.collectAsState()
    val conn by Engine.conn.collectAsState()
    val scroll = rememberScrollState()
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    var alwaysOn by remember { mutableStateOf(Prefs.background(ctx) && Prefs.instant(ctx)) }
    val lifecycle = (ctx as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) alwaysOn = Prefs.background(ctx) && Prefs.instant(ctx) }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val org by ChatLists.state.collectAsState()

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(scroll)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(30.dp))
            // Profile: big photo, name, number. Tap for your profile.
            Column(
                Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { nav.push(Route.Profile) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Avatar(me?.jid ?: "", false, 108.dp)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(p.groupedBackground)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(p.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, "Profile photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(me?.name?.ifBlank { null } ?: "Your profile", style = Type.title2.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = p.label)
                Spacer(Modifier.height(2.dp))
                Text(me?.phone ?: "", style = Type.subhead, color = p.secondaryLabel)
            }
            Spacer(Modifier.height(22.dp))
            SectionCard {
                SettingRow2(com.glasschat.ui.glass.LineIcons.Person, Color(0xFF0A84FF), "Account", "Profile photo, name, about", { nav.push(Route.Profile) })
                RowDivider2()
                SettingRow2(com.glasschat.ui.glass.LineIcons.Chat, Color(0xFFFF9500), "Chat Settings", "Wallpaper, text size, themes", { nav.push(Route.ChatSettings) })
                RowDivider2()
                SettingRow2(com.glasschat.ui.glass.LineIcons.Shield, Color(0xFF34C759), "Privacy & Security", "Read receipts, chat lock, status", { nav.push(Route.Settings("privacy")) })
                RowDivider2()
                SettingRow2(com.glasschat.ui.glass.LineIcons.Bell, Color(0xFFFF3B30), "Notifications", "Previews, test, delivery log", { nav.push(Route.Settings("notifications")) })
                RowDivider2()
                SettingRow2(com.glasschat.ui.glass.LineIcons.Storage, Color(0xFF5856D6), "Data and Storage", "Media quality, auto-download", { nav.push(Route.Settings("data")) })
                RowDivider2()
                SettingRow2(
                    Icons.Outlined.Folder, Color(0xFF0A84FF), "Chat Folders", "Sort chats into folders", { nav.push(Route.Settings("folders")) },
                    trailing = if (org.lists.isEmpty()) null else "${org.lists.size}",
                )
                RowDivider2()
                SettingRow2(
                    com.glasschat.ui.glass.LineIcons.Laptop, Color(0xFF30B0C7), "Devices", when (conn) {
                        Engine.Conn.Connected -> "This linked device · connected"
                        Engine.Conn.Connecting -> "This linked device · connecting…"
                        else -> "This linked device · offline"
                    }, { nav.push(Route.Settings("devices")) },
                )
                RowDivider2()
                SettingRow2(
                    Icons.Outlined.Sync, Color(0xFFFF9500), "Always-on connection", "Messages while the app is closed",
                    { nav.push(Route.BackgroundSetup) }, trailing = if (alwaysOn) "On" else "Off",
                )
            }
            Spacer(Modifier.height(22.dp))
            SectionCard {
                SettingRow2(com.glasschat.ui.glass.LineIcons.Star, Color(0xFFFFCC00), "Starred Messages", null, { nav.push(Route.Starred("")) })
            }
            Text(
                "${ctx.getString(R.string.app_name)} $versionName · engine ${Engine.engineVersion()}",
                style = Type.footnote,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            Spacer(Modifier.height(130.dp))
        }
        TopEdgeFade(alpha = (scroll.value / 40f).coerceIn(0f, 1f))
        TopControls(backdrop, "Settings", scroll.value > 200, left = {}, right = {})
    }
}

@Composable
private fun RowDivider2() {
    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 60.dp), color = LocalPalette.current.separator, thickness = 0.5.dp)
}

/** Route.Settings(section). */
@Composable
fun SettingsSection(section: String, nav: Navigator) {
    when (section) {
        "privacy" -> PrivacyScreen(nav)
        "notifications" -> NotificationsScreen(nav)
        "data" -> DataStorageScreen(nav)
        "folders" -> ChatFoldersScreen(nav)
        "devices" -> DevicesScreen(nav)
        "chatlock" -> ChatLockSettingsScreen(nav)
        "status-privacy" -> StatusPrivacyScreen(nav)
        "notif-private" -> ChatNotificationsScreen(nav, groups = false)
        "notif-groups" -> ChatNotificationsScreen(nav, groups = true)
        "notif-reactions" -> ReactionNotificationsScreen(nav)
        "personal-info" -> PersonalInfoScreen(nav)
        else -> SubPage("Settings", nav) {}
    }
}

// -----------------------------------------------------------------------------
// Privacy & Security

@Composable
private fun PrivacyScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var receipts by remember { mutableStateOf(Prefs.readReceipts(ctx)) }
    var secret by remember { mutableStateOf(Prefs.secretStatus(ctx)) }
    var keepDeleted by remember { mutableStateOf(Prefs.flag(ctx, "keepDeleted", true)) }
    val lock by ChatLock.settings.collectAsState()
    val org by ChatLists.state.collectAsState()
    val chats by Engine.chats.collectAsState()
    val blocked = remember(chats) { chats.filter { it.blocked } }
    var toast by remember { mutableStateOf<String?>(null) }
    SubPage("Privacy & Security", nav) {
        SectionHeader("Account")
        SectionCard {
            SettingRow2(com.glasschat.ui.glass.LineIcons.Person, Color(0xFF0A84FF), "Account privacy", "Last seen, profile photo, about", onClick = { nav.push(Route.Settings("personal-info")) })
        }
        Spacer(Modifier.height(14.dp))
        SectionHeader("Messages")
        SectionCard {
            SettingRow(Icons.Outlined.DoneAll, Color(0xFF34B7F1), "Read receipts", trailing = {
                IosSwitch(receipts) { receipts = it; Prefs.setReadReceipts(ctx, it) }
            })
            RowDivider()
            SettingRow(Icons.Outlined.Restore, Color(0xFFFF9500), "Keep deleted messages", trailing = {
                IosSwitch(keepDeleted) { keepDeleted = it; Engine.setKeepDeleted(it) }
            })
        }
        FootNote(
            "Read receipts off: people don't see blue ticks when you read their messages from this app. " +
                "Keep deleted messages: when someone deletes a message for everyone, you can still read it here " +
                "(marked \"Deleted by sender\"). It has to arrive on this device before it's deleted.",
        )
        Spacer(Modifier.height(14.dp))
        SectionHeader("Status")
        SectionCard {
            SettingRow(Icons.Outlined.VisibilityOff, Color(0xFF5856D6), "Secretly view status", trailing = {
                IosSwitch(secret) { secret = it; Prefs.setSecretStatus(ctx, it) }
            })
            RowDivider()
            SettingRow(Icons.Outlined.Visibility, Color(0xFF34C759), "Status privacy", onClick = { nav.push(Route.Settings("status-privacy")) })
        }
        FootNote(
            "Secretly view status: status updates you open here are marked seen only on this device, so the person " +
                "won't see you in their viewers list. Opening the same update in WhatsApp on your phone still shows you as a viewer.",
        )
        Spacer(Modifier.height(14.dp))
        SectionHeader("Chat lock")
        SectionCard {
            SettingRow(com.glasschat.ui.glass.LineIcons.Lock, Color(0xFF8E8E93), "Chat lock", onClick = { nav.push(Route.Settings("chatlock")) }, trailing = {
                Text(
                    "${org.locked.size} locked" + if (lock.hasCode) " · code" else "",
                    style = Type.body,
                    color = p.secondaryLabel,
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
            })
        }
        Spacer(Modifier.height(22.dp))
        SectionHeader("Blocked")
        SectionCard {
            if (blocked.isEmpty()) {
                Text("No blocked contacts. Long-press a chat and choose Block.", style = Type.subhead, color = p.secondaryLabel, modifier = Modifier.padding(16.dp))
            }
            blocked.forEachIndexed { i, c ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.jid, c.isGroup, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, style = Type.body, color = p.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Unblock",
                        style = Type.body,
                        color = p.link,
                        modifier = Modifier.clickable {
                            scope.launch {
                                Engine.setBlocked(c.jid, false)
                                    .onSuccess { toast = "${c.name} unblocked"; Engine.refreshChats() }
                                    .onFailure { toast = "Couldn't unblock: ${it.message}" }
                            }
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
    Toast(toast) { toast = null }
}

@Composable
private fun StatusPrivacyScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var failed by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf(Prefs.secretStatus(ctx)) }
    var picking by remember { mutableStateOf<String?>(null) } // "blacklist" or "whitelist"
    var saving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val r = Engine.statusPrivacyLists()
        if (r == null) failed = "offline" else { type = r.first; lists = r.second }
    }
    fun save(kind: String, people: List<String>) {
        saving = true
        scope.launch {
            Engine.setStatusPrivacy(kind, people)
                .onSuccess {
                    type = kind
                    lists = lists + (kind to people)
                    toast = "Status privacy updated"
                }
                .onFailure { toast = it.message ?: "Couldn't change it" }
            saving = false
        }
    }
    SubPage("Status privacy", nav) {
        SectionHeader("Who can see my status updates")
        SectionCard {
            val t = type ?: ""
            ChoiceRow("My contacts", null, t == "contacts", enabled = !saving && type != null) { save("contacts", emptyList()) }
            RowDivider()
            ChoiceRow(
                "My contacts except…",
                lists["blacklist"]?.size?.takeIf { it > 0 }?.let { "$it excluded" },
                t == "blacklist",
                enabled = !saving && type != null,
            ) { picking = "blacklist" }
            RowDivider()
            ChoiceRow(
                "Only share with…",
                lists["whitelist"]?.size?.takeIf { it > 0 }?.let { "$it included" },
                t == "whitelist",
                enabled = !saving && type != null,
            ) { picking = "whitelist" }
        }
        FootNote(
            when {
                failed != null -> "Couldn't load your setting right now ($failed)."
                type == null -> "Loading your setting…"
                saving -> "Saving…"
                else -> "Changes apply to status updates you share after this, from here and from your phone."
            },
        )
        Spacer(Modifier.height(14.dp))
        SectionCard {
            SettingRow(Icons.Outlined.VisibilityOff, Color(0xFF5856D6), "Secretly view status", trailing = {
                IosSwitch(secret) { secret = it; Prefs.setSecretStatus(ctx, it) }
            })
        }
        FootNote("When on, people can't see that you viewed their status from this app.")
    }
    picking?.let { kind ->
        PeoplePicker(
            title = if (kind == "blacklist") "Hide status from…" else "Share status with…",
            initial = lists[kind].orEmpty(),
            onClose = { picking = null },
            onDone = { people ->
                picking = null
                save(kind, people)
            },
        )
    }
    Toast(toast) { toast = null }
}

/** Pick people from your contacts (ticks), e.g. for status privacy lists. */
@Composable
fun PeoplePicker(title: String, initial: List<String>, onClose: () -> Unit, onDone: (List<String>) -> Unit) {
    val p = LocalPalette.current
    androidx.activity.compose.BackHandler(onBack = onClose)
    var contacts by remember { mutableStateOf<List<com.glasschat.engine.Contact>?>(null) }
    var query by remember { mutableStateOf("") }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(initial) } }
    LaunchedEffect(Unit) { contacts = Engine.contacts() }
    val shown = remember(contacts, query) {
        val q = query.trim().lowercase()
        contacts.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q.replace(" ", "")) }
    }
    com.glasschat.ui.glass.GlassOverlay {
        Column(Modifier.fillMaxSize().background(p.groupedBackground).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = Type.body, color = p.link, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = Type.rowTitle, color = p.label)
                    Text("${picked.size} selected", style = Type.footnote, color = p.secondaryLabel)
                }
                Text(
                    "Done",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.link,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onDone(picked.toList()) }.padding(8.dp),
                )
            }
            SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Search name or number")
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                items(shown.size) { i ->
                    val c = shown[i]
                    val on = c.jid in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) picked.remove(c.jid) else picked.add(c.jid) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.glasschat.ui.components.Avatar(c.jid, false, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = Type.body, color = p.label, maxLines = 1)
                            Text(c.phone, style = Type.footnote, color = p.secondaryLabel, maxLines = 1)
                        }
                        ChoiceMark(on, square = false, size = 24.dp)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Notifications

@Composable
private fun NotificationsScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    var privateOn by remember { mutableStateOf(Prefs.notifyPrivate(ctx)) }
    var groups by remember { mutableStateOf(Prefs.notifyGroups(ctx)) }
    var status by remember { mutableStateOf(Prefs.notifyStatus(ctx)) }
    var reactions by remember { mutableStateOf(Prefs.notifyReactions(ctx)) }
    var smart by remember { mutableStateOf(Prefs.smartReplies(ctx)) }
    var badge by remember { mutableStateOf(Prefs.showBadge(ctx)) }
    var badgeMuted by remember { mutableStateOf(Prefs.badgeIncludeMuted(ctx)) }
    var badgeMessages by remember { mutableStateOf(Prefs.badgeCountMessages(ctx)) }
    var hideService by remember { mutableStateOf(Prefs.flag(ctx, "hide_service_notif", false)) }
    val chats by Engine.chats.collectAsState()
    val mutedPrivate = chats.count { !it.isGroup && it.isMuted }
    val mutedGroups = chats.count { it.isGroup && it.isMuted }
    SubPage("Notifications", nav) {
        CardTitle("Notifications for chats")
        SectionCard {
            ToggleNavRow(Icons.Outlined.Person, "Private Chats", onOff(privateOn, mutedPrivate), privateOn, { privateOn = it; Prefs.setFlag(ctx, "n_private", it) }) {
                nav.push(Route.Settings("notif-private"))
            }
            RowDivider2()
            ToggleNavRow(Icons.Outlined.Groups, "Groups", onOff(groups, mutedGroups), groups, { groups = it; Prefs.setFlag(ctx, "n_groups", it) }) {
                nav.push(Route.Settings("notif-groups"))
            }
            RowDivider2()
            ToggleNavRow(Icons.Outlined.DataUsage, "Status updates", if (status) "On" else "Off", status, { status = it; Prefs.setFlag(ctx, "n_status", it) }, null)
            RowDivider2()
            ToggleNavRow(Icons.Outlined.FavoriteBorder, "Reactions", if (reactions) "Messages" else "Off", reactions, { reactions = it; Prefs.setFlag(ctx, "n_reactions", it) }) {
                nav.push(Route.Settings("notif-reactions"))
            }
        }
        Spacer(Modifier.height(14.dp))
        CardTitle("Replies")
        SectionCard {
            SettingRow(com.glasschat.ui.glass.LineIcons.Chat, Color(0xFF34C759), "Smart replies", trailing = {
                IosSwitch(smart) { smart = it; Prefs.setFlag(ctx, "n_smart", it) }
            })
        }
        FootNote("One-tap reply suggestions under message notifications (for example “Ok”, “👍”, “Yes”).")
        Spacer(Modifier.height(14.dp))
        CardTitle("Calls")
        SectionCard {
            ValueRow("Call alerts", "Default") { openChannel(ctx, "calls") }
        }
        FootNote("Calls ring in WhatsApp on your phone; here you get an alert to answer there.")
        Spacer(Modifier.height(14.dp))
        CardTitle("Badge counter")
        SectionCard {
            SwitchRow("Show badge icon", badge) { badge = it; Prefs.setFlag(ctx, "n_badge", it) }
            RowDivider()
            SwitchRow("Include muted chats", badgeMuted) { badgeMuted = it; Prefs.setFlag(ctx, "n_badge_muted", it) }
            RowDivider()
            SwitchRow("Count unread messages", badgeMessages) { badgeMessages = it; Prefs.setFlag(ctx, "n_badge_messages", it) }
        }
        FootNote("The number on the app icon. Off counts chats instead of messages. Some home screens only show a dot.")
        Spacer(Modifier.height(14.dp))
        SectionCard {
            SettingRow(com.glasschat.ui.glass.LineIcons.Bell, Color(0xFFFF3B30), "Sounds and system settings", onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            })
            RowDivider()
            SettingRow(com.glasschat.ui.glass.LineIcons.Bell, Color(0xFF34C759), "Send a test notification", onClick = {
                Notifier.test(ctx)
                toast = "Test notification sent"
            })
            RowDivider()
            SettingRow(Icons.AutoMirrored.Outlined.ReceiptLong, Color(0xFF8E8E93), "Delivery log", onClick = { nav.push(Route.DeliveryLog) })
            RowDivider()
            SettingRow(Icons.Outlined.Sync, Color(0xFFFF9500), "Always-on connection", onClick = { nav.push(Route.BackgroundSetup) })
            RowDivider()
            SettingRow(Icons.Outlined.VisibilityOff, Color.Unspecified, "Hide \"Connected\" notification", trailing = {
                IosSwitch(hideService) { on ->
                    hideService = on
                    Prefs.setFlag(ctx, "hide_service_notif", on)
                    // Android only reads the channel when the notification is posted again.
                    runCatching {
                        androidx.core.app.NotificationManagerCompat.from(ctx).cancel(com.glasschat.service.Notifier.SERVICE_ID)
                        com.glasschat.service.Notifier.updateService(ctx, Engine.conn.value, null)
                    }
                }
            })
        }
        FootNote(
            "Android needs a notification while the app stays connected in the background. Hidden, it's silent and " +
                "minimised: no status-bar icon and not on the lock screen. To remove it completely, turn off " +
                "\"Stay connected (hidden)\" in Android's notification settings for this app.",
        )
    }
    Toast(toast) { toast = null }
}

private fun onOff(on: Boolean, exceptions: Int): String =
    (if (on) "On" else "Off") + if (exceptions > 0) ", $exceptions exception${if (exceptions == 1) "" else "s"}" else ""

private fun openChannel(ctx: android.content.Context, channel: String) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel),
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
        color = LocalPalette.current.accentText,
        modifier = Modifier.padding(start = 32.dp, bottom = 7.dp),
    )
}

/** Telegram-style row: icon, title, state; tap opens details, the switch turns it on/off. */
@Composable
private fun ToggleNavRow(icon: ImageVector, title: String, subtitle: String, on: Boolean, onToggle: (Boolean) -> Unit, onOpen: (() -> Unit)?) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = p.secondaryLabel, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.body, color = p.label)
                Text(subtitle, style = Type.footnote, color = p.secondaryLabel)
            }
        }
        if (onOpen != null) {
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(30.dp).background(p.separator))
        }
        IosSwitch(on, onToggle)
    }
}

@Composable
internal fun SwitchRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
        IosSwitch(on, onChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
        Text(value, style = Type.body, color = p.accentText)
    }
}

/** Private chats or Groups: on/off, previews, sound, and muted chats (exceptions). */
@Composable
private fun ChatNotificationsScreen(nav: Navigator, groups: Boolean) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val onKey = if (groups) "n_groups" else "n_private"
    val previewKey = if (groups) "n_group_previews" else "previews"
    var on by remember { mutableStateOf(if (groups) Prefs.notifyGroups(ctx) else Prefs.notifyPrivate(ctx)) }
    var previews by remember { mutableStateOf(if (groups) Prefs.groupPreviews(ctx) else Prefs.previews(ctx)) }
    var more by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val chats by Engine.chats.collectAsState()
    val muted = chats.filter { it.isGroup == groups && it.isMuted }
    SubPage("Notifications", nav) {
        SectionCard {
            Column(Modifier.padding(top = 12.dp)) { CardTitleInCard("Notify me about…") }
            SwitchRow(if (groups) "New Messages in Groups" else "New Messages in Private Chats", on) { on = it; Prefs.setFlag(ctx, onKey, it) }
        }
        Spacer(Modifier.height(14.dp))
        SectionCard {
            Column(Modifier.padding(top = 12.dp)) { CardTitleInCard("Settings") }
            SwitchRow("Show Message Previews", previews) { previews = it; Prefs.setFlag(ctx, previewKey, it) }
            RowDivider()
            ValueRow("Sound", "Default") { openChannel(ctx, if (groups) Notifier.CH_GROUPS else Notifier.CH_MESSAGES) }
            RowDivider()
            Row(Modifier.fillMaxWidth().clickable { more = !more }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("More Options", style = Type.body, color = p.accentText, modifier = Modifier.weight(1f))
                Icon(
                    if (more) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    null, tint = p.accentText, modifier = Modifier.size(24.dp),
                )
            }
            if (more) {
                RowDivider()
                ValueRow("Vibrate, light, pop-up", "System") { openChannel(ctx, if (groups) Notifier.CH_GROUPS else Notifier.CH_MESSAGES) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionCard {
            Row(Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PersonAdd, null, tint = p.accentText, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Text("Add Exception", style = Type.body, color = p.accentText)
            }
            muted.forEach { c ->
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.jid, c.isGroup, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = Type.body, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Muted", style = Type.footnote, color = p.secondaryLabel)
                    }
                    Text("Unmute", style = Type.body, color = p.link, modifier = Modifier.clickable { Engine.setMutedUntil(c.jid, 0) }.padding(6.dp))
                }
            }
        }
        FootNote("Exceptions are chats you've muted. Muting here also mutes the chat on your phone.")
    }
    if (picking) {
        val candidates = chats.filter { it.isGroup == groups && !it.isMuted && !it.archived }
        com.glasschat.ui.glass.GlassModal(onDismiss = { picking = false }) {
            Column(Modifier.padding(horizontal = 24.dp).imePadding().fillMaxWidth().menuGlass(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}.height(460.dp).padding(vertical = 14.dp)) {
                Text("Mute a chat", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    items(candidates.size, key = { candidates[it].jid }) { i ->
                        val c = candidates[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { Engine.setMutedUntil(c.jid, -1); picking = false }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.jid, c.isGroup, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, style = Type.body, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text("Cancel", style = Type.body, color = p.link, modifier = Modifier.align(Alignment.End).clickable { picking = false }.padding(horizontal = 22.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun CardTitleInCard(text: String) {
    Text(text, style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = LocalPalette.current.accentText, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
}

@Composable
private fun ReactionNotificationsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.notifyReactions(ctx)) }
    var sender by remember { mutableStateOf(Prefs.reactionSender(ctx)) }
    SubPage("Notifications", nav) {
        SectionCard {
            Column(Modifier.padding(top = 12.dp)) { CardTitleInCard("Notify me about…") }
            ToggleNavRow(Icons.Outlined.FavoriteBorder, "Reactions to My Messages", "From everyone", on, { on = it; Prefs.setFlag(ctx, "n_reactions", it) }, null)
        }
        Spacer(Modifier.height(14.dp))
        SectionCard {
            Column(Modifier.padding(top = 12.dp)) { CardTitleInCard("Settings") }
            SwitchRow("Show Sender's Name", sender) { sender = it; Prefs.setFlag(ctx, "n_reaction_sender", it) }
            RowDivider()
            ValueRow("Sound", "Default") { openChannel(ctx, "reactions") }
        }
    }
}

// -----------------------------------------------------------------------------
// Data and Storage

private val KindNames = listOf("photo" to "Photos", "audio" to "Audio", "video" to "Videos", "doc" to "Documents")

private fun kindsSummary(kinds: Set<String>): String = when {
    kinds.isEmpty() -> "No media"
    kinds.size == KindNames.size -> "All media"
    else -> KindNames.filter { it.first in kinds }.joinToString(", ") { it.second }
}

@Composable
private fun DataStorageScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaBytes by remember { mutableStateOf(-1L) }
    var hd by remember { mutableStateOf(Prefs.hdUploads(ctx)) }
    var qualitySheet by remember { mutableStateOf(false) }
    var network by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mediaBytes = Engine.mediaCacheSize() }
    SubPage("Data and Storage", nav) {
        SectionCard {
            SettingRow(com.glasschat.ui.glass.LineIcons.Storage, Color(0xFF8E8E93), "Manage storage", onClick = { confirmClear = true }, trailing = {
                Text(if (mediaBytes < 0) "…" else Fmt.size(mediaBytes), style = Type.body, color = p.secondaryLabel)
            })
        }
        FootNote("Photos, videos, voice notes and documents downloaded to this phone. Tap to clear them; they can be downloaded again.")
        Spacer(Modifier.height(14.dp))
        SectionHeader("Media upload quality")
        SectionCard {
            SettingRow(Icons.Outlined.PhotoCamera, Color(0xFF34C759), "Photo upload quality", onClick = { qualitySheet = true }, trailing = {
                Text(if (hd) "HD quality" else "Standard quality", style = Type.body, color = p.secondaryLabel)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
            })
        }
        FootNote("Standard is faster to send and smaller. HD keeps up to 4096 px and can be about 6 times larger. Videos are always sent as recorded.")
        Spacer(Modifier.height(14.dp))
        SectionHeader("Media auto-download")
        SectionCard {
            listOf("mobile" to "When using mobile data", "wifi" to "When connected on Wi-Fi", "roaming" to "When roaming").forEachIndexed { i, (key, label) ->
                if (i > 0) RowDivider()
                val kinds = remember(version) { Prefs.autoDownloadKinds(ctx, key) }
                Row(
                    Modifier.fillMaxWidth().clickable { network = key }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Type.body, color = p.label)
                        Text(kindsSummary(kinds), style = Type.footnote, color = p.secondaryLabel)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
                }
            }
        }
        FootNote("Stickers always download. Anything else shows a download button.")
    }
    if (qualitySheet) {
        OptionsDialog(
            title = "Media upload quality",
            subtitle = "Select the quality for photos to be sent at in chats.",
            onDismiss = { qualitySheet = false },
        ) {
            ChoiceRow("Standard quality", "Faster to send, smaller file size", !hd) { hd = false; Prefs.setHdUploads(ctx, false); qualitySheet = false }
            RowDivider()
            ChoiceRow("HD quality", "Slower to send, can be 6 times larger", hd) { hd = true; Prefs.setHdUploads(ctx, true); qualitySheet = false }
        }
    }
    network?.let { key ->
        var chosen by remember(key) { mutableStateOf(Prefs.autoDownloadKinds(ctx, key)) }
        OptionsDialog(
            title = when (key) {
                "wifi" -> "When connected on Wi-Fi"
                "roaming" -> "When roaming"
                else -> "When using mobile data"
            },
            subtitle = "Voice messages you play always download.",
            onDismiss = {
                Prefs.setAutoDownloadKinds(ctx, key, chosen)
                version++
                network = null
            },
            done = "Save",
        ) {
            KindNames.forEachIndexed { i, (kind, name) ->
                if (i > 0) RowDivider()
                ChoiceRow(name, null, kind in chosen) { chosen = if (kind in chosen) chosen - kind else chosen + kind }
            }
        }
    }
    if (confirmClear) {
        val cancel: () -> Unit = { confirmClear = false }
        val clear: () -> Unit = {
            confirmClear = false
            scope.launch {
                Engine.clearMediaCache()
                MediaState.forget()
                mediaBytes = Engine.mediaCacheSize()
            }
        }
        GlassAlert(
            title = "Clear downloaded media?",
            message = "Photos, videos, voice notes and documents are removed from this phone. You can download them again from the chat.",
            onDismiss = cancel,
            buttons = listOf("Cancel" to cancel, "Clear" to clear),
            destructiveIndex = 1,
        )
    }
}

/** A centred card of choices (like Android's radio dialogs, in the app's style). */
@Composable
private fun OptionsDialog(title: String, subtitle: String?, onDismiss: () -> Unit, done: String = "Done", content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding().fillMaxWidth().menuGlass(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}.padding(vertical = 18.dp)) {
            Text(title, style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label, modifier = Modifier.padding(horizontal = 20.dp))
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = Type.footnote, color = p.secondaryLabel, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(p.cell)) { content() }
            Spacer(Modifier.height(10.dp))
            Text(
                done,
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.link,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Chat folders

@Composable
private fun ChatFoldersScreen(nav: Navigator) {
    val p = LocalPalette.current
    val org by ChatLists.state.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.glasschat.engine.ChatList?>(null) }
    var renaming by remember { mutableStateOf<com.glasschat.engine.ChatList?>(null) }
    SubPage("Chat Folders", nav) {
        SectionHeader("My folders")
        SectionCard {
            org.lists.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                SettingRow(Icons.Outlined.Folder, Color(0xFF0A84FF), l.name, onClick = { editing = l }, trailing = {
                    Text("${l.chats.size} chat${if (l.chats.size == 1) "" else "s"}", style = Type.body, color = p.secondaryLabel)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
                })
            }
            if (org.lists.isNotEmpty()) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, tint = p.link, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Create a folder", style = Type.body, color = p.link)
            }
        }
        FootNote(
            "Folders show as filters above your chats. Add chats to a folder by long-pressing a chat → Add to list. " +
                "Favorites has its own filter (${org.favorites.size} chat${if (org.favorites.size == 1) "" else "s"}).",
        )
    }
    if (creating) NewListDialog(onDone = { creating = false })
    editing?.let { l ->
        ActionSheet(
            title = l.name,
            actions = listOf(
                SheetAction("Rename") { renaming = l },
                SheetAction("Delete folder", destructive = true) { ChatLists.deleteList(l.id) },
            ),
            onDismiss = { editing = null },
        )
    }
    renaming?.let { l ->
        var name by remember(l.id) { mutableStateOf(l.name) }
        OptionsDialog(
            title = "Rename folder",
            subtitle = null,
            onDismiss = {
                if (name.isNotBlank()) ChatLists.renameList(l.id, name.trim())
                renaming = null
            },
            done = "Save",
        ) {
            Box(Modifier.fillMaxWidth().padding(14.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    textStyle = Type.body.copy(color = p.label),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Devices

@Composable
private fun DevicesScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val me by Engine.me.collectAsState()
    val conn by Engine.conn.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    SubPage("Devices", nav) {
        SectionHeader("This device")
        SectionCard {
            val (label, color) = when (conn) {
                Engine.Conn.Connected -> "Connected" to Color(0xFF34C759)
                Engine.Conn.Connecting -> "Connecting…" to Color(0xFFFF9500)
                else -> "Offline" to p.danger
            }
            SettingRow(com.glasschat.ui.glass.LineIcons.Laptop, Color(0xFF30B0C7), "${ctx.getString(R.string.app_name)} on ${android.os.Build.MODEL}", trailing = {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = Type.body, color = p.secondaryLabel)
            })
            RowDivider()
            SettingRow(com.glasschat.ui.glass.LineIcons.Person, Color(0xFF0A84FF), "Linked to", trailing = {
                Text(me?.phone ?: "", style = Type.body, color = p.secondaryLabel)
            })
        }
        FootNote(
            "This app is one of your WhatsApp linked devices, like WhatsApp Web. Your phone's WhatsApp → Settings → Linked devices " +
                "lists it and can log it out. Other linked devices can only be managed from the phone.",
        )
        Spacer(Modifier.height(14.dp))
        SectionCard {
            SettingRow(Icons.Outlined.Description, Color(0xFF8E8E93), "Engine log", onClick = { nav.push(Route.Log) })
            RowDivider()
            SettingRow(Icons.Outlined.Description, Color(0xFF8E8E93), "Status check", onClick = { nav.push(Route.StatusCheck) })
        }
        Spacer(Modifier.height(22.dp))
        SectionCard {
            Box(Modifier.fillMaxWidth().clickable { confirmLogout = true }.padding(15.dp), contentAlignment = Alignment.Center) {
                Text("Unlink this device", style = Type.body, color = p.danger)
            }
        }
    }
    if (confirmLogout) {
        val cancel: () -> Unit = { confirmLogout = false }
        val unlink: () -> Unit = {
            confirmLogout = false
            scope.launch { Engine.logout() }
        }
        GlassAlert(
            title = "Unlink this device?",
            message = "${ctx.getString(R.string.app_name)} will be removed from Linked devices on your phone and its local chat copy deleted.",
            onDismiss = cancel,
            buttons = listOf("Cancel" to cancel, "Unlink" to unlink),
            destructiveIndex = 1,
        )
    }
}

// -----------------------------------------------------------------------------
// Your profile

/** Square, 640 px JPEG of the middle of a picked photo, ready for WhatsApp. */
private fun squareJpeg(ctx: android.content.Context, uri: Uri): File {
    val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > 2048) {
            val s = 2048f / longSide
            d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val side = minOf(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(crop, 640, 640, true)
    val f = File(ctx.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return f
}

@Composable
fun ProfileScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = remember { Backdrop() }
    val me by Engine.me.collectAsState()
    val conn by Engine.conn.collectAsState()
    val avatarVersion by Engine.avatarVersion.collectAsState()
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var about by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var editInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<File?>(null) }
    val jid = me?.jid ?: ""
    LaunchedEffect(jid, avatarVersion) {
        if (jid.isEmpty()) return@LaunchedEffect
        photo = withContext(Dispatchers.IO) {
            (Engine.fullAvatarPath(jid) ?: Engine.avatarPath(jid))?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
    }
    LaunchedEffect(Unit) { about = Engine.myAbout() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                pending = withContext(Dispatchers.IO) { runCatching { squareJpeg(ctx, uri) }.getOrNull() }
                if (pending == null) toast = "Couldn't read that photo"
            }
        }
    }
    fun upload(file: File?) {
        busy = if (file == null) "Removing photo…" else "Updating photo…"
        scope.launch {
            Engine.setProfilePhoto(file?.absolutePath ?: "")
                .onSuccess { toast = if (file == null) "Photo removed" else "Profile photo updated" }
                .onFailure { toast = "Couldn't change the photo: ${it.message}" }
            file?.delete()
            busy = null
        }
    }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(rememberScrollState())) {
            // Round photo, name, number, About and quick buttons (iPhone style).
            ProfileHeader(
                jid = jid,
                isGroup = false,
                photo = photo,
                name = me?.name?.ifBlank { null } ?: "Your name",
                lines = listOf(me?.phone.orEmpty(), about.orEmpty(), if (conn == Engine.Conn.Connected) "" else "connecting…"),
                actions = listOf(
                    ProfileAction(Icons.Outlined.PhotoCamera, "Photo") {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    },
                    ProfileAction(Icons.Outlined.Edit, "Edit") { editInfo = true },
                    ProfileAction(com.glasschat.ui.glass.LineIcons.Shield, "Privacy") { nav.push(Route.Settings("privacy")) },
                ),
                // Tap your photo to see it big (like anyone else's); change it with the Photo button or ⋯.
                onPhoto = { if (photo != null) viewPhoto = true else menu = true },
            )
            Spacer(Modifier.height(16.dp))
            SectionCard {
                InfoLine(me?.phone ?: "", "Mobile")
                RowDivider()
                InfoLine(me?.name?.ifBlank { null } ?: "—", "Name") { editInfo = true }
                RowDivider()
                InfoLine(about?.ifBlank { null } ?: if (about == null) "…" else "Add an About", "About") { editInfo = true }
            }
            FootNote("Your photo, name and About are your WhatsApp profile: changes show on your phone and to your contacts.")
            Spacer(Modifier.height(60.dp))
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() }
            Spacer(Modifier.weight(1f))
            GlassCircleButton(backdrop, Icons.Rounded.MoreHoriz, "More") { menu = true }
        }
        busy?.let {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(p.groupedBackground).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = Type.subhead, color = p.label)
                }
            }
        }
        if (viewPhoto && jid.isNotEmpty()) ProfilePhotoViewer(jid, false, me?.name ?: "You", onClose = { viewPhoto = false })
        Toast(toast) { toast = null }
    }
    if (menu) {
        ActionSheet(
            title = null,
            actions = listOf(
                SheetAction("Set new photo") { runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                SheetAction("Remove photo", destructive = true) { upload(null) },
            ),
            onDismiss = { menu = false },
        )
    }
    pending?.let { f ->
        val preview = remember(f) { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }
        val cancel: () -> Unit = { f.delete(); pending = null }
        BackHandler(onBack = cancel)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape))
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp).windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cancel", style = Type.body, color = Color.White, modifier = Modifier.clickable(onClick = cancel).padding(12.dp))
                Text(
                    "Set as profile photo",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).clickable {
                        pending = null
                        upload(f)
                    }.padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
        }
    }
    if (editInfo) {
        EditInfoDialog(
            name = me?.name ?: "",
            about = about ?: "",
            onDone = { newName, newAbout ->
                editInfo = false
                if (newName != null && newAbout != null) {
                    scope.launch {
                        busy = "Saving…"
                        val errors = mutableListOf<String>()
                        if (newName.trim() != (me?.name ?: "").trim()) Engine.setPushName(newName).onFailure { errors += "name: ${it.message}" }
                        if (newAbout.trim() != (about ?: "").trim()) {
                            Engine.setAbout(newAbout).onSuccess { about = newAbout.trim() }.onFailure { errors += "about: ${it.message}" }
                        }
                        busy = null
                        toast = if (errors.isEmpty()) "Saved" else "Couldn't save " + errors.joinToString("; ")
                    }
                }
            },
        )
    }
}

@Composable
internal fun ProfileButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun InfoLine(value: String, label: String, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(value, style = Type.body.copy(fontSize = 18.sp), color = p.label)
        Text(label, style = Type.footnote, color = p.secondaryLabel)
    }
}

@Composable
private fun EditInfoDialog(name: String, about: String, onDone: (String?, String?) -> Unit) {
    val p = LocalPalette.current
    var n by remember { mutableStateOf(name) }
    var a by remember { mutableStateOf(about) }
    BackHandler { onDone(null, null) }
    com.glasschat.ui.glass.GlassOverlay {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { onDone(null, null) }
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .menuGlass(RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(18.dp),
            ) {
                Text("Edit info", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label)
                Spacer(Modifier.height(14.dp))
                Text("NAME", style = Type.footnote, color = p.secondaryLabel)
                Spacer(Modifier.height(4.dp))
                EditField(n, { n = it.take(25) }, "Your name")
                Text("${n.length}/25", style = Type.caption, color = p.tertiaryLabel, modifier = Modifier.align(Alignment.End))
                Spacer(Modifier.height(8.dp))
                Text("ABOUT", style = Type.footnote, color = p.secondaryLabel)
                Spacer(Modifier.height(4.dp))
                EditField(a, { a = it.take(139) }, "Available")
                Text("${a.length}/139", style = Type.caption, color = p.tertiaryLabel, modifier = Modifier.align(Alignment.End))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Cancel", style = Type.body, color = p.link, modifier = Modifier.clickable { onDone(null, null) }.padding(10.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Save",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (n.isBlank()) p.tertiaryLabel else p.link,
                        modifier = Modifier.clickable(enabled = n.isNotBlank()) { onDone(n, a) }.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.cell).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, style = Type.body, color = p.tertiaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = Type.body.copy(color = p.label),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// -----------------------------------------------------------------------------
// Who can see my personal info (WhatsApp's own privacy settings)

private val VisibilityNames = mapOf(
    "all" to "Everyone",
    "contacts" to "My contacts",
    "contact_blacklist" to "My contacts except…",
    "none" to "Nobody",
    "match_last_seen" to "Same as last seen",
)

@Composable
private fun PersonalInfoScreen(nav: Navigator) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Map<String, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Engine.privacySettings().onSuccess { settings = it }.onFailure { error = it.message ?: "offline" }
    }
    val rows = listOf(
        Triple("last", "Last seen and online", Icons.Outlined.Person),
        Triple("profile", "Profile picture", Icons.Outlined.PhotoCamera),
        Triple("status", "About", Icons.Outlined.Edit),
        Triple("groupadd", "Groups", Icons.Outlined.Groups),
    )
    SubPage("Account privacy", nav) {
        SectionHeader("Who can see my personal info")
        SectionCard {
            rows.forEachIndexed { i, (key, title, _) ->
                if (i > 0) RowDivider()
                val value = settings?.get(key)
                val sub = when {
                    settings == null -> if (error != null) "Not available offline" else "Loading…"
                    key == "last" -> {
                        val online = settings?.get("online")
                        (VisibilityNames[value] ?: "—") + if (online == "all") " · online: everyone" else ""
                    }
                    key == "groupadd" -> "Who can add me: " + (VisibilityNames[value] ?: "—")
                    else -> VisibilityNames[value] ?: "—"
                }
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = settings != null) { editing = key }.padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Text(title, style = Type.body, color = p.label)
                    Text(sub, style = Type.footnote, color = p.secondaryLabel)
                }
            }
        }
        FootNote(
            error?.let { "Couldn't load your settings ($it). Connect to the internet and open this page again." }
                ?: "These are your WhatsApp settings: changing them here changes them on your phone too. " +
                    "If you don't share your last seen or online, you can't see other people's either.",
        )
    }
    editing?.let { key ->
        val current = settings?.get(key)
        val options = when (key) {
            "groupadd" -> listOf("all", "contacts", "contact_blacklist")
            else -> listOf("all", "contacts", "contact_blacklist", "none")
        }
        var onlineChoice by remember(key) { mutableStateOf(settings?.get("online") ?: "all") }
        OptionsDialog(
            title = rows.first { it.first == key }.second,
            subtitle = if (key == "groupadd") "Who can add me to groups" else "Who can see my ${rows.first { it.first == key }.second.lowercase()}",
            onDismiss = { editing = null },
        ) {
            options.forEachIndexed { i, opt ->
                if (i > 0) RowDivider()
                val listOnly = opt == "contact_blacklist"
                ChoiceRow(
                    VisibilityNames[opt] ?: opt,
                    if (listOnly) "The list can only be edited on your phone" else null,
                    current == opt,
                    enabled = !listOnly || current == opt,
                ) {
                    scope.launch {
                        Engine.setPrivacy(key, opt)
                            .onSuccess { settings = settings.orEmpty() + (key to opt); toast = "Saved" }
                            .onFailure { toast = "Couldn't change it: ${it.message}" }
                    }
                }
            }
            if (key == "last") {
                RowDivider()
                Column(Modifier.padding(top = 10.dp)) { CardTitleInCard("Who can see when I'm online") }
                listOf("all", "match_last_seen").forEachIndexed { i, opt ->
                    if (i > 0) RowDivider()
                    ChoiceRow(VisibilityNames[opt] ?: opt, null, onlineChoice == opt) {
                        onlineChoice = opt
                        scope.launch {
                            Engine.setPrivacy("online", opt)
                                .onSuccess { settings = settings.orEmpty() + ("online" to opt); toast = "Saved" }
                                .onFailure { toast = "Couldn't change it: ${it.message}" }
                        }
                    }
                }
            }
        }
    }
    Toast(toast) { toast = null }
}
