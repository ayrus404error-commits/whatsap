package com.glasschat.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.glasschat.engine.Engine
import com.glasschat.engine.ReceiptEntry
import com.glasschat.media.MediaPrep
import com.glasschat.media.PendingMedia
import com.glasschat.media.VoiceRecorder
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// -----------------------------------------------------------------------------
// Voice status

/** Record a voice status: tap to start, tap to stop, then post. */
@Composable
fun VoiceStatusRecorder(onPosted: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var ms by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(recording) {
        while (recording) {
            ms = recorder.elapsedMs()
            if (ms >= 60_000) {
                recording = false
                recorder.stop(keep = true)?.let { Engine.sendMedia(Engine.STATUS_CHAT, MediaPrep.voice(it)); onPosted() }
            }
            delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.stop(keep = false) } }

    Box(Modifier.fillMaxSize().background(Color(0xFF5B3FA8)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (recording) Fmt.duration((ms / 1000).toInt()) else "Voice status", style = Type.largeTitle, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                error ?: if (recording) "Tap to stop and post (up to 1 minute)" else if (granted) "Tap the microphone to record" else "Allow the microphone to record",
                style = Type.subhead,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (recording) Color(0xFFFF3B30) else Color.White)
                    .clickable {
                        if (!granted) {
                            ask.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (!recording) {
                            if (recorder.start()) recording = true else error = "Couldn't start the microphone"
                        } else {
                            recording = false
                            val rec = recorder.stop(keep = true)
                            if (rec == null) {
                                error = "Too short: hold on a little longer"
                            } else {
                                Engine.sendMedia(Engine.STATUS_CHAT, MediaPrep.voice(rec))
                                onPosted()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (recording) Color.White else Color(0xFF5B3FA8), modifier = Modifier.size(44.dp))
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).size(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.2f)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// -----------------------------------------------------------------------------
// Layout (photo collage)

private val LayoutsFor = mapOf(
    2 to listOf("rows", "columns"),
    3 to listOf("rows", "big-top", "big-left"),
    4 to listOf("grid", "rows"),
)

/** Cells for a layout, as fractions (left, top, right, bottom) of a 9:16 canvas. */
private fun cells(layout: String, n: Int): List<FloatArray> = when {
    layout == "columns" -> List(n) { i -> floatArrayOf(i / n.toFloat(), 0f, (i + 1) / n.toFloat(), 1f) }
    layout == "grid" -> List(4) { i -> floatArrayOf((i % 2) / 2f, (i / 2) / 2f, (i % 2 + 1) / 2f, (i / 2 + 1) / 2f) }
    layout == "big-top" -> listOf(floatArrayOf(0f, 0f, 1f, 0.5f), floatArrayOf(0f, 0.5f, 0.5f, 1f), floatArrayOf(0.5f, 0.5f, 1f, 1f))
    layout == "big-left" -> listOf(floatArrayOf(0f, 0f, 0.6f, 1f), floatArrayOf(0.6f, 0f, 1f, 0.5f), floatArrayOf(0.6f, 0.5f, 1f, 1f))
    else -> List(n) { i -> floatArrayOf(0f, i / n.toFloat(), 1f, (i + 1) / n.toFloat()) }
}

/** Pick 2–4 photos and put them together in one status picture. */
@Composable
fun LayoutMaker(onDone: (List<PendingMedia>) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var thumbs by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    var layout by remember { mutableStateOf("rows") }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.size < 2) {
            if (photos.isEmpty()) onClose()
        } else {
            photos = uris.take(4)
            layout = LayoutsFor[photos.size]?.first() ?: "rows"
        }
    }
    LaunchedEffect(Unit) { runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
    LaunchedEffect(photos) {
        thumbs = withContext(Dispatchers.IO) { photos.map { u -> runCatching { decodeMax(ctx, u, 900).asImageBitmap() }.getOrNull() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text("Layout", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                if (photos.size >= 2) {
                    Text(
                        if (saving) "…" else "Next",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).clickable(enabled = !saving) {
                            saving = true
                            scope.launch {
                                val f = withContext(Dispatchers.IO) { runCatching { renderCollage(ctx, photos, layout) }.getOrNull() }
                                saving = false
                                if (f != null) onDone(listOf(PendingMedia(Uri.fromFile(f), false)))
                            }
                        }.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (photos.size < 2) {
                    Text("Choose 2 to 4 photos", style = Type.body, color = Color.White.copy(alpha = 0.7f))
                } else {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E))) {
                        cells(layout, photos.size).forEachIndexed { i, c ->
                            val t = thumbs.getOrNull(i)
                            Box(
                                Modifier
                                    .padding(start = maxWidth * c[0], top = maxHeight * c[1])
                                    .size(maxWidth * (c[2] - c[0]), maxHeight * (c[3] - c[1]))
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2C2C2E)),
                            ) {
                                if (t != null) Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center) {
                LayoutsFor[photos.size].orEmpty().forEach { l ->
                    val sel = l == layout
                    Box(
                        Modifier.padding(horizontal = 8.dp).size(width = 44.dp, height = 70.dp).clip(RoundedCornerShape(8.dp))
                            .border(2.dp, if (sel) p.accent else Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { layout = l },
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                            cells(l, photos.size).forEach { c ->
                                drawRect(
                                    Color.White.copy(alpha = if (sel) 0.85f else 0.45f),
                                    topLeft = androidx.compose.ui.geometry.Offset(size.width * c[0] + 1.5f, size.height * c[1] + 1.5f),
                                    size = androidx.compose.ui.geometry.Size(size.width * (c[2] - c[0]) - 3f, size.height * (c[3] - c[1]) - 3f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeMax(ctx: Context, uri: Uri, max: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        val long = maxOf(info.size.width, info.size.height)
        if (long > max) {
            val s = max.toFloat() / long
            d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

private fun renderCollage(ctx: Context, photos: List<Uri>, layout: String): File {
    val w = 1080
    val h = 1920
    val gap = 8
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(android.graphics.Color.rgb(17, 17, 17))
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    cells(layout, photos.size).forEachIndexed { i, cell ->
        val src = decodeMax(ctx, photos[i], 1600)
        val dst = Rect((cell[0] * w).toInt() + gap, (cell[1] * h).toInt() + gap, (cell[2] * w).toInt() - gap, (cell[3] * h).toInt() - gap)
        // Centre-crop the photo to the cell.
        val cellRatio = dst.width().toFloat() / dst.height()
        val srcRatio = src.width.toFloat() / src.height
        val crop = if (srcRatio > cellRatio) {
            val cw = (src.height * cellRatio).toInt()
            Rect((src.width - cw) / 2, 0, (src.width + cw) / 2, src.height)
        } else {
            val ch = (src.width / cellRatio).toInt()
            Rect(0, (src.height - ch) / 2, src.width, (src.height + ch) / 2)
        }
        c.drawBitmap(src, crop, dst, paint)
        src.recycle()
    }
    val f = File(ctx.cacheDir, "layout_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return f
}

// -----------------------------------------------------------------------------
// Who viewed my status

@Composable
fun StatusViewersSheet(statusId: String, onClose: () -> Unit) {
    val p = LocalPalette.current
    BackHandler(onBack = onClose)
    var viewers by remember { mutableStateOf<List<ReceiptEntry>?>(null) }
    LaunchedEffect(statusId) { viewers = Engine.statusViewers(statusId) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(p.groupedBackground)
                .clickable(enabled = false) {}
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            val list = viewers
            Text(
                if (list == null) "Viewed by…" else "Viewed by ${list.size}",
                style = Type.title.copy(fontWeight = FontWeight.SemiBold),
                color = p.label,
                modifier = Modifier.padding(20.dp),
            )
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent)
                }
                list.isEmpty() -> Text(
                    "No views yet. People who have read receipts turned off don't show here.",
                    style = Type.subhead,
                    color = p.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                else -> LazyColumn {
                    items(list, key = { it.jid }) { v ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(v.jid, false, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name.removePrefix("~ "), style = Type.body, color = p.label)
                                if (v.ts > 0) Text(timeAgo(v.ts), style = Type.footnote, color = p.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plays a voice/audio status while it's on screen. */
@Composable
fun StatusAudio(path: String?, paused: Boolean) {
    val player = remember { android.media.MediaPlayer() }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (path == null) return@LaunchedEffect
        runCatching {
            player.reset()
            player.setDataSource(path)
            player.prepare()
            player.start()
            ready = true
        }
    }
    LaunchedEffect(paused, ready) {
        if (!ready) return@LaunchedEffect
        runCatching { if (paused) player.pause() else player.start() }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    Box(Modifier.fillMaxSize().background(Color(0xFF5B3FA8)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        if (path == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
        }
    }
}
