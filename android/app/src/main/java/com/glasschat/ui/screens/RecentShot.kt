package com.glasschat.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WhatsApp's "Send photo?" pill: a screenshot you took in the last two
 * minutes, offered when you open a chat (or right after you take it there).
 * Tap it to send, × to hide it. Each screenshot is only offered once.
 */
object RecentShot {
    private val dismissed = mutableSetOf<Long>()
    private const val WINDOW_S = 120L

    fun permission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canRead(ctx: Context): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, permission()) == PackageManager.PERMISSION_GRANTED) return true
        // Android 14 "selected photos only" still lets new screenshots through.
        return Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }

    fun dismiss(id: Long) {
        dismissed += id
    }

    /** The newest screenshot from the last two minutes, if not offered yet. */
    fun latest(ctx: Context): Pair<Long, Uri>? {
        if (!canRead(ctx)) return null
        val since = System.currentTimeMillis() / 1000 - WINDOW_S
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME)
        return runCatching {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Images.Media.DATE_ADDED} >= ?", arrayOf(since.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = (c.getString(2).orEmpty() + "/" + c.getString(3).orEmpty()).lowercase()
                    if (!path.contains("screenshot")) continue
                    if (id in dismissed) return@use null
                    return@use id to ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
                null
            }
        }.getOrNull()
    }
}

@Composable
fun RecentShotPill(onSend: (Uri) -> Unit) {
    val ctx = LocalContext.current
    var shot by remember { mutableStateOf<Pair<Long, Uri>?>(null) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    // Ask once, the first time a chat opens, so screenshots can be offered.
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
        if (!RecentShot.canRead(ctx) && !prefs.getBoolean("asked_shots", false)) {
            prefs.edit().putBoolean("asked_shots", true).apply()
            runCatching { ask.launch(RecentShot.permission()) }
        }
    }
    // Notice screenshots taken while the chat is open.
    DisposableEffect(Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                tick++
            }
        }
        runCatching { ctx.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs) }
        onDispose { runCatching { ctx.contentResolver.unregisterContentObserver(obs) } }
    }
    LaunchedEffect(tick) {
        val found = withContext(Dispatchers.IO) { RecentShot.latest(ctx) }
        if (found != null && found.first != shot?.first) {
            thumb = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.loadThumbnail(found.second, Size(120, 120), null) }.getOrNull()
            }
            shot = found
        }
    }
    val s = shot ?: return
    Box(Modifier.padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE6000000))
                .clickable {
                    RecentShot.dismiss(s.first)
                    shot = null
                    onSend(s.second)
                }
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Text("Send photo?", style = Type.body, color = Color.White)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable {
                    RecentShot.dismiss(s.first)
                    shot = null
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
