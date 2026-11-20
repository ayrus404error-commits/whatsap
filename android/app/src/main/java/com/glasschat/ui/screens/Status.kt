package com.glasschat.ui.screens

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.FileDownload
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import com.glasschat.ui.glass.backdropSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.engine.StatusGroup
import com.glasschat.engine.StatusItem
import com.glasschat.media.rememberImage
import com.glasschat.media.rememberMedia
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

// -----------------------------------------------------------------------------
// Updates tab

@Composable
fun UpdatesTab(
    backdrop: Backdrop,
    nav: com.glasschat.ui.Navigator,
    onOpen: (groups: List<StatusGroup>, index: Int) -> Unit,
    onAdd: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val version by Engine.statusVersion.collectAsState()
    // Ask the phone for any statuses WhatsApp didn't push to this device.
    androidx.compose.runtime.LaunchedEffect(Unit) { Engine.requestStatusSync(force = false) }
    val me by Engine.me.collectAsState()
    var groups by remember { mutableStateOf<List<StatusGroup>?>(null) }
    LaunchedEffect(version) { groups = Engine.statuses() }
    // Refresh "x minutes ago" and expiry while the tab is open.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            groups = Engine.statuses()
        }
    }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf(com.glasschat.service.Prefs.secretStatus(ctx)) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val all = groups.orEmpty()
    val mine = all.firstOrNull { it.mine }
    val q = query.trim().lowercase()
    val others = all.filter { !it.mine && (q.isEmpty() || it.name.lowercase().contains(q)) }
    // Unseen first, like WhatsApp; the order is what the viewer steps through.
    val ordered = others.filter { it.unviewed > 0 } + others.filter { it.unviewed == 0 }

    Box(Modifier.fillMaxSize().background(p.background)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.background).verticalScroll(scroll)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(56.dp))
            Text("Updates", style = Type.largeTitle, color = p.label, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
            if (searching) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Search status")
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = Type.title2.copy(fontWeight = FontWeight.Bold), color = p.label, modifier = Modifier.weight(1f))
                if (secret) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.VisibilityOff, null, tint = p.secondaryLabel, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Secret view", style = Type.caption, color = p.secondaryLabel)
                    }
                }
            }
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "mine") {
                    if (mine == null) {
                        AddStatusCard(me?.jid ?: "", onAdd)
                    } else {
                        StatusCard(mine, label = "My status", myJid = me?.jid ?: "", onClick = { onOpen(listOf(mine), 0) }, onAdd = onAdd)
                    }
                }
                items(ordered.size, key = { ordered[it].sender }) { i ->
                    val g = ordered[i]
                    StatusCard(g, label = g.name, myJid = "", onClick = { onOpen(ordered, i) }, onAdd = null)
                }
            }
            if (groups != null && others.isEmpty()) {
                Text(
                    if (q.isNotEmpty()) "No status updates match." else "No status updates from your contacts in the last 24 hours.",
                    style = Type.subhead,
                    color = p.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 22.dp),
                )
            }
            if (mine != null) {
                Spacer(Modifier.height(18.dp))
                SectionCard {
                    val views = mine.items.sumOf { it.views }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(listOf(mine), 0) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusAvatar(me?.jid ?: "", mine.items.size, 0, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("My status", style = Type.rowTitle, color = p.label)
                            Text(
                                "${mine.items.size} update${if (mine.items.size == 1) "" else "s"} · ${timeAgo(mine.lastTs)}" +
                                    if (views > 0) " · $views view${if (views == 1) "" else "s"}" else "",
                                style = Type.subhead,
                                color = p.secondaryLabel,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard {
                SettingRow(Icons.Outlined.Visibility, Color(0xFF34C759), "Status privacy", onClick = {
                    nav.push(com.glasschat.ui.Route.Settings("status-privacy"))
                })
                RowDivider()
                SettingRow(Icons.Outlined.VisibilityOff, Color(0xFF5856D6), "Secretly view status", trailing = {
                    IosSwitch(secret) { secret = it; com.glasschat.service.Prefs.setSecretStatus(ctx, it) }
                })
            }
            Text(
                "Status updates disappear after 24 hours. With secret view on, people don't see that you viewed theirs from this app.",
                style = Type.footnote,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp),
            )
            Spacer(Modifier.height(120.dp))
        }
        com.glasschat.ui.components.TopEdgeFade(alpha = (scroll.value / 40f).coerceIn(0f, 1f))
        TopControls(
            backdrop,
            title = "Updates",
            showTitle = scroll.value > 90,
            left = {},
            right = {
                com.glasschat.ui.glass.GlassCircleButton(backdrop, Icons.Rounded.Search, "Search") {
                    searching = !searching
                    if (!searching) query = ""
                }
                Spacer(Modifier.width(10.dp))
                com.glasschat.ui.glass.GlassCircleButton(backdrop, Icons.Rounded.MoreVert, "More") { menu = true }
            },
        )
    }
    if (menu) {
        com.glasschat.ui.components.ActionSheet(
            title = null,
            actions = listOf(
                com.glasschat.ui.components.SheetAction("Add status") { onAdd() },
                com.glasschat.ui.components.SheetAction("Status privacy") { nav.push(com.glasschat.ui.Route.Settings("status-privacy")) },
                com.glasschat.ui.components.SheetAction(if (secret) "Turn off secret view" else "Secretly view status") {
                    secret = !secret
                    com.glasschat.service.Prefs.setSecretStatus(ctx, secret)
                },
            ),
            onDismiss = { menu = false },
        )
    }
}

private val CardShape = RoundedCornerShape(22.dp)

/** "Add status" card: your photo with a plus. */
@Composable
private fun AddStatusCard(myJid: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(112.dp)
            .height(184.dp)
            .clip(CardShape)
            .background(p.cell)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box {
            Avatar(myJid, false, 70.dp)
            PlusBadge(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("Add status", style = Type.subhead.copy(fontWeight = FontWeight.Medium), color = p.label, maxLines = 1)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlusBadge(modifier: Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(p.cell)
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(p.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/** One person's latest update as a tall card: picture or text, their photo in a ring, their name. */
@Composable
private fun StatusCard(g: StatusGroup, label: String, myJid: String, onClick: () -> Unit, onAdd: (() -> Unit)?) {
    val p = LocalPalette.current
    val last = g.items.lastOrNull()
    val thumb = remember(last?.id) { last?.let { statusThumb(it) } }
    Box(
        Modifier
            .width(112.dp)
            .height(184.dp)
            .clip(CardShape)
            .background(p.cell)
            .clickable(onClick = onClick),
    ) {
        when {
            last == null -> {}
            last.type == "text" -> {
                val bg = last.extra?.optLong("bg")?.takeIf { it != 0L }?.toInt()
                Box(Modifier.fillMaxSize().background(if (bg != null) Color(bg) else Color(0xFF128C7E)).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(last.text, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            thumb != null -> Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.5f), p.accent.copy(alpha = 0.15f)))))
        }
        // Dim seen updates a little, and shade the bottom for the name.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.25f),
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f),
                ),
            ),
        )
        Box(Modifier.padding(8.dp)) {
            StatusAvatar(if (g.mine) myJid else g.sender, g.items.size, if (g.mine) 0 else g.unviewed, 44.dp)
            if (onAdd != null) {
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).clickable(onClick = onAdd)) { PlusBadge(Modifier) }
            }
        }
        Text(
            label,
            style = Type.subhead.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

private fun statusThumb(item: StatusItem): androidx.compose.ui.graphics.ImageBitmap? {
    val b64 = item.extra?.optString("thumb")?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * "Add status" sheet: Text and Camera buttons, then your recent photos
 * (first tile opens the camera). Picking a photo goes to the send preview.
 */
@Composable
fun AddStatusSheet(
    onText: () -> Unit,
    onVoice: () -> Unit,
    onLayout: () -> Unit,
    onCamera: () -> Unit,
    onLibrary: () -> Unit,
    onMedia: (List<com.glasschat.media.PendingMedia>) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    BackHandler(onBack = onClose)
    var granted by remember { mutableStateOf(com.glasschat.media.PhoneGallery.hasAccess(ctx)) }
    var recent by remember { mutableStateOf<List<com.glasschat.media.GalleryItem>>(emptyList()) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        granted = com.glasschat.media.PhoneGallery.hasAccess(ctx)
    }
    LaunchedEffect(granted) { if (granted) recent = com.glasschat.media.PhoneGallery.recent(ctx, 120) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(p.groupedBackground)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(p.tertiaryLabel))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = p.label, modifier = Modifier.size(26.dp))
                }
                Text("Add status", style = Type.title.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp), color = p.label, modifier = Modifier.align(Alignment.Center))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(Icons.Outlined.Edit, "Text", Modifier.weight(1f), onText)
                SheetButton(Icons.Outlined.Mic, "Voice", Modifier.weight(1f), onVoice)
                SheetButton(Icons.Outlined.GridView, "Layout", Modifier.weight(1f), onLayout)
                SheetButton(Icons.Outlined.PhotoCamera, "Camera", Modifier.weight(1f), onCamera)
            }
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recents", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label, modifier = Modifier.weight(1f))
                Text("All photos", style = Type.body, color = p.link, modifier = Modifier.clickable(onClick = onLibrary).padding(6.dp))
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "camera") {
                    CameraTile(Modifier.aspectRatio(1f).padding(1.5.dp)) { onCamera() }
                }
                if (!granted) {
                    item(key = "allow", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        AllowPhotosTile(Modifier.height(120.dp).padding(1.5.dp)) { ask.launch(com.glasschat.media.PhoneGallery.permissions) }
                    }
                }
                items(recent.size, key = { recent[it].uri.toString() }) { i ->
                    val g = recent[i]
                    RecentTile(g, -1, Modifier.aspectRatio(1f).padding(1.5.dp)) {
                        onMedia(listOf(com.glasschat.media.PendingMedia(g.uri, g.video)))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(31.dp)).background(p.cell), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = p.label, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = Type.subhead, color = p.label)
    }
}

/** Profile photo inside a ring with one segment per update (green = new). */
@Composable
fun StatusAvatar(jid: String, total: Int, unviewed: Int, diameter: Dp = 56.dp) {
    val p = LocalPalette.current
    val seenColor = p.tertiaryLabel
    val newColor = p.accent
    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                val stroke = 2.4.dp.toPx()
                val n = total.coerceAtLeast(1)
                val gap = if (n > 1) 7f else 0f
                val sweep = 360f / n
                for (i in 0 until n) {
                    val isNew = i >= n - unviewed
                    drawArc(
                        color = if (isNew) newColor else seenColor,
                        startAngle = -90f + i * sweep + gap / 2f,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(this.size.width - stroke, this.size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(jid, false, diameter - 8.dp)
    }
}

fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 24 * 3_600_000L -> "${diff / 3_600_000} h ago"
        else -> Fmt.dayHeader(ts)
    }
}

// -----------------------------------------------------------------------------
// Story viewer

@Composable
fun StatusViewer(groups: List<StatusGroup>, start: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val me by Engine.me.collectAsState()
    var gi by remember { mutableIntStateOf(start.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    var ii by remember { mutableIntStateOf(firstUnseen(groups.getOrNull(gi))) }
    var paused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    val group = groups.getOrNull(gi)
    val item = group?.items?.getOrNull(ii)
    if (group == null || item == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    fun next() {
        if (ii < group.items.lastIndex) {
            ii++
        } else if (gi < groups.lastIndex) {
            gi++
            ii = firstUnseen(groups[gi])
        } else {
            onClose()
        }
    }
    fun prev() {
        if (ii > 0) {
            ii--
        } else if (gi > 0) {
            gi--
            ii = 0
        }
    }

    val isAudio = item.type == "voice" || item.type == "audio"
    val isMedia = item.type == "image" || item.type == "video" || item.type == "gif" || item.type == "sticker" || isAudio
    val media = rememberMedia(Engine.STATUS_CHAT, item.id, item.local, auto = isMedia)
    val ready = !isMedia || media.path != null
    val durationMs = if (item.type == "video" || isAudio) ((item.extra?.optInt("seconds") ?: 0) * 1000 + 500).coerceIn(4000, 60_000) else 5000
    var viewers by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    var replying by remember { mutableStateOf(false) }
    var sentNote by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val itemKey = "${group.sender}/${item.id}"
    var captionOpen by remember(itemKey) { mutableStateOf(false) }
    val viewerCtx = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(itemKey) {
        progress.snapTo(0f)
        if (!group.mine) Engine.markStatusViewed(item.id)
    }
    LaunchedEffect(itemKey, ready, paused, confirmDelete, viewers, replying, captionOpen) {
        if (!ready || paused || confirmDelete || viewers || replying || captionOpen) return@LaunchedEffect
        val remaining = ((1f - progress.value) * durationMs).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(itemKey) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { at -> if (at.x < size.width * 0.3f) prev() else next() },
                )
            },
    ) {
        when (item.type) {
            "text" -> TextStatusBody(item)
            "image" -> {
                val img = rememberImage(media.path, 2048)
                if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            "video", "gif" -> {
                val path = media.path
                if (path != null) {
                    val w = item.extra?.optInt("w") ?: 0
                    val h = item.extra?.optInt("h") ?: 0
                    VideoPlayer(
                        path,
                        loop = item.type == "gif",
                        initialAspect = if (w > 0 && h > 0) w.toFloat() / h else 9f / 16f,
                        onTap = {},
                        showControls = false,
                    )
                }
            }
            "sticker" -> {
                val img = rememberImage(media.path, 1024)
                if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.7f))
            }
            "waiting" -> Text(
                "This update hasn't arrived on this device yet. It usually comes through in a moment — or watch it on your phone.",
                style = Type.body,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
            "voice", "audio" -> StatusAudio(media.path, paused || viewers || replying || confirmDelete)
            else -> Text(
                if (item.text.isNotBlank()) item.text + "\n\nThis kind of update can't be shown here — open it on your phone to see it fully."
                else "This kind of update can't be shown here. Open WhatsApp on your phone to see it.",
                style = Type.body,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        if (!ready) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                val err = media.error
                if (err != null && !media.loading) {
                    Text(err, style = Type.subhead, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Try again",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { media.download() }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                }
            }
        }

        // Top: one progress segment per update, then who and when.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.items.forEachIndexed { i, _ ->
                    val fill = when {
                        i < ii -> 1f
                        i == ii -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(2.5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(if (group.mine) me?.jid ?: "" else group.sender, false, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(timeAgo(item.ts), style = Type.footnote, color = Color.White.copy(alpha = 0.8f))
                }
                if (isMedia) {
                    // Save this status to the phone.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable {
                            scope.launch {
                                val path = media.path ?: com.glasschat.media.MediaState.fetch(Engine.STATUS_CHAT, item.id).getOrNull()
                                sentNote = when {
                                    path == null -> "Couldn't download it"
                                    isAudio -> if (com.glasschat.media.MediaFiles.saveToDownloads(viewerCtx, path, "Status_${item.ts}.ogg")) "Saved to Downloads" else "Couldn't save"
                                    com.glasschat.media.MediaFiles.saveToGallery(viewerCtx, path, item.type != "image") -> "Saved to gallery"
                                    else -> "Couldn't save"
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.FileDownload, "Download", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
                if (group.mine) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Bottom: caption, and view count on your own updates.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (item.type != "text" && item.text.isNotBlank()) {
                // Long captions: 3 lines, then "Read more" (pauses the status while open).
                var overflow by remember(itemKey) { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (captionOpen) 320.dp else 120.dp)
                        .then(if (captionOpen) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            if (overflow || captionOpen) captionOpen = !captionOpen
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.text,
                        style = Type.body,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = if (captionOpen) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!captionOpen) overflow = it.hasVisualOverflow },
                    )
                    if (overflow || captionOpen) {
                        Text(
                            if (captionOpen) "Show less" else "Read more",
                            style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            sentNote?.let {
                Text(it, style = Type.footnote, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (!group.mine) {
                // React or reply: it arrives in your chat with them, quoting this update.
                fun react(text: String) {
                    scope.launch {
                        Engine.replyToStatus(item.id, text)
                            .onSuccess { sentNote = if (text.length <= 4) "Reacted $text" else "Reply sent to ${group.name}" }
                            .onFailure { sentNote = "Couldn't send: ${it.message}" }
                    }
                }
                if (replying && reply.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("😍", "😂", "😮", "😢", "👏", "🙏", "🔥", "💯").forEach { e ->
                            Text(e, fontSize = 30.sp, modifier = Modifier.clip(CircleShape).clickable { react(e) }.padding(4.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.16f)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).padding(vertical = 5.dp)) {
                            if (reply.isEmpty()) Text("Reply", style = Type.body, color = Color.White.copy(alpha = 0.75f))
                            BasicTextField(
                                value = reply,
                                onValueChange = { reply = it.take(1000) },
                                textStyle = Type.body.copy(color = Color.White),
                                cursorBrush = SolidColor(Color.White),
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth().onFocusChanged { replying = it.isFocused },
                            )
                        }
                        if (reply.isEmpty()) {
                            listOf("😍", "😂").forEach { e ->
                                Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable { react(e) }.padding(horizontal = 6.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (reply.isNotBlank()) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1DAA61)).clickable {
                                val text = reply
                                reply = ""
                                react(text)
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        var liked by remember(item.id) { mutableStateOf(false) }
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable {
                                if (!liked) {
                                    liked = true
                                    react("❤️")
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "Like",
                                tint = if (liked) Color(0xFFFF3B5C) else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            if (group.mine) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).clickable { viewers = true }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (item.status <= 0) "Sending…" else "${item.views} view${if (item.views == 1) "" else "s"}",
                        style = Type.subhead,
                        color = Color.White,
                    )
                }
            }
        }
    }
    if (viewers) StatusViewersSheet(item.id, onClose = { viewers = false })
    if (confirmDelete) {
        val cancel: () -> Unit = { confirmDelete = false }
        val delete: () -> Unit = {
            confirmDelete = false
            Engine.deleteStatus(item.id)
            next()
        }
        com.glasschat.ui.components.GlassAlert(
            title = "Delete this status update?",
            message = "It will be deleted for everyone who received it.",
            onDismiss = cancel,
            buttons = listOf("Cancel" to cancel, "Delete" to delete),
            destructiveIndex = 1,
        )
    }
}

private fun firstUnseen(g: StatusGroup?): Int {
    if (g == null) return 0
    val i = g.items.indexOfFirst { !it.viewed }
    return if (i >= 0) i else 0
}

private val StatusFonts = listOf(
    FontFamily.SansSerif,
    FontFamily.Serif,
    FontFamily.Cursive,
    FontFamily.Monospace,
    FontFamily.SansSerif,
    FontFamily.Serif,
)

private fun statusTextStyle(font: Int, length: Int): TextStyle = TextStyle(
    color = Color.White,
    fontSize = when {
        length < 40 -> 34.sp
        length < 120 -> 26.sp
        else -> 20.sp
    },
    lineHeight = when {
        length < 40 -> 42.sp
        length < 120 -> 33.sp
        else -> 27.sp
    },
    fontFamily = StatusFonts[font.coerceIn(0, StatusFonts.lastIndex)],
    fontWeight = if (font == 4) FontWeight.Black else if (font == 0) FontWeight.Medium else FontWeight.Normal,
    fontStyle = if (font == 2) FontStyle.Italic else FontStyle.Normal,
    textAlign = TextAlign.Center,
)

@Composable
private fun TextStatusBody(item: StatusItem) {
    val bg = item.extra?.optLong("bg")?.takeIf { it != 0L }?.toInt()
    val font = item.extra?.optInt("font") ?: 0
    Box(
        Modifier.fillMaxSize().background(if (bg != null) Color(bg) else Color(0xFF128C7E)),
        contentAlignment = Alignment.Center,
    ) {
        Text(item.text, style = statusTextStyle(font, item.text.length), modifier = Modifier.padding(28.dp))
    }
}

// -----------------------------------------------------------------------------
// Text status composer

private val StatusColors = listOf(
    0xFF7ACBA5, 0xFF6E257E, 0xFF5696FF, 0xFFFF8A8C, 0xFF54C265,
    0xFFC1A03F, 0xFF243640, 0xFFAE8774, 0xFF8294CA, 0xFFA62C71,
)

@Composable
fun TextStatusComposer(onPost: (text: String, background: Long, font: Int) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var colorIndex by remember { mutableIntStateOf(0) }
    var font by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val bg = StatusColors[colorIndex]
    Box(Modifier.fillMaxSize().background(Color(bg.toInt())).imePadding()) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            if (text.isEmpty()) {
                Text("Type a status", style = statusTextStyle(font, 0).copy(color = Color.White.copy(alpha = 0.6f)))
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(700) },
                textStyle = statusTextStyle(font, text.length),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerButton(Icons.Rounded.Close, "Close", onClose)
            Spacer(Modifier.weight(1f))
            ComposerButton(Icons.Outlined.TextFields, "Font") { font = (font + 1) % 6 }
            Spacer(Modifier.width(6.dp))
            ComposerButton(Icons.Outlined.Palette, "Colour") { colorIndex = (colorIndex + 1) % StatusColors.size }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(18.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(if (text.isBlank()) Color.White.copy(alpha = 0.25f) else Color(0xFF1DAA61))
                .clickable(enabled = text.isNotBlank()) { onPost(text.trim(), bg, font) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowUpward, "Post", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ComposerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
