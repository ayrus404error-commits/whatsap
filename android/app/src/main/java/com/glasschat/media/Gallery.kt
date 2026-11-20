package com.glasschat.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** A photo or video on the phone, for the recent-photos grid. */
@Immutable
data class GalleryItem(val uri: Uri, val video: Boolean, val seconds: Int)

object PhoneGallery {
    /** Permissions to ask for, depending on the Android version. */
    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasAccess(ctx: Context): Boolean =
        permissions.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** The newest photos and videos on the phone (or the ones the user allowed). */
    suspend fun recent(ctx: Context, limit: Int = 90): List<GalleryItem> = withContext(Dispatchers.IO) {
        if (!hasAccess(ctx)) return@withContext emptyList()
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            type,
            MediaStore.Files.FileColumns.DURATION,
        )
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "$type=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR $type=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}",
            )
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<GalleryItem>()
        runCatching {
            ctx.contentResolver.query(files, projection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = c.getColumnIndexOrThrow(type)
                val durCol = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val ms = if (durCol >= 0) c.getLong(durCol) else 0L
                    out.add(GalleryItem(ContentUris.withAppendedId(base, id), video, (ms / 1000).toInt()))
                }
            }
        }
        out
    }

    private val thumbs = LruCache<Uri, ImageBitmap>(160)

    fun cachedThumb(uri: Uri): ImageBitmap? = thumbs.get(uri)

    suspend fun thumbnail(ctx: Context, uri: Uri): ImageBitmap? {
        thumbs.get(uri)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap() }.getOrNull()
        }
        if (bmp != null) thumbs.put(uri, bmp)
        return bmp
    }
}

/** A contact picked from the phone's address book, ready to share. */
data class PickedContact(val name: String, val vcard: String)

object Contacts {
    /**
     * Reads the name and number of a phone-number row returned by the
     * system contact picker and builds a WhatsApp-style vCard (with the
     * waid tag, so WhatsApp offers "Message" for it).
     */
    fun fromPhoneUri(ctx: Context, uri: Uri): PickedContact? = runCatching {
        ctx.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getString(0).orEmpty().ifBlank { "Contact" }
            val number = c.getString(1).orEmpty()
            PickedContact(name, vcard(name, number))
        }
    }.getOrNull()

    fun vcard(name: String, number: String): String {
        var digits = number.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        if (digits.startsWith("0") && digits.length == 11) digits = "91" + digits.drop(1)
        val safe = name.replace("\n", " ").replace(";", ",")
        return buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("N:;$safe;;;\n")
            append("FN:$safe\n")
            if (digits.isNotEmpty()) append("TEL;type=CELL;type=VOICE;waid=$digits:+$digits\n")
            append("END:VCARD")
        }
    }

    /** First phone number in a vCard, as digits with country code. */
    fun numberIn(vcard: String): String? {
        val line = vcard.lineSequence().firstOrNull { it.startsWith("TEL", ignoreCase = true) } ?: return null
        val wa = Regex("waid=(\\d+)").find(line)?.groupValues?.get(1)
        if (wa != null) return wa
        val digits = line.substringAfterLast(':').filter { it.isDigit() }
        return digits.ifEmpty { null }
    }
}

object PhoneLocation {
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasAccess(ctx: Context) = permissions.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** One fresh location fix (or the last known one), within about 15 seconds. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasAccess(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT < 30 || providers.isEmpty()) return last
        val provider = if (LocationManager.FUSED_PROVIDER in providers) LocationManager.FUSED_PROVIDER
        else if (LocationManager.NETWORK_PROVIDER in providers) LocationManager.NETWORK_PROVIDER
        else providers.first()
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: last
    }

    /** A street address for the spot, when the phone can look it up. */
    @Suppress("DEPRECATION")
    suspend fun address(ctx: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext ""
        runCatching {
            Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()?.let { a ->
                (0..a.maxAddressLineIndex).joinToString(", ") { a.getAddressLine(it) }
            }
        }.getOrNull().orEmpty()
    }
}
