package com.glasschat.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.glasschat.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A stretch of time when the always-on connection should have been running
 * but wasn't: the phone closed the app ("closed"), froze it ("paused"), was
 * asleep with instant messages off ("sleep"), or restarted ("restart").
 */
@Immutable
data class Downtime(val from: Long, val to: Long, val kind: String) {
    val minutes: Long get() = (to - from) / 60_000
}

/**
 * Notices when the phone stops the app in the background, so the reason
 * messages were late is shown instead of guessed. The background service
 * ticks every minute; a long silence between ticks — or between the last
 * tick and the next app start — is recorded.
 */
object Uptime {
    private const val GAP_MS = 4 * 60_000L
    private val _gaps = MutableStateFlow<List<Downtime>>(emptyList())
    val gaps = _gaps.asStateFlow()
    private var lastTickElapsed = 0L

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("uptime", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        _gaps.value = runCatching {
            val a = JSONArray(sp(ctx).getString("gaps", "[]"))
            List(a.length()) { a.getJSONObject(it).let { o -> Downtime(o.optLong("from"), o.optLong("to"), o.optString("kind")) } }
        }.getOrDefault(emptyList())
        // Was the connection supposed to be running when this process last died?
        val p = sp(ctx)
        val wall = p.getLong("alive_wall", 0)
        val el = p.getLong("alive_elapsed", 0)
        if (p.getBoolean("wanted", false) && wall > 0) {
            val now = System.currentTimeMillis()
            if (now - wall > GAP_MS) {
                val restarted = SystemClock.elapsedRealtime() < el
                add(ctx, Downtime(wall, now, if (restarted) "restart" else "closed"))
                DeliveryLog.add(
                    if (restarted) "phone restarted; no connection for ${(now - wall) / 60_000} min"
                    else "THE PHONE CLOSED THE APP at ${hhmm(wall)} — it was closed for ${(now - wall) / 60_000} min",
                )
            }
        }
    }

    /**
     * The always-on connection is running. Called every minute by the service.
     * Returns true when it's coming back from a long pause.
     */
    fun tick(ctx: Context, awake: Boolean): Boolean {
        val el = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val gap = lastTickElapsed > 0 && el - lastTickElapsed > GAP_MS
        if (gap) {
            val from = now - (el - lastTickElapsed)
            val kind = if (awake) "paused" else "sleep"
            add(ctx, Downtime(from, now, kind))
            DeliveryLog.add(
                if (awake) "THE PHONE PAUSED THE APP for ${(el - lastTickElapsed) / 60_000} min (since ${hhmm(from)})"
                else "phone slept for ${(el - lastTickElapsed) / 60_000} min (instant messages off)",
            )
        }
        lastTickElapsed = el
        sp(ctx).edit().putLong("alive_wall", now).putLong("alive_elapsed", el).putBoolean("wanted", true).apply()
        return gap
    }

    /** The connection was switched off on purpose (not a crash or a kill). */
    fun stoppedOnPurpose(ctx: Context) {
        lastTickElapsed = 0
        sp(ctx).edit().putBoolean("wanted", false).apply()
    }

    /** The newest stretch of downtime worth telling about that hasn't been dismissed. */
    fun unseen(ctx: Context, list: List<Downtime>): Downtime? {
        val seen = sp(ctx).getLong("seen", 0)
        val dayAgo = System.currentTimeMillis() - 24 * 3600_000L
        return list.lastOrNull { it.to > seen && it.to > dayAgo && it.kind != "restart" && it.minutes >= 5 }
    }

    fun dismiss(ctx: Context) {
        sp(ctx).edit().putLong("seen", System.currentTimeMillis()).apply()
        _gaps.value = _gaps.value.toList()
    }

    private fun add(ctx: Context, d: Downtime) {
        val list = (_gaps.value + d).takeLast(30)
        _gaps.value = list
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("from", it.from).put("to", it.to).put("kind", it.kind)) }
        sp(ctx).edit().putString("gaps", a.toString()).apply()
    }

    fun hhmm(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}

/**
 * A second safety net next to the heartbeat alarm: every 15 minutes the
 * system runs this job, which restarts the background connection if the
 * phone stopped it.
 */
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        if (!Prefs.background(ctx)) return false
        Engine.init(ctx)
        Engine.scope.launch(Dispatchers.IO) {
            try {
                repeat(50) { if (!Engine.ready) delay(100) }
                if (Engine.isLoggedIn && !ConnectionService.running) {
                    DeliveryLog.add("watchdog: background service wasn't running, restarting it")
                    ConnectionService.start(ctx)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val ID = 4711

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.allPendingJobs.any { it.id == ID }) return
            val job = JobInfo.Builder(ID, ComponentName(ctx, WatchdogJob::class.java))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }

        fun cancel(ctx: Context) {
            ctx.getSystemService(JobScheduler::class.java)?.cancel(ID)
        }
    }
}

/**
 * "Background test": lock the phone for 3 minutes; afterwards a
 * notification says whether the app stayed connected the whole time.
 */
object BackgroundTest {
    const val MINUTES = 3
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("bg_test", Context.MODE_PRIVATE)

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val id = System.currentTimeMillis()
        val due = SystemClock.elapsedRealtime() + MINUTES * 60_000L
        sp(app).edit().putLong("id", id).putBoolean("done", false).apply()
        _running.value = true
        DeliveryLog.add("background test started ($MINUTES min)")
        // While the app is alive and awake this fires on time…
        Engine.scope.launch(Dispatchers.IO) {
            delay(MINUTES * 60_000L)
            if (sp(app).getLong("id", 0) != id) return@launch
            val late = SystemClock.elapsedRealtime() - due
            val conn = Engine.keepAliveNow(8)
            finish(app, id, passed = late < 45_000 && conn == "connected", late, conn)
        }
        // …and if the phone closed or froze it, this alarm brings it back to say so.
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, 78, Intent(app, BackgroundTestReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = due + 45_000L
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi) }
            .onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi) } }
    }

    internal fun finish(ctx: Context, id: Long, passed: Boolean, lateMs: Long, conn: String) {
        val p = sp(ctx)
        synchronized(this) {
            if (p.getLong("id", 0) != id || p.getBoolean("done", false)) return
            p.edit().putBoolean("done", true).putBoolean("passed", passed).apply()
        }
        _running.value = false
        val (title, text) = when {
            passed -> "Background test passed ✓" to
                "Whatsap stayed connected for $MINUTES minutes in the background. Messages should arrive while it's minimised."
            conn == "" || lateMs >= 45_000 -> "Background test failed ✗" to
                "Your phone paused or closed Whatsap in the background, so messages can't arrive. Open the app → Settings → Always-on connection and do every step."
            else -> "Background test: connection dropped" to
                "Whatsap was running but had lost its connection ($conn). It reconnects on its own; if this keeps happening, check Wi-Fi and mobile data settings."
        }
        DeliveryLog.add("background test ${if (passed) "PASSED" else "FAILED"} (late ${lateMs / 1000}s, connection: ${conn.ifBlank { "none" }})")
        Notifier.plain(ctx, 4243, title, text)
    }

    fun lastResult(ctx: Context): Boolean? {
        val p = sp(ctx)
        return if (p.getBoolean("done", false)) p.getBoolean("passed", false) else null
    }
}

class BackgroundTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val id = intent.getLongExtra("id", 0)
        val pending = goAsync()
        WakeHold.brief(ctx, 20_000)
        Engine.init(ctx)
        Engine.scope.launch(Dispatchers.IO) {
            try {
                // Give the in-app check a moment if the app was only just woken.
                delay(3000)
                BackgroundTest.finish(ctx, id, passed = false, lateMs = 45_000, conn = "")
            } finally {
                pending.finish()
            }
        }
    }
}

/** Saves the details when the app crashes, to show (and share) next time. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date())
                file(app).writeText("$stamp · thread ${thread.name}\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n\n$sw")
                DeliveryLog.add("app crashed: ${e.javaClass.simpleName}: ${e.message}")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun pending(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
