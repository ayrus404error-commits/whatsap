package com.glasschat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.ui.components.AppMark
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.glass.liquidGlass
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun LinkScreen() {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    val qr by Engine.qr.collectAsState()
    val error by Engine.pairError.collectAsState()
    var phoneMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Engine.connect() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize().backdropSource(backdrop))
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            AppMark(64.dp)
            Spacer(Modifier.height(16.dp))
            Text("Link Whatsap", style = Type.largeTitle.copy(fontSize = 30.sp), color = p.label)
            Text(
                "Use WhatsApp on this phone as a linked device.",
                style = Type.subhead,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))

            val card = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassShadow(card, 24.dp, Color.Black.copy(alpha = 0.18f), 8.dp)
                    .liquidGlass(backdrop, card, p.glassStrong)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(phoneMode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { usePhone ->
                    if (usePhone) PhoneCodePanel() else QrPanel(qr, error)
                }
            }

            Spacer(Modifier.height(16.dp))
            val pill = RoundedCornerShape(50)
            Box(
                Modifier
                    .glassShadow(pill, 14.dp, Color.Black.copy(alpha = 0.12f), 4.dp)
                    .liquidGlass(backdrop, pill, p.glass)
                    .clip(pill)
                    .clickable { phoneMode = !phoneMode }
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            ) {
                Text(
                    if (phoneMode) "Scan a QR code instead" else "Link with phone number instead",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = p.accentText,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (p.dark) Color(0x33FF9F0A) else Color(0x22FF9500))
                    .padding(14.dp),
            ) {
                Icon(Icons.Outlined.Info, null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Whatsap is an unofficial WhatsApp client (not made by WhatsApp). WhatsApp can restrict numbers that use third-party apps, so test with a spare number first. Calls aren't supported on this kind of linked device.",
                    style = Type.footnote,
                    color = p.label.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QrPanel(qr: String?, error: String?) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(244.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null && qr == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, style = Type.subhead, color = Color(0xFF3C3C43), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1DAA61))
                            .clickable { Engine.restartLinking() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New code", style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
                qr == null -> CircularProgressIndicator(color = Color(0xFF1DAA61), strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                else -> QrCode(qr, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(20.dp))
        Step(1, "Open WhatsApp on the phone with your number.")
        Step(2, "Go to Settings → Linked devices (on Android: ⋮ → Linked devices).")
        Step(3, "Tap Link a device and point the phone at this code.")
        if (error != null && qr != null) {
            Text(error, style = Type.footnote, color = p.danger, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = Type.footnote.copy(fontWeight = FontWeight.Bold), color = p.accentText)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.subhead, color = p.label, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PhoneCodePanel() {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("+91 ") }
    var code by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val qr by Engine.qr.collectAsState()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (code == null) {
            Text("Enter the WhatsApp number to link", style = Type.title, color = p.label)
            Text(
                "Include the country code. WhatsApp will show a notification on that phone.",
                style = Type.footnote,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            BasicTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = p.label, textAlign = TextAlign.Center, letterSpacing = 1.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.fill)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            val ready = phone.count { it.isDigit() } >= 10 && !busy && qr != null
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (ready) p.accent else p.accent.copy(alpha = 0.35f))
                    .clickable(enabled = ready) {
                        busy = true
                        err = null
                        scope.launch {
                            Engine.pairWithPhone(phone)
                                .onSuccess { code = it }
                                .onFailure { err = it.message ?: "Couldn't get a code" }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text(if (qr == null) "Connecting…" else "Get code", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
            }
            err?.let { Text(it, style = Type.footnote, color = p.danger, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
        } else {
            Text("Enter this code on your phone", style = Type.title, color = p.label)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                code!!.forEach { ch ->
                    if (ch == '-') {
                        Text("–", style = Type.title2, color = p.secondaryLabel)
                    } else {
                        Box(
                            Modifier
                                .size(width = 30.dp, height = 42.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(p.fill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch.toString(), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = p.label)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Step(1, "On your phone, open WhatsApp → Linked devices → Link a device.")
            Step(2, "Tap “Link with phone number instead”.")
            Step(3, "Type the code above.")
            Spacer(Modifier.height(6.dp))
            Text(
                "Get a new code",
                style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = p.accentText,
                modifier = Modifier.clickable { code = null }.padding(8.dp),
            )
        }
    }
}

/** Draws a QR matrix with rounded modules and soft finder patterns. */
@Composable
fun QrCode(code: String, modifier: Modifier = Modifier) {
    val rows = remember(code) { Engine.qrModules(code) }
    if (rows.isEmpty()) return
    val ink = Color(0xFF111B21)
    val accent = Color(0xFF0F9D58)
    Canvas(modifier.aspectRatio(1f)) {
        val n = rows.size
        val m = size.minDimension / n
        fun finder(r: Int, c: Int) = (r < 7 && c < 7) || (r < 7 && c >= n - 7) || (r >= n - 7 && c < 7)
        for (r in 0 until n) {
            val row = rows[r]
            for (c in row.indices) {
                if (row[c] == '1' && !finder(r, c)) {
                    drawRoundRect(ink, Offset(c * m + m * 0.06f, r * m + m * 0.06f), Size(m * 0.88f, m * 0.88f), CornerRadius(m * 0.42f))
                }
            }
        }
        for ((r, c) in listOf(0 to 0, 0 to n - 7, n - 7 to 0)) {
            val o = Offset(c * m, r * m)
            drawRoundRect(ink, o + Offset(m * 0.5f, m * 0.5f), Size(m * 6f, m * 6f), CornerRadius(m * 1.9f), style = Stroke(width = m))
            drawRoundRect(accent, o + Offset(m * 2f, m * 2f), Size(m * 3f, m * 3f), CornerRadius(m * 1.1f))
        }
    }
}

/** Slowly drifting colour blobs — gives the glass something to refract. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(modifier.background(p.background)) {
        Canvas(Modifier.fillMaxSize().blur(70.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF25D366).copy(alpha = if (p.dark) 0.55f else 0.45f), Color.Transparent), center = Offset(w * (0.15f + 0.5f * a), h * 0.18f), radius = w * 0.7f),
                radius = w * 0.7f, center = Offset(w * (0.15f + 0.5f * a), h * 0.18f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF34B7F1).copy(alpha = if (p.dark) 0.45f else 0.35f), Color.Transparent), center = Offset(w * (0.9f - 0.4f * b), h * 0.48f), radius = w * 0.65f),
                radius = w * 0.65f, center = Offset(w * (0.9f - 0.4f * b), h * 0.48f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFC857).copy(alpha = if (p.dark) 0.35f else 0.35f), Color.Transparent), center = Offset(w * (0.3f + 0.3f * b), h * (0.82f - 0.1f * a)), radius = w * 0.6f),
                radius = w * 0.6f, center = Offset(w * (0.3f + 0.3f * b), h * (0.82f - 0.1f * a)),
            )
        }
    }
}
