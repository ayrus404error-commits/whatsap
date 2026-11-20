package com.glasschat.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.glasschat.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the WhatsApp connection alive while the app is in the background,
 * so messages arrive and notify like the real app. Shows a quiet ongoing
 * notification, which Android requires for this.
 *
 * WhatsApp can't wake this app with push messages the way it wakes the
 * official app, so this service has to keep a live connection. Three things
 * keep it healthy while the phone sleeps: the foreground service itself
 * (which keeps network access in Doze), a heartbeat alarm every few minutes
 * that checks the connection and revives it, and a re-check whenever the
 * network changes (Wi-Fi ↔ mobile data).
 */
class ConnectionService : Service() {
    private var watch: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var awake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var ticker: Job? = null
    private var screen: BroadcastReceiver? = null

    /**
     * "Instant messages": keep the processor awake while connected, so the
     * engine's own pings (every 20–30 s) keep the connection open while the
     * screen is off. Without it the phone sleeps between heartbeats and a
     * dropped connection is only noticed at the next one (up to ~15 minutes
     * in deep sleep). Costs some battery, so it can be switched off.
     */
    private fun updateAwake() {
        if (Prefs.instant(this)) {
            if (awake == null) {
                awake = runCatching {
                    getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glasschat:connection")
                        .apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                }.getOrNull()
                DeliveryLog.add(if (awake != null) "instant messages on (processor kept awake)" else "couldn't keep the processor awake")
            }
        } else {
            awake?.let { if (it.isHeld) it.release() }
            awake = null
        }
        // Keep Wi-Fi from powering down with the screen (some phones drop
        // the connection a few minutes after locking).
        if (Prefs.instant(this)) {
            if (wifi == null) {
                wifi = runCatching {
                    @Suppress("DEPRECATION")
                    getSystemService(WifiManager::class.java)
                        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "glasschat:connection")
                        .apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                }.getOrNull()
            }
        } else {
            wifi?.let { if (it.isHeld) it.release() }
            wifi = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        ServiceCompat.startForeground(this, Notifier.SERVICE_ID, Notifier.serviceNotification(this, Engine.conn.value), type)
        running = true
        DeliveryLog.add("background service started · ${OemSettings.statusLine(this)}")
        watch = Engine.scope.launch {
            combine(Engine.conn, Engine.history) { c, h -> c to h }.collect { (c, h) ->
                if (c == Engine.Conn.NeedsLink) {
                    // Started at boot but no account is linked: nothing to keep alive.
                    Heartbeat.cancel(this@ConnectionService)
                    Uptime.stoppedOnPurpose(this@ConnectionService)
                    stopSelf()
                } else {
                    Notifier.updateService(this@ConnectionService, c, h)
                }
            }
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        netCallback = object : ConnectivityManager.NetworkCallback() {
            private var first = true

            override fun onAvailable(network: Network) {
                // Called right away for the current network, then on every switch.
                if (first) {
                    first = false
                    Engine.connect()
                } else {
                    DeliveryLog.add("network changed, checking connection")
                    WakeHold.brief(this@ConnectionService, 15_000)
                    Engine.checkConnection()
                }
            }
        }.also { runCatching { cm.registerDefaultNetworkCallback(it) } }
        Heartbeat.schedule(this)
        WatchdogJob.schedule(this)
        updateAwake()
        // The moment the phone wakes up, make sure the connection is alive, so
        // anything that waited arrives (and notifies) without opening the app.
        screen = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> DeliveryLog.add("screen off")
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        if (intent.action == Intent.ACTION_SCREEN_ON) DeliveryLog.add("screen on: checking connection")
                        WakeHold.brief(this@ConnectionService, 20_000)
                        Engine.checkConnection()
                    }
                }
            }
        }.also {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            runCatching { ContextCompat.registerReceiver(this, it, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        }
        // A tick every minute: proves the app is alive, and notices when the
        // phone paused it (see Uptime / the delivery log).
        ticker = Engine.scope.launch {
            while (true) {
                if (Uptime.tick(this@ConnectionService, awake?.isHeld == true)) {
                    // Coming back from being paused: the old connection is
                    // almost certainly dead, so check it right now.
                    WakeHold.brief(this@ConnectionService, 20_000)
                    Engine.checkConnection()
                }
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateAwake()
        Engine.connect()
        return START_STICKY
    }

    /**
     * Swiped away from Recent apps. Stock Android keeps the service running,
     * but many phones close the whole app shortly after; ask for a restart
     * in a few seconds just in case (the heartbeat handles it).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        DeliveryLog.add("app swiped away from Recent apps")
        Heartbeat.schedule(this, 3_000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        DeliveryLog.add("background service stopped")
        awake?.let { if (it.isHeld) it.release() }
        awake = null
        wifi?.let { if (it.isHeld) it.release() }
        wifi = null
        ticker?.cancel()
        watch?.cancel()
        screen?.let { runCatching { unregisterReceiver(it) } }
        screen = null
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set

        fun start(ctx: Context) {
            if (!Prefs.background(ctx)) {
                Engine.connect()
                return
            }
            Heartbeat.schedule(ctx)
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ConnectionService::class.java))
            } catch (t: Throwable) {
                // Android may refuse when we're not in the foreground; the
                // heartbeat and the next app start will try again.
                Log.w("ConnectionService", "start refused: ${t.message}")
                DeliveryLog.add("Android refused to start the background service (${t.javaClass.simpleName})")
                Engine.connect()
            }
        }

        fun stop(ctx: Context) {
            Uptime.stoppedOnPurpose(ctx)
            WatchdogJob.cancel(ctx)
            Heartbeat.cancel(ctx)
            ctx.stopService(Intent(ctx, ConnectionService::class.java))
        }
    }
}

/** Short wake locks, so work started while the phone sleeps can finish. */
object WakeHold {
    @Volatile private var lock: PowerManager.WakeLock? = null

    fun brief(ctx: Context, ms: Long = 10_000) {
        runCatching {
            val l = lock ?: ctx.applicationContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glasschat:incoming")
                .apply { setReferenceCounted(false) }
                .also { lock = it }
            l.acquire(ms)
        }
    }
}

/**
 * A wake-up every few minutes, even in Doze, that checks the WhatsApp
 * connection, revives it if it died, and restarts the service if the system
 * stopped it.
 */
object Heartbeat {
    private const val INTERVAL_MS = 5 * 60_000L
    /** With alarm-clock wake-ups: more often, since each one is guaranteed. */
    private const val STRONG_INTERVAL_MS = 3 * 60_000L

    private fun intent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        77,
        Intent(ctx, HeartbeatReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context, delayMs: Long? = null) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(ctx)
        if (Prefs.strongWake(ctx)) {
            // An alarm-clock alarm: the one kind of alarm phones never delay or
            // skip, and it brings the phone out of deep sleep and un-freezes the
            // app. The price is the alarm icon in the status bar.
            val wait = delayMs ?: STRONG_INTERVAL_MS
            val show = PendingIntent.getActivity(
                ctx, 79,
                Intent(ctx, com.glasschat.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val ok = runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + wait, show), pi)
            }.isSuccess
            if (ok) return
            DeliveryLog.add("alarm-clock wake-up refused, using a normal alarm")
        }
        val at = SystemClock.elapsedRealtime() + (delayMs ?: INTERVAL_MS)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(intent(ctx))
    }
}

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        if (!Prefs.background(ctx)) return
        val pending = goAsync()
        WakeHold.brief(ctx, 30_000)
        Engine.init(ctx)
        Engine.scope.launch(Dispatchers.IO) {
            try {
                repeat(50) { if (!Engine.ready) delay(100) }
                if (Engine.isLoggedIn) {
                    if (!ConnectionService.running) {
                        DeliveryLog.add("heartbeat: background service wasn't running, restarting it")
                        ConnectionService.start(ctx)
                    }
                    val result = Engine.keepAliveNow()
                    DeliveryLog.add("heartbeat: $result")
                    when (result) {
                        // Give it a moment to reconnect and collect what arrived meanwhile.
                        "reconnecting", "connecting" -> delay(10_000)
                    }
                    Heartbeat.schedule(ctx)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Phone makers' own "auto-start" screens (Infinix/Tecno/itel, Xiaomi, Oppo,
 * Vivo, Huawei, OnePlus). Their battery savers close apps that aren't
 * allowed there, even with a foreground service. Falls back to App info.
 */
object OemSettings {
    private val autostart = listOf(
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
    )

    fun openAutostart(ctx: Context) {
        for ((pkg, cls) in autostart) {
            val i = Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(i) }.isSuccess) return
        }
        openAppInfo(ctx)
    }

    fun openAppInfo(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** The system "allow running in background / unrestricted battery" prompt. */
    fun requestUnrestrictedBattery(ctx: Context) {
        val asked = runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!asked) openAppInfo(ctx)
    }

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /**
     * "Restricted" battery use (set by the user or the phone): Android then
     * stops the app's background work, including this connection.
     */
    fun backgroundRestricted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 28 &&
            runCatching { ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false)

    /** Everything that can stop background delivery is fine. */
    fun allClear(ctx: Context): Boolean =
        batteryUnrestricted(ctx) && !backgroundRestricted(ctx) &&
            androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun statusLine(ctx: Context): String = listOf(
        "battery " + if (batteryUnrestricted(ctx)) "unrestricted" else "optimised",
        if (backgroundRestricted(ctx)) "background RESTRICTED" else "background allowed",
        "notifications " + if (androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) "on" else "OFF",
        "instant " + if (Prefs.instant(ctx)) "on" else "off",
        "alarm-clock wake-ups " + if (Prefs.strongWake(ctx)) "on" else "off",
        "Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER}",
    ).joinToString(", ")
}

object Prefs {
    /** Keep the processor awake in the background for instant messages. */
    fun instant(ctx: Context) = sp(ctx).getBoolean("instant", true)
    fun setInstant(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("instant", on).apply()

    /** The one-time "get messages when the app is closed" tips were shown. */
    fun backgroundTipShown(ctx: Context) = sp(ctx).getBoolean("bg_tip_shown", false)
    fun setBackgroundTipShown(ctx: Context) = sp(ctx).edit().putBoolean("bg_tip_shown", true).apply()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    /** Heartbeat as an alarm-clock alarm (never delayed by the phone; shows the alarm icon). */
    fun strongWake(ctx: Context) = sp(ctx).getBoolean("strong_wake", false)
    fun setStrongWake(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("strong_wake", on).apply()

    fun background(ctx: Context) = sp(ctx).getBoolean("background", true)
    fun setBackground(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("background", on).apply()

    fun previews(ctx: Context) = sp(ctx).getBoolean("previews", true)
    fun setPreviews(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("previews", on).apply()

    fun readReceipts(ctx: Context) = sp(ctx).getBoolean("read_receipts", true)
    fun setReadReceipts(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("read_receipts", on).apply()

    // ---- Notifications (Settings → Notifications) ----
    fun notifyPrivate(ctx: Context) = sp(ctx).getBoolean("n_private", true)
    fun notifyGroups(ctx: Context) = sp(ctx).getBoolean("n_groups", true)
    fun notifyStatus(ctx: Context) = sp(ctx).getBoolean("n_status", false)
    fun notifyReactions(ctx: Context) = sp(ctx).getBoolean("n_reactions", true)
    fun reactionSender(ctx: Context) = sp(ctx).getBoolean("n_reaction_sender", true)
    fun groupPreviews(ctx: Context) = sp(ctx).getBoolean("n_group_previews", previews(ctx))
    fun smartReplies(ctx: Context) = sp(ctx).getBoolean("n_smart", true)
    fun showBadge(ctx: Context) = sp(ctx).getBoolean("n_badge", true)
    fun badgeIncludeMuted(ctx: Context) = sp(ctx).getBoolean("n_badge_muted", false)
    fun badgeCountMessages(ctx: Context) = sp(ctx).getBoolean("n_badge_messages", true)
    fun setFlag(ctx: Context, key: String, on: Boolean) = sp(ctx).edit().putBoolean(key, on).apply()
    fun flag(ctx: Context, key: String, def: Boolean) = sp(ctx).getBoolean(key, def)

    /** View status updates without the sender seeing it (no read receipt). */
    fun secretStatus(ctx: Context) = sp(ctx).getBoolean("secret_status", false)
    fun setSecretStatus(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("secret_status", on).apply()

    /** Photo upload quality: false = Standard (faster, smaller), true = HD. */
    fun hdUploads(ctx: Context) = sp(ctx).getBoolean("hd_uploads", false)
    fun setHdUploads(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("hd_uploads", on).apply()

    /**
     * What downloads by itself on each kind of network ("mobile", "wifi",
     * "roaming"): any of "photo", "audio", "video", "doc".
     */
    fun autoDownloadKinds(ctx: Context, network: String): Set<String> {
        val def = when (network) {
            "wifi" -> setOf("photo", "audio", "video", "doc")
            "mobile" -> if (autoDownload(ctx)) setOf("photo") else emptySet()
            else -> emptySet()
        }
        return sp(ctx).getStringSet("auto_$network", null)?.toSet() ?: def
    }
    fun setAutoDownloadKinds(ctx: Context, network: String, kinds: Set<String>) =
        sp(ctx).edit().putStringSet("auto_$network", kinds).apply()

    /** Whether this kind of media downloads by itself on the current network. */
    fun shouldAutoDownload(ctx: Context, kind: String): Boolean {
        val network = runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "mobile"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "wifi"
                !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> "roaming"
                else -> "mobile"
            }
        }.getOrDefault("mobile")
        return kind in autoDownloadKinds(ctx, network)
    }

    /** Download photos and stickers as soon as they're shown. */
    fun autoDownload(ctx: Context) = sp(ctx).getBoolean("auto_download", true)
    fun setAutoDownload(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("auto_download", on).apply()
}
