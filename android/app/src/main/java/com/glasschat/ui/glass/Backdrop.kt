package com.glasschat.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Liquid glass, done the way iOS does it: whatever sits behind a glass
 * element is captured live, blurred, colour-boosted, bent like a lens at the
 * rim, then lit with a specular edge highlight.
 *
 * A [Backdrop] is attached to the content behind the glass with
 * [backdropSource]; glass elements (drawn as siblings on top) read from it
 * with [liquidGlass]. Because the backdrop is a RenderNode reference, glass
 * updates for free as the content underneath scrolls.
 */
@Stable
class Backdrop {
    // Plain field: set during composition, read during draw.
    internal var layer: GraphicsLayer? = null
    internal var origin by mutableStateOf(Offset.Zero)

    /** True while the source content is being recorded (draw thread only). */
    internal var recording = false
}

fun Modifier.backdropSource(backdrop: Backdrop): Modifier = composed {
    val layer = rememberGraphicsLayer()
    backdrop.layer = layer
    this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.recording = true
            try {
                layer.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                    this@drawWithContent.drawContent()
                }
            } finally {
                backdrop.recording = false
            }
            drawLayer(layer)
        }
}

/** Visual recipe for a piece of glass. */
data class GlassStyle(
    val blur: Dp = 10.dp,
    val tint: Color = Color.White.copy(alpha = 0.32f),
    val saturation: Float = 1.7f,
    /** How far content bends at the rim, in dp. 0 turns the lens off. */
    val refraction: Dp = 14.dp,
    /** Width of the curved rim band where bending happens. */
    val rim: Dp = 16.dp,
    val highlight: Float = 0.75f,
    val dispersion: Float = 0.35f,
    val fallback: Color = Color(0xF2F6F6F8),
)

fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    style: GlassStyle,
): Modifier = composed {
    val child = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val cache = remember { EffectCache() }
    // Glass placed *inside* its own backdrop would draw the backdrop into
    // itself — an endless loop that crashes the app. If this element is ever
    // drawn while its backdrop is recording, it's inside: use a plain fill.
    val insideSource = remember { booleanArrayOf(false) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (backdrop.recording) insideSource[0] = true
            val source = backdrop.layer
            if (source != null && !insideSource[0] && Build.VERSION.SDK_INT >= 31 && size.width > 0 && size.height > 0) {
                val radius = cornerRadiusOf(outline, size)
                child.renderEffect = cache.get(size, radius, style, this)
                val origin = backdrop.origin
                child.record(IntSize(size.width.toInt(), size.height.toInt())) {
                    translate(origin.x - pos.x, origin.y - pos.y) { drawLayer(source) }
                }
                clipOutline(outline) { drawLayer(child) }
                drawOutline(outline, style.tint)
            } else {
                drawOutline(outline, style.fallback)
            }
            drawRimLight(outline, style.highlight)
        }
}

/** Plain frosted fill for places with no live backdrop (dialogs, sheets). */
fun Modifier.frosted(shape: Shape, color: Color, highlight: Float = 0.5f): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color)
    drawRimLight(outline, highlight)
}

private fun DrawScope.clipOutline(outline: Outline, block: DrawScope.() -> Unit) {
    val path = Path()
    when (outline) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    clipPath(path) { block() }
}

/**
 * The specular edge: bright along the top-left where light hits, fading
 * around the sides, with a softer bounce light at the bottom-right.
 */
internal fun DrawScope.drawRimLight(outline: Outline, strength: Float) {
    if (strength <= 0f) return
    val w = size.width
    val h = size.height
    val brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.6f * strength),
        0.22f to Color.White.copy(alpha = 0.12f * strength),
        0.55f to Color.White.copy(alpha = 0.03f * strength),
        0.85f to Color.White.copy(alpha = 0.08f * strength),
        1f to Color.White.copy(alpha = 0.2f * strength),
        start = Offset(0f, 0f),
        end = Offset(w, h),
    )
    drawOutline(outline, brush, style = Stroke(width = 0.8.dp.toPx()))
    // Inner glow just inside the rim gives the glass its thickness.
    val glow = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.16f * strength),
        0.35f to Color.Transparent,
        startY = 0f,
        endY = h,
    )
    drawOutline(outline, glow)
}

private fun cornerRadiusOf(outline: Outline, size: Size): Float = when (outline) {
    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
    is Outline.Rectangle -> 0f
    is Outline.Generic -> minOf(size.width, size.height) / 2f
}

private class EffectCache {
    private var key: List<Any>? = null
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun get(size: Size, radius: Float, style: GlassStyle, density: DrawScope): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < 31) return null
        val k = listOf(size.width.toInt(), size.height.toInt(), radius.toInt(), style)
        if (k != key) {
            key = k
            effect = with(density) {
                buildGlassEffect(size, radius, style.blur.toPx(), style.saturation, style.refraction.toPx(), style.rim.toPx(), style.dispersion)
            }
        }
        return effect
    }
}

@RequiresApi(31)
private fun buildGlassEffect(
    size: Size,
    radius: Float,
    blurPx: Float,
    saturation: Float,
    refractionPx: Float,
    rimPx: Float,
    dispersion: Float,
): androidx.compose.ui.graphics.RenderEffect {
    var effect = RenderEffect.createBlurEffect(blurPx.coerceAtLeast(0.5f), blurPx.coerceAtLeast(0.5f), Shader.TileMode.CLAMP)
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    effect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), effect)
    if (Build.VERSION.SDK_INT >= 33 && refractionPx > 0f) {
        effect = RenderEffect.createChainEffect(lensEffect(size, radius, refractionPx, rimPx, dispersion), effect)
    }
    return effect.asComposeRenderEffect()
}

@RequiresApi(33)
private fun lensEffect(size: Size, radius: Float, depth: Float, rim: Float, dispersion: Float): RenderEffect {
    val shader = RuntimeShader(LENS_AGSL)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("radius", radius.coerceAtMost(minOf(size.width, size.height) / 2f))
    shader.setFloatUniform("depth", depth)
    shader.setFloatUniform("rim", rim.coerceAtLeast(1f))
    shader.setFloatUniform("dispersion", dispersion)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/**
 * Lens shader: inside a rounded rectangle, pixels near the rim are pulled in
 * along the surface normal (like light through the curved edge of thick
 * glass), with a touch of chromatic dispersion. The centre stays flat.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float rim;
uniform float dispersion;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 half_ = size * 0.5;
    float2 p = coord - half_;
    float d = sdRoundRect(p, half_, radius);
    if (d > 0.0) {
        return content.eval(coord);
    }
    // 0 in the flat middle, 1 right at the edge.
    float t = clamp(1.0 + d / rim, 0.0, 1.0);
    float curve = t * t * (3.0 - 2.0 * t);
    float e = 1.0;
    float2 n = float2(
        sdRoundRect(p + float2(e, 0.0), half_, radius) - sdRoundRect(p - float2(e, 0.0), half_, radius),
        sdRoundRect(p + float2(0.0, e), half_, radius) - sdRoundRect(p - float2(0.0, e), half_, radius));
    float len = length(n);
    n = len > 0.0001 ? n / len : float2(0.0);
    float bend = depth * curve;
    float2 base = coord - n * bend;
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 g = content.eval(clamp(base, lo, hi));
    if (dispersion <= 0.0) {
        return g;
    }
    float spread = bend * dispersion * 0.12;
    half4 r = content.eval(clamp(base - n * spread, lo, hi));
    half4 b = content.eval(clamp(base + n * spread, lo, hi));
    return half4(r.r, g.g, b.b, g.a);
}
"""

/**
 * Soft drop shadow drawn only *outside* the shape, so translucent glass
 * doesn't look dirty (Android's elevation shadow shows through it).
 */
fun Modifier.glassShadow(shape: Shape, blur: Dp = 16.dp, color: Color = Color.Black.copy(alpha = 0.16f), dy: Dp = 4.dp): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        // A normal blurred shadow, clipped so it never shows inside or right
        // at the edge of the glass (that left a hard "outline" under buttons).
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            setMaskFilter(android.graphics.BlurMaskFilter(blur.toPx().coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL))
        }
        val shapePath = android.graphics.Path().apply {
            when (outline) {
                is Outline.Rounded -> {
                    val r = outline.roundRect
                    addRoundRect(r.left, r.top, r.right, r.bottom, r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, android.graphics.Path.Direction.CW)
                }
                is Outline.Rectangle -> addRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, android.graphics.Path.Direction.CW)
                is Outline.Generic -> addPath(outline.path.asAndroidPath())
            }
        }
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.clipOutPath(shapePath)
            nc.translate(0f, (dy * 0.5f).toPx())
            nc.drawPath(shapePath, paint)
            nc.restore()
        }
    }
