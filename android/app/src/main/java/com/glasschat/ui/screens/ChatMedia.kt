package com.glasschat.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.media.MediaFiles
import com.glasschat.media.MediaState
import com.glasschat.media.rememberImage
import com.glasschat.media.rememberMedia
import com.glasschat.service.Prefs
import com.glasschat.ui.Navigator
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.glass.liquidGlass
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import com.glasschat.ui.theme.senderColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Tabs = listOf("Media", "Links", "Docs")
private val UrlPattern = Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)

/** Every photo, video, link and document shared in one chat. */
@Composable
fun ChatMediaScreen(jid: String, nav: Navigator) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val name = chats.find { it.jid == jid }?.name.orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var media by remember { mutableStateOf<List<Message>?>(null) }
    var mediaEnd by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf<List<Message>?>(null) }
    var docs by remember { mutableStateOf<List<Message>?>(null) }
    var viewer by remember { mutableStateOf<Message?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var forwardIds by remember { mutableStateOf<List<String>?>(null) }
    val pageSize = 240
    fun tap(m: Message, open: () -> Unit) {
        if (selecting) { if (m.id in selected) selected.remove(m.id) else selected.add(m.id) } else open()
    }

    suspend fun reload() {
        val m = Engine.chatMedia(jid, "media", 0, pageSize)
        media = m
        mediaEnd = m.size < pageSize
        links = Engine.chatMedia(jid, "links", 0, 600)
        docs = Engine.chatMedia(jid, "docs", 0, 600)
    }
    LaunchedEffect(jid) { reload() }
    // New photos, links or files arriving while this is open.
    LaunchedEffect(jid) {
        Engine.events.collect { ev ->
            if (ev is Engine.Event.NewMessage && ev.msg.chat == jid && ev.msg.type in setOf("image", "video", "gif", "document", "text")) reload()
        }
    }

    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 66.dp
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + 24.dp

    Box(Modifier.fillMaxSize().background(p.background)) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop).background(p.background)) {
            when (tab) {
                0 -> {
                    val list = media
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Empty(Icons.Outlined.PhotoLibrary, "No media", "Photos and videos shared in this chat will appear here.")
                        else -> {
                            val grid = rememberLazyGridState()
                            LaunchedEffect(grid, list.size) {
                                snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                    .distinctUntilChanged()
                                    .collect { last ->
                                        val cur = media ?: return@collect
                                        if (!mediaEnd && !loadingMore && last >= grid.layoutInfo.totalItemsCount - 24) {
                                            loadingMore = true
                                            val more = Engine.chatMedia(jid, "media", cur.last().ts, pageSize)
                                            val known = cur.map { it.id }.toHashSet()
                                            media = cur + more.filter { it.id !in known }
                                            if (more.size < pageSize) mediaEnd = true
                                            loadingMore = false
                                        }
                                    }
                            }
                            val groups = remember(list) { list.groupBy { monthLabel(it.ts) } }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                state = grid,
                                contentPadding = PaddingValues(top = top, bottom = bottom),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                groups.forEach { (label, msgs) ->
                                    item(key = "h:$label", span = { GridItemSpan(maxLineSpan) }) { MonthHeader(label) }
                                    items(msgs, key = { it.id }) { m ->
                                        Box {
                                            MediaCell(m) { tap(m) { viewer = m } }
                                            if (selecting) Box(Modifier.align(Alignment.TopEnd).padding(5.dp)) { ChoiceMark(m.id in selected, square = false) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val list = links
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Empty(Icons.Outlined.Link, "No links", "Links shared in this chat will appear here.")
                        else -> {
                            val groups = remember(list) { list.groupBy { monthLabel(it.ts) } }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = bottom)) {
                                groups.forEach { (label, msgs) ->
                                    item(key = "h:$label") { MonthHeader(label) }
                                    items(msgs, key = { it.id }) { m -> SelectableRow(selecting, m.id in selected, { tap(m) {} }) { LinkRow(m, onFailed = { toast = it }) } }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val list = docs
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Empty(Icons.Outlined.Description, "No documents", "Files shared in this chat will appear here.")
                        else -> {
                            val groups = remember(list) { list.groupBy { monthLabel(it.ts) } }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = bottom)) {
                                groups.forEach { (label, msgs) ->
                                    item(key = "h:$label") { MonthHeader(label) }
                                    items(msgs, key = { it.id }) { m -> SelectableRow(selecting, m.id in selected, { tap(m) {} }) { DocRow(m, onFailed = { toast = it }) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        TopEdgeFade(extra = 40.dp)
        // One row, like WhatsApp on iPhone: back · Media | Links | Docs · Select.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", size = 44.dp) {
                if (selecting) { selecting = false; selected.clear() } else nav.pop()
            }
            Spacer(Modifier.width(8.dp))
            // Compact segmented capsule: the chosen tab is a darker pill inside it.
            com.glasschat.ui.glass.GlassCapsule(
                backdrop,
                Modifier.height(46.dp),
                radius = 23.dp,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Tabs.forEachIndexed { i, label ->
                    val on = i == tab
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) (if (p.dark) Color.Black.copy(alpha = 0.6f) else Color.White) else Color.Transparent)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { tab = i; selected.clear() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            com.glasschat.ui.glass.GlassCapsule(
                backdrop,
                Modifier.height(44.dp).clickable(remember { MutableInteractionSource() }, indication = null) {
                    selecting = !selecting
                    selected.clear()
                },
                radius = 22.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(if (selecting) "Cancel" else "Select", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label)
            }
        }
        if (selecting) {
            // What to do with the chosen items.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(p.cell)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Forward",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected.isEmpty()) p.tertiaryLabel else p.link,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) { forwardIds = selected.toList() },
                )
                Text(
                    if (selected.isEmpty()) "Select items" else "${selected.size} selected",
                    style = Type.subhead,
                    color = p.secondaryLabel,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Delete",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected.isEmpty()) p.tertiaryLabel else p.danger,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                        val gone = selected.toSet()
                        gone.forEach { Engine.deleteForMe(jid, it) }
                        media = media?.filterNot { it.id in gone }
                        links = links?.filterNot { it.id in gone }
                        docs = docs?.filterNot { it.id in gone }
                        toast = "Deleted for you"
                        selected.clear()
                        selecting = false
                    },
                )
            }
        }
        forwardIds?.let { ids ->
            ForwardPicker(fromChat = jid, ids = ids, onClose = { forwardIds = null }, onDone = { msg ->
                forwardIds = null
                selected.clear()
                selecting = false
                toast = msg
            })
        }
        viewer?.let { MediaViewer(jid, it, onClose = { viewer = null }) }
        Toast(toast) { toast = null }
    }
}

/** A list row that, while selecting, shows a tick and takes the tap. */
@Composable
private fun SelectableRow(selecting: Boolean, selected: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Box {
        Box(Modifier.padding(start = if (selecting) 36.dp else 0.dp)) { content() }
        if (selecting) {
            Box(
                Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.padding(start = 12.dp)) { ChoiceMark(selected, square = false) }
            }
        }
    }
}

/** iOS-style segmented control on glass, with a lens that slides to the choice. */
@Composable
fun GlassSegmented(backdrop: Backdrop, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .glassShadow(shape, 14.dp, Color.Black.copy(alpha = if (p.dark) 0.4f else 0.1f), 3.dp)
            .liquidGlass(backdrop, shape, p.glass)
            .padding(3.dp),
    ) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.75f, stiffness = 450f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .clip(shape)
                .background(if (p.dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f))
                .frosted(shape, Color.Transparent, 0.5f),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Type.subhead.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = p.label,
                    )
                }
            }
        }
    }
}

private fun monthLabel(ts: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.MONTH) == c.get(Calendar.MONTH) -> "This month"
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ts))
    }
}

@Composable
private fun MonthHeader(label: String) {
    val p = LocalPalette.current
    Text(
        label,
        style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
        color = p.label,
        modifier = Modifier.fillMaxWidth().background(p.background).padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPalette.current.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun Empty(icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.secondaryLabel, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = Type.title, color = p.label)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Type.subhead, color = p.secondaryLabel, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MediaCell(m: Message, onClick: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val isImage = m.type == "image"
    val media = rememberMedia(m.chat, m.id, m.local, auto = isImage && Prefs.shouldAutoDownload(ctx, "photo"))
    val full = if (isImage) rememberImage(media.path, 360) else null
    val thumb = remember(m.id) { messageThumb(m) }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(p.fill)
            .clickable(onClick = onClick),
    ) {
        val img = full ?: thumb
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (!isImage) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                val secs = m.extra?.optInt("seconds") ?: 0
                Text(
                    if (m.type == "gif") "GIF" else if (secs > 0) Fmt.duration(secs) else "",
                    style = Type.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
    }
}

private fun firstUrl(text: String): String? = UrlPattern.find(text)?.value?.trimEnd('.', ',', ')', '!', '?', ';', ':', '"', '\'')

private fun normalized(url: String): String = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"

private fun domainOf(url: String): String =
    runCatching { Uri.parse(normalized(url)).host.orEmpty().removePrefix("www.") }.getOrDefault("").ifBlank { url }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(m: Message, onFailed: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val url = remember(m.id) { firstUrl(m.text) } ?: return
    val domain = remember(url) { domainOf(url) }
    val rest = m.text.replace(url, "").trim()
    Column(Modifier.background(p.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized(url))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        onFailed("No app can open this link")
                    }
                })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(senderColor(domain).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(domain.take(1).uppercase(), style = Type.title2, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(domain, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = Type.footnote, color = p.link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (rest.isNotEmpty()) {
                    Text(rest, style = Type.footnote, color = p.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    "${if (m.fromMe) "You" else m.senderName.removePrefix("~ ")} · ${Fmt.listStamp(ctx, m.ts)}",
                    style = Type.caption.copy(fontSize = 12.sp),
                    color = p.tertiaryLabel,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 76.dp), color = p.separator, thickness = 0.5.dp)
    }
}

/** A colour per kind of file, like the coloured badges in file managers. */
private fun docColor(ext: String): Color = when (ext.lowercase()) {
    "pdf" -> Color(0xFFE53935)
    "doc", "docx", "odt", "rtf" -> Color(0xFF2B6CD8)
    "xls", "xlsx", "csv", "ods" -> Color(0xFF1E8E3E)
    "ppt", "pptx", "odp", "key" -> Color(0xFFE8710A)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8E6E53)
    "apk" -> Color(0xFF3DDC84)
    "mp3", "m4a", "wav", "ogg", "aac", "flac" -> Color(0xFFFF8A00)
    "txt", "md" -> Color(0xFF8E8E93)
    else -> Color(0xFF5856D6)
}

@Composable
private fun DocRow(m: Message, onFailed: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val media = rememberMedia(m.chat, m.id, m.local, auto = false)
    val name = m.extra?.optString("fileName")?.ifBlank { null } ?: m.text.ifBlank { "Document" }
    val ext = name.substringAfterLast('.', "").take(4)
    val size = m.extra?.optLong("size") ?: 0L
    val pages = m.extra?.optInt("pages") ?: 0
    val details = listOfNotNull(
        if (pages > 0) "$pages page${if (pages == 1) "" else "s"}" else null,
        if (size > 0) Fmt.size(size) else null,
        ext.uppercase().ifBlank { null },
        Fmt.listStamp(ctx, m.ts),
    ).joinToString(" · ")
    Column(Modifier.background(p.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        MediaState.fetch(m.chat, m.id)
                            .onSuccess { MediaFiles.open(ctx, it, m.extra?.optString("mime").orEmpty()) }
                            .onFailure { onFailed(it.message ?: "Couldn't download the file") }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(docColor(ext)),
                contentAlignment = Alignment.Center,
            ) {
                if (media.loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else if (ext.isNotBlank()) {
                    Text(ext.uppercase(), style = Type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White)
                } else {
                    Icon(Icons.Outlined.Description, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = Type.body, color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    media.error ?: details,
                    style = Type.footnote,
                    color = if (media.error != null) p.danger else p.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 68.dp), color = p.separator, thickness = 0.5.dp)
    }
}

/** The "Media, links and docs" entry on the contact/group info page. */
@Composable
fun ChatMediaSummary(jid: String, onOpen: () -> Unit) {
    val p = LocalPalette.current
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var recent by remember { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(jid) {
        counts = Engine.chatMediaCounts(jid)
        recent = Engine.chatMedia(jid, "media", 0, 12)
    }
    val total = counts?.values?.sum() ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.cell)
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(com.glasschat.ui.glass.LineIcons.Photo, null, tint = LocalPalette.current.label, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Media, links and docs", style = Type.body, color = p.label, modifier = Modifier.weight(1f))
            Text(if (counts == null) "" else "$total", style = Type.body, color = p.secondaryLabel)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
        }
        if (recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recent.take(4).forEach { m -> SummaryThumb(m, Modifier.weight(1f)) }
                repeat(4 - recent.take(4).size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SummaryThumb(m: Message, modifier: Modifier) {
    val p = LocalPalette.current
    val thumb = remember(m.id) { messageThumb(m) }
    val full = if (m.type == "image") rememberImage(m.local.ifEmpty { null }, 300) else null
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(p.fill)) {
        val img = full ?: thumb
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (m.type == "video" || m.type == "gif") {
            Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(15.dp))
        }
    }
}
