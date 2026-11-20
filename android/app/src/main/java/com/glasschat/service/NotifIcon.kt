package com.glasschat.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.drawable.IconCompat
import com.glasschat.R
import com.glasschat.ui.screens.AppIcons
import kotlin.math.abs

/**
 * The small icon in the status bar and on notifications. With the Custom
 * app icon chosen it is made from your picture. Android draws small icons
 * in one colour only, so the picture becomes a one-colour outline: the
 * parts that stand out from its background are kept.
 */
object NotifIcon {
    @Volatile private var cached: Pair<String, IconCompat>? = null
    @Volatile private var cachedLarge: Bitmap? = null

    private fun isCustom(ctx: Context) = runCatching { AppIcons.current(ctx) == "Custom" }.getOrDefault(false)

    fun small(ctx: Context): IconCompat {
        val key = if (isCustom(ctx)) "Custom" else "Default"
        cached?.let { if (it.first == key) return it.second }
        val icon = if (key == "Custom") {
            runCatching { IconCompat.createWithBitmap(mask(picture(ctx, 96))) }
                .getOrElse { IconCompat.createWithResource(ctx, R.drawable.ic_stat_glass) }
        } else {
            IconCompat.createWithResource(ctx, R.drawable.ic_stat_glass)
        }
        cached = key to icon
        return icon
    }

    /** Your picture in full colour, for the "Connected" notification; null otherwise. */
    fun large(ctx: Context): Bitmap? {
        if (!isCustom(ctx)) return null
        cachedLarge?.let { return it }
        return runCatching { picture(ctx, 192) }.getOrNull()?.also { cachedLarge = it }
    }

    /** Forget the icons after switching, so the next notification uses the new one. */
    fun reset() {
        cached = null
        cachedLarge = null
    }

    private fun picture(ctx: Context, size: Int): Bitmap {
        val src = BitmapFactory.decodeResource(ctx.resources, R.drawable.chat_icon_custom)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val s = minOf(src.width, src.height)
        Canvas(out).drawBitmap(
            src,
            Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** White where the picture differs from its background colour, clear elsewhere. */
    private fun mask(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        fun lum(c: Int): Float {
            val a = (c ushr 24) / 255f
            val l = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
            return l * a
        }
        // Background = the average of the picture's edge.
        var sum = 0f
        var n = 0
        for (x in 0 until w) { sum += lum(px[x]) + lum(px[(h - 1) * w + x]); n += 2 }
        for (y in 0 until h) { sum += lum(px[y * w]) + lum(px[y * w + w - 1]); n += 2 }
        val bg = sum / n
        val out = IntArray(w * h)
        for (i in px.indices) {
            val d = abs(lum(px[i]) - bg)
            val a = ((d - 0.12f) / 0.25f).coerceIn(0f, 1f)
            out[i] = ((a * 255).toInt() shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
