package com.glasschat.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.glass.menuGlass
import com.glasschat.ui.theme.Type

/** iOS-style alert with a frosted card. */
@Composable
fun GlassAlert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    buttons: List<Pair<String, () -> Unit>> = listOf("OK" to onDismiss),
    destructiveIndex: Int = -1,
) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss, dim = 0.25f) {
        val scale = remember { Animatable(1.15f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = Type.title, color = p.label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp))
            Text(
                message,
                style = Type.footnote.copy(fontSize = 13.5.sp),
                color = p.label.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
            )
            HorizontalDivider(color = p.separator, thickness = 0.5.dp)
            if (buttons.size == 2) {
                Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    buttons.forEachIndexed { i, (label, action) ->
                        // Full height of the row, so both labels sit in the middle of their half.
                        AlertButton(label, i == destructiveIndex, bold = i == 1, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = action)
                        if (i == 0) Box(Modifier.size(0.5.dp, 50.dp).background(p.separator))
                    }
                }
            } else {
                buttons.forEachIndexed { i, (label, action) ->
                    if (i > 0) HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                    AlertButton(label, i == destructiveIndex, bold = i == buttons.lastIndex, modifier = Modifier.fillMaxWidth().height(50.dp), onClick = action)
                }
            }
        }
    }
}

@Composable
private fun AlertButton(label: String, destructive: Boolean, bold: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = Type.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            color = if (destructive) p.danger else p.link,
        )
    }
}

data class SheetAction(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * iOS 26 sheet (like WhatsApp's "Delete message?"): a glass sheet from the
 * bottom with the title, a round × to close, and the choices in one grouped
 * card — destructive ones in red.
 */
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss, alignment = Alignment.BottomCenter, dim = 0.32f) {
        val slide = remember { Animatable(500f) }
        LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f)) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 34.dp, bottomEnd = 34.dp)
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { translationY = slide.value }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .menuGlass(sheet)
                    .clip(sheet)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
            ) {
                // Header: title in the middle, × on the right.
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    if (title != null) {
                        val short = title.length <= 34
                        Text(
                            title,
                            style = if (short) Type.rowTitle else Type.footnote,
                            color = if (short) p.label else p.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 52.dp, vertical = 6.dp),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(p.label.copy(alpha = if (p.dark) 0.14f else 0.08f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Close", tint = p.label, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                val card = RoundedCornerShape(24.dp)
                Column(Modifier.fillMaxWidth().clip(card).background(p.label.copy(alpha = if (p.dark) 0.08f else 0.05f))) {
                    actions.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = p.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = if (a.icon != null) 56.dp else 20.dp, end = 20.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { onDismiss(); a.onClick() }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val c = if (a.destructive) p.danger else p.label
                            if (a.icon != null) {
                                Icon(a.icon, null, tint = c, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(14.dp))
                            }
                            Text(a.label, style = Type.body.copy(fontSize = 18.sp), color = c)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Small glass menu that drops from a button (like "Select chats / Read all"):
 * icon on the left, label next to it.
 */
@Composable
fun PopMenu(actions: List<SheetAction>, onDismiss: () -> Unit, alignment: Alignment = Alignment.TopStart, offsetTop: androidx.compose.ui.unit.Dp = 64.dp) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss, alignment = alignment, dim = 0.08f) {
        val scale = remember { Animatable(0.85f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 12.dp, top = offsetTop)
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (alignment == Alignment.TopEnd) 1f else 0f, 0f)
                }
                .widthIn(min = 210.dp, max = 280.dp)
                .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 6.dp),
        ) {
            actions.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable { onDismiss(); a.onClick() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val c = if (a.destructive) p.danger else p.label
                    if (a.icon != null) {
                        Icon(a.icon, null, tint = c, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(a.label, style = Type.body.copy(fontSize = 17.sp), color = c, maxLines = 1)
                }
            }
        }
    }
}

/** iOS 26 "scroll edge effect": content fades softly under the top controls. */
@Composable
fun TopEdgeFade(extra: androidx.compose.ui.unit.Dp = 56.dp, alpha: Float = 1f) {
    val p = LocalPalette.current
    if (alpha <= 0.01f) return
    Column(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(p.background.copy(alpha = 0.82f * alpha)),
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(extra)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(p.background.copy(alpha = 0.82f * alpha), p.background.copy(alpha = 0f))))
                },
        )
    }
}

@Composable
fun BottomEdgeFade(height: androidx.compose.ui.unit.Dp = 90.dp, color: Color = LocalPalette.current.background) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(height)
                .drawBehind { drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.7f)))) },
        )
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(color.copy(alpha = 0.7f)))
    }
}

/** Launch screen. */
@Composable
fun Splash() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(p.background), contentAlignment = Alignment.Center) {
        AppMark(96.dp)
    }
}

/** The app's mark: a glossy glass speech bubble on a green gradient tile. */
@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF34E089), Color(0xFF0F9D58), Color(0xFF0B7A6E)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            com.glasschat.ui.glass.LineIcons.Chat,
            null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(size * 0.56f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), endY = size.value * 1.4f)),
        )
    }
}

/** Little floating glass notice that fades away ("Coming in phase 2"). */
@Composable
fun Toast(message: String?, onDone: () -> Unit) {
    if (message == null) return
    val p = LocalPalette.current
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2200)
        onDone()
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
        val shape = RoundedCornerShape(50)
        Text(
            message,
            style = Type.subhead,
            color = p.label,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(shape)
                .frosted(shape, if (p.dark) Color(0xEE2C2C2E) else Color(0xF2FFFFFF), 0.7f)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.cell),
        content = content,
    )
}
