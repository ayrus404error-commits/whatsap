package com.glasschat.ui.screens

import com.glasschat.ui.glass.menuGlass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

private fun hsvOf(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
private fun colorOf(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
fun hexOf(c: Color) = String.format("%06X", c.toArgb() and 0xFFFFFF)

/**
 * A full colour picker: saturation/brightness square, a hue strip, the new
 * colour next to the current one, and the hex code and RGB values.
 */
@Composable
fun HsvColorPicker(color: Color, current: Color, onChange: (Color) -> Unit) {
    val p = LocalPalette.current
    var hue by remember { mutableFloatStateOf(hsvOf(color)[0]) }
    var sat by remember { mutableFloatStateOf(hsvOf(color)[1]) }
    var value by remember { mutableFloatStateOf(hsvOf(color)[2]) }
    // Follow colours set from outside (swatches, switching which bubble).
    LaunchedEffect(color) {
        if (colorOf(hue, sat, value).toArgb() != color.toArgb()) {
            val hsv = hsvOf(color)
            if (hsv[1] > 0.01f && hsv[2] > 0.01f) hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    fun emit() = change(colorOf(hue, sat, value))

    Column {
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            // Saturation (left → right) and brightness (top → bottom).
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        fun at(o: Offset) {
                            sat = (o.x / size.width).coerceIn(0f, 1f)
                            value = (1f - o.y / size.height).coerceIn(0f, 1f)
                            emit()
                        }
                        detectTapGestures { at(it) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { o ->
                            sat = (o.x / size.width).coerceIn(0f, 1f)
                            value = (1f - o.y / size.height).coerceIn(0f, 1f)
                            emit()
                        }) { change, _ ->
                            change.consume()
                            sat = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            emit()
                        }
                    },
            ) {
                Canvas(Modifier.matchParentSizeCompat()) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, colorOf(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val c = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.3f), radius = 10.5.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
                }
            }
            Spacer(Modifier.width(12.dp))
            // Hue strip (top → bottom).
            Box(
                Modifier
                    .width(26.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { o -> hue = (o.y / size.height * 360f).coerceIn(0f, 359.9f); emit() }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = (change.position.y / size.height * 360f).coerceIn(0f, 359.9f)
                            emit()
                        }
                    },
            ) {
                Canvas(Modifier.matchParentSizeCompat()) {
                    drawRect(Brush.verticalGradient((0..6).map { colorOf(it * 60f % 360f, 1f, 1f) }))
                    val y = hue / 360f * size.height
                    drawRoundRect(
                        Color.White,
                        topLeft = Offset(-2f, y - 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + 4f, 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // New over current, like a desktop colour picker.
            Column(Modifier.width(56.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, p.separator, RoundedCornerShape(8.dp))) {
                Box(Modifier.fillMaxWidth().height(22.dp).background(colorOf(hue, sat, value)))
                Box(Modifier.fillMaxWidth().height(22.dp).background(current))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val shown = colorOf(hue, sat, value)
                var hex by remember { mutableStateOf(hexOf(shown)) }
                var editingHex by remember { mutableStateOf(false) }
                LaunchedEffect(shown.toArgb()) { if (!editingHex) hex = hexOf(shown) }
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(p.label.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#", style = Type.body, color = p.secondaryLabel)
                    BasicTextField(
                        value = hex,
                        onValueChange = { t ->
                            editingHex = true
                            hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            if (hex.length == 6) {
                                val c = Color(0xFF000000 or hex.toLong(16))
                                val hsv = hsvOf(c)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                onChange(c)
                            }
                        },
                        singleLine = true,
                        textStyle = Type.body.copy(color = p.label, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.width(90.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "R ${(shown.red * 255).toInt()}   G ${(shown.green * 255).toInt()}   B ${(shown.blue * 255).toInt()}   " +
                        "H ${hue.toInt()}°  S ${(sat * 100).toInt()}%  B ${(value * 100).toInt()}%",
                    style = Type.caption,
                    color = p.secondaryLabel,
                )
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().fillMaxHeight())

/** One round colour swatch. */
@Composable
fun ColorDot(c: Color, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(30.dp)
            .aspectRatio(1f)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(c)
            .border(if (selected) 3.dp else 0.5.dp, if (selected) p.label else p.separator, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
    )
}

private val QuickColors = listOf(
    0xFF1DAA61, 0xFF00A884, 0xFF0A84FF, 0xFF34B7F1, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D95, 0xFFFF3B30,
    0xFFFF9500, 0xFFFFCC00, 0xFFA2845E, 0xFF8E8E93, 0xFF30B0C7, 0xFF64D2FF, 0xFFBF5AF2, 0xFFFFFFFF,
)

/**
 * A glass sheet to pick one colour (app colour, link colour…): the full
 * picker, quick picks, and Default to go back to the theme's own colour.
 */
@Composable
fun ColorSettingSheet(title: String, sample: String, initial: Color, onSave: (Color?) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    var color by remember { mutableStateOf(initial) }
    com.glasschat.ui.glass.GlassModal(onDismiss = onClose, alignment = Alignment.BottomCenter, dim = 0.3f) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(8.dp)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                .fillMaxWidth()
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            Text(title, style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = p.label)
            Spacer(Modifier.height(10.dp))
            Text(sample, style = Type.rowTitle, color = color)
            Spacer(Modifier.height(14.dp))
            HsvColorPicker(color, initial) { color = it }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickColors.take(8).forEach { c -> ColorDot(Color(c), Color(c).toArgb() == color.toArgb()) { color = Color(c) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickColors.drop(8).forEach { c -> ColorDot(Color(c), Color(c).toArgb() == color.toArgb()) { color = Color(c) } }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton("Default", p.label.copy(alpha = 0.1f), p.label, Modifier.weight(1f)) { onSave(null); onClose() }
                SheetButton("Cancel", p.label.copy(alpha = 0.1f), p.label, Modifier.weight(1f), onClose)
                SheetButton("Use", p.accent, Color.White, Modifier.weight(1f)) { onSave(color); onClose() }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}
