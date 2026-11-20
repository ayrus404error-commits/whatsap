package com.glasschat.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.glasschat.engine.Engine
import com.glasschat.service.BackgroundTest
import com.glasschat.service.ConnectionService
import com.glasschat.service.OemSettings
import com.glasschat.service.Prefs
import com.glasschat.service.Uptime
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

/**
 * Settings → Always-on connection: one switch, a checklist of everything on
 * this phone that can stop messages arriving while the app is minimised,
 * a real 3-minute background test, and the last times the phone stopped it.
 */
@Composable
fun BackgroundSetupScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val backdrop = remember { Backdrop() }
    val conn by Engine.conn.collectAsState()
    val testing by BackgroundTest.running.collectAsState()
    val gaps by Uptime.gaps.collectAsState()
    var alwaysOn by remember { mutableStateOf(Prefs.background(ctx) && Prefs.instant(ctx)) }
    var toast by remember { mutableStateOf<String?>(null) }
    var strongWake by remember { mutableStateOf(Prefs.strongWake(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = (ctx as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val checks = remember(refresh) { PhoneChecks.read(ctx) }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))

            SectionCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Always-on connection", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label)
                        Text(
                            when {
                                !alwaysOn -> "Off — messages arrive when you open the app"
                                conn == Engine.Conn.Connected -> "On · connected to WhatsApp"
                                else -> "On · reconnecting…"
                            },
                            style = Type.footnote,
                            color = if (alwaysOn && conn == Engine.Conn.Connected) p.accentText else p.secondaryLabel,
                        )
                    }
                    IosSwitch(alwaysOn) {
                        alwaysOn = it
                        Prefs.setBackground(ctx, it)
                        Prefs.setInstant(ctx, it)
                        if (it) ConnectionService.start(ctx) else ConnectionService.stop(ctx)
                    }
                }
            }
            Text(
                "Keeps the app connected to WhatsApp's servers all the time, like WhatsApp Web left open: the background service never " +
                    "sleeps, keeps Wi-Fi awake, restarts itself if it's stopped and shows a small “Stay connected” notification. " +
                    "It uses more battery than the official app, which WhatsApp can wake with push messages instead.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
            SectionHeader("This phone — do every step")
            SectionCard {
                CheckRow(1, "Notifications on", checks.notifications, "Turn on") {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                }
                RowDivider()
                CheckRow(2, "Battery: not optimised", checks.battery, "Allow") { OemSettings.requestUnrestrictedBattery(ctx) }
                RowDivider()
                CheckRow(3, "Background use not restricted", !checks.restricted, "Fix", hint = "App info → Battery → Unrestricted / No restriction") {
                    OemSettings.openAppInfo(ctx)
                }
                RowDivider()
                CheckRow(4, "Background data allowed", !checks.dataSaver, "Fix", hint = "Allow data use when Data Saver is on") {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { OemSettings.openAppInfo(ctx) }
                }
                RowDivider()
                CheckRow(5, "Auto-start on", null, "Open", hint = "In your phone's Auto-start / App launch / Startup manager, allow this app to start by itself. Not every phone has this screen.") {
                    OemSettings.openAutostart(ctx)
                }
                RowDivider()
                CheckRow(
                    6, "Background running allowed", null, "Open",
                    hint = "App info → Battery (or Battery saver) → allow background activity / “Unrestricted”. If your phone has " +
                        "“Other permissions”, also allow running and launching in the background.",
                ) { OemSettings.openAppInfo(ctx) }
                RowDivider()
                CheckRow(7, "Locked in Recent apps", null, null, hint = "Open Recent apps and lock this app (long-press it, or pull it down, and tap the lock 🔒) so “Clear all” doesn't close it.") {}
            }
            Text(
                "Many phones close background apps to save battery — even ones showing a notification — unless steps 5, 6 and 7 " +
                    "are done. Android can't check those three for you, and some phones don't have all of them.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
            SectionHeader("If messages still wait until you open the app")
            SectionCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alarm-clock wake-ups", style = Type.body, color = p.label)
                        Text(
                            if (strongWake) "On · checked every 3 minutes" else "Off",
                            style = Type.footnote,
                            color = if (strongWake) p.accentText else p.secondaryLabel,
                        )
                    }
                    IosSwitch(strongWake) {
                        strongWake = it
                        Prefs.setStrongWake(ctx, it)
                        com.glasschat.service.Heartbeat.cancel(ctx)
                        com.glasschat.service.Heartbeat.schedule(ctx)
                    }
                }
            }
            Text(
                "Wakes the app every 3 minutes with the same kind of alarm as your alarm clock — the one kind phones never delay " +
                    "or block, even when they pause apps to save battery. Messages that waited arrive and notify then, and also " +
                    "the moment you turn the screen on. The alarm ⏰ icon stays in the status bar while this is on.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
            SectionHeader("Test it")
            SectionCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    val last = BackgroundTest.lastResult(ctx)
                    Text(
                        when {
                            testing -> "Test running — lock the phone now and wait ${BackgroundTest.MINUTES} minutes. A notification will tell you the result."
                            last == true -> "Last test passed ✓ — the app stays connected in the background."
                            last == false -> "Last test failed ✗ — the phone stopped the app. Do the steps above, then test again."
                            else -> "Lock the phone for ${BackgroundTest.MINUTES} minutes. A notification then says whether the app stayed connected the whole time."
                        },
                        style = Type.subhead,
                        color = when {
                            testing -> p.label
                            last == true -> p.accentText
                            last == false -> p.danger
                            else -> p.label
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (testing) "Running…" else "Start ${BackgroundTest.MINUTES}-minute test",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (testing) p.tertiaryLabel else p.accent)
                            .clickable(enabled = !testing) {
                                if (!alwaysOn) {
                                    alwaysOn = true
                                    Prefs.setBackground(ctx, true)
                                    Prefs.setInstant(ctx, true)
                                    ConnectionService.start(ctx)
                                }
                                BackgroundTest.start(ctx)
                                toast = "Now lock your phone and wait ${BackgroundTest.MINUTES} minutes"
                            }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    )
                }
            }

            val recent = gaps.takeLast(5).reversed().filter { it.kind != "restart" }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("When the phone stopped the app")
                SectionCard {
                    recent.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = p.separator, thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Text(
                                "${com.glasschat.ui.components.Fmt.dayHeader(d.from)} ${Uptime.hhmm(d.from)}–${Uptime.hhmm(d.to)}",
                                style = Type.subhead,
                                color = p.label,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${d.minutes} min · " + when (d.kind) {
                                    "paused" -> "paused"
                                    "sleep" -> "asleep"
                                    else -> "closed"
                                },
                                style = Type.subhead,
                                color = p.secondaryLabel,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionCard {
                SettingRow(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    Color(0xFF8E8E93),
                    "Delivery log",
                    onClick = { nav.push(Route.DeliveryLog) },
                )
            }
            Spacer(Modifier.height(60.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "Always-on connection",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {},
        )
        Toast(toast) { toast = null }
    }
}

/** What Android lets us check about background delivery on this phone. */
private data class PhoneChecks(val notifications: Boolean, val battery: Boolean, val restricted: Boolean, val dataSaver: Boolean) {
    companion object {
        fun read(ctx: Context) = PhoneChecks(
            notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            battery = OemSettings.batteryUnrestricted(ctx),
            restricted = OemSettings.backgroundRestricted(ctx),
            dataSaver = runCatching {
                Build.VERSION.SDK_INT >= 24 &&
                    ctx.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus ==
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            }.getOrDefault(false),
        )
    }
}

/** One numbered step: ✓ when done, ! when not, or no mark when it can't be checked. */
@Composable
private fun CheckRow(n: Int, title: String, ok: Boolean?, action: String?, hint: String? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = action != null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bg = when (ok) {
            true -> Color(0xFF34C759)
            false -> p.danger
            null -> Color(0xFF8E8E93)
        }
        Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            when (ok) {
                true -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                false -> Icon(Icons.Rounded.PriorityHigh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                null -> Text("$n", style = Type.footnote.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.body, color = p.label)
            if (hint != null) Text(hint, style = Type.footnote, color = p.secondaryLabel)
        }
        if (action != null && ok != true) {
            Spacer(Modifier.width(8.dp))
            Text(
                action,
                style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
