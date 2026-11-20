package com.glasschat.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasschat.engine.ChatLock
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.engine.previewFor
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.delay

/** Chats you opened from search, newest first (kept on this phone). */
object RecentSearches {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("recent_search", Context.MODE_PRIVATE)
    fun get(ctx: Context): List<String> = sp(ctx).getString("jids", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun add(ctx: Context, jid: String) {
        val list = (listOf(jid) + get(ctx).filter { it != jid }).take(12)
        sp(ctx).edit().putString("jids", list.joinToString("\n")).apply()
    }
    fun clear(ctx: Context) = sp(ctx).edit().remove("jids").apply()
}

private data class Chip(val key: String, val label: String, val icon: ImageVector)

private val Chips = listOf(
    Chip("unread", "Unread", Icons.Outlined.MarkChatUnread),
    Chip("photos", "Photos", Icons.Outlined.Image),
    Chip("videos", "Videos", Icons.Outlined.Videocam),
    Chip("links", "Links", Icons.Outlined.Link),
    Chip("docs", "Documents", Icons.Outlined.Description),
    Chip("audio", "Audio", Icons.Outlined.Headphones),
)

/**
 * Search across all chats, like WhatsApp: filter chips, your recent
 * searches, then matching chats and messages (photos and videos as a grid).
 */
@Composable
fun GlobalSearchScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val chats by Engine.chats.collectAsState()
    val lock by ChatLock.settings.collectAsState()
    val names = remember(chats) { chats.associate { it.jid to it.name } }
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(RecentSearches.get(ctx)) }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BackHandler { if (chip != null) chip = null else nav.pop() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val q = query.trim()
    val messageKind = chip?.takeIf { it != "unread" }
    LaunchedEffect(q, messageKind) {
        if (q.isEmpty() && messageKind == null) {
            results = emptyList()
            return@LaunchedEffect
        }
        loading = true
        delay(200) // wait for typing to pause
        results = Engine.searchChat("", q, messageKind ?: "all", 300)
        loading = false
    }
    val matchingChats = remember(chats, q, chip) {
        val l = q.lowercase()
        when {
            chip == "unread" -> chats.filter { it.hasUnread && !it.archived && (l.isEmpty() || it.name.lowercase().contains(l)) }
            chip != null || l.isEmpty() -> emptyList()
            else -> chats.filter { it.name.lowercase().contains(l) || it.phone.contains(l) }.take(30)
        }
    }

    fun openChat(jid: String) {
        RecentSearches.add(ctx, jid)
        keyboard?.hide()
        nav.push(Route.Chat(jid))
    }
    fun openMessage(m: Message) {
        RecentSearches.add(ctx, m.chat)
        keyboard?.hide()
        Engine.requestJump(m.chat, m)
        nav.push(Route.Chat(m.chat))
    }

    Column(Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        // Search bar with back arrow.
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(p.fill)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { nav.pop() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.label, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search chats and messages", style = Type.body, color = p.secondaryLabel)
                BasicTextField(
                    value = query,
                    onValueChange = { t ->
                        // Typing the chat-lock secret code opens your locked chats.
                        if (lock.hasCode && t.trim().length >= 4 && ChatLock.checkCode(ctx, t)) {
                            query = ""
                            nav.push(Route.LockedChats)
                        } else {
                            query = t
                        }
                    },
                    singleLine = true,
                    textStyle = Type.body.copy(color = p.label),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Clear", tint = p.secondaryLabel, modifier = Modifier.size(20.dp))
                }
            }
        }
        // Filter chips.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chips.forEach { c ->
                val on = chip == c.key
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) p.accent.copy(alpha = 0.22f) else p.fill)
                        .clickable { chip = if (on) null else c.key }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, null, tint = if (on) p.accentText else p.label, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(c.label, style = Type.subhead.copy(fontWeight = FontWeight.Medium), color = if (on) p.accentText else p.label)
                }
            }
        }

        when {
            // Nothing typed, no filter: recent searches.
            q.isEmpty() && chip == null -> {
                if (recent.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", style = Type.body, color = p.secondaryLabel, modifier = Modifier.weight(1f))
                        Text(
                            "Clear all",
                            style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = p.label,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.fill).clickable {
                                RecentSearches.clear(ctx)
                                recent = emptyList()
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        recent.forEach { jid ->
                            val name = names[jid] ?: "+" + jid.substringBefore('@')
                            Column(
                                Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).clickable { openChat(jid) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(jid, jid.endsWith("@g.us"), 66.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(name, style = Type.footnote, color = p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    Text(
                        "Search for chats, messages, photos, videos, links and documents.",
                        style = Type.subhead,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            // Photos / videos: a grid across all chats.
            chip == "photos" || chip == "videos" -> {
                if (results.isEmpty() && !loading) NoResults()
                LazyVerticalGrid(
                    GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.chat + "/" + it.id }) { m ->
                        val thumb = remember(m.id) { messageThumb(m) }
                        Box(Modifier.aspectRatio(1f).background(p.fill).clickable { openMessage(m) }) {
                            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (m.type != "image") {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp))
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (matchingChats.isNotEmpty()) {
                    item(key = "chats-h") { SearchHeader(if (chip == "unread") "Unread chats" else "Chats") }
                    items(matchingChats, key = { "c/" + it.jid }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openChat(c.jid) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.jid, c.isGroup, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = Type.rowTitle.copy(fontWeight = FontWeight.Normal), color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.unread > 0) Text("${c.unread} unread", style = Type.footnote, color = p.accentText)
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    item(key = "msgs-h") { SearchHeader("Messages") }
                    items(results, key = { "m/" + it.chat + "/" + it.id }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openMessage(m) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(m.chat, m.chat.endsWith("@g.us"), 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        names[m.chat] ?: "+" + m.chat.substringBefore('@'),
                                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                                        color = p.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(Fmt.listStamp(ctx, m.ts), style = Type.footnote, color = p.secondaryLabel)
                                }
                                val who = if (m.fromMe) "You: " else if (m.chat.endsWith("@g.us")) m.senderName.removePrefix("~ ") + ": " else ""
                                val body = m.extra?.optString("fileName")?.takeIf { it.isNotBlank() && m.type == "document" }
                                    ?: m.text.ifBlank { previewFor(m.type, "", false) }
                                Text(who + body, style = Type.subhead, color = p.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (matchingChats.isEmpty() && results.isEmpty() && !loading) item(key = "none") { NoResults() }
            }
        }
    }
}

@Composable
private fun SearchHeader(text: String) {
    Text(
        text,
        style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun NoResults() {
    Text(
        "No results",
        style = Type.subhead,
        color = LocalPalette.current.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}
