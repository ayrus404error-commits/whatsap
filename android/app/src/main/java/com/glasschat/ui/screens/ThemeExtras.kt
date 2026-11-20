package com.glasschat.ui.screens

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.imePadding
import com.glasschat.ui.glass.menuGlass
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.glasschat.ui.theme.AppFont
import com.glasschat.ui.theme.Appearance
import com.glasschat.ui.theme.ChatStyle
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chat settings → Font: use a TTF/OTF from the phone for all text (or go back to the phone's font). */
@Composable
fun FontPicker(onMessage: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val style by Appearance.style.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val display = runCatching {
                    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: "font.ttf"
                val name = withContext(Dispatchers.IO) { AppFont.import(ctx, uri, display) }
                if (name == null) {
                    onMessage("That file isn't a font this phone can use (choose a .ttf or .otf).")
                } else {
                    Appearance.update { it.copy(font = name) }
                    onMessage("Font changed to $display")
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            if (style.font.isEmpty()) "Phone's font" else "Your font",
            style = Type.body.copy(fontWeight = FontWeight.SemiBold),
            color = p.label,
        )
        Text("The quick brown fox · 😀 👍 ❤️ 🎉", style = Type.title2, color = p.label, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Choose font file",
                style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                    .clickable { runCatching { picker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) } }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
            if (style.font.isNotEmpty()) {
                Text(
                    "Use phone's font",
                    style = Type.subhead,
                    color = p.link,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                        Appearance.update { it.copy(font = "") }
                        AppFont.dir(ctx).listFiles()?.forEach { it.delete() }
                        onMessage("Back to the phone's font")
                    }.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Any .ttf or .otf font on your phone works, for all text in the app. For iPhone-style emoji, choose an emoji font made for " +
                "Android (a colour-emoji .ttf). The app doesn't include Apple's own font. Letters a font doesn't have use the phone's font.",
            style = Type.footnote,
            color = p.secondaryLabel,
        )
    }
}

private val Swatches = listOf(
    0xFF1DAA61, 0xFF2E7D5B, 0xFF00796B, 0xFF00A3C4, 0xFF0A84FF, 0xFF2B5FD9, 0xFF3F3DB8, 0xFF6A4BD8,
    0xFF8E6FD8, 0xFFAF52DE, 0xFFFF2D95, 0xFFB83B6B, 0xFFD6334A, 0xFFFF3B30, 0xFFE5654E, 0xFFFF9500,
    0xFFE5A823, 0xFF7DB82E, 0xFF6B4226, 0xFF1B2A4A, 0xFF45454C, 0xFF111111,
)

/** Pick your own bubble colour, and optionally a second colour for a gradient. */
@Composable
fun CustomThemeDialog(onClose: () -> Unit) {
    val p = LocalPalette.current
    val style by Appearance.style.collectAsState()
    // What you're colouring: their bubble, your bubble, or the end of your gradient.
    var target by remember { mutableIntStateOf(1) }
    var mine by remember { mutableStateOf(Color(style.customA or 0xFF000000L)) }
    var gradient by remember { mutableStateOf(if (style.customB != 0L) Color(style.customB or 0xFF000000L) else null) }
    var theirs by remember { mutableStateOf(if (style.customIn != 0L) Color(style.customIn or 0xFF000000L) else null) }
    val start = remember { Triple(mine, gradient, theirs) }
    val theirsShown = theirs ?: p.bubbleIn
    com.glasschat.ui.glass.GlassModal(onDismiss = onClose, alignment = Alignment.BottomCenter, dim = 0.3f) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .fillMaxWidth()
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bubble colours", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Close", tint = p.label, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            // Live preview: tap a bubble to colour it.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.background).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val theirText = if (theirs == null) p.label else if (theirsShown.luminance() > 0.45f) Color.Black else Color.White
                Text(
                    "Their message",
                    style = Type.body,
                    color = theirText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(theirsShown)
                        .border(if (target == 0) 2.dp else 0.dp, if (target == 0) p.label else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { target = 0 }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "My message",
                        style = Type.body,
                        color = if (mine.luminance() > 0.6f) Color.Black else Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(mine, gradient ?: mine)))
                            .border(if (target != 0) 2.dp else 0.dp, if (target != 0) p.label else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable { target = 1 }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassSegmented(
                remember { com.glasschat.ui.glass.Backdrop() },
                listOf("Their bubble", "My bubble", "Gradient end"),
                target,
                onSelect = { t ->
                    target = t
                    if (t == 2 && gradient == null) gradient = mine
                },
            )
            Spacer(Modifier.height(10.dp))
            when (target) {
                0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Their bubbles use your colour", style = Type.footnote, color = p.secondaryLabel, modifier = Modifier.weight(1f))
                    IosSwitch(theirs != null) { on -> theirs = if (on) Color(0xFF3A3A3C) else null }
                }
                2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gradient on my bubbles", style = Type.footnote, color = p.secondaryLabel, modifier = Modifier.weight(1f))
                    IosSwitch(gradient != null) { on -> gradient = if (on) mine else null }
                }
                else -> Text("Pick any colour below, or type its hex code.", style = Type.footnote, color = p.secondaryLabel)
            }
            Spacer(Modifier.height(12.dp))
            val editing = when (target) {
                0 -> theirsShown
                2 -> gradient ?: mine
                else -> mine
            }
            val before = when (target) {
                0 -> start.third ?: p.bubbleIn
                2 -> start.second ?: start.first
                else -> start.first
            }
            fun set(c: Color) {
                when (target) {
                    0 -> theirs = c
                    2 -> gradient = c
                    else -> mine = c
                }
            }
            HsvColorPicker(editing, before) { set(it) }
            Spacer(Modifier.height(14.dp))
            Text("Quick picks", style = Type.footnote, color = p.secondaryLabel)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatches.forEach { argb ->
                    val c = Color(argb)
                    ColorDot(c, c.toArgb() == editing.toArgb()) { set(c) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cancel",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.label,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(p.label.copy(alpha = 0.1f)).clickable(onClick = onClose).padding(vertical = 13.dp),
                )
                Text(
                    "Use",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(p.accent).clickable {
                        Appearance.update {
                            it.copy(
                                theme = ChatStyle.CUSTOM_THEME,
                                customA = mine.toArgb().toLong() and 0xFFFFFFFFL,
                                customB = gradient?.let { c -> c.toArgb().toLong() and 0xFFFFFFFFL } ?: 0L,
                                customIn = theirs?.let { c -> c.toArgb().toLong() and 0xFFFFFFFFL } ?: 0L,
                            )
                        }
                        onClose()
                    }.padding(vertical = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun Seg(label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label,
        style = Type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = if (on) Color.White else p.label,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) p.accent else p.fill).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun hsv(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

/** Slide along the rainbow to pick any hue. */
@Composable
private fun HueBar(current: Color, onPick: (Color) -> Unit) {
    val hues = remember { List(7) { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 60f, 0.85f, 0.85f))) } }
    var width by remember { mutableFloatStateOf(1f) }
    val h = hsv(current)
    fun pick(x: Float) {
        val hue = (x / width).coerceIn(0f, 1f) * 360f
        onPick(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, h[1].coerceAtLeast(0.45f), h[2].coerceAtLeast(0.35f)))))
    }
    Box(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(hues))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { pick(it.x) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position.x) } },
    )
}

/** Darker ↔ lighter version of the current colour. */
@Composable
private fun BrightnessBar(current: Color, onPick: (Color) -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    val h = hsv(current)
    val dark = Color(android.graphics.Color.HSVToColor(floatArrayOf(h[0], h[1], 0.15f)))
    val light = Color(android.graphics.Color.HSVToColor(floatArrayOf(h[0], (h[1] * 0.6f), 1f)))
    fun pick(x: Float) {
        val t = (x / width).coerceIn(0f, 1f)
        onPick(Color(android.graphics.Color.HSVToColor(floatArrayOf(h[0], h[1].coerceAtLeast(0.3f) * (1f - t * 0.4f), 0.15f + t * 0.85f))))
    }
    Box(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(dark, current, light)))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { pick(it.x) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position.x) } },
    )
}
