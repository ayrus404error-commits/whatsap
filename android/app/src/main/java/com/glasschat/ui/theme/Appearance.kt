package com.glasschat.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How chats look, from Settings → Chat settings. Applied live. */
@Immutable
data class ChatStyle(
    /** Message text size in sp (16.5 is the default). */
    val textSize: Float = DEFAULT_TEXT,
    /** Bubble corner radius in dp. */
    val corner: Float = DEFAULT_CORNER,
    /** Index into [BubbleThemes]. */
    val theme: Int = 0,
    /** 0 = follow the phone, 1 = always light, 2 = always dark. */
    val mode: Int = 0,
    /** Chat list preview: 1 or 2 lines of the last message. */
    val previewLines: Int = 1,
    /** Floating tab bar height (dp) and width (fraction of the screen). */
    val tabHeight: Float = DEFAULT_TAB_HEIGHT,
    val tabWidth: Float = 1f,
    /** Your own theme (theme == CUSTOM_THEME): bubble colour, optional gradient end (0 = none). */
    val customA: Long = 0xFF2E7D5BL,
    val customB: Long = 0L,
    /** File name of a font you added (in the app's files), or "" for the phone's font. */
    val font: String = "",
    /** Text size for the whole app (1 = the phone's size). */
    val appScale: Float = 1f,
    /** Colour for other people's bubbles (0 = the theme's). */
    val customIn: Long = 0L,
    /** The app's own colour (buttons, ticks, switches…); 0 = default green. */
    val appAccent: Long = 0L,
    /** Colour of links in messages; 0 = default blue. */
    val linkColor: Long = 0L,
    /** Extra space between letters in sp (0 = the font's own spacing). */
    val letterGap: Float = 0f,
    /** Line spacing (1 = normal). */
    val lineGap: Float = 1f,
    /** Background of Settings, Updates, Chats list and other pages; 0 = default. */
    val appBg: Long = 0L,
    /** A light vibration on every tap. */
    val tapHaptic: Boolean = true,
    /** A click sound on every tap. */
    val tapSound: Boolean = true,
) {
    companion object {
        const val CUSTOM_THEME = 999
        const val DEFAULT_TEXT = 16.5f
        const val DEFAULT_CORNER = 18f
        const val DEFAULT_TAB_HEIGHT = 64f
    }
}

val LocalChatStyle = compositionLocalOf { ChatStyle() }

/**
 * A colour theme: the colour(s) of your own bubbles in dark and light mode
 * (two colours = a gradient) and the accent used for buttons and badges.
 */
@Immutable
data class BubbleTheme(
    val name: String,
    val dark: List<Color>,
    val light: List<Color>,
    val accentDark: Color,
    val accentLight: Color,
) {
    fun out(isDark: Boolean) = if (isDark) dark else light
    fun accent(isDark: Boolean) = if (isDark) accentDark else accentLight
}

val BubbleThemes = listOf(
    BubbleTheme("Classic", listOf(Color(0xFF144D37)), listOf(Color(0xFFD9FDD3)), Color(0xFF21C063), Color(0xFF1DAA61)),
    BubbleTheme("Ocean", listOf(Color(0xFF2B5FD9), Color(0xFF1B8FD1)), listOf(Color(0xFFD6E9FF), Color(0xFFC4E0FF)), Color(0xFF3B9BFF), Color(0xFF0A6FE0)),
    BubbleTheme("Violet", listOf(Color(0xFF6A4BD8), Color(0xFF4A6CE0)), listOf(Color(0xFFE6DDFF), Color(0xFFD9E2FF)), Color(0xFF9A7BFF), Color(0xFF6E4BD8)),
    BubbleTheme("Sunset", listOf(Color(0xFFD9583A), Color(0xFFB83E78)), listOf(Color(0xFFFFE1D2), Color(0xFFFFD6E4)), Color(0xFFFF7A45), Color(0xFFD9583A)),
    BubbleTheme("Rose", listOf(Color(0xFFB83B6B), Color(0xFF8E3BB8)), listOf(Color(0xFFFFD9E6), Color(0xFFF0DAFF)), Color(0xFFFF5C93), Color(0xFFD6336C)),
    BubbleTheme("Mint", listOf(Color(0xFF0E8C7A), Color(0xFF1A9E5C)), listOf(Color(0xFFCFF5EC), Color(0xFFD5F7DC)), Color(0xFF2BD4B4), Color(0xFF0E8C7A)),
    BubbleTheme("Gold", listOf(Color(0xFFB0802A), Color(0xFF946318)), listOf(Color(0xFFFFF0C9), Color(0xFFFFE3A8)), Color(0xFFE5A823), Color(0xFFA9761C)),
    BubbleTheme("Graphite", listOf(Color(0xFF45454C), Color(0xFF2E2E33)), listOf(Color(0xFFE8E8ED), Color(0xFFDADADF)), Color(0xFFAEAEB2), Color(0xFF636366)),
    BubbleTheme("Sky", listOf(Color(0xFF0A84FF)), listOf(Color(0xFFCCE4FF)), Color(0xFF0A84FF), Color(0xFF0A6FE0)),
    BubbleTheme("Indigo", listOf(Color(0xFF3F3DB8), Color(0xFF5E5CE6)), listOf(Color(0xFFDCDCFF), Color(0xFFE4E0FF)), Color(0xFF7D7AFF), Color(0xFF4B49C9)),
    BubbleTheme("Lavender", listOf(Color(0xFF8E6FD8), Color(0xFFB28BE8)), listOf(Color(0xFFEFE6FF), Color(0xFFF5ECFF)), Color(0xFFB59CFF), Color(0xFF8457D6)),
    BubbleTheme("Crimson", listOf(Color(0xFFB3202D), Color(0xFFD6334A)), listOf(Color(0xFFFFD6DA), Color(0xFFFFDDE3)), Color(0xFFFF4D5E), Color(0xFFC4202F)),
    BubbleTheme("Coral", listOf(Color(0xFFE5654E), Color(0xFFF08A5D)), listOf(Color(0xFFFFE0D8), Color(0xFFFFE6D6)), Color(0xFFFF8066), Color(0xFFD9573F)),
    BubbleTheme("Peach", listOf(Color(0xFFD98A5B), Color(0xFFE8A87C)), listOf(Color(0xFFFFE9DA), Color(0xFFFFEFE2)), Color(0xFFFFA36E), Color(0xFFC77443)),
    BubbleTheme("Lime", listOf(Color(0xFF5E9E1F), Color(0xFF7DB82E)), listOf(Color(0xFFE6F7CF), Color(0xFFEDF9D8)), Color(0xFF8FD43B), Color(0xFF5A9A1C)),
    BubbleTheme("Teal", listOf(Color(0xFF00796B)), listOf(Color(0xFFCCEFEA)), Color(0xFF1DB9A6), Color(0xFF00796B)),
    BubbleTheme("Aqua", listOf(Color(0xFF00A3C4), Color(0xFF2BD9C7)), listOf(Color(0xFFCCF2F8), Color(0xFFD2F8F2)), Color(0xFF2BD9C7), Color(0xFF0090AD)),
    BubbleTheme("Chocolate", listOf(Color(0xFF6B4226), Color(0xFF8B5A35)), listOf(Color(0xFFF0E0D2), Color(0xFFF4E6DA)), Color(0xFFC08350), Color(0xFF7A4B2A)),
    BubbleTheme("Midnight", listOf(Color(0xFF1B2A4A), Color(0xFF26355E)), listOf(Color(0xFFD9E0EF), Color(0xFFDDE3F2)), Color(0xFF6E8BCF), Color(0xFF2C3E6B)),
    BubbleTheme("Neon", listOf(Color(0xFFFF2D95), Color(0xFF7A3CFF)), listOf(Color(0xFFFFD6EC), Color(0xFFE6D9FF)), Color(0xFFFF4FAE), Color(0xFFD61F7C)),
)

/** The theme in use, including your own custom one. */
fun bubbleThemeFor(style: ChatStyle): BubbleTheme {
    if (style.theme != ChatStyle.CUSTOM_THEME) return BubbleThemes.getOrElse(style.theme) { BubbleThemes[0] }
    val a = Color(style.customA or 0xFF000000L)
    val b = if (style.customB != 0L) Color(style.customB or 0xFF000000L) else null
    fun light(c: Color) = Color(
        red = c.red + (1f - c.red) * 0.78f,
        green = c.green + (1f - c.green) * 0.78f,
        blue = c.blue + (1f - c.blue) * 0.78f,
    )
    val dark = listOfNotNull(a, b)
    return BubbleTheme("Custom", dark, dark.map(::light), accent(a, 0.25f), a)
}

private fun accent(c: Color, lift: Float) = Color(
    red = c.red + (1f - c.red) * lift,
    green = c.green + (1f - c.green) * lift,
    blue = c.blue + (1f - c.blue) * lift,
)

object Appearance {
    private val _style = MutableStateFlow(ChatStyle())
    val style = _style.asStateFlow()
    private var app: Context? = null

    private fun sp(ctx: Context) = ctx.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        val p = sp(ctx)
        _style.value = ChatStyle(
            textSize = p.getFloat("text", ChatStyle.DEFAULT_TEXT),
            corner = p.getFloat("corner", ChatStyle.DEFAULT_CORNER),
            theme = p.getInt("theme", 0).let { if (it == ChatStyle.CUSTOM_THEME) it else it.coerceIn(0, BubbleThemes.lastIndex) },
            mode = p.getInt("mode", 0),
            previewLines = p.getInt("lines", 1),
            tabHeight = p.getFloat("tab_h", ChatStyle.DEFAULT_TAB_HEIGHT),
            tabWidth = p.getFloat("tab_w", 1f),
            customA = p.getLong("custom_a", 0xFF2E7D5BL),
            customB = p.getLong("custom_b", 0L),
            font = p.getString("font", "") ?: "",
            appScale = p.getFloat("app_scale", 1f).coerceIn(0.8f, 1.4f),
            customIn = p.getLong("custom_in", 0L),
            appAccent = p.getLong("app_accent", 0L),
            linkColor = p.getLong("link_color", 0L),
            letterGap = p.getFloat("letter_gap", 0f).coerceIn(-0.5f, 2f),
            lineGap = p.getFloat("line_gap", 1f).coerceIn(0.8f, 1.8f),
            appBg = p.getLong("app_bg", 0L),
            tapHaptic = p.getBoolean("tap_haptic", true),
            tapSound = p.getBoolean("tap_sound", true),
        )
        AppFont.load(ctx, _style.value.font)
        TextGap.set(_style.value)
    }

    fun update(change: (ChatStyle) -> ChatStyle) {
        val s = change(_style.value)
        _style.value = s
        TextGap.set(s)
        val ctx = app ?: return
        sp(ctx).edit()
            .putFloat("text", s.textSize)
            .putFloat("corner", s.corner)
            .putInt("theme", s.theme)
            .putInt("mode", s.mode)
            .putInt("lines", s.previewLines)
            .putFloat("tab_h", s.tabHeight)
            .putFloat("tab_w", s.tabWidth)
            .putLong("custom_a", s.customA)
            .putLong("custom_b", s.customB)
            .putString("font", s.font)
            .putFloat("app_scale", s.appScale)
            .putLong("custom_in", s.customIn)
            .putLong("app_accent", s.appAccent)
            .putLong("link_color", s.linkColor)
            .putFloat("letter_gap", s.letterGap)
            .putFloat("line_gap", s.lineGap)
            .putLong("app_bg", s.appBg)
            .putBoolean("tap_haptic", s.tapHaptic)
            .putBoolean("tap_sound", s.tapSound)
            .apply()
        AppFont.load(ctx, s.font)
        TextGap.set(s)
    }

    fun reset() = update { ChatStyle(font = it.font) }
}

/**
 * A font you add from your phone (TTF / OTF), used for all text in the app.
 * Letters or emoji it doesn't have fall back to the phone's own font.
 */
/** Letter and line spacing for all app text (Chat settings → Font). */
object TextGap {
    var letter by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    var line by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    fun set(s: ChatStyle) {
        letter = s.letterGap
        line = s.lineGap
    }
}

object AppFont {
    /** Compose font family for the chosen font, or null for the phone's. */
    var family by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.font.FontFamily?>(null)
        private set
    /** The same font for drawing on pictures (stickers, photo text). */
    var typeface: android.graphics.Typeface? = null
        private set

    fun dir(ctx: Context) = java.io.File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun load(ctx: Context, name: String) {
        if (name.isEmpty()) {
            family = null
            typeface = null
            return
        }
        val f = java.io.File(dir(ctx), name)
        val tf = runCatching { android.graphics.Typeface.createFromFile(f) }.getOrNull()
        if (tf == null || !f.exists()) {
            family = null
            typeface = null
            return
        }
        typeface = tf
        family = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(tf))
    }

    /** Copies a picked font into the app. Returns its file name, or null if it isn't a usable font. */
    fun import(ctx: Context, uri: android.net.Uri, displayName: String): String? = runCatching {
        val ext = displayName.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf", "ttc") } ?: "ttf"
        val name = "custom_${System.currentTimeMillis()}.$ext"
        val out = java.io.File(dir(ctx), name)
        ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        val ok = runCatching { android.graphics.Typeface.createFromFile(out) }.isSuccess && out.length() > 1000
        if (!ok) {
            out.delete()
            null
        } else {
            // Keep only the newest font file.
            dir(ctx).listFiles()?.filter { it.name != name }?.forEach { it.delete() }
            name
        }
    }.getOrNull()
}
