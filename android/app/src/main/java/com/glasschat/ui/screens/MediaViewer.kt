package com.glasschat.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.media.MediaFiles
import com.glasschat.media.rememberImage
import com.glasschat.media.rememberMedia
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Full-screen viewer for a chat's photos, videos and GIFs. Swipe sideways
 * for the next/previous one, pinch or double-tap to zoom, swipe down to
 * close, tap to show/hide the controls.
 */
@Composable
fun MediaViewer(chat: String, start: Message, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var items by remember(start.id) { mutableStateOf(listOf(start)) }
    var startIndex by remember(start.id) { mutableIntStateOf(0) }
    var loaded by remember(start.id) { mutableStateOf(false) }
    LaunchedEffect(chat, start.id) {
        // Both lists come newest first; "newer" only holds what's after this one.
        val older = Engine.chatMedia(chat, "media", before = start.ts + 1, limit = 400)
        val newer = Engine.chatMedia(chat, "media", 0, 400).filter { it.ts > start.ts }
        var list = (newer + older).distinctBy { it.id }.reversed()
        if (list.none { it.id == start.id }) list = (list + start).sortedBy { it.ts }
        startIndex = list.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
        items = list
        loaded = true
    }

    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val dragY = remember { Animatable(0f) }
    val closePx = with(density) { 120.dp.toPx() }
    val fade = (1f - abs(dragY.value) / (closePx * 3f)).coerceIn(0.25f, 1f)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
        key(loaded) {
            val pager = rememberPagerState(initialPage = if (loaded) startIndex else 0) { items.size }
            val current = items.getOrNull(pager.currentPage) ?: start
            LaunchedEffect(pager.currentPage) { zoomed = false }
            val strip = items.size > 1
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = dragY.value }
                    .draggable(
                        rememberDraggableState { d -> scope.launch { dragY.snapTo(dragY.value + d) } },
                        Orientation.Vertical,
                        enabled = !zoomed,
                        onDragStopped = { v ->
                            if (abs(dragY.value) > closePx || abs(v) > 2500f) onClose()
                            else dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                        },
                    ),
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                userScrollEnabled = !zoomed,
                key = { items[it].id },
            ) { page ->
                ViewerPage(
                    m = items[page],
                    isCurrent = page == pager.settledPage,
                    onTap = { chrome = !chrome },
                    onZoom = { z -> if (page == pager.currentPage) zoomed = z },
                    controlsPadding = if (chrome && strip) 70.dp else 0.dp,
                )
            }

            AnimatedVisibility(chrome && dragY.value == 0f, enter = fadeIn(), exit = fadeOut()) {
                val media = rememberMedia(current.chat, current.id, current.local, auto = false)
                val path = media.path
                val video = current.type == "video" || current.type == "gif"
                Box(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewerButton(Icons.Rounded.Close, "Close", onClose)
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(
                                current.senderName.removePrefix("~ ").ifBlank { if (current.fromMe) "You" else "Photo" },
                                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${Fmt.dayHeader(current.ts)}, ${Fmt.time(ctx, current.ts)}" + if (loaded && strip) "  ·  ${pager.currentPage + 1} of ${items.size}" else "",
                                style = Type.footnote,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                        if (path != null) {
                            ViewerButton(Icons.Outlined.Share, "Share") { MediaFiles.share(ctx, path) }
                            ViewerButton(Icons.Outlined.Download, "Save") {
                                scope.launch {
                                    val ok = MediaFiles.saveToGallery(ctx, path, video)
                                    Toast.makeText(ctx, if (ok) "Saved to your gallery" else "Couldn't save", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 10.dp),
                    ) {
                        if (current.text.isNotBlank() && !video) {
                            Text(
                                current.text,
                                style = Type.body,
                                color = Color.White,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        if (strip) {
                            val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (pager.currentPage - 3).coerceAtLeast(0))
                            LaunchedEffect(pager.currentPage) {
                                stripState.animateScrollToItem((pager.currentPage - 3).coerceAtLeast(0))
                            }
                            LazyRow(
                                state = stripState,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                itemsIndexed(items, key = { _, m -> m.id }) { i, m ->
                                    StripThumb(m, i == pager.currentPage) { scope.launch { pager.animateScrollToPage(i) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StripThumb(m: Message, selected: Boolean, onClick: () -> Unit) {
    val thumb = remember(m.id) { messageThumb(m) }
    val local = if (thumb == null && m.type == "image") rememberImage(m.local.ifEmpty { null }, 160) else null
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(width = if (selected) 52.dp else 38.dp, height = 52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        val img = thumb ?: local
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (m.type == "video" || m.type == "gif") {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(18.dp))
        }
    }
}

/** One photo or video in the pager. */
@Composable
private fun ViewerPage(m: Message, isCurrent: Boolean, onTap: () -> Unit, onZoom: (Boolean) -> Unit, controlsPadding: Dp) {
    val video = m.type == "video" || m.type == "gif"
    // Photos next to the current one load in advance; videos only when shown.
    val media = rememberMedia(m.chat, m.id, m.local, auto = !video || isCurrent)
    val path = media.path
    val tap by rememberUpdatedState(onTap)
    Box(Modifier.fillMaxSize()) {
        when {
            path == null || (video && !isCurrent) -> {
                val thumb = remember(m.id) { messageThumb(m) }
                Box(Modifier.fillMaxSize().pointerInput(m.id) { detectTapGestures(onTap = { tap() }) }, contentAlignment = Alignment.Center) {
                    if (thumb != null) {
                        Image(thumb, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().blur(if (path == null) 10.dp else 0.dp))
                    }
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val err = media.error
                        when {
                            path != null -> Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                            err != null && !media.loading -> {
                                Text(err, style = Type.subhead, color = Color.White, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Try again",
                                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clickable { media.download() }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                            }
                            media.loading || !video || isCurrent -> {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Downloading…", style = Type.subhead, color = Color.White.copy(alpha = 0.8f))
                            }
                            else -> Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                        }
                    }
                }
            }
            video -> VideoPlayer(path, loop = m.type == "gif", initialAspect = aspectOf(m), onTap = onTap, controlsPadding = controlsPadding)
            else -> ZoomableImage(path, onTap = onTap, onZoom = onZoom, reset = !isCurrent)
        }
    }
}

private fun aspectOf(m: Message): Float {
    val w = m.extra?.optInt("w") ?: 0
    val h = m.extra?.optInt("h") ?: 0
    return if (w > 0 && h > 0) w.toFloat() / h else 9f / 16f
}

@Composable
private fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Pinch to zoom, drag to pan when zoomed, double-tap to zoom in/out. A
 * single finger on an un-zoomed photo is left alone, so the pager can swipe
 * to the next photo and the viewer can be swiped down to close.
 */
@Composable
fun ZoomableImage(path: String, onTap: () -> Unit, onZoom: (Boolean) -> Unit = {}, reset: Boolean = false) {
    val img = rememberImage(path, 2560)
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)
    val zoomChanged by rememberUpdatedState(onZoom)
    val isZoomed by remember { derivedStateOf { scale > 1.01f } }
    LaunchedEffect(isZoomed) { zoomChanged(isZoomed) }
    LaunchedEffect(reset) {
        if (reset) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { at ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = clamp((Offset(size.width / 2f, size.height / 2f) - at) * 1.5f, 2.5f)
                        }
                    },
                )
            }
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            val s = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            scale = s
                            offset = if (s <= 1.01f) Offset.Zero else clamp(offset + event.calculatePan(), s)
                            event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(
                img,
                null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
        }
    }
}

/** In-app video player with play/pause and a seek bar. */
@Composable
fun VideoPlayer(
    path: String,
    loop: Boolean,
    initialAspect: Float,
    onTap: () -> Unit,
    showControls: Boolean = true,
    controlsPadding: Dp = 0.dp,
) {
    var aspect by remember(path) { mutableFloatStateOf(initialAspect) }
    var playing by remember(path) { mutableStateOf(true) }
    var position by remember(path) { mutableIntStateOf(0) }
    var duration by remember(path) { mutableIntStateOf(0) }
    val holder = remember(path) { arrayOfNulls<VideoView>(1) }
    val tap by rememberUpdatedState(onTap)
    LaunchedEffect(path, playing) {
        while (playing) {
            holder[0]?.let { v ->
                position = v.currentPosition
                if (duration <= 0) duration = v.duration.coerceAtLeast(0)
            }
            delay(200)
        }
    }
    DisposableEffect(path) {
        onDispose { holder[0]?.stopPlayback() }
    }
    Box(
        Modifier
            .fillMaxSize()
            // Without controls (status viewer) the parent handles taps.
            .then(if (showControls) Modifier.pointerInput(path) { detectTapGestures(onTap = { tap() }) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = loop
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) aspect = mp.videoWidth.toFloat() / mp.videoHeight
                        duration = mp.duration
                        start()
                    }
                    setOnCompletionListener {
                        playing = false
                        position = duration
                    }
                    setVideoPath(path)
                }.also { holder[0] = it }
            },
            modifier = Modifier.aspectRatio(aspect.coerceIn(0.2f, 5f)),
        )
        if (showControls && !loop) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = controlsPadding)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable {
                            val v = holder[0] ?: return@clickable
                            if (v.isPlaying) {
                                v.pause()
                                playing = false
                            } else {
                                if (duration > 0 && position >= duration - 300) v.seekTo(0)
                                v.start()
                                playing = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(Fmt.duration(position / 1000), style = Type.footnote, color = Color.White)
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .pointerInput(path, duration) {
                            detectTapGestures(onTap = { at ->
                                val v = holder[0]
                                if (v != null && duration > 0) {
                                    val to = (at.x / size.width * duration).toInt()
                                    v.seekTo(to)
                                    position = to
                                }
                            })
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White))
                }
                Text(Fmt.duration(duration / 1000), style = Type.footnote, color = Color.White)
            }
        }
    }
}

/** First frame of a video (for previews before sending). */
suspend fun videoFrame(ctx: android.content.Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        r.frameAtTime?.asImageBitmap()
    } catch (e: Throwable) {
        null
    } finally {
        runCatching { r.release() }
    }
}
