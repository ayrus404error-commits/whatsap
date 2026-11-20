package com.glasschat.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.key
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface Route {
    val key: String

    data object Home : Route { override val key = "home" }
    data class Chat(val jid: String) : Route { override val key = "chat:$jid" }
    data class ChatInfo(val jid: String) : Route { override val key = "info:$jid" }
    data object NewChat : Route { override val key = "new" }
    data object NewGroup : Route { override val key = "new-group" }
    data object Archived : Route { override val key = "archived" }
    data object Log : Route { override val key = "log" }
    data object StatusCheck : Route { override val key = "status-check" }
    data class ChatMedia(val jid: String) : Route { override val key = "media:$jid" }

    /** Wallpaper picker for one chat, or for all chats when [jid] is "". */
    data class Wallpaper(val jid: String) : Route { override val key = "wallpaper:$jid" }
    data class ChatSearch(val jid: String) : Route { override val key = "search:$jid" }
    /** Search across all chats. */
    data object Search : Route { override val key = "global-search" }
    data object ChatSettings : Route { override val key = "chat-settings" }
    data object DeliveryLog : Route { override val key = "delivery-log" }
    data object LockedChats : Route { override val key = "locked" }
    data object BackgroundSetup : Route { override val key = "always-on" }

    /** Starred messages of one chat, or of all chats when [jid] is "". */
    data class Starred(val jid: String) : Route { override val key = "starred:$jid" }
    /** Your profile (photo, name, about). */
    data object Profile : Route { override val key = "profile" }
    /** A Settings section: "privacy", "notifications", "data", "folders", "devices", "chatlock", "status-privacy". */
    data class Settings(val section: String) : Route { override val key = "settings:$section" }
}

private val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

/**
 * A tiny navigator with the iOS push/pop feel: the new screen slides in from
 * the right while the one underneath drifts left and dims. The system back
 * gesture scrubs the same animation (predictive back).
 *
 * The back stack itself always changes immediately; animations only move
 * pixels. So an interrupted animation can never leave navigation stuck.
 */
@Stable
class Navigator(private val scope: CoroutineScope) {
    val stack = mutableStateListOf<Route>(Route.Home)

    /**
     * How far the moving screen is pushed off to the right (0 = fully shown,
     * 1 = gone). The moving screen is [leaving] while a pop animates, else the
     * top of the stack.
     */
    val offset = Animatable(0f)

    /** A screen that was just popped and is sliding away. */
    var leaving by mutableStateOf<Route?>(null)
        private set

    private var anim: Job? = null

    val top: Route get() = stack.last()

    fun push(route: Route) {
        if (stack.last() == route) return
        // Opening a screen that's already in the stack: drop back to it.
        val existing = stack.indexOf(route)
        if (existing >= 0) {
            while (stack.size > existing + 1) stack.removeAt(stack.lastIndex)
            settle()
            return
        }
        anim?.cancel()
        leaving = null
        stack.add(route)
        // Undispatched so the new screen starts off-screen in the very next frame.
        anim = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offset.snapTo(1f)
            offset.animateTo(0f, tween(420, easing = IosEase))
        }
    }

    fun pop() {
        if (stack.size <= 1) return
        anim?.cancel()
        val gone = stack.removeAt(stack.lastIndex)
        leaving = gone
        anim = scope.launch {
            try {
                offset.animateTo(1f, tween(300, easing = IosEase))
                // Both changes land in the same frame (snapTo doesn't suspend here).
                offset.snapTo(0f)
                leaving = null
            } finally {
                // If another navigation interrupted us, it has already set the
                // offset itself; just make sure this screen is gone.
                if (leaving == gone) leaving = null
            }
        }
    }

    /** Swap the top screen (e.g. New chat → the chat you picked). */
    fun replaceTop(route: Route) {
        if (stack.size > 1 && stack.getOrNull(stack.size - 2) == route) {
            stack.removeAt(stack.lastIndex)
        } else {
            stack[stack.lastIndex] = route
        }
        settle()
    }

    fun resetTo(route: Route) {
        anim?.cancel()
        leaving = null
        stack.clear()
        stack.add(Route.Home)
        if (route != Route.Home) stack.add(route)
        settle()
    }

    /** Animate the top screen back to fully shown (e.g. a cancelled back swipe). */
    fun settle() {
        anim?.cancel()
        anim = scope.launch { offset.animateTo(0f, tween(200, easing = IosEase)) }
    }

    internal suspend fun scrub(progress: Float) {
        anim?.cancel()
        leaving = null
        offset.snapTo(progress.coerceIn(0f, 1f))
    }
}

@Composable
fun rememberNavigator(): Navigator {
    val scope = rememberCoroutineScope()
    return remember { Navigator(scope) }
}

@Composable
fun NavHost(nav: Navigator, content: @Composable (Route) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val seen = remember { mutableSetOf<String>() }
    val leaving = nav.leaving
    LaunchedEffect(nav.stack.size, nav.top, leaving) {
        val live = nav.stack.map { it.key }.toSet() + listOfNotNull(leaving?.key)
        seen.filter { it !in live }.forEach { holder.removeState(it); seen.remove(it) }
        seen.addAll(live)
    }
    // Always on while there's somewhere to go back to. The gesture only moves
    // pixels; the actual pop happens in the navigator's own scope.
    PredictiveBackHandler(enabled = nav.stack.size > 1) { events ->
        try {
            events.collect { nav.scrub(it.progress * 0.9f) }
            nav.pop()
        } catch (e: CancellationException) {
            nav.settle()
            throw e
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val t = nav.offset.value
        // While popping, the stack top is already the screen underneath.
        val moving: Route = leaving ?: nav.stack.last()
        val below: Route? = if (leaving != null) nav.stack.last() else nav.stack.getOrNull(nav.stack.size - 2)
        // Both screens are drawn in one keyed list, so a screen keeps its
        // composition (no reload, no blink) when it moves between "underneath"
        // and "on top" — e.g. coming back to a chat from its profile.
        val realBack = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val deadBack = remember(lifecycleOwner) {
            object : androidx.activity.OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
                override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleOwner.lifecycle
            }
        }
        val layers = buildList {
            // The screen underneath stays alive (just hidden) so going back
            // shows it instantly, exactly as it was.
            if (below != null) add(below to true)
            if (leaving == null || t < 1f) add(moving to false)
        }
        layers.forEach { (route, isBelow) ->
            key(route.key) {
                // Same structure in both roles (only the modifier changes), so
                // the screen's content is moved, not rebuilt.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (isBelow) -width * 0.3f * (1f - t) else width * t
                            alpha = if (isBelow && t <= 0f) 0f else 1f
                        }
                        .drawBehind {
                            if (!isBelow && t > 0f) {
                                // Soft shadow on the left edge of the sliding screen.
                                val w = 18.dp.toPx()
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)), startX = -w, endX = 0f),
                                    topLeft = Offset(-w, 0f),
                                    size = Size(w, size.height),
                                )
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (isBelow) drawRect(Color.Black.copy(alpha = 0.14f * (1f - t)))
                        }
                        // The screen underneath never takes taps.
                        .pointerInput(isBelow) {
                            if (isBelow) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    // The hidden screen's back handlers go to a dead dispatcher,
                    // so back always acts on the screen you can see (e.g. a
                    // Settings page goes back to Settings, not to Chats).
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides (if (isBelow) deadBack else realBack!!),
                    ) {
                        holder.SaveableStateProvider(route.key) { content(route) }
                    }
                }
            }
        }
    }
}
