package com.glasschat.media

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.glasschat.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// -----------------------------------------------------------------------------
// Download state, shared by bubbles, viewers and the status screen.

object MediaState {
    private val _paths = MutableStateFlow<Map<String, String>>(emptyMap())
    val paths = _paths.asStateFlow()
    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    val loading = _loading.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()

    fun key(chat: String, id: String) = "$chat|$id"

    suspend fun fetch(chat: String, id: String): Result<String> {
        val k = key(chat, id)
        _paths.value[k]?.let { if (File(it).exists()) return Result.success(it) }
        _loading.update { it + k }
        _errors.update { it - k }
        val r = Engine.downloadMedia(chat, id)
        r.onSuccess { p -> _paths.update { it + (k to p) } }
            .onFailure { e -> _errors.update { it + (k to (e.message ?: "Download failed")) } }
        _loading.update { it - k }
        return r
    }

    fun forget() {
        _paths.value = emptyMap()
        _errors.value = emptyMap()
    }
}

@Stable
class MediaHandle(val path: String?, val loading: Boolean, val error: String?, val download: () -> Unit)

/**
 * The file for a message's attachment. With [auto] it's downloaded as soon
 * as this is shown (photos, stickers); otherwise call [MediaHandle.download].
 */
@Composable
fun rememberMedia(chat: String, id: String, local: String, auto: Boolean): MediaHandle {
    val k = MediaState.key(chat, id)
    val paths by MediaState.paths.collectAsState()
    val loading by MediaState.loading.collectAsState()
    val errors by MediaState.errors.collectAsState()
    val scope = rememberCoroutineScope()
    val path = paths[k] ?: local.takeIf { it.isNotEmpty() }
    val failed = errors[k]
    LaunchedEffect(k, auto) {
        if (auto && path == null && failed == null) MediaState.fetch(chat, id)
    }
    val download = remember(k) {
        val f: () -> Unit = { scope.launch { MediaState.fetch(chat, id) } }
        f
    }
    return MediaHandle(path, k in loading, failed, download)
}

// -----------------------------------------------------------------------------
// Images

object Images {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            runCatching { value.asAndroidBitmap().allocationByteCount }.getOrDefault(1_000_000)
    }

    fun cached(path: String, maxPx: Int): ImageBitmap? = cache.get("$path@$maxPx")

    /** Decodes an image file (JPEG/PNG/WebP/HEIC), scaled so its longer side is at most [maxPx]. */
    suspend fun load(path: String, maxPx: Int): ImageBitmap? {
        val key = "$path@$maxPx"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = maxOf(w, h).toFloat() / maxPx
                    if (scale > 1f) decoder.setTargetSize(maxOf(1, (w / scale).toInt()), maxOf(1, (h / scale).toInt()))
                }.asImageBitmap()
            }.getOrNull()
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}

/** Decodes a picked image (content:// or file://) for previews. */
suspend fun loadUriImage(ctx: Context, uri: Uri, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val src = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path ?: "")) else ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxOf(w, h).toFloat() / maxPx
            if (scale > 1f) decoder.setTargetSize(maxOf(1, (w / scale).toInt()), maxOf(1, (h / scale).toInt()))
        }.asImageBitmap()
    }.getOrNull()
}

@Composable
fun rememberImage(path: String?, maxPx: Int): ImageBitmap? {
    var bmp by remember(path, maxPx) { mutableStateOf(path?.let { Images.cached(it, maxPx) }) }
    LaunchedEffect(path, maxPx) {
        if (path != null && bmp == null) bmp = Images.load(path, maxPx)
    }
    return bmp
}

/** Plays an animated WebP/GIF (animated stickers). */
@Composable
fun AnimatedImage(path: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            if (view.tag != path) {
                view.tag = path
                val d = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) }.getOrNull()
                view.setImageDrawable(d)
                if (d is AnimatedImageDrawable) {
                    d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    d.start()
                }
            }
        },
        modifier = modifier,
    )
}

// -----------------------------------------------------------------------------
// Opening, sharing and saving files

object MediaFiles {
    fun authority(ctx: Context) = ctx.packageName + ".files"

    fun uriFor(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, authority(ctx), file)

    fun mimeOf(path: String, fallback: String = ""): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: fallback.ifBlank { "application/octet-stream" }
    }

    /** Opens a downloaded file in another app (PDF viewer, music player…). */
    fun open(ctx: Context, path: String, mime: String = "") {
        val type = mime.substringBefore(';').trim().ifBlank { mimeOf(path) }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, File(path)), type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, path: String, mime: String = "") {
        val type = mime.substringBefore(';').trim().ifBlank { mimeOf(path) }
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, File(path)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Copies a file (document, audio…) into the phone's Downloads/Whatsap folder. */
    suspend fun saveToDownloads(ctx: Context, path: String, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(path)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name.ifBlank { f.name })
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(path).ifBlank { "application/octet-stream" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Whatsap")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** Copies a photo or video into the phone's gallery (Pictures or Movies / Whatsap). */
    suspend fun saveToGallery(ctx: Context, path: String, video: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(path)
            val ext = f.extension.ifBlank { if (video) "mp4" else "jpg" }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "GlassChat_${System.currentTimeMillis()}.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(path, if (video) "video/mp4" else "image/jpeg"))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Whatsap",
                )
            }
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(collection, values) ?: return@runCatching false
            val out = ctx.contentResolver.openOutputStream(uri) ?: return@runCatching false
            out.use { o -> f.inputStream().use { it.copyTo(o) } }
            true
        }.getOrDefault(false)
    }

    /** Opens a place in the maps app. */
    fun openMap(ctx: Context, lat: Double, lng: Double, label: String) {
        val q = Uri.encode(if (label.isBlank()) "$lat,$lng" else "$lat,$lng($label)")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }
}

// -----------------------------------------------------------------------------
// Calls can't run on a linked device; hand them to WhatsApp or the dialer.

object CallHandoff {
    private val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

    private fun installed(ctx: Context): String? =
        packages.firstOrNull { pkg -> runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess }

    fun hasWhatsApp(ctx: Context) = installed(ctx) != null

    private fun digits(jid: String): String? =
        if (jid.endsWith("@s.whatsapp.net")) jid.substringBefore('@').substringBefore(':') else null

    /** Opens this chat in WhatsApp, where the call buttons work. */
    fun openInWhatsApp(ctx: Context, jid: String): Boolean {
        val pkg = installed(ctx)
        val number = digits(jid)
        val intent = if (number != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply { if (pkg != null) setPackage(pkg) }
        } else {
            pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) } ?: return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    /** Opens the phone dialer with this person's number (a normal phone call). */
    fun dial(ctx: Context, jid: String): Boolean {
        val number = digits(jid) ?: return false
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    fun canDial(jid: String) = digits(jid) != null
}
