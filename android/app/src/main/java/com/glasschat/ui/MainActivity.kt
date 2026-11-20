package com.glasschat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.glasschat.engine.Engine
import com.glasschat.R
import com.glasschat.service.ConnectionService
import com.glasschat.service.CrashLog
import com.glasschat.service.Notifier
import com.glasschat.service.OemSettings
import com.glasschat.service.Prefs
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.components.Splash
import com.glasschat.ui.screens.ArchivedScreen
import com.glasschat.ui.screens.ChatInfoScreen
import com.glasschat.ui.screens.BackgroundSetupScreen
import com.glasschat.ui.screens.ChatMediaScreen
import com.glasschat.ui.screens.LockGate
import com.glasschat.ui.screens.LockedChatsScreen
import com.glasschat.ui.screens.StarredScreen
import com.glasschat.ui.screens.ChatSearchScreen
import com.glasschat.ui.screens.ChatSettingsScreen
import com.glasschat.ui.screens.DeliveryLogScreen
import com.glasschat.ui.screens.WallpaperScreen
import com.glasschat.ui.screens.ChatScreen
import com.glasschat.ui.screens.HomeScreen
import com.glasschat.ui.screens.LinkScreen
import com.glasschat.ui.screens.LogScreen
import com.glasschat.ui.screens.NewChatScreen
import com.glasschat.ui.theme.GlassTheme
import com.glasschat.ui.theme.LocalPalette

class MainActivity : ComponentActivity() {
    private val pendingChat = mutableStateOf<String?>(null)
    private val pendingShare = mutableStateOf<IncomingShare?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val tapFeedback by lazy { TapFeedback(this) }

    override fun onResume() {
        super.onResume()
        runCatching { com.glasschat.ui.components.ContactBook.onResume(this) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == com.glasschat.ui.components.ContactBook.REQUEST) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runCatching { com.glasschat.ui.components.ContactBook.onResume(this) }
            } else {
                com.glasschat.ui.components.ContactBook.pending = null
            }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        runCatching { tapFeedback.onTouch(ev) }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Engine.init(applicationContext)
        Notifier.ensureChannels(this)
        pendingChat.value = intent?.getStringExtra(Notifier.EXTRA_CHAT)?.also(::lockIfNeeded)
        pendingShare.value = ShareIn.parse(this, intent)
        setContent {
            GlassTheme { Root() }
        }
    }

    override fun onStart() {
        super.onStart()
        // Android only lets the background service start while the app is on
        // screen, so (re)start it every time the app is opened.
        if (Engine.isLoggedIn) ConnectionService.start(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val share = ShareIn.parse(this, intent)
        if (share != null) {
            pendingShare.value = share
            return
        }
        intent.getStringExtra(Notifier.EXTRA_CHAT)?.let {
            lockIfNeeded(it)
            pendingChat.value = it
        }
    }

    /** Opening a locked chat from a notification always asks for the code / fingerprint. */
    private fun lockIfNeeded(chat: String) {
        if (com.glasschat.engine.ChatLists.isLocked(chat)) com.glasschat.engine.ChatLock.lockNow()
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Composable
    private fun Root() {
        val p = LocalPalette.current
        val conn by Engine.conn.collectAsState()
        val alert by Engine.alert.collectAsState()
        val nav = rememberNavigator()
        val chatToOpen by pendingChat

        val stage = when (conn) {
            Engine.Conn.Starting -> 0
            Engine.Conn.NeedsLink -> 1
            else -> 2
        }
        // Status/navigation bar icons follow the app's day/night setting.
        val dark = p.dark
        LaunchedEffect(dark) {
            val clear = android.graphics.Color.TRANSPARENT
            val bars = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
            enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
        var backgroundTip by remember { mutableStateOf(false) }
        var crash by remember { mutableStateOf(CrashLog.pending(this@MainActivity)) }
        LaunchedEffect(stage) {
            if (stage == 2) {
                // The engine may have finished starting before the app was on
                // screen, when Android refuses to start the service.
                ConnectionService.start(applicationContext)
                askNotificationPermission()
                // Once: explain what this phone needs to deliver messages while closed.
                delay(2500)
                if (!Prefs.backgroundTipShown(this@MainActivity)) {
                    backgroundTip = true
                }
            }
        }
        // Recent chats appear in Android's share sheet.
        val chatsForShare by Engine.chats.collectAsState()
        LaunchedEffect(chatsForShare.size, chatsForShare.firstOrNull()?.jid, stage) {
            if (stage == 2 && chatsForShare.isNotEmpty()) {
                delay(1500)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ShareIn.publishTargets(applicationContext, chatsForShare) }
            }
        }
        LaunchedEffect(chatToOpen, stage) {
            val c = chatToOpen
            if (c != null && stage == 2) {
                nav.resetTo(Route.Chat(c))
                pendingChat.value = null
            }
        }
        // Everything is drawn into one glass backdrop so pop-ups can be real liquid glass.
        com.glasschat.ui.glass.GlassHostRoot {
        Box(Modifier.fillMaxSize().background(p.background)) {
            AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
                when (s) {
                    0 -> Splash()
                    1 -> LinkScreen()
                    else -> NavHost(nav) { route ->
                        when (route) {
                            Route.Home -> HomeScreen(nav)
                            is Route.Chat -> LockGate(route.jid, onBack = { nav.pop() }) { ChatScreen(route.jid, nav) }
                            is Route.ChatInfo -> ChatInfoScreen(route.jid, nav)
                            Route.NewChat -> NewChatScreen(nav)
                            Route.NewGroup -> com.glasschat.ui.screens.NewGroupScreen(nav)
                            Route.Archived -> ArchivedScreen(nav)
                            Route.Log -> LogScreen(nav)
                            Route.StatusCheck -> LogScreen(nav, statusCheck = true)
                            is Route.ChatMedia -> ChatMediaScreen(route.jid, nav)
                            is Route.Wallpaper -> WallpaperScreen(route.jid, nav)
                            is Route.ChatSearch -> ChatSearchScreen(route.jid, nav)
                            Route.Search -> com.glasschat.ui.screens.GlobalSearchScreen(nav)
                            Route.ChatSettings -> ChatSettingsScreen(nav)
                            Route.DeliveryLog -> DeliveryLogScreen(nav)
                            Route.LockedChats -> LockedChatsScreen(nav)
                            Route.BackgroundSetup -> BackgroundSetupScreen(nav)
                            is Route.Starred -> StarredScreen(route.jid, nav)
                            Route.Profile -> com.glasschat.ui.screens.ProfileScreen(nav)
                            is Route.Settings -> com.glasschat.ui.screens.SettingsSection(route.section, nav)
                        }
                    }
                }
            }
            // Shared from another app: pick a chat, then send.
            val share by pendingShare
            if (stage == 2) share?.let { sh -> ShareFlow(sh, nav, onDone = { pendingShare.value = null }) }
            alert?.let { GlassAlert(title = getString(R.string.app_name), message = it, onDismiss = Engine::dismissAlert) }
            crash?.let { report ->
                val close: () -> Unit = {
                    CrashLog.clear(this@MainActivity)
                    crash = null
                }
                val share: () -> Unit = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Whatsap crash report")
                        .putExtra(Intent.EXTRA_TEXT, report.take(60_000))
                    runCatching { startActivity(Intent.createChooser(send, "Share crash report")) }
                    close()
                }
                GlassAlert(
                    title = "Whatsap closed unexpectedly",
                    message = "Last time the app crashed. Share the report so it can be fixed.\n\n" +
                        report.lineSequence().drop(3).take(3).joinToString("\n").take(300),
                    onDismiss = close,
                    buttons = listOf("Close" to close, "Share report" to share),
                )
            }
            if (backgroundTip && alert == null && crash == null) {
                val later: () -> Unit = {
                    backgroundTip = false
                    Prefs.setBackgroundTipShown(this@MainActivity)
                }
                val fix: () -> Unit = {
                    later()
                    nav.push(Route.BackgroundSetup)
                    if (!OemSettings.batteryUnrestricted(this@MainActivity)) OemSettings.requestUnrestrictedBattery(this@MainActivity)
                }
                GlassAlert(
                    title = "Get messages when the app is closed",
                    message = "Your phone may stop ${getString(R.string.app_name)} in the background. The next screen walks you through the " +
                        "few settings that keep it connected all the time, with a 3-minute test.",
                    onDismiss = later,
                    buttons = listOf("Later" to later, "Set up" to fix),
                )
            }
        }
        }
    }
}
