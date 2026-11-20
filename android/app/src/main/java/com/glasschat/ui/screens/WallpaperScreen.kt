package com.glasschat.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.ui.Navigator
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.components.Wallpaper
import com.glasschat.ui.components.WallpaperSpec
import com.glasschat.ui.components.Wallpapers
import com.glasschat.ui.components.wallpaperColor
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Pick a background: for one chat, for all chats (""), or for the chats
 * screen itself ([Wallpapers.HOME]).
 */
@Composable
fun WallpaperScreen(chat: String, nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val chatName = chats.find { it.jid == chat }?.name.orEmpty()
    val isHome = chat == Wallpapers.HOME
    val perChat = chat.isNotEmpty() && !isHome
    val own = remember(chat) { Wallpapers.forChat(ctx, chat) }
    var draft by remember(chat) { mutableStateOf(if (isHome) Wallpapers.home(ctx) else own ?: Wallpapers.global(ctx)) }
    // A chat without its own wallpaper follows the one for all chats.
    var followAll by remember(chat) { mutableStateOf(perChat && own == null) }
    val defaultSpec = if (isHome) WallpaperSpec(kind = WallpaperSpec.KIND_PLAIN, doodles = false) else WallpaperSpec()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                Wallpapers.importImage(ctx, uri)?.let {
                    draft = WallpaperSpec(kind = WallpaperSpec.KIND_IMAGE, image = it, dim = if (isHome) 0.45f else 0f)
                    followAll = false
                }
                importing = false
            }
        }
    }
    fun choose(spec: WallpaperSpec) {
        draft = spec
        followAll = false
    }
    fun apply() {
        if (perChat) Wallpapers.set(ctx, chat, if (followAll) null else draft) else Wallpapers.set(ctx, chat, draft)
        nav.pop()
    }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(
            Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            val frame = RoundedCornerShape(34.dp)
            Box(
                Modifier
                    .width(210.dp)
                    .height(380.dp)
                    .glassShadow(frame, 24.dp, Color.Black.copy(alpha = if (p.dark) 0.5f else 0.18f), 6.dp)
                    .clip(frame)
                    .border(4.dp, if (p.dark) Color(0xFF2C2C2E) else Color.White, frame),
            ) {
                Wallpaper(Modifier.fillMaxSize(), spec = draft, forList = isHome)
                if (isHome) PreviewList() else PreviewBubbles()
                if (importing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Text(
                when {
                    isHome -> "Background for the chats screen"
                    chat.isEmpty() -> "Wallpaper for all chats"
                    else -> "Wallpaper for ${chatName.ifBlank { "this chat" }}"
                },
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(top = 12.dp, bottom = 18.dp),
            )

            if (perChat) {
                SectionCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Same as all chats", style = Type.body, color = p.label, modifier = Modifier.weight(1f))
                        IosSwitch(followAll) {
                            followAll = it
                            if (it) draft = Wallpapers.global(ctx)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Choose")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceTile(
                        "Default",
                        selected = draft.kind == defaultSpec.kind,
                        modifier = Modifier.weight(1f),
                        onClick = { choose(defaultSpec) },
                    ) {
                        Wallpaper(Modifier.fillMaxSize(), spec = defaultSpec, forList = isHome)
                    }
                    ChoiceTile(
                        "My photos",
                        selected = draft.kind == WallpaperSpec.KIND_IMAGE,
                        modifier = Modifier.weight(1f),
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) {
                        if (draft.kind == WallpaperSpec.KIND_IMAGE) {
                            Wallpaper(Modifier.fillMaxSize(), spec = draft, forList = isHome)
                        } else {
                            Box(Modifier.fillMaxSize().background(p.fill), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PhotoLibrary, null, tint = p.secondaryLabel, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Solid colours")
                SwatchGrid(Wallpapers.Solids.size, 6) { i, mod ->
                    val c = Wallpapers.Solids[i]
                    Swatch(
                        Brush.verticalGradient(listOf(wallpaperColor(c, p.dark), wallpaperColor(c, p.dark))),
                        selected = draft.kind == WallpaperSpec.KIND_COLOR && draft.color == c,
                        modifier = mod,
                    ) { choose(WallpaperSpec(kind = WallpaperSpec.KIND_COLOR, color = c, doodles = draft.doodles)) }
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Gradients")
                SwatchGrid(Wallpapers.Gradients.size, 4) { i, mod ->
                    val (a, b) = Wallpapers.Gradients[i]
                    Swatch(
                        Brush.verticalGradient(listOf(wallpaperColor(a, p.dark), wallpaperColor(b, p.dark))),
                        selected = draft.kind == WallpaperSpec.KIND_GRADIENT && draft.color == a && draft.color2 == b,
                        modifier = mod,
                    ) { choose(WallpaperSpec(kind = WallpaperSpec.KIND_GRADIENT, color = a, color2 = b, doodles = draft.doodles)) }
                }

                Spacer(Modifier.height(24.dp))
                SectionCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isHome && !p.dark) "Soften" else "Dim",
                                style = Type.body,
                                color = p.label,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(draft.dim * 100).toInt()}%", style = Type.body, color = p.secondaryLabel)
                        }
                        Slider(
                            value = draft.dim,
                            onValueChange = { choose(draft.copy(dim = it)) },
                            valueRange = 0f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = p.accent,
                                inactiveTrackColor = p.fill,
                            ),
                        )
                    }
                    if (draft.kind != WallpaperSpec.KIND_IMAGE) {
                        RowDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Doodles", style = Type.body, color = p.label, modifier = Modifier.weight(1f))
                            IosSwitch(draft.doodles) { choose(draft.copy(doodles = it)) }
                        }
                    }
                }
                Text(
                    "Colours are shown darker automatically in dark mode, so messages stay easy to read.",
                    style = Type.footnote,
                    color = p.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(120.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = if (isHome) "Chats background" else "Chat wallpaper",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {},
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
                .widthIn(min = 220.dp)
                .glassShadow(RoundedCornerShape(50), 18.dp, p.accent.copy(alpha = 0.4f), 4.dp)
                .clip(RoundedCornerShape(50))
                .background(p.accent)
                .clickable(enabled = !importing) { apply() }
                .padding(horizontal = 28.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Set wallpaper", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        }
    }
}

@Composable
private fun PreviewBubbles() {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Today",
            style = Type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = p.label.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(if (p.dark) Color(0xE61C1C1E) else Color(0xEBFFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.weight(1f))
        MiniBubble("Hi! How does this look? 👋", out = false)
        MiniBubble("Looks great 😍", out = true)
        MiniBubble("Love the new background", out = false)
        Spacer(Modifier.height(30.dp))
    }
}

/** A miniature chat list for previewing the chats screen background. */
@Composable
private fun PreviewList() {
    val p = LocalPalette.current
    val rows = listOf(
        "Family" to "Dinner at 8?",
        "Office" to "Report sent ✓",
        "Riya" to "Photo",
        "Weekend trip" to "Booked the tickets",
        "Arjun" to "See you tomorrow",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("Chats", style = Type.title2.copy(fontSize = 18.sp), color = p.label, modifier = Modifier.padding(bottom = 6.dp))
        rows.forEachIndexed { i, (name, msg) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(com.glasschat.ui.theme.SenderColors[(i * 3) % com.glasschat.ui.theme.SenderColors.size].copy(alpha = 0.85f)),
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = Type.footnote.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 1)
                    Text(msg, style = Type.caption.copy(fontSize = 9.5.sp), color = p.secondaryLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MiniBubble(text: String, out: Boolean) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (out) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            style = Type.footnote.copy(fontSize = 12.sp),
            color = p.label,
            modifier = Modifier
                .widthIn(max = 150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (out) p.bubbleOut else p.bubbleIn)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChoiceTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(shape)
                .border(if (selected) 3.dp else 0.5.dp, if (selected) p.accent else p.separator, shape)
                .clickable(onClick = onClick),
        ) {
            content()
        }
        Text(label, style = Type.footnote, color = p.label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SwatchGrid(count: Int, perRow: Int, cell: @Composable (Int, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until count).chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i -> cell(i, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .background(brush)
            .border(if (selected) 3.dp else 0.5.dp, if (selected) p.accent else p.separator, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
