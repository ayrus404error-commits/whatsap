package com.glasschat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.ui.glass.GlassStyle

/** iOS-style semantic colours, in light and dark. */
@Immutable
data class Palette(
    val dark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val cell: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val accent: Color,
    val accentText: Color,
    val link: Color,
    val danger: Color,
    val readTick: Color,
    val wallpaper: Color,
    val wallpaperInk: Color,
    val bubbleOut: Color,
    val bubbleIn: Color,
    val bubbleMeta: Color,
    val bubbleMetaOut: Color,
    val quoteBar: Color,
    val quoteFill: Color,
    val pill: Color,
    val glass: GlassStyle,
    val glassStrong: GlassStyle,
    val glassSelected: Color,
)

val LightPalette = Palette(
    dark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x5C3C3C43),
    fill = Color(0x1F767680),
    accent = Color(0xFF1DAA61),
    accentText = Color(0xFF15803D),
    link = Color(0xFF007AFF),
    danger = Color(0xFFFF3B30),
    readTick = Color(0xFF34B7F1),
    wallpaper = Color(0xFFEFE8DF),
    wallpaperInk = Color(0x14000000),
    bubbleOut = Color(0xFFD9FDD3),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleMeta = Color(0x8C000000),
    bubbleMetaOut = Color(0x8C000000),
    quoteBar = Color(0xFF1DAA61),
    quoteFill = Color(0x0F000000),
    pill = Color(0xFFFFFFFF),
    glass = GlassStyle(
        blur = 9.dp,
        tint = Color.White.copy(alpha = 0.30f),
        saturation = 1.8f,
        refraction = 12.dp,
        rim = 14.dp,
        highlight = 0.85f,
        fallback = Color(0xF0F9F9F9),
    ),
    glassStrong = GlassStyle(
        blur = 18.dp,
        tint = Color.White.copy(alpha = 0.55f),
        saturation = 1.6f,
        refraction = 10.dp,
        rim = 12.dp,
        highlight = 0.8f,
        fallback = Color(0xF7F9F9F9),
    ),
    glassSelected = Color.Black.copy(alpha = 0.07f),
)

val DarkPalette = Palette(
    dark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x3D767680),
    accent = Color(0xFF21C063),
    accentText = Color(0xFF21C063),
    link = Color(0xFF0A84FF),
    danger = Color(0xFFFF453A),
    readTick = Color(0xFF53BDEB),
    wallpaper = Color(0xFF0E0E0F),
    wallpaperInk = Color(0x12FFFFFF),
    bubbleOut = Color(0xFF144D37),
    bubbleIn = Color(0xFF262628),
    bubbleMeta = Color(0x99FFFFFF),
    bubbleMetaOut = Color(0x99FFFFFF),
    quoteBar = Color(0xFF21C063),
    quoteFill = Color(0x1AFFFFFF),
    pill = Color(0xFF1C1C1E),
    glass = GlassStyle(
        blur = 9.dp,
        tint = Color(0xFF1C1C1E).copy(alpha = 0.38f),
        saturation = 1.7f,
        refraction = 12.dp,
        rim = 14.dp,
        highlight = 0.42f,
        fallback = Color(0xF01C1C1E),
    ),
    glassStrong = GlassStyle(
        blur = 18.dp,
        tint = Color(0xFF1C1C1E).copy(alpha = 0.62f),
        saturation = 1.5f,
        refraction = 10.dp,
        rim = 12.dp,
        highlight = 0.4f,
        fallback = Color(0xF71C1C1E),
    ),
    glassSelected = Color.White.copy(alpha = 0.12f),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Type {
    private val baseLargeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, lineHeight = 41.sp)
    private val baseTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    private val baseTitle2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    private val baseBody = TextStyle(fontSize = 17.sp, letterSpacing = (-0.3).sp, lineHeight = 22.sp)
    private val baseBubble = TextStyle(fontSize = 16.5.sp, letterSpacing = (-0.2).sp, lineHeight = 21.5.sp)
    private val baseRowTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    private val baseSubhead = TextStyle(fontSize = 15.sp, letterSpacing = (-0.2).sp, lineHeight = 20.sp)
    private val baseFootnote = TextStyle(fontSize = 13.sp, letterSpacing = (-0.05).sp)
    private val baseCaption = TextStyle(fontSize = 11.sp, letterSpacing = 0.05.sp)
    private val baseTab = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)

    /** Applies your own font (if any); reading it in composition follows changes. */
    private fun f(s: TextStyle): TextStyle {
        // The font's own letter spacing (no tightening), plus your setting.
        val gap = TextGap.letter
        val line = TextGap.line
        var out = s.copy(letterSpacing = gap.sp)
        if (line != 1f) {
            val base = if (s.lineHeight.isSp) s.lineHeight.value else s.fontSize.value * 1.25f
            out = out.copy(lineHeight = (base * line).sp)
        }
        return AppFont.family?.let { out.copy(fontFamily = it) } ?: out
    }

    val largeTitle get() = f(baseLargeTitle)
    val title get() = f(baseTitle)
    val title2 get() = f(baseTitle2)
    val body get() = f(baseBody)
    val bubble get() = f(baseBubble)
    val rowTitle get() = f(baseRowTitle)
    val subhead get() = f(baseSubhead)
    val footnote get() = f(baseFootnote)
    val caption get() = f(baseCaption)
    val tab get() = f(baseTab)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val style by Appearance.style.collectAsState()
    val dark = when (style.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    // A custom background picks light or dark text to stay readable on it.
    val bg = if (style.appBg != 0L) Color(style.appBg or 0xFF000000L) else null
    val bgDark = bg?.let { it.luminance() < 0.45f } ?: dark
    val base0 = if (bgDark) DarkPalette else LightPalette
    val base = if (bg == null) base0 else base0.copy(
        background = bg,
        groupedBackground = bg,
        cell = androidx.compose.ui.graphics.lerp(bg, Color.White, if (bgDark) 0.10f else 0.60f),
    )
    val theme = bubbleThemeFor(style)
    // Bubble colours, app colour and link colour are separate settings.
    var palette = if (style.theme == 0) base else base.copy(bubbleOut = theme.out(dark).first())
    if (style.appAccent != 0L) {
        val a = Color(style.appAccent or 0xFF000000L)
        palette = palette.copy(accent = a, accentText = a, quoteBar = a)
    }
    val scheme = if (palette.dark) {
        darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.cell, onSurface = palette.label)
    } else {
        lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.cell, onSurface = palette.label)
    }
    // App text size (Chat settings → Font): scales all text, not layouts.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scaled = androidx.compose.runtime.remember(density, style.appScale) {
        androidx.compose.ui.unit.Density(density.density, density.fontScale * style.appScale)
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalChatStyle provides style, androidx.compose.ui.platform.LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme) {
            // Your own font (Chat settings → Font) for text without its own style.
            val family = AppFont.family
            if (family != null) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = family),
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

/** Distinct, readable colours for sender names in group chats. */
val SenderColors = listOf(
    Color(0xFFE17076), Color(0xFF7BC862), Color(0xFFE5A64E), Color(0xFF65AADD),
    Color(0xFFA695E7), Color(0xFFEE7AAE), Color(0xFF6EC9CB), Color(0xFFF08D52),
    Color(0xFF5DB7A3), Color(0xFFC77DDB), Color(0xFF8FA9E0), Color(0xFFD99C3C),
)

fun senderColor(jid: String): Color = SenderColors[(jid.hashCode() and 0x7fffffff) % SenderColors.size]
