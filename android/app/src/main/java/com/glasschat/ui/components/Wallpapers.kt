package com.glasschat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * A chat background: the default doodles, a solid colour or gradient (with
 * or without doodles), or one of your photos (with optional dimming).
 */
@Immutable
data class WallpaperSpec(
    val kind: String = KIND_DEFAULT,
    val color: Long = 0,
    val color2: Long = 0,
    val doodles: Boolean = true,
    val image: String = "",
    val dim: Float = 0f,
) {
    fun toJson(): String = JSONObject()
        .put("kind", kind)
        .put("color", color)
        .put("color2", color2)
        .put("doodles", doodles)
        .put("image", image)
        .put("dim", dim.toDouble())
        .toString()

    companion object {
        const val KIND_DEFAULT = "default"
        const val KIND_COLOR = "color"
        const val KIND_GRADIENT = "gradient"
        const val KIND_IMAGE = "image"

        /** Just the theme's background (the chats screen default). */
        const val KIND_PLAIN = "plain"

        fun parse(s: String?): WallpaperSpec? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(s)
                WallpaperSpec(
                    kind = o.optString("kind", KIND_DEFAULT),
                    color = o.optLong("color"),
                    color2 = o.optLong("color2"),
                    doodles = o.optBoolean("doodles", true),
                    image = o.optString("image"),
                    dim = o.optDouble("dim", 0.0).toFloat(),
                )
            }.getOrNull()
        }
    }
}

object Wallpapers {
    /** Pseudo chat id for the chats screen (main list) background. */
    const val HOME = "@home"

    private val _version = MutableStateFlow(0)

    /** Bumps whenever any wallpaper changes, so open chats redraw. */
    val version = _version.asStateFlow()

    /** Solid colours, light enough to read bubbles on (darkened in dark mode). */
    val Solids: List<Long> = listOf(
        0xFFEFE8DF, 0xFFD9E7D3, 0xFFCFE3E8, 0xFFDCD6F0, 0xFFF3D7DF, 0xFFF6E3C6,
        0xFFE6E6E6, 0xFFB7D3C4, 0xFFA9C8DA, 0xFFC5B8E3, 0xFFE8B7C1, 0xFFE9CDA0,
        0xFF6E8C7A, 0xFF5B7590, 0xFF7B6A9B, 0xFF9B5F6E, 0xFF3D4A55, 0xFF1F2A30,
    )

    /** Two-colour gradients (top → bottom). */
    val Gradients: List<Pair<Long, Long>> = listOf(
        0xFFFDE2C8 to 0xFFF6B7C8,
        0xFFCDE8F6 to 0xFFB9C7F2,
        0xFFD7F2D9 to 0xFFA8DCCB,
        0xFFEADCF8 to 0xFFF8D6E8,
        0xFFFFF1C1 to 0xFFF9C9A0,
        0xFF2B3A55 to 0xFF0F1626,
        0xFF243B35 to 0xFF0E1A17,
        0xFF3B2645 to 0xFF140E1C,
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)

    private fun keyFor(chat: String) = when (chat) {
        "" -> "global"
        HOME -> "home"
        else -> "chat:$chat"
    }

    /** The wallpaper for all chats. */
    fun global(ctx: Context): WallpaperSpec = WallpaperSpec.parse(sp(ctx).getString("global", null)) ?: WallpaperSpec()

    /** The chats screen background (plain theme background by default). */
    fun home(ctx: Context): WallpaperSpec =
        WallpaperSpec.parse(sp(ctx).getString("home", null)) ?: WallpaperSpec(kind = WallpaperSpec.KIND_PLAIN, doodles = false)

    /** This chat's own wallpaper, or null when it uses the one for all chats. */
    fun forChat(ctx: Context, chat: String): WallpaperSpec? =
        if (chat.isEmpty() || chat == HOME) null else WallpaperSpec.parse(sp(ctx).getString(keyFor(chat), null))

    fun effective(ctx: Context, chat: String): WallpaperSpec = if (chat == HOME) home(ctx) else forChat(ctx, chat) ?: global(ctx)

    /** Saves a wallpaper ("" = all chats). null removes a chat's own one / resets all chats. */
    fun set(ctx: Context, chat: String, spec: WallpaperSpec?) {
        val e = sp(ctx).edit()
        if (spec == null) e.remove(keyFor(chat)) else e.putString(keyFor(chat), spec.toJson())
        e.commit()
        cleanup(ctx)
        _version.update { it + 1 }
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    /** Copies a picked photo into the app (at most 2400 px), returning its path. */
    suspend fun importImage(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxOf(w, h) / 2400f
                if (scale > 1f) decoder.setTargetSize(maxOf(1, (w / scale).roundToInt()), maxOf(1, (h / scale).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val f = File(dir(ctx), "wp-${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            f.absolutePath
        }.getOrNull()
    }

    /** Deletes imported photos no wallpaper uses any more. */
    fun cleanup(ctx: Context, keep: String = "") {
        val used = sp(ctx).all.values.mapNotNull { WallpaperSpec.parse(it as? String)?.image?.ifBlank { null } }.toSet() + keep
        dir(ctx).listFiles()?.forEach { f ->
            if (f.absolutePath !in used && System.currentTimeMillis() - f.lastModified() > 60_000L) f.delete()
        }
    }
}

/** A colour stored as ARGB, shown darker in dark mode so bubbles stay readable. */
fun wallpaperColor(argb: Long, dark: Boolean): Color {
    val c = Color(argb.toInt())
    if (!dark) return c
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    if (lum < 0.3f) return c
    val k = 0.32f
    return Color(c.red * k, c.green * k, c.blue * k, 1f)
}

/** Doodle ink that shows on the given background. */
fun inkFor(bg: Color): Color {
    val lum = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (lum < 0.45f) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.075f)
}
