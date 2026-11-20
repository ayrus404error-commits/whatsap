package com.glasschat.ui.screens

import androidx.compose.material.icons.rounded.BlurOn
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.media.PendingMedia
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Something drawn on the photo. Positions are fractions of the photo (0..1). */
private sealed interface Mark

private class StrokeMark(val points: List<Offset>, val color: Color, val width: Float) : Mark

/** Text or an emoji: centre (x, y), size as a fraction of the photo's width. */
private class TextMark(val text: String, val color: Color, val x: Float, val y: Float, val size: Float, val emoji: Boolean) : Mark

/** A blur brush stroke: hides faces, numbers, addresses… */
private class BlurMark(val points: List<Offset>, val width: Float) : Mark

private enum class Tool { None, Crop, Draw, Blur }

/** A heavily blurred copy of [src] (same size), used to paint the blur brush. */
private fun blurredCopy(src: Bitmap): Bitmap {
    val sw = maxOf(1, src.width / 24)
    val sh = maxOf(1, src.height / 24)
    val tiny = Bitmap.createScaledBitmap(src, sw, sh, true)
    val mid = Bitmap.createScaledBitmap(tiny, maxOf(1, src.width / 6), maxOf(1, src.height / 6), true)
    return Bitmap.createScaledBitmap(mid, src.width, src.height, true)
}

private fun blurShader(blurred: Bitmap, scale: Float): android.graphics.BitmapShader =
    android.graphics.BitmapShader(blurred, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(scale, scale) })
    }

private val Palette = listOf(
    Color.White, Color.Black, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00),
    Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D55),
)

/**
 * Photo editor for the send preview: crop, rotate, draw, text and emoji.
 * [onDone] gets a new JPEG file with everything burned in.
 */
@Composable
fun ImageEditor(item: PendingMedia, onDone: (PendingMedia) -> Unit, onCancel: () -> Unit, initialTool: String = "") {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onCancel)

    var base by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) { base = withContext(Dispatchers.IO) { runCatching { decode(ctx, item.uri) }.getOrNull() } }
    val image: ImageBitmap? = remember(base) { base?.asImageBitmap() }
    val marks = remember { mutableStateListOf<Mark>() }
    var tool by remember { mutableStateOf(if (initialTool == "blur") Tool.Blur else if (initialTool == "draw") Tool.Draw else if (initialTool == "crop") Tool.Crop else Tool.None) }
    var blurSize by remember { mutableStateOf(0.07f) }
    val blurred: Bitmap? by androidx.compose.runtime.produceState<Bitmap?>(null, base) {
        val b = base
        value = if (b == null) null else withContext(Dispatchers.Default) { runCatching { blurredCopy(b) }.getOrNull() }
    }
    var color by remember { mutableStateOf(Color.White) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var textEntry by remember { mutableStateOf<String?>(null) }
    var emojiPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = base
        if (bmp == null || image == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 64.dp, bottom = 150.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Where the photo sits on screen (fitted).
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val k = minOf(boxW / bmp.width, boxH / bmp.height)
                val w = bmp.width * k
                val h = bmp.height * k
                Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                    Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    // Drawing layer.
                    ComposeCanvas(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (tool == Tool.Draw || tool == Tool.Blur) {
                                    Modifier.pointerInput(color, tool, blurSize) {
                                        detectDragGestures(
                                            onDragStart = { o -> current = listOf(Offset(o.x / w, o.y / h), Offset(o.x / w + 0.0001f, o.y / h)) },
                                            onDrag = { change, _ -> current = current + Offset(change.position.x / w, change.position.y / h) },
                                            onDragEnd = {
                                                if (current.size > 1) {
                                                    marks.add(if (tool == Tool.Blur) BlurMark(current, blurSize) else StrokeMark(current, color, 0.012f))
                                                }
                                                current = emptyList()
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        fun drawStroke(points: List<Offset>, c: Color, width: Float) {
                            if (points.size < 2) return
                            val path = Path().apply {
                                moveTo(points[0].x * w, points[0].y * h)
                                points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                            }
                            drawPath(path, c, style = Stroke(width * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        val bl = blurred
                        val blurBrush = if (bl != null) androidx.compose.ui.graphics.ShaderBrush(blurShader(bl, w / bl.width)) else null
                        fun drawBlur(points: List<Offset>, width: Float) {
                            if (points.size < 2) return
                            val path = Path().apply {
                                moveTo(points[0].x * w, points[0].y * h)
                                points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                            }
                            val st = Stroke(width * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            if (blurBrush != null) drawPath(path, blurBrush, style = st) else drawPath(path, Color.Gray, style = st)
                        }
                        marks.forEach {
                            when (it) {
                                is StrokeMark -> drawStroke(it.points, it.color, it.width)
                                is BlurMark -> drawBlur(it.points, it.width)
                                else -> {}
                            }
                        }
                        if (tool == Tool.Blur) drawBlur(current, blurSize) else drawStroke(current, color, 0.012f)
                    }
                    // Text and emoji, draggable and pinch-to-resize.
                    marks.forEachIndexed { i, m ->
                        if (m is TextMark) {
                            val px = m.size * w
                            Text(
                                m.text,
                                fontSize = with(density) { px.toSp() },
                                fontWeight = if (m.emoji) FontWeight.Normal else FontWeight.Bold,
                                color = m.color,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .offset { IntOffset((m.x * w).roundToInt(), (m.y * h).roundToInt()) }
                                    .layout { measurable, constraints ->
                                        // Centre the text on its point.
                                        val pl = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (w * 2).roundToInt()))
                                        layout(pl.width, pl.height) { pl.place(-pl.width / 2, -pl.height / 2) }
                                    }
                                    .then(
                                        if (tool == Tool.None) {
                                            Modifier.pointerInput(i) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    val old = marks.getOrNull(i) as? TextMark ?: return@detectTransformGestures
                                                    marks[i] = TextMark(
                                                        old.text, old.color,
                                                        (old.x + pan.x / w).coerceIn(0f, 1f),
                                                        (old.y + pan.y / h).coerceIn(0f, 1f),
                                                        (old.size * zoom).coerceIn(0.03f, 0.6f),
                                                        old.emoji,
                                                    )
                                                }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                    if (tool == Tool.Crop) CropOverlay(crop, w, h) { crop = it }
                }
            }
        }

        // Top bar: cancel · tools · done.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ToolButton(Icons.Rounded.Close, "Cancel", false, onCancel)
            Spacer(Modifier.weight(1f))
            if (marks.isNotEmpty()) ToolButton(Icons.AutoMirrored.Rounded.Undo, "Undo", false) { marks.removeAt(marks.lastIndex) }
            ToolButton(Icons.Rounded.Crop, "Crop", tool == Tool.Crop) { tool = if (tool == Tool.Crop) Tool.None else Tool.Crop }
            ToolButton(Icons.Rounded.RotateRight, "Rotate", false) {
                base?.let { b ->
                    base = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true)
                    marks.clear()
                    crop = Rect(0f, 0f, 1f, 1f)
                }
            }
            ToolButton(Icons.Rounded.EmojiEmotions, "Emoji", false) { tool = Tool.None; emojiPicker = true }
            ToolButton(Icons.Rounded.TextFields, "Text", false) { tool = Tool.None; textEntry = "" }
            ToolButton(Icons.Rounded.BlurOn, "Blur", tool == Tool.Blur) { tool = if (tool == Tool.Blur) Tool.None else Tool.Blur }
            ToolButton(Icons.Rounded.Draw, "Draw", tool == Tool.Draw) { tool = if (tool == Tool.Draw) Tool.None else Tool.Draw }
        }

        // Bottom: colours (for drawing and text) and Done.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tool == Tool.Blur) {
                // Brush size: small · medium · large.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.035f to 14.dp, 0.07f to 22.dp, 0.13f to 30.dp).forEach { (sz, d) ->
                        Box(
                            Modifier.size(d).clip(CircleShape).background(Color.White.copy(alpha = if (sz == blurSize) 0.9f else 0.35f))
                                .clickable { blurSize = sz },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (tool == Tool.Draw || textEntry != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.forEach { c ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(c)
                                .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                when (tool) {
                    Tool.Crop -> "Drag the corners to crop"
                    Tool.Draw -> "Draw with your finger"
                    Tool.Blur -> "Paint over what you want to hide"
                    else -> if (marks.any { it is TextMark }) "Drag text and emoji to move them, pinch to resize" else " "
                },
                style = Type.footnote,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (saving) "Saving…" else "Done",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.accent)
                    .clickable(enabled = base != null && !saving) {
                        val b = base ?: return@clickable
                        saving = true
                        val snapshot = marks.toList()
                        val c = crop
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { runCatching { render(ctx, b, snapshot, c) }.getOrNull() }
                            saving = false
                            if (out != null) onDone(PendingMedia(Uri.fromFile(out), false)) else onCancel()
                        }
                    }
                    .padding(horizontal = 34.dp, vertical = 12.dp),
            )
        }

        textEntry?.let { t ->
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = t,
                    onValueChange = { textEntry = it.take(120) },
                    textStyle = Type.title2.copy(color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 30.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(24.dp).focusRequester(focus),
                )
                Text(
                    "Add",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(p.accent)
                        .clickable {
                            if (t.isNotBlank()) marks.add(TextMark(t.trim(), color, 0.5f, 0.45f, 0.08f, emoji = false))
                            textEntry = null
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
        if (emojiPicker) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { emojiPicker = false }, contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp).clickable(enabled = false) {}) {
                    EmojiStickerPanel(
                        onEmoji = { e ->
                            marks.add(TextMark(e, Color.White, 0.5f, 0.5f, 0.18f, emoji = true))
                            emojiPicker = false
                        },
                        onBackspace = {},
                        onSticker = {},
                        onStickerFile = {},
                        emojiOnly = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (active) Color.White else Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Dims outside the crop and lets the four corners be dragged. */
@Composable
private fun CropOverlay(crop: Rect, w: Float, h: Float, onChange: (Rect) -> Unit) {
    val handle = 28.dp
    val density = LocalDensity.current
    val hp = with(density) { handle.toPx() }
    ComposeCanvas(Modifier.fillMaxSize()) {
        val r = Rect(crop.left * w, crop.top * h, crop.right * w, crop.bottom * h)
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset.Zero, androidx.compose.ui.geometry.Size(w, r.top))
        drawRect(dim, Offset(0f, r.bottom), androidx.compose.ui.geometry.Size(w, h - r.bottom))
        drawRect(dim, Offset(0f, r.top), androidx.compose.ui.geometry.Size(r.left, r.height))
        drawRect(dim, Offset(r.right, r.top), androidx.compose.ui.geometry.Size(w - r.right, r.height))
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.4f), Offset(r.left + r.width * i / 3, r.top), Offset(r.left + r.width * i / 3, r.bottom))
            drawLine(Color.White.copy(alpha = 0.4f), Offset(r.left, r.top + r.height * i / 3), Offset(r.right, r.top + r.height * i / 3))
        }
    }
    val min = 0.12f
    val latest by rememberUpdatedState(crop)
    listOf(0, 1, 2, 3).forEach { corner ->
        val cx = if (corner % 2 == 0) crop.left else crop.right
        val cy = if (corner < 2) crop.top else crop.bottom
        Box(
            Modifier
                .offset { IntOffset((cx * w - hp / 2).roundToInt(), (cy * h - hp / 2).roundToInt()) }
                .size(handle)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(corner) {
                    var r = latest
                    detectDragGestures(onDragStart = { r = latest }) { change, drag ->
                        change.consume()
                        val dx = drag.x / w
                        val dy = drag.y / h
                        r = when (corner) {
                            0 -> r.copy(left = (r.left + dx).coerceIn(0f, r.right - min), top = (r.top + dy).coerceIn(0f, r.bottom - min))
                            1 -> r.copy(right = (r.right + dx).coerceIn(r.left + min, 1f), top = (r.top + dy).coerceIn(0f, r.bottom - min))
                            2 -> r.copy(left = (r.left + dx).coerceIn(0f, r.right - min), bottom = (r.bottom + dy).coerceIn(r.top + min, 1f))
                            else -> r.copy(right = (r.right + dx).coerceIn(r.left + min, 1f), bottom = (r.bottom + dy).coerceIn(r.top + min, 1f))
                        }
                        onChange(r)
                    }
                },
        )
    }
}

private fun decode(ctx: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        val long = maxOf(info.size.width, info.size.height)
        if (long > 2560) {
            val s = 2560f / long
            d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        d.isMutableRequired = true
    }

/** Burns drawings, text and emoji into the photo, then crops it. */
private fun render(ctx: Context, src: Bitmap, marks: List<Mark>, crop: Rect): File {
    val full = src.copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(full)
    val w = full.width.toFloat()
    val h = full.height.toFloat()
    val blurred = if (marks.any { it is BlurMark }) blurredCopy(src) else null
    for (m in marks) {
        when (m) {
            is BlurMark -> if (blurred != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = m.width * w
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    shader = blurShader(blurred, 1f)
                }
                val path = android.graphics.Path()
                path.moveTo(m.points[0].x * w, m.points[0].y * h)
                m.points.drop(1).forEach { path.lineTo(it.x * w, it.y * h) }
                c.drawPath(path, paint)
            }
            is StrokeMark -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = m.width * w
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = m.color.toArgb()
                }
                val path = android.graphics.Path()
                path.moveTo(m.points[0].x * w, m.points[0].y * h)
                m.points.drop(1).forEach { path.lineTo(it.x * w, it.y * h) }
                c.drawPath(path, paint)
            }
            is TextMark -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = m.size * w
                    textAlign = Paint.Align.CENTER
                    color = m.color.toArgb()
                    isFakeBoldText = !m.emoji
                    com.glasschat.ui.theme.AppFont.typeface?.let { typeface = it }
                    if (!m.emoji) setShadowLayer(textSize * 0.06f, 0f, textSize * 0.03f, android.graphics.Color.argb(120, 0, 0, 0))
                }
                c.drawText(m.text, m.x * w, m.y * h + paint.textSize * 0.35f, paint)
            }
        }
    }
    val l = (crop.left * w).roundToInt().coerceIn(0, full.width - 1)
    val t = (crop.top * h).roundToInt().coerceIn(0, full.height - 1)
    val r = (crop.right * w).roundToInt().coerceIn(l + 1, full.width)
    val b = (crop.bottom * h).roundToInt().coerceIn(t + 1, full.height)
    val out = Bitmap.createBitmap(full, l, t, r - l, b - t)
    val f = File(ctx.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    return f
}
