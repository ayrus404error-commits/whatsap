package com.glasschat.service

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device diary of what happens in the background: when the app
 * process starts (a restart means the phone killed it), connection changes,
 * heartbeats, messages that arrive while minimised and whether a
 * notification was shown. Visible in Settings → Background messages →
 * Delivery log, so problems on a particular phone can be seen and shared.
 * Chats are identified only by the last digits of their number.
 */
object DeliveryLog {
    private const val MAX_BYTES = 96 * 1024
    private val lock = Any()
    @Volatile private var file: File? = null
    private val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US)

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.applicationContext.filesDir, "delivery.log")
    }

    fun add(line: String) {
        Log.i("Delivery", line)
        val f = file ?: return
        synchronized(lock) {
            runCatching {
                f.appendText("${fmt.format(Date())}  $line\n")
                if (f.length() > MAX_BYTES) {
                    val keep = f.readLines().takeLast(500)
                    f.writeText(keep.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    fun read(): String = synchronized(lock) { runCatching { file?.readText() }.getOrNull().orEmpty() }

    fun clear() {
        synchronized(lock) { runCatching { file?.writeText("") } }
    }

    /** "…4521" for a chat id, so the log doesn't hold names or full numbers. */
    fun tag(jid: String): String {
        val user = jid.substringBefore('@')
        val kind = if (jid.endsWith("@g.us")) "group" else "chat"
        return "$kind …${user.takeLast(4)}"
    }
}
