package com.glasschat.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import com.glasschat.ui.components.Fmt
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.glasschat.media.PendingMedia
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pick the part of a video to send (start and end), before sending. */
@Composable
fun VideoTrimmer(item: PendingMedia, onDone: (PendingMedia) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var duration by remember { mutableStateOf(0L) }
    var strip by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var range by remember { mutableStateOf(0f..1f) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewAt by remember { mutableStateOf(0L) }
    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            val r = retriever(ctx, item.uri) ?: return@withContext
            try {
                val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                duration = d
                if (d > 0) {
                    range = if (item.trimEndMs > item.trimStartMs) {
                        (item.trimStartMs.toFloat() / d)..(item.trimEndMs.toFloat() / d).coerceAtMost(1f)
                    } else {
                        0f..1f
                    }
                    strip = (0 until 8).mapNotNull { i ->
                        frame(r, d * (2 * i + 1) / 16, 160)
                    }
                }
            } finally {
                runCatching { r.release() }
            }
        }
    }
    LaunchedEffect(previewAt, duration) {
        if (duration <= 0) return@LaunchedEffect
        preview = withContext(Dispatchers.IO) {
            val r = retriever(ctx, item.uri) ?: return@withContext null
            try { frame(r, previewAt, 1080) } finally { runCatching { r.release() } }
        }
    }
    val startMs = (range.start * duration).toLong()
    val endMs = (range.endInclusive * duration).toLong()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        preview?.let {
            Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(bottom = 170.dp, top = 60.dp))
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cancel", style = Type.body, color = Color.White, modifier = Modifier.clickable(onClick = onCancel))
            Spacer(Modifier.weight(1f))
            Text("Trim video", style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent,
                modifier = Modifier.clickable {
                    val whole = range.start <= 0.001f && range.endInclusive >= 0.999f
                    onDone(if (whole || duration <= 0) item.copy(trimStartMs = 0, trimEndMs = 0) else item.copy(trimStartMs = startMs, trimEndMs = endMs))
                },
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${Fmt.duration((startMs / 1000).toInt())} – ${Fmt.duration((endMs / 1000).toInt())} · ${Fmt.duration(((endMs - startMs + 500) / 1000).toInt())}",
                style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    strip.forEach { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).height(48.dp)) }
                }
                RangeSlider(
                    value = range,
                    onValueChange = { v ->
                        // Keep at least one second.
                        val minGap = if (duration > 0) (1000f / duration).coerceAtMost(1f) else 0f
                        if (v.endInclusive - v.start >= minGap) {
                            if (v.start != range.start) previewAt = (v.start * duration).toLong()
                            else if (v.endInclusive != range.endInclusive) previewAt = (v.endInclusive * duration).toLong()
                            range = v
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = p.accent.copy(alpha = 0.55f),
                        inactiveTrackColor = Color.Black.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Drag the ends to choose the part to send", style = Type.footnote, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun retriever(ctx: Context, uri: Uri): MediaMetadataRetriever? = runCatching {
    MediaMetadataRetriever().apply {
        if (uri.scheme == "file") setDataSource(uri.path) else setDataSource(ctx, uri)
    }
}.getOrNull()

private fun frame(r: MediaMetadataRetriever, ms: Long, maxSide: Int): ImageBitmap? = runCatching {
    val b = r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
    val s = maxSide.toFloat() / maxOf(b.width, b.height)
    val out = if (s < 1f) Bitmap.createScaledBitmap(b, maxOf(1, (b.width * s).toInt()), maxOf(1, (b.height * s).toInt()), true) else b
    out.asImageBitmap()
}.getOrNull()
