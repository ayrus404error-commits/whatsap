package com.glasschat.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.service.ConnectionService
import com.glasschat.service.DeliveryLog
import com.glasschat.service.Notifier
import com.glasschat.ui.Navigator
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What happened in the background, newest last: app starts, connection
 * changes, heartbeats, messages and notifications. For finding out why a
 * phone doesn't deliver messages while the app is minimised.
 */
@Composable
fun DeliveryLogScreen(nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = remember { Backdrop() }
    var text by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    fun reload() {
        text = DeliveryLog.read()
    }
    LaunchedEffect(Unit) {
        reload()
        scroll.scrollTo(scroll.maxValue)
    }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(scroll)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            Text(
                "To test: tap “Test notification”, lock the phone, and wait. Then ask someone to message you while the app is " +
                    "minimised, wait a few minutes and come back here. “app process started” at a time you didn't open the app " +
                    "means the phone closed it.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogButton("Test notification", p.accent, Modifier.weight(1f)) {
                    toast = "Lock the phone — it arrives in 10 seconds"
                    DeliveryLog.add("test notification requested (in 10 s)")
                    Engine.scope.launch {
                        delay(10_000)
                        Notifier.test(ctx.applicationContext)
                    }
                }
                LogButton("Share", Color(0xFF0A84FF), Modifier.weight(1f)) {
                    val status = "Background service running: ${ConnectionService.running}, connection: ${Engine.conn.value.name}"
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Delivery log")
                        .putExtra(Intent.EXTRA_TEXT, status + "\n\n" + DeliveryLog.read().takeLast(60_000))
                    runCatching { ctx.startActivity(Intent.createChooser(send, "Share delivery log")) }
                }
                LogButton("Clear", Color(0xFF8E8E93), Modifier.weight(0.7f)) {
                    DeliveryLog.clear()
                    reload()
                }
            }
            SectionCard {
                SelectionContainer {
                    Text(
                        text.ifBlank { "Nothing logged yet." },
                        style = Type.footnote.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                        color = p.label,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "Delivery log",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {
                GlassCircleButton(backdrop, Icons.Rounded.Refresh, "Refresh") {
                    reload()
                    scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                }
            },
        )
        Toast(toast) { toast = null }
    }
}

@Composable
private fun LogButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .then(Modifier),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
