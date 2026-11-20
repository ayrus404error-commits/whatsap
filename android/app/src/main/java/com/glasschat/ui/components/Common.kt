package com.glasschat.ui.components

import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.glasschat.media.rememberImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.ui.theme.LocalPalette
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// -----------------------------------------------------------------------------
// Avatars

object AvatarCache {
    private val mem = LruCache<String, ImageBitmap>(160)
    private var version = -1

    fun get(jid: String, v: Int): ImageBitmap? {
        if (v != version) {
            mem.evictAll()
            version = v
        }
        return mem.get(jid)
    }

    fun put(jid: String, bmp: ImageBitmap) {
        mem.put(jid, bmp)
    }
}

@Composable
fun Avatar(jid: String, isGroup: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val version by Engine.avatarVersion.collectAsState()
    var bmp by remember(jid, version) { mutableStateOf(AvatarCache.get(jid, version)) }
    LaunchedEffect(jid, version) {
        if (bmp == null && jid.isNotEmpty()) {
            val loaded = withContext(Engine.io) {
                Engine.avatarPath(jid)?.let { path ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
                }
            }
            if (loaded != null) {
                AvatarCache.put(jid, loaded)
                bmp = loaded
            }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (p.dark) Color(0xFF3A3A3C) else Color(0xFFD1D6DB)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(
                if (isGroup) Icons.Outlined.Group else Icons.Outlined.Person,
                null,
                tint = Color.White.copy(alpha = if (p.dark) 0.55f else 0.95f),
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Delivery ticks

@Composable
fun Ticks(status: Int, modifier: Modifier = Modifier, color: Color = LocalPalette.current.bubbleMeta, size: Dp = 16.dp) {
    val p = LocalPalette.current
    when (status) {
        Message.PENDING -> Icon(Icons.Outlined.Schedule, "Sending", tint = color, modifier = modifier.size(size * 0.8f))
        Message.FAILED -> Icon(Icons.Outlined.Error, "Not sent", tint = p.danger, modifier = modifier.size(size * 0.85f))
        else -> {
            val double = status >= Message.DELIVERED
            val tint = if (status >= Message.READ) p.readTick else color
            Canvas(modifier.size(width = size * 1.1f, height = size)) { drawChecks(double, tint) }
        }
    }
}

private fun DrawScope.drawChecks(double: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = h * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun check(dx: Float, short: Boolean) {
        val path = Path().apply {
            if (!short) moveTo(w * 0.08f + dx, h * 0.55f)
            if (!short) lineTo(w * 0.30f + dx, h * 0.76f) else moveTo(w * 0.36f + dx, h * 0.70f)
            lineTo(w * 0.70f + dx, h * 0.27f)
        }
        drawPath(path, color, style = stroke)
    }
    if (double) {
        check(-w * 0.12f, false)
        check(w * 0.16f, true)
    } else {
        check(0f, false)
    }
}

// -----------------------------------------------------------------------------
// Time formatting

object Fmt {
    private fun cal(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun daysAgo(ts: Long): Int {
        val now = Calendar.getInstance()
        val c = cal(ts)
        if (sameDay(now, c)) return 0
        val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (sameDay(y, c)) return 1
        val startToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (((startToday - ts) / 86_400_000L) + 1).toInt()
    }

    fun time(ctx: android.content.Context, ts: Long): String {
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))
    }

    /** Chat list: time today, "Yesterday", weekday this week, else a date. */
    fun listStamp(ctx: android.content.Context, ts: Long): String = when (daysAgo(ts)) {
        0 -> time(ctx, ts)
        1 -> "Yesterday"
        in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))
    }

    /** Date separators inside a chat. */
    fun dayHeader(ts: Long): String = when (daysAgo(ts)) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))
        else -> {
            val sameYear = cal(ts).get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
            SimpleDateFormat(if (sameYear) "EEE, d MMM" else "d MMM yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    fun sameDay(a: Long, b: Long) = sameDay(cal(a), cal(b))

    fun lastSeen(ctx: android.content.Context, ts: Long): String {
        if (ts <= 0) return "tap here for contact info"
        return when (daysAgo(ts)) {
            0 -> "last seen today at ${time(ctx, ts)}"
            1 -> "last seen yesterday at ${time(ctx, ts)}"
            else -> "last seen ${SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))}"
        }
    }

    fun duration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    fun size(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%d kB".format(bytes / 1024)
        else -> "$bytes B"
    }
}

@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val ctx = LocalContext.current
    return remember(ctx) { { ts: Long -> Fmt.time(ctx, ts) } }
}

// -----------------------------------------------------------------------------
// Chat wallpaper: default doodles (an original pattern, not WhatsApp's
// artwork), a colour or gradient, or your own photo.

/**
 * [forList]: drawn behind a plain list (the chats screen) rather than chat
 * bubbles, so in light mode a photo gets a white veil instead of a dark one
 * to keep the black text readable.
 */
@Composable
fun Wallpaper(modifier: Modifier = Modifier, chat: String = "", spec: WallpaperSpec? = null, forList: Boolean = false) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val version by Wallpapers.version.collectAsState()
    val s = spec ?: remember(chat, version) { Wallpapers.effective(ctx, chat) }
    Box(modifier) {
        when (s.kind) {
            WallpaperSpec.KIND_COLOR -> {
                val bg = wallpaperColor(s.color, p.dark)
                Box(Modifier.matchParentSize().background(bg)) {
                    if (s.doodles) DoodlePattern(Modifier.matchParentSize(), inkFor(bg))
                }
            }
            WallpaperSpec.KIND_GRADIENT -> {
                val top = wallpaperColor(s.color, p.dark)
                val bottom = wallpaperColor(s.color2, p.dark)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
                    if (s.doodles) DoodlePattern(Modifier.matchParentSize(), inkFor(top))
                }
            }
            WallpaperSpec.KIND_IMAGE -> {
                val img = rememberImage(s.image.ifEmpty { null }, 2400)
                Box(Modifier.matchParentSize().background(p.wallpaper)) {
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
            WallpaperSpec.KIND_PLAIN -> Box(Modifier.matchParentSize().background(p.background)) {
                if (s.doodles) DoodlePattern(Modifier.matchParentSize(), p.wallpaperInk)
            }
            else -> Box(Modifier.matchParentSize().background(p.wallpaper)) {
                if (s.doodles) DoodlePattern(Modifier.matchParentSize(), p.wallpaperInk)
            }
        }
        // Dimming (any kind): darker, or lighter for a list in light mode so
        // black text stays readable. Photos get a little extra in dark mode.
        val extra = if (s.kind == WallpaperSpec.KIND_IMAGE && p.dark) 0.15f else 0f
        val dim = (s.dim + extra).coerceIn(0f, 0.85f)
        val veil = if (forList && !p.dark) Color.White else Color.Black
        if (dim > 0f) Box(Modifier.matchParentSize().background(veil.copy(alpha = dim)))
    }
}

@Composable
fun DoodlePattern(modifier: Modifier, ink: Color) {
    Canvas(modifier) {
        val cell = 72.dp.toPx()
        val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val seed = (r * 7919 + c * 104729) and 0xffff
                val cx = c * cell + (if (r % 2 == 0) 0f else cell / 2) + ((seed % 13) - 6) * 1.5f
                val cy = r * cell + ((seed / 13 % 11) - 5) * 1.5f
                val s = cell * 0.16f
                when (seed % 6) {
                    0 -> drawCircle(ink, s * 0.9f, Offset(cx, cy), style = stroke)
                    1 -> { // little speech bubble
                        val path = Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - s, cy - s * 0.7f, cx + s, cy + s * 0.6f, s * 0.5f, s * 0.5f))
                            moveTo(cx - s * 0.4f, cy + s * 0.6f); lineTo(cx - s * 0.7f, cy + s * 1.1f); lineTo(cx - s * 0.05f, cy + s * 0.6f)
                        }
                        drawPath(path, ink, style = stroke)
                    }
                    2 -> { // sparkle
                        drawLine(ink, Offset(cx - s, cy), Offset(cx + s, cy), stroke.width, StrokeCap.Round)
                        drawLine(ink, Offset(cx, cy - s), Offset(cx, cy + s), stroke.width, StrokeCap.Round)
                    }
                    3 -> { // leaf
                        val path = Path().apply {
                            moveTo(cx - s, cy + s * 0.6f)
                            quadraticTo(cx - s * 0.6f, cy - s, cx + s, cy - s * 0.6f)
                            quadraticTo(cx + s * 0.6f, cy + s, cx - s, cy + s * 0.6f)
                        }
                        drawPath(path, ink, style = stroke)
                    }
                    4 -> { // heart
                        val path = Path().apply {
                            moveTo(cx, cy + s * 0.8f)
                            cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 1.1f, cx, cy - s * 0.35f)
                            cubicTo(cx + s * 0.6f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.8f)
                        }
                        drawPath(path, ink, style = stroke)
                    }
                    else -> drawCircle(ink, s * 0.18f, Offset(cx, cy))
                }
            }
        }
    }
}

/** Opens the phone's "new contact" screen with this number (and name) filled in. */
fun addToContacts(ctx: android.content.Context, phone: String, name: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }.let { if (it.startsWith("+")) it else "+$it" }
    val intent = android.content.Intent(android.provider.ContactsContract.Intents.Insert.ACTION)
        .setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, digits)
        .apply { if (name.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name) }
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    ContactBook.pending = digits
    runCatching { ctx.startActivity(intent) }
}
