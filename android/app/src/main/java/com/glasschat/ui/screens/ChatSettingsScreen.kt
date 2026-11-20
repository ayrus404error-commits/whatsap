package com.glasschat.ui.screens

import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Message
import com.glasschat.engine.Reaction
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.components.Wallpaper
import com.glasschat.ui.components.WallpaperSpec
import com.glasschat.ui.components.Wallpapers
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.GlassTabBar
import com.glasschat.ui.glass.TabItem
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.Appearance
import com.glasschat.ui.theme.BubbleThemes
import com.glasschat.ui.theme.ChatStyle
import com.glasschat.ui.theme.LocalChatStyle
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlin.math.roundToInt

/**
 * Settings → Chat settings: message text size and corners with a live
 * preview, colour themes, day/night, chat list view, tab bar size, and the
 * wallpaper/background pickers.
 */
@Composable
fun ChatSettingsScreen(nav: Navigator) {
    val p = LocalPalette.current
    val style = LocalChatStyle.current
    val backdrop = remember { Backdrop() }
    // Glass inside the scrolling page can't read the page itself (it would
    // draw into its own source), so it gets its own backdrops.
    val flatBackdrop = remember { Backdrop() }
    val tabPreviewBackdrop = remember { Backdrop() }
    val scroll = rememberScrollState()
    var iconToast by remember { mutableStateOf<String?>(null) }
    var customTheme by remember { mutableStateOf(false) }
    var colorSheet by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        Column(Modifier.fillMaxSize().backdropSource(backdrop).background(p.groupedBackground).verticalScroll(scroll)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))

            // Text size, with the preview right under it.
            SettingsCard {
                SliderRow(
                    "Message text size",
                    value = style.textSize,
                    range = 12f..24f,
                    steps = 23,
                    label = style.textSize.let { if (it % 1f == 0f) it.roundToInt().toString() else "%.1f".format(it) },
                ) { v -> Appearance.update { it.copy(textSize = (v * 2).roundToInt() / 2f) } }
                ChatPreview()
                SettingRow(com.glasschat.ui.glass.LineIcons.Palette, Color(0xFF5856D6), "Chat wallpaper", onClick = { nav.push(Route.Wallpaper("")) })
                RowDivider()
                SettingRow(Icons.Outlined.FormatPaint, Color(0xFF34C759), "Chats screen background", onClick = { nav.push(Route.Wallpaper(Wallpapers.HOME)) })
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Taps")
            SettingsCard {
                SwitchRow("Vibrate on tap", style.tapHaptic) { v -> Appearance.update { it.copy(tapHaptic = v) } }
                RowDivider()
                SwitchRow("Click sound on tap", style.tapSound) { v -> Appearance.update { it.copy(tapSound = v) } }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("App icon")
            SettingsCard {
                AppIconPicker(
                    onChanged = { iconToast = "Icon changed to $it. Your home screen may take a few seconds to update." },
                    onMessage = { iconToast = it },
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("App colours")
            SettingsCard {
                // Each colour is its own setting: app, links, and (below) bubbles.
                SettingRow(Icons.Outlined.Palette, Color.Unspecified, "App colour", onClick = { colorSheet = "app" }, trailing = {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
                })
                RowDivider()
                SettingRow(Icons.Outlined.FormatPaint, Color.Unspecified, "Background colour", onClick = { colorSheet = "bg" }, trailing = {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(p.groupedBackground).border(0.5.dp, p.separator, CircleShape))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
                })
                RowDivider()
                SettingRow(Icons.Outlined.Link, Color.Unspecified, "Link colour", onClick = { colorSheet = "link" }, trailing = {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (style.linkColor != 0L) Color(style.linkColor or 0xFF000000L) else p.link))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
                })
            }
            FootNote("App colour is used for buttons, switches, ticks and highlights. Bubble colours are set separately below.")

            Spacer(Modifier.height(22.dp))
            SectionHeader("Font")
            SettingsCard {
                SliderRow(
                    "App text size",
                    value = style.appScale,
                    range = 0.8f..1.4f,
                    steps = 11,
                    label = "${(style.appScale * 100).roundToInt()}%",
                ) { v -> Appearance.update { it.copy(appScale = (v * 20).roundToInt() / 20f) } }
                SliderRow(
                    "Letter spacing",
                    value = style.letterGap,
                    range = -0.5f..2f,
                    steps = 24,
                    label = if (style.letterGap == 0f) "Normal" else "%+.1f".format(style.letterGap),
                ) { v -> Appearance.update { it.copy(letterGap = (v * 10).roundToInt() / 10f) } }
                SliderRow(
                    "Line spacing",
                    value = style.lineGap,
                    range = 0.8f..1.8f,
                    steps = 9,
                    label = if (style.lineGap == 1f) "Normal" else "%.1f×".format(style.lineGap),
                ) { v -> Appearance.update { it.copy(lineGap = (v * 10).roundToInt() / 10f) } }
                FontPicker(onMessage = { iconToast = it })
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Colour theme")
            SettingsCard {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(BubbleThemes) { i, t -> ThemeCard(t, t.name, selected = style.theme == i) { Appearance.update { it.copy(theme = i) } } }
                    item {
                        // Your own colours.
                        ThemeCard(
                            com.glasschat.ui.theme.bubbleThemeFor(style.copy(theme = com.glasschat.ui.theme.ChatStyle.CUSTOM_THEME)),
                            "Custom",
                            selected = style.theme == com.glasschat.ui.theme.ChatStyle.CUSTOM_THEME,
                        ) { customTheme = true }
                    }
                }
                RowDivider()
                SettingRow(Icons.Outlined.Palette, Color(0xFFAF52DE), "My bubble & their bubble colours", onClick = { customTheme = true })
                RowDivider()
                Text("Appearance", style = Type.body, color = p.label, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                GlassSegmented(
                    flatBackdrop,
                    listOf("Automatic", "Day", "Night"),
                    style.mode.coerceIn(0, 2),
                    onSelect = { m -> Appearance.update { it.copy(mode = m) } },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsCard {
                SliderRow(
                    "Message corners",
                    value = style.corner,
                    range = 4f..24f,
                    steps = 19,
                    label = style.corner.roundToInt().toString(),
                ) { v -> Appearance.update { it.copy(corner = v.roundToInt().toFloat()) } }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Chat list view")
            SettingsCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListViewCard("Two lines", lines = 1, selected = style.previewLines == 1, Modifier.weight(1f)) {
                        Appearance.update { it.copy(previewLines = 1) }
                    }
                    ListViewCard("Three lines", lines = 2, selected = style.previewLines == 2, Modifier.weight(1f)) {
                        Appearance.update { it.copy(previewLines = 2) }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Tab bar")
            SettingsCard {
                SliderRow(
                    "Height",
                    value = style.tabHeight,
                    range = 48f..80f,
                    steps = 15,
                    label = style.tabHeight.roundToInt().toString(),
                ) { v -> Appearance.update { it.copy(tabHeight = (v / 2).roundToInt() * 2f) } }
                SliderRow(
                    "Width",
                    value = style.tabWidth,
                    range = 0.6f..1f,
                    steps = 7,
                    label = "${(style.tabWidth * 100).roundToInt()}%",
                ) { v -> Appearance.update { it.copy(tabWidth = (v * 20).roundToInt() / 20f) } }
                // Live preview on the same glass as the real one.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.matchParentSize().backdropSource(tabPreviewBackdrop)) {
                        Wallpaper(Modifier.fillMaxSize(), chat = Wallpapers.HOME, forList = true)
                    }
                    var sel by remember { mutableIntStateOf(1) }
                    GlassTabBar(
                        backdrop = tabPreviewBackdrop,
                        items = listOf(
                            TabItem("Updates", com.glasschat.ui.glass.TabIcons.Updates, com.glasschat.ui.glass.TabIcons.UpdatesSelected),
                            TabItem("Chats", com.glasschat.ui.glass.TabIcons.Chats, com.glasschat.ui.glass.TabIcons.ChatsSelected),
                            TabItem("Settings", Icons.Outlined.Settings, Icons.Outlined.Settings),
                        ),
                        selected = sel,
                        onSelect = { sel = it },
                        height = style.tabHeight.dp,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp).fillMaxWidth(style.tabWidth * 0.82f),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsCard {
                SettingRow(Icons.Outlined.RestartAlt, Color(0xFF8E8E93), "Reset chat settings", onClick = { Appearance.reset() })
            }
            Spacer(Modifier.height(60.dp))
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "Chat settings",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {},
        )
        if (customTheme) CustomThemeDialog(onClose = { customTheme = false })
        when (colorSheet) {
            "app" -> ColorSettingSheet(
                "App colour", "Buttons, switches and ticks", p.accent,
                onSave = { c -> Appearance.update { it.copy(appAccent = c?.let { x -> x.toArgb().toLong() and 0xFFFFFFFFL } ?: 0L) } },
                onClose = { colorSheet = null },
            )
            "bg" -> ColorSettingSheet(
                "Background colour", "Settings, Updates, Chats and other pages", p.groupedBackground,
                onSave = { c -> Appearance.update { it.copy(appBg = c?.let { x -> x.toArgb().toLong() and 0xFFFFFFFFL } ?: 0L) } },
                onClose = { colorSheet = null },
            )
            "link" -> ColorSettingSheet(
                "Link colour", "https://example.com", if (style.linkColor != 0L) Color(style.linkColor or 0xFF000000L) else p.link,
                onSave = { c -> Appearance.update { it.copy(linkColor = c?.let { x -> x.toArgb().toLong() and 0xFFFFFFFFL } ?: 0L) } },
                onClose = { colorSheet = null },
            )
        }
        com.glasschat.ui.components.Toast(iconToast) { iconToast = null }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    SectionCard { content() }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(title, style = Type.body, color = p.accentText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = p.accent,
                    activeTrackColor = p.accent,
                    inactiveTrackColor = p.fill,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(label, style = Type.body, color = p.accentText, modifier = Modifier.padding(start = 12.dp).width(44.dp))
        }
    }
}

/** Two sample messages on the current wallpaper, drawn with the real bubbles. */
@Composable
private fun ChatPreview() {
    val now = remember { System.currentTimeMillis() }
    val incoming = remember(now) {
        Message(
            chat = "preview", id = "preview-in", sender = "preview@s.whatsapp.net", senderName = "Riya",
            fromMe = false, ts = now - 15 * 60_000L, type = "text", text = "Are we still on for dinner at 8?",
            extra = null, status = 0, quotedId = "preview-q", quotedName = "You", quotedText = "Booked the table 🎉",
            quotedType = "text", quotedSender = "", edited = false, revoked = false, reactions = emptyList<Reaction>(),
        )
    }
    val outgoing = remember(now) {
        Message(
            chat = "preview", id = "preview-out", sender = "", senderName = "You",
            fromMe = true, ts = now, type = "text", text = "Yes! See you there 🙂",
            extra = null, status = Message.READ, quotedId = "", quotedName = "", quotedText = "",
            quotedType = "", quotedSender = "", edited = false, revoked = false, reactions = emptyList<Reaction>(),
        )
    }
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(200.dp)) {
        Wallpaper(Modifier.matchParentSize())
        CompositionLocalProvider(LocalBubbleInteractive provides false) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Bubble(BubbleSpec(incoming, firstInRun = true, lastInRun = true, showName = false))
                    Spacer(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Bubble(BubbleSpec(outgoing, firstInRun = true, lastInRun = true, showName = false))
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: com.glasschat.ui.theme.BubbleTheme, name: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 84.dp, height = 112.dp)
                .clip(shape)
                .border(if (selected) 3.dp else 0.5.dp, if (selected) theme.accent(p.dark) else p.separator, shape)
                .clickable(onClick = onClick),
        ) {
            Wallpaper(Modifier.matchParentSize(), spec = WallpaperSpec())
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.height(10.dp))
                // Incoming
                Box(Modifier.size(width = 44.dp, height = 16.dp).clip(RoundedCornerShape(8.dp)).background(p.bubbleIn))
                // Outgoing, in the theme's colours
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    val out = theme.out(p.dark)
                    Box(
                        Modifier
                            .size(width = 50.dp, height = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(if (out.size > 1) out else listOf(out[0], out[0]))),
                    )
                }
                Box(Modifier.size(width = 36.dp, height = 16.dp).clip(RoundedCornerShape(8.dp)).background(p.bubbleIn))
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally).size(14.dp).clip(CircleShape).background(theme.accent(p.dark)))
            }
        }
        Text(
            name,
            style = Type.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) p.label else p.secondaryLabel,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ListViewCard(label: String, lines: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) p.accent else p.separator, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(p.fill))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(50)).background(p.label.copy(alpha = 0.55f)))
                    repeat(lines) { Box(Modifier.fillMaxWidth(if (it == 0) 0.9f else 0.7f).height(5.dp).clip(RoundedCornerShape(50)).background(p.secondaryLabel.copy(alpha = 0.4f))) }
                }
            }
        }
        Text(label, style = Type.footnote.copy(fontSize = 12.5.sp), color = if (selected) p.accentText else p.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
    }
}
