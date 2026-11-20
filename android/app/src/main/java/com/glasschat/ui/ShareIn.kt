package com.glasschat.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.glasschat.engine.Chat
import com.glasschat.engine.Engine
import com.glasschat.media.MediaPrep
import com.glasschat.media.PendingMedia
import com.glasschat.ui.screens.SendPreview
import com.glasschat.ui.screens.SendToPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Something another app shared with us ("Share → Whatsap"). */
data class IncomingShare(
    val files: List<Pair<Uri, String>>,
    val text: String?,
    /** Set when the person picked a chat right in Android's share sheet. */
    val chat: String?,
)

/** Text waiting to be put in a chat's input box (from a share). */
object Drafts {
    val pending = mutableStateMapOf<String, String>()
}

/**
 * Unsent text you typed in a chat, kept when you leave it (and across
 * restarts) and shown in the chat list as "Draft: …", like WhatsApp.
 */
object ChatDrafts {
    val all = mutableStateMapOf<String, String>()
    private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        prefs(ctx).all.forEach { (k, v) -> if (v is String && v.isNotBlank()) all[k] = v }
    }

    fun get(ctx: Context, jid: String): String {
        load(ctx)
        return all[jid].orEmpty()
    }

    fun set(ctx: Context, jid: String, text: String) {
        load(ctx)
        if (text.isBlank()) {
            if (all.remove(jid) != null) prefs(ctx).edit().remove(jid).apply()
        } else if (all[jid] != text) {
            all[jid] = text
            prefs(ctx).edit().putString(jid, text).apply()
        }
    }
}

object ShareIn {
    const val CATEGORY = "com.glasschat.category.SHARE_TARGET"

    fun parse(ctx: Context, intent: Intent?): IncomingShare? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val uris = buildList {
            if (action == Intent.ACTION_SEND) {
                streamExtra(intent)?.let { add(it) }
            } else {
                addAll(streamListExtra(intent))
            }
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.ifBlank { null }
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()?.ifBlank { null }
        if (uris.isEmpty() && text == null) return null
        val chat = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        val files = uris.map { u -> u to (ctx.contentResolver.getType(u) ?: intent.type ?: "application/octet-stream") }
        return IncomingShare(files, text, chat)
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM))
            ?.filterNotNull().orEmpty()

    /** Copies shared files into the app (the other app's permission doesn't last). */
    fun importFile(ctx: Context, uri: Uri, mime: String): Pair<File, String>? = runCatching {
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "${System.nanoTime()}-${name.replace('/', '_')}")
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } } ?: return null
        out to name
    }.getOrNull()

    /**
     * Your recent chats as Android "direct share" targets, so they show at the
     * top of every share sheet (like WhatsApp's contacts there).
     */
    fun publishTargets(ctx: Context, chats: List<Chat>) {
        runCatching {
            val max = minOf(ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx), 8).coerceAtLeast(4)
            val top = chats.filter { !it.archived && !com.glasschat.engine.ChatLists.isLocked(it.jid) }
                .sortedByDescending { it.lastTs }
                .take(max)
            val list = top.mapIndexed { rank, c ->
                val icon = Engine.cachedAvatarPath(c.jid)?.let { p -> BitmapFactory.decodeFile(p) }?.let { IconCompat.createWithAdaptiveBitmap(squareFor(it)) }
                    ?: IconCompat.createWithResource(ctx, com.glasschat.R.mipmap.ic_launcher)
                ShortcutInfoCompat.Builder(ctx, c.jid)
                    .setShortLabel(c.name.ifBlank { c.phone }.take(24))
                    .setLongLabel(c.name.ifBlank { c.phone })
                    .setIcon(icon)
                    .setRank(rank)
                    .setLongLived(true)
                    .setCategories(setOf(CATEGORY))
                    .apply { if (!c.isGroup) setPerson(Person.Builder().setName(c.name).setKey(c.jid).build()) }
                    .setIntent(
                        Intent(ctx, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(com.glasschat.service.Notifier.EXTRA_CHAT, c.jid),
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        }
    }

    /** Adaptive icons crop the edges: pad the photo so the face stays visible. */
    private fun squareFor(b: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = 216
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val s = minOf(b.width, b.height)
        val src = android.graphics.Rect((b.width - s) / 2, (b.height - s) / 2, (b.width + s) / 2, (b.height + s) / 2)
        c.drawBitmap(b, src, android.graphics.Rect(0, 0, size, size), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

/**
 * The share flow: pick a chat (unless one was picked in Android's sheet),
 * then photos/videos go to the send preview, files are sent as documents and
 * text lands in the chat's input box, ready to send.
 */
@Composable
fun ShareFlow(share: IncomingShare, nav: Navigator, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by Engine.chats.collectAsState()
    var target by remember(share) { mutableStateOf(share.chat?.let { j -> chats.firstOrNull { it.jid == j } ?: chatStub(j) }) }
    var preview by remember(share) { mutableStateOf<List<PendingMedia>?>(null) }
    var busy by remember(share) { mutableStateOf(false) }
    val visual = share.files.isNotEmpty() && share.files.all { it.second.startsWith("image/") || it.second.startsWith("video/") }

    fun go(c: Chat) {
        target = c
        if (share.files.isEmpty()) {
            Drafts.pending[c.jid] = share.text.orEmpty()
            onDone()
            nav.resetTo(Route.Chat(c.jid))
            return
        }
        busy = true
        Engine.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val imported = withContext(Dispatchers.IO) {
                share.files.mapNotNull { (u, mime) -> ShareIn.importFile(ctx, u, mime)?.let { Triple(it.first, it.second, mime) } }
            }
            busy = false
            if (imported.isEmpty()) {
                android.widget.Toast.makeText(ctx, "Couldn't read what was shared", android.widget.Toast.LENGTH_SHORT).show()
                onDone()
                return@launch
            }
            if (visual) {
                preview = imported.map { (f, _, mime) -> PendingMedia(Uri.fromFile(f), mime.startsWith("video/")) }
            } else {
                imported.forEach { (f, name, mime) ->
                    val spec = org.json.JSONObject()
                        .put("kind", "document")
                        .put("path", f.absolutePath)
                        .put("fileName", name)
                        .put("mime", mime)
                        .put("caption", "")
                    Engine.sendMedia(c.jid, spec)?.join()
                }
                share.text?.let { Engine.sendText(c.jid, it, null) }
                onDone()
                nav.resetTo(Route.Chat(c.jid))
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(share) {
        target?.let { go(it) }
    }

    val items = preview
    val t = target
    when {
        items != null && t != null -> SendPreview(
            items,
            target = t.name,
            onSend = { cap, edited ->
                // On the app-wide scope: this screen closes right away, which
                // used to cancel the send before the photo went out.
                val app = ctx.applicationContext
                Engine.scope.launch {
                    MediaPrep.sendPicked(app, t.jid, edited, cap.ifBlank { share.text.orEmpty() }, null)
                }
                onDone()
                nav.resetTo(Route.Chat(t.jid))
            },
            onCancel = onDone,
            allowViewOnce = true,
        )
        t == null -> SendToPicker(
            onPick = { go(it) },
            onStatus = null,
            onClose = onDone,
            previewText = share.text ?: if (share.files.size == 1) "1 item" else "${share.files.size} items",
        )
        busy -> com.glasschat.ui.components.Splash()
        else -> {}
    }
}

private fun chatStub(jid: String) = Engine.chats.value.firstOrNull { it.jid == jid }
