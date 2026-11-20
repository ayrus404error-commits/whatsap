package com.glasschat.ui.screens

import androidx.compose.material.icons.outlined.FileDownload
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.engine.MessageInfo
import com.glasschat.engine.ReceiptEntry
import com.glasschat.engine.previewFor
import com.glasschat.media.MediaFiles
import com.glasschat.media.MediaState
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.Ticks
import com.glasschat.ui.components.Toast
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.glass.menuGlass
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

val QuickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

/** Message types that can be forwarded (WhatsApp re-sends the same media). */
val ForwardableTypes = setOf("text", "image", "video", "gif", "voice", "audio", "document", "sticker", "location", "contact")

fun canForward(m: Message) = !m.revoked && m.type in ForwardableTypes && m.extra?.optBoolean("viewOnce") != true

/** Everything you can do with a message, from a long-press. */
data class MessageActions(
    val onReact: (String) -> Unit,
    val onMoreEmoji: () -> Unit,
    val onReply: () -> Unit,
    val onForward: () -> Unit,
    val onCopy: () -> Unit,
    val onInfo: () -> Unit,
    val onStar: () -> Unit,
    val onKeep: () -> Unit,
    val onPin: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onMore: () -> Unit,
    val onSave: () -> Unit = {},
)

/**
 * The long-press menu: reactions (with + for any emoji), the message, and
 * Reply, Forward, Copy, Info, Keep or Star, Pin, Edit, Delete and More….
 */
@Composable
fun MessageMenu(
    m: Message,
    isGroup: Boolean,
    disappearing: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    actions: MessageActions,
) {
    val p = LocalPalette.current
    val scale = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 520f)) }
    val mine = m.reactions.firstOrNull { it.mine }?.emoji
    val usable = !m.revoked && m.type != "waiting"
    val canEdit = m.fromMe && m.type == "text" && !m.revoked && System.currentTimeMillis() - m.ts < 15 * 60_000L && m.status >= Message.SENT
    com.glasschat.ui.glass.GlassOverlay {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (p.dark) 0.35f else 0.15f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start,
            ) {
                if (usable) {
                    val pill = RoundedCornerShape(50)
                    Row(
                        Modifier
                            .menuGlass(pill)
                            .clip(pill)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuickReactions.forEach { e ->
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (e == mine) p.fill else Color.Transparent)
                                    .clickable { actions.onReact(e) },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 26.sp) }
                        }
                        if (mine != null && mine !in QuickReactions) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(p.fill).clickable { actions.onReact(mine) }, contentAlignment = Alignment.Center) {
                                Text(mine, fontSize = 26.sp)
                            }
                        }
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(p.fill).clickable(onClick = actions.onMoreEmoji),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, "More reactions", tint = p.secondaryLabel, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Box(Modifier.width(IntrinsicSize.Max).heightIn(max = 260.dp).clip(RoundedCornerShape(18.dp))) {
                    CompositionLocalProvider(LocalBubbleInteractive provides false) {
                        Bubble(BubbleSpec(m.copy(reactions = emptyList()), firstInRun = true, lastInRun = true, showName = isGroup && !m.fromMe))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val menuShape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .width(250.dp)
                        .menuGlass(menuShape)
                        .clip(menuShape),
                ) {
                    // Like WhatsApp on iPhone: the essentials here, the rest under More….
                    if (usable) {
                        MenuItem("Reply", Icons.AutoMirrored.Rounded.Reply, onClick = actions.onReply)
                        MenuDivider()
                    }
                    if (canForward(m)) {
                        MenuItem("Forward", Icons.AutoMirrored.Outlined.Forward, onClick = actions.onForward)
                        MenuDivider()
                    }
                    if (!m.revoked && (m.text.isNotBlank() || ((m.type == "image" || m.type == "video" || m.type == "gif") && m.extra?.optBoolean("viewOnce") != true))) {
                        MenuItem("Copy", Icons.Outlined.ContentCopy, onClick = actions.onCopy)
                        MenuDivider()
                    }
                    if (usable && m.type in setOf("image", "video", "gif", "document", "audio", "voice", "sticker") && m.extra?.optBoolean("viewOnce") != true) {
                        MenuItem("Save", com.glasschat.ui.glass.LineIcons.SaveDown, onClick = actions.onSave)
                        MenuDivider()
                    }
                    MenuItem("Delete", Icons.Outlined.Delete, destructive = true, onClick = actions.onDelete)
                    MenuSectionGap()
                    MenuItem("More…", Icons.Rounded.MoreHoriz, onClick = actions.onMore)
                }
            }
        }
    }
}

/** The thicker gap before "More…", as on iPhone. */
@Composable
private fun MenuSectionGap() {
    // A plain hairline, same as between the other items.
    MenuDivider()
}

// -----------------------------------------------------------------------------
// Emoji picker for reactions

private val EmojiGroups: List<Pair<String, String>> = listOf(
    "😀" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖",
    "👍" to "👍 👎 👌 🤌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 🦾 🫶 👀 👁️ 👅 👄 🧠 🫀 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙋 🙅 🙆 💁 🤷 🤦 🙇 🧘 🏃 🚶 💃 🕺 👫 👪",
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 ⚡ 🎉 🎊 🎈 🎁 🏆 🥇 ✅ ❌ ❓ ❗ ‼️ ⁉️ 💤 🆗 🆒 🆕 🔝",
    "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐬 🐳 🐟 🐠 🦈 🐊 🐘 🦒 🐄 🐎 🐑 🐐 🐕 🐈 🌵 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌺 🌻 🌹 🌷 🌼 🌸 💐 🌞 🌝 🌛 🌈 ☁️ ⛄ ❄️ 🌊",
    "🍕" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🌽 🥕 🧄 🧅 🥔 🍞 🥐 🧀 🥚 🍳 🥞 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍚 🍙 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🧃 🥤 🍺 🍻 🥂 🍷 🍸 🍹",
    "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏏 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏋️ 🤸 🚴 🏊 🧗 🎯 🎮 🕹️ 🎲 🧩 ♟️ 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻",
    "✈️" to "🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚜 🏍️ 🛵 🚲 🛴 🚨 🚆 🚇 🚊 🚝 ✈️ 🛫 🛬 🚁 🚀 🛸 ⛵ 🚤 🛳️ 🚢 ⚓ 🗺️ 🏔️ ⛰️ 🏕️ 🏖️ 🏝️ 🏜️ 🏞️ 🏟️ 🏛️ 🏨 🏠 🏡 🏢 🏥 🏦 🏪 🏫 ⛪ 🕌 🛕 🗼 🗽 🌉 🎡 🎢 🌅 🌄 🌇 🌃",
    "💡" to "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⏳ 💡 🔦 🕯️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 🛠️ ⚙️ 🔑 🗝️ 🔒 🔓 📌 📍 📎 ✂️ 📝 ✏️ 📚 📖 📒 📅 📆 📊 📈 📉 🗂️ 📦 📫 ✉️ 📩 🎀 🛍️ 💊 💉 🩺",
)

/** Pick any emoji to react with. */
@Composable
fun EmojiPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var group by remember { mutableIntStateOf(0) }
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss, alignment = Alignment.BottomCenter, dim = 0.2f) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
                    .height(420.dp)
                    .menuGlass(shape)
                    .clip(shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EmojiGroups.forEachIndexed { i, (icon, _) ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (i == group) p.fill else Color.Transparent).clickable { group = i },
                            contentAlignment = Alignment.Center,
                        ) { Text(icon, fontSize = 22.sp) }
                    }
                }
                HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                val list = remember(group) { EmojiGroups[group].second.split(' ').filter { it.isNotBlank() } }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list) { e ->
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onPick(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 27.sp) }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Forwarding

/** Choose up to 5 chats to forward to. */
@Composable
fun ForwardPicker(
    fromChat: String,
    ids: List<String>,
    onClose: () -> Unit,
    onDone: (String) -> Unit,
    previewText: String = "",
    previewThumb: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SendToSheet(
            title = if (ids.size > 1) "Forward ${ids.size} messages" else "Send to",
            previewText = previewText.ifBlank { if (ids.size > 1) "${ids.size} messages" else "Message" },
            previewThumb = previewThumb,
            onClose = onClose,
            onSend = { targets, _ ->
                if (!sending) {
                    sending = true
                    scope.launch {
                        Engine.forward(fromChat, ids, targets.map { it.jid })
                            .onSuccess { n -> onDone(if (n == 1) "Forwarded" else "Forwarded $n messages") }
                            .onFailure { toast = it.message ?: "Couldn't forward"; sending = false }
                    }
                }
            },
        )
        Toast(toast) { toast = null }
    }
}

// -----------------------------------------------------------------------------
// Message info

/** Who got and read one of your messages, and when. */
@Composable
fun MessageInfoScreen(m: Message, isGroup: Boolean, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<MessageInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(m.id) {
        info = Engine.messageInfo(m.chat, m.id)
        loaded = true
    }
    fun stamp(ts: Long) = if (ts <= 0) "" else "${Fmt.dayHeader(ts)}, ${Fmt.time(ctx, ts)}"
    Box(
        Modifier
            .fillMaxSize()
            .background(p.groupedBackground)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    "Back",
                    tint = p.link,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(6.dp),
                )
                Text("Message info", style = Type.title, color = p.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), contentAlignment = Alignment.CenterEnd) {
                CompositionLocalProvider(LocalBubbleInteractive provides false) {
                    Bubble(BubbleSpec(m.copy(reactions = emptyList()), firstInRun = true, lastInRun = true, showName = false))
                }
            }
            val i = info
            if (!loaded) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (!isGroup) {
                SectionCard {
                    val readTs = i?.read?.firstOrNull()?.ts ?: i?.played?.firstOrNull()?.ts ?: 0L
                    val delTs = i?.delivered?.firstOrNull()?.ts ?: 0L
                    val status = i?.status ?: m.status
                    InfoRow(Message.READ, "Read", if (status >= Message.READ) stamp(readTs).ifBlank { "Yes" } else "—")
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = p.separator, thickness = 0.5.dp)
                    if (m.type == "voice" || m.type == "video") {
                        InfoRow(Message.PLAYED, "Played", if (status >= Message.PLAYED) stamp(i?.played?.firstOrNull()?.ts ?: 0L).ifBlank { "Yes" } else "—")
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = p.separator, thickness = 0.5.dp)
                    }
                    InfoRow(Message.DELIVERED, "Delivered", if (status >= Message.DELIVERED) stamp(delTs.takeIf { it > 0 } ?: readTs).ifBlank { "Yes" } else "—")
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = p.separator, thickness = 0.5.dp)
                    InfoRow(Message.SENT, "Sent", stamp(i?.sent ?: m.ts))
                }
            } else {
                val read = (i?.played.orEmpty() + i?.read.orEmpty())
                if (read.isNotEmpty()) {
                    SectionHeader("Read by")
                    SectionCard { ReceiptList(read, ::stamp) }
                    Spacer(Modifier.height(20.dp))
                }
                SectionHeader("Delivered to")
                SectionCard {
                    val del = i?.delivered.orEmpty()
                    if (del.isEmpty()) {
                        Text(
                            if (read.isEmpty()) "No one yet" else "Everyone above",
                            style = Type.subhead,
                            color = p.secondaryLabel,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        ReceiptList(del, ::stamp)
                    }
                }
                Text(
                    "Sent ${stamp(i?.sent ?: m.ts)}",
                    style = Type.footnote,
                    color = p.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(status: Int, label: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(26.dp)) { Ticks(status, color = p.secondaryLabel, size = 18.dp) }
        Spacer(Modifier.width(10.dp))
        Text(label, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
        Text(value, style = Type.subhead, color = p.secondaryLabel)
    }
}

@Composable
private fun ReceiptList(list: List<ReceiptEntry>, stamp: (Long) -> String) {
    val p = LocalPalette.current
    list.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = p.separator, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.jid, false, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(r.name.ifBlank { "+" + r.jid.substringBefore('@') }, style = Type.body, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(stamp(r.ts), style = Type.footnote, color = p.secondaryLabel)
        }
    }
}

// -----------------------------------------------------------------------------
// Pinned messages banner

/** "📌 Pinned message" under the chat header; tap to see it, tap again for the next pin. */
@Composable
fun PinnedBanner(pinned: List<Message>, index: Int, modifier: Modifier, onTap: () -> Unit) {
    val p = LocalPalette.current
    val m = pinned.getOrNull(index % pinned.size.coerceAtLeast(1)) ?: return
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .glassShadow(shape, 12.dp, Color.Black.copy(alpha = if (p.dark) 0.35f else 0.1f), 2.dp)
            .clip(shape)
            .frosted(shape, if (p.dark) Color(0xEB1C1C1E) else Color(0xF2FFFFFF), 0.6f)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pinned.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = 3.dp, height = (26 / pinned.size).dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == index % pinned.size) p.accent else p.secondaryLabel.copy(alpha = 0.35f)),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Rounded.PushPin, null, tint = p.accentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pinned.size > 1) "Pinned message ${index % pinned.size + 1} of ${pinned.size}" else "Pinned message",
                style = Type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = p.accentText,
            )
            Text(previewFor(m.type, m.text, m.revoked), style = Type.subhead, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -----------------------------------------------------------------------------
// Sharing

/** Shares a message's text, or its photo/video/file, with another app. */
suspend fun shareMessage(ctx: android.content.Context, m: Message): String? {
    if (m.type in setOf("image", "video", "gif", "voice", "audio", "document", "sticker")) {
        val path = m.local.ifBlank { null } ?: MediaState.fetch(m.chat, m.id).getOrNull()
        if (path == null) return "Couldn't download it to share"
        MediaFiles.share(ctx, path, m.extra?.optString("mime").orEmpty())
        return null
    }
    val text = when (m.type) {
        "location" -> m.extra?.let { "https://maps.google.com/?q=${it.optDouble("lat")},${it.optDouble("lng")}" } ?: m.text
        else -> m.text
    }
    if (text.isBlank()) return "Nothing to share"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    return runCatching { ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .exceptionOrNull()?.let { "Couldn't share" }
}

// -----------------------------------------------------------------------------
// Selecting several messages

/** Replaces the chat header while messages are selected. */
@Composable
fun SelectionHeader(backdrop: com.glasschat.ui.glass.Backdrop, count: Int, onCancel: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.glasschat.ui.glass.GlassCapsule(
            backdrop,
            Modifier.fillMaxWidth().height(46.dp),
            radius = 23.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text("$count selected", style = Type.title.copy(fontSize = 16.sp), color = p.label, modifier = Modifier.weight(1f))
            Text(
                "Cancel",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.link,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Star · Copy · Forward · Delete for the selected messages. */
@Composable
fun SelectionBar(
    backdrop: com.glasschat.ui.glass.Backdrop,
    allStarred: Boolean,
    canCopy: Boolean,
    canForward: Boolean,
    onStar: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassCapsule(
        backdrop,
        Modifier.fillMaxWidth().height(58.dp),
        radius = 29.dp,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        BarAction(if (allStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (allStarred) "Unstar" else "Star", p.label, true, Modifier.weight(1f), onStar)
        BarAction(Icons.Outlined.ContentCopy, "Copy", p.label, canCopy, Modifier.weight(1f), onCopy)
        BarAction(Icons.AutoMirrored.Outlined.Forward, "Forward", p.label, canForward, Modifier.weight(1f), onForward)
        BarAction(Icons.Outlined.Delete, "Delete", p.danger, true, Modifier.weight(1f), onDelete)
    }
}

@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = if (enabled) color else color.copy(alpha = 0.3f)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, style = Type.caption.copy(fontSize = 11.sp), color = c)
    }
}
