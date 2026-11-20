package com.glasschat.ui.glass

import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

/** Press feedback that feels like iOS 26 glass: a soft squish. */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 1.12f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return s
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GlassCircleButton(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalPalette.current.label,
    filled: Color? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Box(
        modifier
            .size(size)
            .scale(scale)
            .glassShadow(CircleShape, 12.dp, Color.Black.copy(alpha = if (p.dark) 0.35f else 0.12f), 3.dp)
            .then(
                if (filled != null) Modifier.clip(CircleShape).background(filled).frosted(CircleShape, Color.Transparent, 0.6f)
                else Modifier.liquidGlass(backdrop, CircleShape, p.glass),
            )
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.5f))
    }
}

/** A glass capsule that holds a row of content (toolbars, input bars). */
@Composable
fun GlassCapsule(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    style: GlassStyle = LocalPalette.current.glass,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .glassShadow(shape, 16.dp, Color.Black.copy(alpha = if (LocalPalette.current.dark) 0.4f else 0.12f), 4.dp)
            .liquidGlass(backdrop, shape, style)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Small centred pill (date separators, "unread messages", system notes). */
@Composable
fun GlassPill(backdrop: Backdrop, text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .liquidGlass(backdrop, shape, p.glass.copy(tint = if (p.dark) Color(0x991C1C1E) else Color.White.copy(0.62f), refraction = 6.dp, rim = 8.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = Type.footnote.copy(fontSize = 12.5.sp), color = p.label.copy(alpha = 0.75f))
    }
}

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/**
 * The floating iOS 26 tab bar: one glass capsule, with a lighter glass
 * "lens" that slides under the selected tab.
 */
@Composable
fun GlassTabBar(
    backdrop: Backdrop,
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    // Short bars drop the labels and keep just the icons.
    val showLabels = height >= 56.dp
    val latestSelected = remember { intArrayOf(selected) }
    val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val iconSize = if (showLabels) 25.dp else (height * 0.42f).coerceIn(20.dp, 26.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .glassShadow(shape, 20.dp, Color.Black.copy(alpha = if (p.dark) 0.45f else 0.14f), 6.dp)
            .liquidGlass(backdrop, shape, p.glass)
            .padding(4.dp)
            // Slide a finger along the bar to move between tabs, like iOS.
            .pointerInput(items.size) {
                val count = items.size
                detectHorizontalDragGestures { change, _ ->
                    val i = (change.position.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                    if (i != latestSelected[0]) {
                        latestSelected[0] = i
                        currentOnSelect(i)
                    }
                }
            },
    ) {
        latestSelected[0] = selected
        val itemWidth = maxWidth / items.size
        val x by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.72f, stiffness = 420f), label = "lens")
        Box(
            Modifier
                .offset(x = x)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(shape)
                .background(p.glassSelected)
                .background(p.accent.copy(alpha = if (p.dark) 0.16f else 0.10f))
                .frosted(shape, Color.Transparent, if (p.dark) 0.35f else 0.6f),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                val isSel = i == selected
                val color = if (isSel) p.accentText else p.label
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(if (isSel) item.selectedIcon else item.icon, item.label, tint = color, modifier = Modifier.size(iconSize))
                        if (item.badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(p.accent)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    if (item.badge > 99) "99+" else item.badge.toString(),
                                    style = Type.caption.copy(fontSize = 10.sp),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    if (showLabels) {
                        Text(item.label, style = Type.tab, color = color, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Horizontal filter chips ("All", "Unread", "Groups"). */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = if (selected) p.accent.copy(alpha = if (p.dark) 0.25f else 0.16f) else p.fill
    val fg = if (selected) p.accentText else p.secondaryLabel.copy(alpha = 0.9f)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = Type.subhead.copy(fontSize = 14.5.sp), color = fg)
    }
}
