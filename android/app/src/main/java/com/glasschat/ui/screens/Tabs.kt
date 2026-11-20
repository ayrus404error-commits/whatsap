package com.glasschat.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Palette
import com.glasschat.ui.components.Wallpapers
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Bolt
import com.glasschat.R
import com.glasschat.service.OemSettings
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.outlined.RocketLaunch
import com.glasschat.service.ConnectionService
import com.glasschat.service.Prefs
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.media.MediaState
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
internal fun TabPage(title: String, backdrop: Backdrop, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(scroll)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(56.dp))
            Text(title, style = Type.largeTitle, color = p.label, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
            content()
            Spacer(Modifier.height(120.dp))
        }
        TopEdgeFade()
        TopControls(backdrop, title, scroll.value > 90, left = {}, right = {})
    }
}

@Composable
private fun ComingSoon(icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accentText, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Type.title2, color = p.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = Type.subhead, color = p.secondaryLabel, textAlign = TextAlign.Center)
    }
}

@Composable
fun CallsTab(backdrop: Backdrop) = TabPage("Calls", backdrop) {
    ComingSoon(
        Icons.Outlined.Call,
        "Calls stay on your phone",
        "WhatsApp only allows voice and video calls in its own app. Incoming calls show a notification here so you can answer on your phone, and the call buttons in a chat open WhatsApp or your dialer.",
    )
}

@Composable
fun CommunitiesTab(backdrop: Backdrop) = TabPage("Communities", backdrop) {
    ComingSoon(Icons.Outlined.Groups, "Communities", "Community groups already appear in your chat list. A dedicated Communities view is planned for a later phase.")
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = Type.footnote,
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, bottom = 7.dp),
    )
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 58.dp), color = LocalPalette.current.separator, thickness = 0.5.dp)
}

/** Row icons are outline icons everywhere now (the app is minified, so no lookups by name). */
fun monoIcon(icon: ImageVector): ImageVector = icon

@Composable
fun SettingRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Plain black & white icons, like WhatsApp on iPhone (the colour is ignored).
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(monoIcon(icon), null, tint = p.label, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
        }
    }
}

/** "On" in green or "Off" in red, for status rows. */
@Composable
private fun StatusText(ok: Boolean, good: String, bad: String) {
    val p = LocalPalette.current
    Text(if (ok) good else bad, style = Type.body, color = if (ok) p.secondaryLabel else p.danger)
}

/** iOS-style switch. */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val track by animateColorAsState(if (checked) Color(0xFF34C759) else p.fill.copy(alpha = if (p.dark) 0.5f else 0.25f), label = "track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "knob")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(27.dp)
                .glassShadow(CircleShape, 4.dp, Color.Black.copy(alpha = 0.2f), 2.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun LogScreen(nav: Navigator, statusCheck: Boolean = false) {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    var text by remember { mutableStateOf("") }
    val load: () -> String = { if (statusCheck) Engine.statusCheck() else Engine.logTail() }
    LaunchedEffect(Unit) { text = load() }
    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(p.groupedBackground)
                .verticalScroll(rememberScrollState(Int.MAX_VALUE)),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            SelectionContainer {
                Text(
                    text.ifBlank { if (statusCheck) "Loading…" else "Log is empty." },
                    style = Type.footnote.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = p.label,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = if (statusCheck) "Status check" else "Engine log",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = { GlassCircleButton(backdrop, Icons.Rounded.Refresh, "Refresh") {
                if (statusCheck) Engine.requestStatusSync(force = true)
                text = load()
            } },
        )
    }
}
