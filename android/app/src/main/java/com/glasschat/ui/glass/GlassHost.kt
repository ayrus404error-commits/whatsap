package com.glasschat.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.glasschat.ui.theme.LocalPalette

/**
 * Lets menus, sheets and alerts be real liquid glass: the whole app is drawn
 * into one [Backdrop], and pop-ups are drawn *on top of it* (not in separate
 * dialog windows, which can't see the app behind them), so their glass bends
 * and blurs whatever is underneath, like the tab bar.
 */
class GlassHost {
    val backdrop = Backdrop()
    internal val layers = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()
}

val LocalGlassHost = staticCompositionLocalOf<GlassHost?> { null }

/**
 * True for everything drawn *into* the app-wide backdrop (the app itself).
 * Pop-up glass there would sample a picture that contains itself, which
 * crashes the renderer — so it uses a frosted fill instead.
 */
val LocalInGlassSource = staticCompositionLocalOf { false }

/** Wrap the app's content once, at the top. */
@Composable
fun GlassHostRoot(content: @Composable () -> Unit) {
    val host = remember { GlassHost() }
    CompositionLocalProvider(LocalGlassHost provides host) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().backdropSource(host.backdrop)) {
                CompositionLocalProvider(LocalInGlassSource provides true) { content() }
            }
            CompositionLocalProvider(LocalInGlassSource provides false) {
                host.layers.toList().forEach { (id, layer) -> key(id) { layer() } }
            }
        }
    }
}

/**
 * Shows [content] above the whole app for as long as this is composed
 * (call it where you'd show a dialog). Falls back to drawing in place.
 */
@Composable
fun GlassOverlay(content: @Composable () -> Unit) {
    val host = LocalGlassHost.current
    if (host == null) {
        content()
        return
    }
    val latest by rememberUpdatedState(content)
    val id = remember { Any() }
    DisposableEffect(host, id) {
        val entry: Pair<Any, @Composable () -> Unit> = id to { latest() }
        host.layers.add(entry)
        onDispose { host.layers.remove(entry) }
    }
}

/** A dimmed, tap-to-close layer above the app with [content] on it; back closes it too. */
@Composable
fun GlassModal(
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.Center,
    dim: Float = 0.3f,
    content: @Composable () -> Unit,
) {
    GlassOverlay {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = alignment,
        ) {
            content()
        }
    }
}

/**
 * Glass for menus and sheets: reads the whole app behind it (through the
 * [GlassHost]); a plain frosted fill where that isn't available.
 */
fun Modifier.menuGlass(shape: Shape): Modifier = composed {
    val p = LocalPalette.current
    val host = LocalGlassHost.current
    val style = p.glassStrong.copy(
        blur = 22.dp,
        tint = if (p.dark) Color(0xFF1C1C1E).copy(alpha = 0.52f) else Color.White.copy(alpha = 0.58f),
        refraction = 16.dp,
        rim = 20.dp,
        highlight = if (p.dark) 0.45f else 0.7f,
    )
    if (host != null && !LocalInGlassSource.current) {
        this.glassShadow(shape, 26.dp, Color.Black.copy(alpha = if (p.dark) 0.45f else 0.16f), 6.dp).liquidGlass(host.backdrop, shape, style)
    } else {
        this.frosted(shape, if (p.dark) Color(0xF22C2C2E) else Color(0xF7FAFAFA), 0.6f)
    }
}
