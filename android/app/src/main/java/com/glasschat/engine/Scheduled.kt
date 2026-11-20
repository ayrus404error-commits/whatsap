package com.glasschat.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import com.glasschat.service.ScheduledSendReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A message waiting to be sent later. [media] is a send spec (photo/video/document) or null for text. */
@Immutable
data class ScheduledMessage(
    val id: Long,
    val chat: String,
    val chatName: String,
    val text: String,
    val at: Long,
    val media: String? = null,
) {
    val kind: String get() = media?.let { runCatching { JSONObject(it).optString("kind") }.getOrNull() } ?: "text"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("chat", chat).put("chatName", chatName).put("text", text).put("at", at)
        .apply { if (media != null) put("media", media) }

    companion object {
        fun from(o: JSONObject) = ScheduledMessage(
            o.getLong("id"), o.getString("chat"), o.optString("chatName"), o.optString("text"), o.getLong("at"),
            o.optString("media").ifBlank { null },
        )
    }
}

/**
 * Scheduled messages: texts, links, photos, videos or documents sent
 * automatically at a chosen time, from 10 minutes up to two weeks ahead.
 * They're kept on this phone and sent by this app, so the phone must be on
 * (it doesn't need to be unlocked) at that time.
 */
object Scheduled {
    const val MIN_MS = 10 * 60_000L
    const val MAX_MS = 14 * 24 * 60 * 60_000L
    private const val TAG = "Scheduled"
    private const val PREFS = "scheduled"

    private val _items = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val items = _items.asStateFlow()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
        _items.value = runCatching {
            val a = JSONArray(raw)
            List(a.length()) { ScheduledMessage.from(a.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedBy { it.at }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        _items.value.forEach { a.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", a.toString()).apply()
    }

    /** Checks a chosen time; returns an error message, or null if it's fine. */
    fun check(at: Long): String? {
        val d = at - System.currentTimeMillis()
        return when {
            d < MIN_MS - 30_000 -> "Pick a time at least 10 minutes from now"
            d > MAX_MS + 60_000 -> "You can schedule up to two weeks ahead"
            else -> null
        }
    }

    fun forChat(chat: String): List<ScheduledMessage> = _items.value.filter { it.chat == chat }

    /** Media files are moved somewhere Android won't clear before they're sent. */
    private fun keepMedia(ctx: Context, spec: JSONObject): JSONObject {
        val dir = File(ctx.filesDir, "scheduled").apply { mkdirs() }
        for (k in listOf("path", "thumb")) {
            val p = spec.optString(k)
            if (p.isBlank()) continue
            val src = File(p)
            if (!src.exists()) continue
            val dst = File(dir, "${System.nanoTime()}-${src.name}")
            if (src.renameTo(dst) || runCatching { src.copyTo(dst, true); src.delete() }.isSuccess) spec.put(k, dst.absolutePath)
        }
        return spec
    }

    fun add(ctx: Context, chat: String, chatName: String, text: String, at: Long, media: JSONObject? = null): ScheduledMessage {
        load(ctx)
        val item = ScheduledMessage(
            System.currentTimeMillis() * 1000 + (0..999).random(),
            chat, chatName, text.trim(), at,
            media?.let { keepMedia(ctx, it).toString() },
        )
        _items.value = (_items.value + item).sortedBy { it.at }
        save(ctx)
        arm(ctx)
        return item
    }

    fun cancel(ctx: Context, id: Long) {
        load(ctx)
        val gone = _items.value.filter { it.id == id }
        _items.value = _items.value.filterNot { it.id == id }
        save(ctx)
        gone.forEach { deleteFiles(it) }
        arm(ctx)
    }

    /** Removes it from the list but keeps its files (it's about to be sent). */
    fun cancelKeepFiles(ctx: Context, id: Long) {
        load(ctx)
        _items.value = _items.value.filterNot { it.id == id }
        save(ctx)
        arm(ctx)
    }

    fun reschedule(ctx: Context, id: Long, at: Long) {
        load(ctx)
        _items.value = _items.value.map { if (it.id == id) it.copy(at = at) else it }.sortedBy { it.at }
        save(ctx)
        arm(ctx)
    }

    private fun deleteFiles(m: ScheduledMessage) {
        val spec = m.media?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        for (k in listOf("path", "thumb")) spec.optString(k).takeIf { it.isNotBlank() }?.let { File(it).delete() }
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 91, Intent(ctx, ScheduledSendReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets one exact alarm for the next message due (or clears it). */
    fun arm(ctx: Context) {
        load(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = _items.value.minOfOrNull { it.at }
        val pi = pending(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, System.currentTimeMillis() + 1_000)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Takes the messages that are due now (they're removed from the list). */
    fun takeDue(ctx: Context): List<ScheduledMessage> {
        load(ctx)
        val now = System.currentTimeMillis() + 5_000
        val due = _items.value.filter { it.at <= now }
        if (due.isNotEmpty()) {
            _items.value = _items.value.filterNot { d -> due.any { it.id == d.id } }
            save(ctx)
        }
        return due
    }

    /** Sends one scheduled message; waits until it has been handed to WhatsApp. */
    suspend fun send(m: ScheduledMessage) {
        try {
            val spec = m.media?.let { JSONObject(it) }
            if (spec != null) {
                if (m.text.isNotBlank()) spec.put("caption", m.text)
                Engine.sendMedia(m.chat, spec)?.join()
            } else if (m.text.isNotBlank()) {
                Engine.sendText(m.chat, m.text, null)?.join()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "send failed", t)
        }
    }
}
