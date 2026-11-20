package com.glasschat.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasschat.engine.Chat
import com.glasschat.engine.Contact
import com.glasschat.engine.Engine
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

/** One row target in "Send to": a chat (or a contact without a chat yet). */
data class SendTarget(val jid: String, val name: String, val isGroup: Boolean, val subtitle: String = "")

/**
 * "Send to" like WhatsApp on iPhone: × and title on top, search, then
 * "Frequently contacted" and "Recent chats" with round tick boxes (pick
 * several), and at the bottom a preview of what you're sending plus Send.
 */
@Composable
fun SendToSheet(
    title: String = "Send to",
    previewText: String,
    previewThumb: ImageBitmap? = null,
    allowStatus: Boolean = false,
    onClose: () -> Unit,
    onSend: (targets: List<SendTarget>, toStatus: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    BackHandler(onBack = onClose)
    val chats by Engine.chats.collectAsState()
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf(false) }
    val visible = chats.filter { !it.archived && it.jid != Engine.STATUS_CHAT }
    // Frequently contacted: the busiest recent chats (most unread + most recent).
    val frequent = remember(visible) { visible.sortedByDescending { it.lastTs }.take(5) }
    val q = query.trim().lowercase()
    val filtered = remember(visible, q) {
        if (q.isEmpty()) visible.filter { c -> frequent.none { it.jid == c.jid } }
        else visible.filter { it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q.replace(" ", "")) }
    }
    val total = picked.size + if (status) 1 else 0

    fun toggle(jid: String) {
        if (jid in picked) picked.remove(jid) else picked.add(jid)
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
        val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 10.dp)
                .clip(shape)
                .background(p.groupedBackground)
                .imePadding(),
        ) {
            // Header: × · title · (spacer)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Close", tint = p.label, modifier = Modifier.size(24.dp)) }
                Text(title, style = Type.rowTitle, color = p.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(46.dp))
            }
            // Search
            Row(
                Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.secondaryLabel, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", style = Type.body, color = p.secondaryLabel)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = Type.body.copy(color = p.label), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (allowStatus && q.isEmpty()) {
                    item(key = "status") {
                        Spacer(Modifier.height(14.dp))
                        PickCard {
                            PickRow(
                                leading = {
                                    Box(Modifier.size(46.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(com.glasschat.ui.glass.TabIcons.Updates, null, tint = p.label, modifier = Modifier.size(26.dp))
                                    }
                                },
                                title = "My status",
                                subtitle = null,
                                selected = status,
                                onClick = { status = !status },
                            )
                        }
                    }
                }
                if (q.isEmpty() && frequent.isNotEmpty()) {
                    item(key = "freq-h") { PickHeader("Frequently contacted") }
                    item(key = "freq") {
                        PickCard {
                            frequent.forEachIndexed { i, c ->
                                if (i > 0) PickDivider()
                                ChatPickRow(c, c.jid in picked) { toggle(c.jid) }
                            }
                        }
                    }
                }
                if (filtered.isNotEmpty()) {
                    item(key = "recent-h") { PickHeader(if (q.isEmpty()) "Recent chats" else "Chats") }
                    itemsIndexed(filtered, key = { _, c -> "r/" + c.jid }) { i, c ->
                        // Each row is its own item (long lists stay fast); the card look comes from the row shape.
                        val first = i == 0
                        val last = i == filtered.lastIndex
                        Box(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .clip(RoundedCornerShape(topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp, bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp))
                                .background(p.cell),
                        ) {
                            Column {
                                if (!first) PickDivider()
                                ChatPickRow(c, c.jid in picked) { toggle(c.jid) }
                            }
                        }
                    }
                }
            }
            // Bottom: what you're sending, and Send.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(p.cell)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (previewThumb != null) {
                    Image(previewThumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(p.label.copy(alpha = 0.06f)).padding(10.dp),
                ) {
                    if (total > 0) {
                        Text(
                            picked.mapNotNull { j -> chats.find { it.jid == j }?.name }.let { if (status) listOf("My status") + it else it }.joinToString(", "),
                            style = Type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = p.accentText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(previewText, style = Type.subhead, color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (total > 0) p.accent else p.label.copy(alpha = 0.15f))
                        .clickable(enabled = total > 0) {
                            onSend(picked.mapNotNull { j -> chats.find { it.jid == j }?.let { SendTarget(it.jid, it.name, it.isGroup) } }, status)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun PickHeader(text: String) {
    Text(
        text,
        style = Type.rowTitle,
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 30.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun PickCard(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalPalette.current.cell)) { content() }
}

@Composable
private fun PickDivider() {
    HorizontalDivider(Modifier.padding(start = 76.dp), color = LocalPalette.current.separator, thickness = 0.5.dp)
}

@Composable
private fun ChatPickRow(c: Chat, selected: Boolean, onClick: () -> Unit) {
    PickRow(
        leading = { Avatar(c.jid, c.isGroup, 46.dp) },
        title = c.name,
        subtitle = null,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun PickRow(leading: @Composable () -> Unit, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle.copy(fontWeight = FontWeight.Normal), color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ChoiceMark(selected, square = false, size = 26.dp)
    }
}

// -----------------------------------------------------------------------------
// New group

/** New group: pick people, then name the group. */
@Composable
fun NewGroupScreen(nav: Navigator) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Contact>() }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { contacts = Engine.contacts() }
    BackHandler { if (naming) naming = false else nav.pop() }
    val filtered = remember(contacts, query) {
        val q = query.trim().lowercase()
        contacts.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q.replace(" ", "")) }
    }

    Column(Modifier.fillMaxSize().background(p.groupedBackground).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (naming) "Back" else "Cancel",
                style = Type.body,
                color = p.link,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { if (naming) naming = false else nav.pop() }.padding(8.dp),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (naming) "New group" else "Add members", style = Type.rowTitle, color = p.label)
                Text("${picked.size} selected", style = Type.footnote, color = p.secondaryLabel)
            }
            Text(
                if (naming) "Create" else "Next",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (picked.isEmpty() || (naming && name.isBlank()) || busy) p.tertiaryLabel else p.link,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = picked.isNotEmpty() && !busy) {
                    if (!naming) {
                        naming = true
                    } else if (name.isNotBlank()) {
                        busy = true
                        error = null
                        scope.launch {
                            Engine.createGroup(name, picked.map { it.jid })
                                .onSuccess { jid -> nav.replaceTop(Route.Chat(jid)) }
                                .onFailure { error = it.message ?: "Couldn't create the group" }
                            busy = false
                        }
                    }
                }.padding(8.dp),
            )
        }
        if (picked.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                picked.forEach { c ->
                    Column(Modifier.width(62.dp).clickable { picked.remove(c) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(c.jid, false, 52.dp)
                        Text(c.name.substringBefore(' '), style = Type.caption, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (naming) {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.cell).padding(16.dp)) {
                Text("Group name", style = Type.footnote, color = p.secondaryLabel)
                Spacer(Modifier.height(6.dp))
                Box {
                    if (name.isEmpty()) Text("e.g. Family, Team Gangtok", style = Type.rowTitle.copy(fontWeight = FontWeight.Normal), color = p.tertiaryLabel)
                    BasicTextField(
                        name,
                        { name = it.take(25) },
                        singleLine = true,
                        textStyle = Type.rowTitle.copy(color = p.label, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("${name.length}/25", style = Type.caption, color = p.secondaryLabel, modifier = Modifier.align(Alignment.End))
                if (busy) CircularProgressIndicator(color = p.accent, modifier = Modifier.padding(top = 10.dp).size(24.dp))
                error?.let { Text(it, style = Type.footnote, color = p.danger, modifier = Modifier.padding(top = 8.dp)) }
            }
            return@Column
        }
        SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Search name or number")
        if (contacts == null) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(filtered, key = { _, c -> c.jid }) { _, c ->
                val on = picked.any { it.jid == c.jid }
                PickRow(
                    leading = { Avatar(c.jid, false, 46.dp) },
                    title = c.name,
                    subtitle = c.phone,
                    selected = on,
                    onClick = { if (on) picked.removeAll { it.jid == c.jid } else picked.add(c) },
                )
            }
        }
    }
}
