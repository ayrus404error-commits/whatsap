package com.glasschat.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.FilterChip
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import com.glasschat.ui.theme.senderColor
import kotlinx.coroutines.delay

private val Kinds = listOf(
    "All" to "",
    "Messages" to "text",
    "Photos & videos" to "media",
    "Documents" to "docs",
    "Links" to "links",
    "Audio" to "audio",
    "Places, contacts & polls" to "places",
)

/**
 * Search one chat: message text, photo/video captions, document names,
 * links, contacts, places and polls. Tap a result to jump to it in the chat.
 */
@Composable
fun ChatSearchScreen(jid: String, nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val name = chats.find { it.jid == jid }?.name.orEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Message>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query, kind) {
        val q = query.trim()
        val k = Kinds[kind].second
        if (q.isEmpty() && k.isEmpty()) {
            results = null
            return@LaunchedEffect
        }
        delay(250) // wait for typing to pause
        searching = true
        results = Engine.searchChat(jid, q, k)
        searching = false
    }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    fun open(m: Message) {
        keyboard?.hide()
        // A link result opens the link itself.
        if (Kinds[kind].second == "links") {
            val url = Regex("(https?://[^\\s]+|www\\.[^\\s]+)").find(m.text)?.value?.trimEnd('.', ',', ')', '!', '?')
            if (url != null) {
                runCatching { uriHandler.openUri(if (url.startsWith("http")) url else "https://$url") }
                return
            }
        }
        Engine.requestJump(jid, m)
        // Goes back to the chat if it's already open underneath, else opens it.
        nav.push(Route.Chat(jid))
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        // No glass source here: the back button sits inside this column.
        Column(Modifier.fillMaxSize().background(p.background).imePadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(p.fill)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = p.secondaryLabel, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                if (name.isBlank()) "Search this chat" else "Search in $name",
                                style = Type.body,
                                color = p.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.take(100) },
                            singleLine = true,
                            textStyle = Type.body.copy(color = p.label),
                            cursorBrush = SolidColor(p.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Cancel,
                            "Clear",
                            tint = p.tertiaryLabel,
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { query = "" },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kinds.forEachIndexed { i, (label, _) -> FilterChip(label, kind == i) { kind = i } }
            }
            val list = results
            when {
                list == null -> Hint(
                    "Search messages, photos, videos, documents, links and more in this chat.\nOr pick a type above to see all of it.",
                )
                searching && list.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                }
                list.isEmpty() -> Hint("No results")
                else -> {
                    Text(
                        "${list.size}${if (list.size >= 300) "+" else ""} result${if (list.size == 1) "" else "s"}",
                        style = Type.footnote,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                        items(list, key = { it.id }) { m -> ResultRow(m, query.trim(), ctx) { open(m) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = Type.subhead,
        color = LocalPalette.current.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 40.dp),
    )
}

@Composable
internal fun ResultRow(m: Message, query: String, ctx: android.content.Context, onClick: () -> Unit) {
    val p = LocalPalette.current
    val fileName = m.extra?.optString("fileName").orEmpty()
    val body = when (m.type) {
        "document" -> fileName.ifBlank { "Document" } + if (m.text.isNotBlank()) " — ${m.text}" else ""
        "image" -> m.text.ifBlank { "Photo" }
        "video", "gif" -> m.text.ifBlank { if (m.type == "gif") "GIF" else "Video" }
        "voice" -> "Voice message"
        "audio" -> fileName.ifBlank { "Audio" }
        "location" -> m.text.ifBlank { "Location" }
        "contact" -> "Contact: ${m.text}"
        "poll" -> "Poll: ${m.text}"
        else -> m.text
    }
    val accent = p.accentText
    val snippet = remember(body, query, accent) { highlight(snippetAround(body, query), query, accent) }
    Column(Modifier.background(p.background)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResultLeading(m)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (m.fromMe) "You" else m.senderName.removePrefix("~ ").ifBlank { "Them" },
                        style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = if (m.fromMe) p.accentText else senderColor(m.sender),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Fmt.dayHeader(m.ts)} · ${Fmt.time(ctx, m.ts)}",
                        style = Type.caption.copy(fontSize = 12.sp),
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(snippet, style = Type.subhead, color = p.label, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 72.dp), color = p.separator, thickness = 0.5.dp)
    }
}

@Composable
private fun ResultLeading(m: Message) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    when (m.type) {
        "image", "video", "gif" -> {
            val thumb = remember(m.id) { messageThumb(m) }
            Box(Modifier.size(44.dp).clip(shape).background(p.fill)) {
                if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (m.type != "image") {
                    Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).size(14.dp))
                }
            }
        }
        "document" -> {
            val ext = m.extra?.optString("fileName").orEmpty().substringAfterLast('.', "").take(4).uppercase()
            Box(Modifier.size(width = 38.dp, height = 44.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                if (ext.isNotBlank()) {
                    Text(ext, style = Type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White)
                } else {
                    Icon(Icons.Outlined.Description, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        else -> {
            val icon: ImageVector? = when (m.type) {
                "voice" -> Icons.Rounded.Mic
                "audio" -> Icons.Rounded.Headphones
                "location" -> Icons.Outlined.LocationOn
                "contact" -> Icons.Outlined.Person
                "poll" -> Icons.Outlined.Poll
                else -> null
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.fill), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = p.secondaryLabel, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Rounded.Search, null, tint = p.tertiaryLabel, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A window of the text around the first match, so long messages show the hit. */
private fun snippetAround(text: String, query: String): String {
    val flat = text.replace('\n', ' ')
    if (query.isEmpty()) return flat
    val i = flat.indexOf(query, ignoreCase = true)
    if (i <= 40) return flat
    return "…" + flat.substring(i - 30)
}

private fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    if (query.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var from = 0
    while (true) {
        val i = text.indexOf(query, from, ignoreCase = true)
        if (i < 0) break
        append(text.substring(from, i))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + query.length)) }
        from = i + query.length
    }
    append(text.substring(from))
}
