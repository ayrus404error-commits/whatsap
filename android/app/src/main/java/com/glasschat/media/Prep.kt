package com.glasschat.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.glasschat.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** A photo or video picked to send, before it's been prepared. */
@Immutable
data class PendingMedia(
    val uri: Uri,
    val video: Boolean,
    /** Send as "view once" (the person can open it one time). */
    val viewOnce: Boolean = false,
    /** Video cut, in ms (0..0 = whole video). */
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
)

/**
 * Turns picked files into the send specs the engine expects: photos are
 * shrunk like WhatsApp does, videos get their size/length/preview, and
 * documents keep their name.
 */
object MediaPrep {
    private fun newFile(ctx: Context, prefix: String, ext: String): File {
        val dir = File(ctx.cacheDir, "outgoing").apply { mkdirs() }
        return File(dir, "$prefix-${System.nanoTime()}.$ext")
    }

    fun cameraFile(ctx: Context): File = newFile(ctx, "camera", "jpg")

    fun isVideo(ctx: Context, uri: Uri): Boolean =
        ctx.contentResolver.getType(uri)?.startsWith("video/") == true

    fun displayName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "file").name
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "file"
    }

    private fun source(ctx: Context, uri: Uri): ImageDecoder.Source =
        if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path ?: "")) else ImageDecoder.createSource(ctx.contentResolver, uri)

    private fun thumbOf(ctx: Context, bmp: Bitmap): File? = runCatching {
        val s = 96f / maxOf(bmp.width, bmp.height)
        val small = Bitmap.createScaledBitmap(
            bmp,
            maxOf(1, (bmp.width * s).roundToInt()),
            maxOf(1, (bmp.height * s).roundToInt()),
            true,
        )
        val f = newFile(ctx, "thumb", "jpg")
        FileOutputStream(f).use { small.compress(Bitmap.CompressFormat.JPEG, 60, it) }
        f
    }.getOrNull()

    private fun copy(ctx: Context, uri: Uri, out: File) {
        if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream().use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
            return
        }
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("can't read the file")
        input.use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
    }

    /**
     * A photo as JPEG: Standard quality is at most 1600 px on its longer side,
     * HD quality (Settings → Data and storage) keeps up to 4096 px.
     */
    fun image(ctx: Context, uri: Uri, caption: String): JSONObject {
        val hd = com.glasschat.service.Prefs.hdUploads(ctx)
        val maxSide = if (hd) 4096f else 1600f
        val bmp = ImageDecoder.decodeBitmap(source(ctx, uri)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxOf(w, h) / maxSide
            if (scale > 1f) decoder.setTargetSize(maxOf(1, (w / scale).roundToInt()), maxOf(1, (h / scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = newFile(ctx, "img", "jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, if (hd) 92 else 80, it) }
        val thumb = thumbOf(ctx, bmp)
        // Camera shots live in our cache; the shrunk copy replaces them.
        if (uri.scheme == "file") uri.path?.let { p -> if (p.startsWith(ctx.cacheDir.absolutePath)) File(p).delete() }
        return JSONObject()
            .put("kind", "image")
            .put("path", out.absolutePath)
            .put("thumb", thumb?.absolutePath ?: "")
            .put("mime", "image/jpeg")
            .put("w", bmp.width)
            .put("h", bmp.height)
            .put("caption", caption)
    }

    /** A video, sent as-is, with its dimensions, length and a preview frame. */
    fun video(ctx: Context, uri: Uri, caption: String, trimStartMs: Long = 0, trimEndMs: Long = 0): JSONObject {
        val type = if (uri.scheme == "file") "video/mp4" else ctx.contentResolver.getType(uri) ?: "video/mp4"
        var out = newFile(ctx, "vid", if (type.contains("3gpp")) "3gp" else "mp4")
        copy(ctx, uri, out)
        // Index first, so WhatsApp can play it while it downloads.
        run {
            val fast = newFile(ctx, "vid", "mp4")
            if (Faststart.apply(out, fast)) {
                out.delete()
                out = fast
            } else {
                fast.delete()
            }
        }
        if (trimEndMs > trimStartMs) {
            val cut = newFile(ctx, "vid", "mp4")
            if (VideoTrim.trim(out, cut, trimStartMs, trimEndMs)) {
                out.delete()
                out = cut
                val fast = newFile(ctx, "vid", "mp4")
                if (Faststart.apply(out, fast)) {
                    out.delete()
                    out = fast
                } else {
                    fast.delete()
                }
            } else {
                cut.delete()
            }
        }
        var w = 0
        var h = 0
        var secs = 0
        var thumb: File? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w
                w = h
                h = t
            }
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            secs = ((ms + 500) / 1000).toInt()
            r.frameAtTime?.let { thumb = thumbOf(ctx, it) }
        } catch (e: Throwable) {
            // Unreadable metadata: send without size/preview.
        } finally {
            runCatching { r.release() }
        }
        return JSONObject()
            .put("kind", "video")
            .put("path", out.absolutePath)
            .put("thumb", thumb?.absolutePath ?: "")
            .put("mime", if (type.startsWith("video/")) type else "video/mp4")
            .put("w", w)
            .put("h", h)
            .put("seconds", secs)
            .put("caption", caption)
    }

    /** Any file, sent as a document with its original name. */
    fun document(ctx: Context, uri: Uri, caption: String): JSONObject {
        val name = displayName(ctx, uri)
        val type = ctx.contentResolver.getType(uri) ?: MediaFiles.mimeOf(name)
        val ext = name.substringAfterLast('.', "").ifBlank { "bin" }
        val out = newFile(ctx, "doc", ext)
        copy(ctx, uri, out)
        return JSONObject()
            .put("kind", "document")
            .put("path", out.absolutePath)
            .put("fileName", name)
            .put("mime", type)
            .put("caption", caption)
    }

    /** A music or audio file, sent as an audio message (not a voice note). */
    fun audio(ctx: Context, uri: Uri): JSONObject {
        val name = displayName(ctx, uri)
        val type = ctx.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: MediaFiles.mimeOf(name)
        val ext = name.substringAfterLast('.', "").ifBlank { "m4a" }
        val out = newFile(ctx, "aud", ext)
        copy(ctx, uri, out)
        var secs = 0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            secs = ((ms + 500) / 1000).toInt()
        } catch (e: Throwable) {
            // Length unknown: still send.
        } finally {
            runCatching { r.release() }
        }
        return JSONObject()
            .put("kind", "audio")
            .put("path", out.absolutePath)
            .put("fileName", name)
            .put("mime", if (type.startsWith("audio/")) type else "audio/mpeg")
            .put("seconds", secs)
    }

    suspend fun sendAudio(ctx: Context, chat: String, uri: Uri, quotedId: String?): Boolean {
        val spec = withContext(Dispatchers.IO) { runCatching { audio(ctx, uri) }.getOrNull() } ?: return false
        if (quotedId != null) spec.put("quotedId", quotedId)
        Engine.sendMedia(chat, spec)?.join()
        return true
    }

    fun voice(rec: Recording): JSONObject = JSONObject()
        .put("kind", "voice")
        .put("path", rec.file.absolutePath)
        .put("mime", "audio/ogg; codecs=opus")
        .put("seconds", rec.seconds)
        .put("waveform", rec.waveform)

    /**
     * Prepares and sends picked photos/videos in order. The caption goes on
     * the first one. Returns false if any couldn't be read.
     */
    /**
     * Sends picked photos/videos. [grouped]: two or more go as one album
     * (WhatsApp's grouping). [asFiles]: each goes as a document in full
     * original quality, like WhatsApp's "Send as files".
     */
    suspend fun sendPicked(
        ctx: Context,
        chat: String,
        items: List<PendingMedia>,
        caption: String,
        quotedId: String?,
        grouped: Boolean = true,
        asFiles: Boolean = false,
    ): Boolean {
        var ok = true
        val album = if (!asFiles && grouped && items.size > 1 && items.none { it.viewOnce }) {
            Engine.sendAlbum(chat, items.count { !it.video }, items.count { it.video })
        } else null
        items.forEachIndexed { i, item ->
            val cap = if (i == 0) caption.trim() else ""
            val spec = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        asFiles -> document(ctx, item.uri, cap)
                        item.video -> video(ctx, item.uri, cap, item.trimStartMs, item.trimEndMs)
                        else -> image(ctx, item.uri, cap)
                    }
                }.getOrNull()
            }
            if (spec != null && album != null) {
                spec.put("albumParent", album)
                spec.put("albumIndex", i)
            }
            if (spec != null && item.viewOnce && !asFiles) spec.put("viewOnce", true)
            if (spec == null) {
                ok = false
            } else {
                if (i == 0 && quotedId != null) spec.put("quotedId", quotedId)
                Engine.sendMedia(chat, spec)?.join()
            }
        }
        return ok
    }

    suspend fun sendDocument(ctx: Context, chat: String, uri: Uri, quotedId: String?): Boolean {
        val spec = withContext(Dispatchers.IO) { runCatching { document(ctx, uri, "") }.getOrNull() } ?: return false
        if (quotedId != null) spec.put("quotedId", quotedId)
        Engine.sendMedia(chat, spec)?.join()
        return true
    }
}

/** Launchers for the photo picker, camera and document picker. */
@Stable
class MediaPickers internal constructor(
    val pickGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val pickDocument: () -> Unit,
)

@Composable
fun rememberMediaPickers(onMedia: (List<PendingMedia>) -> Unit, onDocument: (Uri) -> Unit = {}): MediaPickers {
    val ctx = LocalContext.current
    val currentOnMedia by rememberUpdatedState(onMedia)
    val currentOnDocument by rememberUpdatedState(onDocument)
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) currentOnMedia(uris.map { PendingMedia(it, MediaPrep.isVideo(ctx, it)) })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = cameraPath
        if (ok && p != null && File(p).length() > 0) currentOnMedia(listOf(PendingMedia(Uri.fromFile(File(p)), false)))
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) currentOnDocument(uri)
    }
    // The app declares the camera permission (for its own camera screen), so
    // Android also requires it before handing off to the system camera.
    fun launchCamera() {
        val f = MediaPrep.cameraFile(ctx)
        cameraPath = f.absolutePath
        runCatching { camera.launch(MediaFiles.uriFor(ctx, f)) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera()
    }
    return remember(gallery, camera, document) {
        MediaPickers(
            pickGallery = {
                runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            },
            takePhoto = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    launchCamera()
                } else {
                    runCatching { cameraPermission.launch(android.Manifest.permission.CAMERA) }
                }
            },
            pickDocument = { runCatching { document.launch(arrayOf("*/*")) } },
        )
    }
}
