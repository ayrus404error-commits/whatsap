package com.glasschat.ui.screens

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material.icons.outlined.Visibility
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import com.glasschat.media.Contacts
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import com.glasschat.engine.Engine
import com.glasschat.media.AnimatedImage
import com.glasschat.media.MediaState
import com.glasschat.media.VoicePlayer
import com.glasschat.media.rememberImage
import com.glasschat.media.rememberMedia
import com.glasschat.media.waveformBars
import com.glasschat.service.Prefs
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Message
import com.glasschat.engine.previewFor
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.Ticks
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.LocalChatStyle
import com.glasschat.ui.theme.ChatStyle
import com.glasschat.ui.theme.BubbleThemes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import com.glasschat.ui.theme.Type
import com.glasschat.ui.theme.senderColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Immutable
data class BubbleSpec(
    val msg: Message,
    val firstInRun: Boolean,
    val lastInRun: Boolean,
    val showName: Boolean,
    /** Photos/videos sent together, shown as one grid (oldest first); msg is the newest. */
    val album: List<Message> = emptyList(),
)

/** Opens a photo/video from inside an album grid. */
val LocalMediaTap = androidx.compose.runtime.staticCompositionLocalOf<((Message) -> Unit)?> { null }

private val Gutter = 7.dp
/** Bubble corner radius, from Chat settings. */
private val Radius: Dp
    @Composable get() = LocalChatStyle.current.corner.dp

/** One message line, with swipe-right-to-reply. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    spec: BubbleSpec,
    highlighted: Boolean,
    onReply: (Message) -> Unit,
    onLongPress: (Message) -> Unit,
    onQuoteClick: (String) -> Unit,
    onRetry: (Message) -> Unit,
    onMediaTap: (Message) -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelect: (Message) -> Unit = {},
) {
    val m = spec.msg
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Plain state while the finger moves (applied immediately, in order), then
    // one spring back to rest — it always ends at 0, even if a reply opens.
    var drag by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val trigger = with(density) { 56.dp.toPx() }
    val armed = remember { booleanArrayOf(false) }
    var settle by remember { mutableIntStateOf(0) }
    LaunchedEffect(settle) {
        if (settle == 0) return@LaunchedEffect
        androidx.compose.animation.core.animate(drag, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { v, _ -> drag = v }
        drag = 0f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted || selected) p.accent.copy(alpha = if (selected) 0.14f else 0.18f) else Color.Transparent)
            .then(
                if (selecting) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(m) }
                else Modifier,
            )
            .padding(top = if (spec.firstInRun) 7.dp else 1.5.dp, bottom = if (m.reactions.isNotEmpty()) 15.dp else 0.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = !selecting && !m.revoked && m.type != "waiting",
                state = rememberDraggableState { delta ->
                    val next = (drag + delta).coerceIn(0f, trigger * 1.4f)
                    drag = next
                    if (next >= trigger && !armed[0]) {
                        armed[0] = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (next < trigger) {
                        armed[0] = false
                    }
                },
                onDragStopped = {
                    val reply = drag >= trigger
                    armed[0] = false
                    settle++
                    if (reply) onReply(m)
                },
            ),
    ) {
        // Reply arrow that fades in while swiping.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(30.dp)
                .alpha((drag / trigger).coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(p.pill.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = p.label, modifier = Modifier.size(18.dp))
        }
        if (selecting) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) { ChoiceMark(selected, square = false) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drag }
                .padding(start = if (selecting) 44.dp else 8.dp, end = 8.dp),
            horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        ) {
            if (m.fromMe) Spacer(Modifier.width(48.dp))
            CompositionLocalProvider(LocalMediaTap provides if (selecting) null else onMediaTap) {
            Bubble(
                spec,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        when {
                            selecting -> onSelect(m)
                            m.status == Message.FAILED -> onRetry(m)
                            m.type in MediaTypes -> onMediaTap(m)
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selecting) onSelect(m) else onLongPress(m)
                    },
                ),
                onQuoteClick = onQuoteClick,
            )
            }
            if (!m.fromMe) Spacer(Modifier.width(48.dp))
        }
    }
}

val MediaTypes = setOf("image", "video", "gif", "voice", "audio", "document", "sticker", "location", "contact")

/** The bubble itself (also reused, non-interactive, in the long-press menu). */
@Composable
fun Bubble(spec: BubbleSpec, modifier: Modifier = Modifier, onQuoteClick: (String) -> Unit = {}) {
    val m = spec.msg
    val p = LocalPalette.current
    val style = LocalChatStyle.current
    val bare = !m.revoked && (m.type == "sticker" || (m.type == "video" && m.extra?.optBoolean("round") == true) || (m.type == "text" && m.quotedId.isEmpty() && isEmojiOnly(m.text)))
    // Your own bubbles take the chosen colour theme (one colour or a gradient).
    val colors = if (m.fromMe) {
        if (style.theme == 0) listOf(p.bubbleOut) else com.glasschat.ui.theme.bubbleThemeFor(style).out(p.dark)
    } else {
        if (style.customIn != 0L) listOf(Color(style.customIn or 0xFF000000L)) else listOf(p.bubbleIn)
    }
    val tail = spec.lastInRun && !bare
    val radius = Radius
    // Message text size: scale everything written inside the bubble.
    val density = LocalDensity.current
    val scaled = remember(density, style.textSize) {
        Density(density.density, density.fontScale * (style.textSize / ChatStyle.DEFAULT_TEXT))
    }

    Box(contentAlignment = if (m.fromMe) Alignment.BottomEnd else Alignment.BottomStart) {
        Box(
            modifier
                .widthIn(max = 330.dp)
                .then(
                    if (bare) Modifier
                    else Modifier.drawWithCache {
                        // Built once per size, not on every frame.
                        val shape = bubblePath(size, m.fromMe, tail, Gutter.toPx(), radius.toPx())
                        val brush = if (colors.size > 1) {
                            Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height))
                        } else {
                            SolidColor(colors[0])
                        }
                        onDrawBehind { drawPath(shape, brush) }
                    },
                )
                .padding(start = if (!m.fromMe) Gutter else 0.dp, end = if (m.fromMe) Gutter else 0.dp),
        ) {
            // Their bubble in your own colour: keep the text readable on it.
            val pal = if (!m.fromMe && style.customIn != 0L) {
                val light = colors[0].luminance() > 0.45f
                p.copy(
                    label = if (light) Color.Black else Color.White,
                    secondaryLabel = if (light) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f),
                    bubbleMeta = if (light) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f),
                )
            } else {
                p
            }
            CompositionLocalProvider(LocalDensity provides scaled, LocalPalette provides pal) {
                if (bare) BareContent(m) else BubbleContent(spec, onQuoteClick)
            }
        }
        if (m.reactions.isNotEmpty()) {
            ReactionPill(
                m,
                Modifier
                    .align(if (m.fromMe) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(x = if (m.fromMe) (-14).dp else 14.dp, y = 17.dp),
            )
        }
    }
}

@Composable
private fun BubbleContent(spec: BubbleSpec, onQuoteClick: (String) -> Unit) {
    val m = spec.msg
    val p = LocalPalette.current
    val metaColor = if (m.fromMe) p.bubbleMetaOut else p.bubbleMeta
    val hasMediaFrame = spec.album.size > 1 || (!m.revoked && (m.type == "image" || m.type == "video" || m.type == "gif" || (m.type == "location" && m.extra?.has("thumb") == true))
        && m.extra?.optBoolean("viewOnce") != true && m.extra?.optBoolean("round") != true)
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .padding(if (hasMediaFrame) 3.dp else 0.dp),
    ) {
        if (spec.showName) {
            Text(
                m.senderName.ifBlank { "Unknown" },
                style = Type.footnote.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                color = senderColor(m.sender),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = if (hasMediaFrame) 3.dp else 6.dp, bottom = if (hasMediaFrame) 4.dp else 0.dp),
            )
        }
        if (spec.album.size > 1) {
            AlbumGrid(spec.album)
            return@Column
        }
        if (m.forwarded && !m.revoked) {
            Row(
                Modifier.padding(start = 9.dp, end = 10.dp, top = if (spec.showName) 1.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Forward, null, tint = p.secondaryLabel, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("Forwarded", style = Type.caption.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic), color = p.secondaryLabel)
            }
        }
        if (m.quotedId.isNotEmpty() && !m.revoked) {
            QuoteBlock(m, Modifier.padding(start = if (hasMediaFrame) 0.dp else 5.dp, end = if (hasMediaFrame) 0.dp else 5.dp, top = 5.dp).clickable { onQuoteClick(m.quotedId) })
        }
        val meta: @Composable () -> Unit = { Meta(m, metaColor) }
        when {
            // "Keep deleted messages": the sender deleted it, but you can still read it.
            m.revoked && !m.fromMe && (m.text.isNotBlank() || m.local.isNotEmpty()) -> Column {
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Block, null, tint = p.danger.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Deleted by sender", style = Type.caption.copy(fontStyle = FontStyle.Italic), color = p.danger.copy(alpha = 0.8f))
                }
                when (m.type) {
                    "image", "video", "gif" -> MediaFrame(m, meta)
                    else -> TextBody(m.text.ifBlank { previewFor(m.type, "", false) }, meta)
                }
            }
            m.revoked -> SystemLine(Icons.Outlined.Block, if (m.fromMe) "You deleted this message" else "This message was deleted", meta)
            m.type == "viewonce" -> SystemLine(
                Icons.Outlined.Visibility,
                "View once ${m.extra?.optString("kind")?.ifBlank { null } ?: "photo"} · WhatsApp only lets you open it on your phone",
                meta,
            )
            m.type == "text" && m.extra?.has("link") == true && (m.extra?.has("linkTitle") == true || m.extra?.has("thumb") == true) -> Column(Modifier.widthIn(max = 320.dp)) {
                LinkPreviewCard(m)
                TextBody(m.text, meta, m.mentions)
            }
            m.type == "waiting" -> SystemLine(Icons.Outlined.Schedule, "Waiting for this message. This may take a while.", meta)
            m.type == "text" && m.extra?.has("buttons") == true -> BusinessBody(m, meta)
            m.type == "text" -> TextBody(m.text, meta, m.mentions)
            (m.type == "image" || m.type == "video") && m.extra?.optBoolean("viewOnce") == true -> ViewOnceBody(m, meta)
            m.type == "video" && m.extra?.optBoolean("round") == true -> RoundVideoBody(m)
            m.type == "image" || m.type == "video" || m.type == "gif" -> MediaFrame(m, meta)
            m.type == "voice" || m.type == "audio" -> VoiceBody(m, meta)
            m.type == "document" -> DocumentBody(m, meta)
            m.type == "location" -> LocationBody(m, meta)
            m.type == "contact" -> ContactBody(m, meta)
            m.type == "poll" -> PollBody(m, meta)
            m.type == "event" -> IconBody(Icons.Outlined.CalendarMonth, m.text.ifBlank { "Event" }, "Event", meta)
            m.type == "invite" -> IconBody(Icons.Outlined.Groups, m.text.ifBlank { "Group" }, "Group invite", meta)
            m.type == "call" -> IconBody(Icons.Outlined.Call, "Voice call", "Answer calls on your phone", meta)
            else -> SystemLine(Icons.Outlined.EmojiEmotions, "This message type isn't supported yet. Open it on your phone.", meta)
        }
    }
}

@Composable
private fun Meta(m: Message, color: Color, onDark: Boolean = false) {
    val ctx = LocalContext.current
    val c = if (onDark) Color.White.copy(alpha = 0.92f) else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (m.starred && !m.revoked) Icon(Icons.Outlined.Star, "Starred", tint = c, modifier = Modifier.padding(end = 3.dp).size(12.dp))
        if (m.kept && !m.revoked) Icon(Icons.Outlined.Bookmark, "Kept", tint = c, modifier = Modifier.padding(end = 3.dp).size(12.dp))
        if (m.edited && !m.revoked) Text("Edited ", style = Type.caption.copy(fontSize = 11.5.sp), color = c)
        Text(Fmt.time(ctx, m.ts), style = Type.caption.copy(fontSize = 11.5.sp), color = c)
        if (m.fromMe && !m.revoked) {
            Spacer(Modifier.width(3.dp))
            Ticks(m.status, color = c, size = 15.dp)
        }
    }
}

@Composable
private fun TextBody(text: String, meta: @Composable () -> Unit, mentions: List<com.glasschat.engine.Mention> = emptyList()) {
    val p = LocalPalette.current
    // Links in messages: your chosen link colour (Chat settings), else the theme's.
    val linkStyle = LocalChatStyle.current.linkColor
    val linkColor = if (linkStyle != 0L) Color(linkStyle or 0xFF000000L) else p.link
    val accent = p.accent
    val styled = remember(text, linkColor, mentions, accent) { highlightMentions(linkify(text, linkColor), mentions, accent) }
    TextWithMeta(styled, meta, Modifier.padding(start = 10.dp, end = 9.dp, top = 6.dp, bottom = 5.dp))
}

/** Title, description and picture of the first link in a message. */
@Composable
private fun LinkPreviewCard(m: Message) {
    val p = LocalPalette.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val thumb = remember(m.id) { messageThumb(m) }
    val link = m.extra?.optString("link").orEmpty()
    val host = remember(link) { runCatching { android.net.Uri.parse(if (link.startsWith("http")) link else "https://$link").host?.removePrefix("www.") }.getOrNull() ?: "" }
    Column(
        Modifier
            .padding(start = 4.dp, end = 4.dp, top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.label.copy(alpha = 0.07f))
            .clickable { runCatching { uri.openUri(if (link.startsWith("http")) link else "https://$link") } },
    ) {
        if (thumb != null) {
            Box {
                androidx.compose.foundation.Image(
                    thumb, null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                if (m.extra?.optBoolean("linkVideo") == true) {
                    Box(Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            m.extra?.optString("linkTitle")?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            m.extra?.optString("linkDesc")?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.footnote, color = p.secondaryLabel, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (host.isNotEmpty()) Text(host, style = Type.caption, color = p.secondaryLabel)
        }
    }
}

/** A business message (bill, OTP, offer…): optional picture, text, and its buttons. */
@Composable
private fun BusinessBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val thumb = remember(m.id) { messageThumb(m) }
    val buttons = remember(m.id) {
        val a = m.extra?.optJSONArray("buttons")
        List(a?.length() ?: 0) { i -> a!!.getJSONObject(i).let { it.optString("text") to it.optString("url") } }.filter { it.first.isNotBlank() }
    }
    Column(Modifier.widthIn(min = 200.dp)) {
        if (thumb != null) {
            androidx.compose.foundation.Image(
                thumb, null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(3.dp).clip(RoundedCornerShape(14.dp)),
            )
        }
        TextBody(m.text, meta, m.mentions)
        buttons.forEach { (label, url) ->
            androidx.compose.material3.HorizontalDivider(color = p.separator, thickness = 0.5.dp)
            Text(
                label,
                style = Type.body.copy(fontWeight = FontWeight.Medium),
                color = p.link,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = url.isNotBlank()) { runCatching { uri.openUri(url) } }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SystemLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.padding(start = 10.dp, end = 9.dp, top = 7.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Icon(icon, null, tint = p.secondaryLabel, modifier = Modifier.size(16.dp).padding(bottom = 1.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = Type.bubble.copy(fontStyle = FontStyle.Italic, fontSize = 15.sp), color = p.secondaryLabel, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        meta()
    }
}

@Composable
private fun QuoteBlock(m: Message, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val nameColor = if (m.quotedName == "You") p.accentText else senderColor(m.quotedSender)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(p.quoteFill),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(nameColor))
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Text(m.quotedName.ifBlank { "Message" }, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = nameColor, maxLines = 1)
            Text(
                previewFor(m.quotedType, m.quotedText),
                style = Type.footnote.copy(fontSize = 14.sp),
                color = p.label.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Several photos/videos sent together, as one grid like WhatsApp: two side
 * by side, three as two plus one, four or more as 2×2 with "+N" on the last.
 */
@Composable
private fun AlbumGrid(items: List<Message>) {
    val p = LocalPalette.current
    val gap = 3.dp
    val width = 250.dp
    val half = (width - gap) / 2
    val shown = items.take(4)
    val more = items.size - shown.size
    val last = items.last()
    val caption = items.firstOrNull { it.text.isNotBlank() }
    Column(Modifier.width(width)) {
        val rows: List<List<Message>> = when (shown.size) {
            2 -> listOf(shown)
            3 -> listOf(shown.take(2), shown.drop(2))
            else -> listOf(shown.take(2), shown.drop(2))
        }
        rows.forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { i, m ->
                    val isLastTile = r == rows.lastIndex && i == row.lastIndex
                    AlbumTile(
                        m,
                        Modifier.size(if (row.size == 1) width else half, half),
                        plus = if (isLastTile && more > 0) more else 0,
                        showMeta = isLastTile && caption == null,
                        metaOf = last,
                    )
                }
            }
        }
        if (caption != null) {
            val metaColor = if (last.fromMe) p.bubbleMetaOut else p.bubbleMeta
            Box(Modifier.width(width)) { TextBody(caption.text, { Meta(last, metaColor) }, caption.mentions) }
        }
    }
}

@Composable
private fun AlbumTile(m: Message, modifier: Modifier, plus: Int, showMeta: Boolean, metaOf: Message) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val tap = LocalMediaTap.current
    val isImage = m.type == "image"
    val media = rememberMedia(m.chat, m.id, m.local, auto = Prefs.shouldAutoDownload(ctx, if (isImage) "photo" else "video"))
    val full = if (isImage) rememberImage(media.path, 500) else null
    val thumb = remember(m.id) { messageThumb(m) }
    Box(
        modifier
            .clip(RoundedCornerShape(Radius - 3.dp))
            .background(p.fill)
            .then(if (tap != null) Modifier.pointerInput(m.id) { detectTapGestures(onTap = { tap(m) }) } else Modifier),
    ) {
        if (full != null) {
            Image(full, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (thumb != null) {
            Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(if (isImage) 3.dp else 1.dp))
        }
        if (plus > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("+$plus", style = Type.largeTitle.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        } else if (!isImage || full == null) {
            Box(
                Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    media.loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    !isImage -> Icon(Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                    else -> Icon(Icons.Rounded.Download, "Download", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (showMeta) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Meta(metaOf, Color.White, onDark = true) }
        }
    }
}

/** The small preview picture WhatsApp embeds in photo/video/document messages. */
fun messageThumb(m: Message): ImageBitmap? {
    val b64 = m.extra?.optString("thumb")?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** A "view once" photo/video: a ① chip instead of the picture, like WhatsApp. */
@Composable
private fun ViewOnceBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val opened = !m.fromMe && Prefs.flag(ctx, viewOnceKey(m), false)
    val what = if (m.type == "video") "Video" else "Photo"
    Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).border(1.6.dp, if (opened) p.secondaryLabel else p.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("1", style = Type.footnote.copy(fontWeight = FontWeight.Bold), color = if (opened) p.secondaryLabel else p.accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                m.fromMe -> "$what · view once"
                opened -> "Opened"
                else -> what
            },
            style = Type.body.copy(fontWeight = FontWeight.Medium),
            color = if (opened) p.secondaryLabel else p.label,
        )
        Spacer(Modifier.width(10.dp))
        meta()
    }
}

/** Key for remembering that a view-once message was opened here. */
fun viewOnceKey(m: Message) = "vo:${m.chat}:${m.id}"

/** Which instant (round) video is playing inline right now. */
object RoundPlayer {
    var current by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun toggle(m: Message) {
        val k = MediaState.key(m.chat, m.id)
        current = if (current == k) null else k
    }
}

/** An instant (round) video message: plays right in the circle when tapped. */
@Composable
private fun RoundVideoBody(m: Message) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val key = MediaState.key(m.chat, m.id)
    val playing = RoundPlayer.current == key
    val media = rememberMedia(m.chat, m.id, m.local, auto = playing || Prefs.shouldAutoDownload(ctx, "video"))
    val thumb = remember(m.id) { messageThumb(m) }
    val secs = m.extra?.optInt("seconds") ?: 0
    var progress by remember(key) { mutableStateOf(0f) }
    Box(Modifier.padding(4.dp).size(210.dp).clip(CircleShape).background(p.fill)) {
        if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val path = media.path
        if (playing && path != null) {
            RoundVideoSurface(path, onProgress = { progress = it }, onEnd = { if (RoundPlayer.current == key) RoundPlayer.current = null })
        } else {
            Box(
                Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (media.loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                } else {
                    Icon(Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (playing) {
            // Progress ring around the circle, like WhatsApp.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = 4.dp.toPx()
                drawArc(
                    p.accent, -90f, 360f * progress, false,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (secs > 0) {
                Text(Fmt.duration(secs), style = Type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
                Spacer(Modifier.width(6.dp))
            }
            Meta(m, Color.White, onDark = true)
        }
    }
}

/** Plays a video file in a TextureView (which, unlike VideoView, can be clipped to a circle). */
@Composable
private fun RoundVideoSurface(path: String, onProgress: (Float) -> Unit, onEnd: () -> Unit) {
    val end by androidx.compose.runtime.rememberUpdatedState(onEnd)
    val prog by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val player = remember(path) { android.media.MediaPlayer() }
    androidx.compose.runtime.DisposableEffect(path) {
        onDispose { runCatching { player.stop() }; player.release() }
    }
    androidx.compose.runtime.LaunchedEffect(path) {
        while (true) {
            runCatching { if (player.duration > 0) prog(player.currentPosition.toFloat() / player.duration) }
            kotlinx.coroutines.delay(100)
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            android.view.TextureView(c).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            player.setSurface(android.view.Surface(st))
                            player.setDataSource(path)
                            player.setOnVideoSizeChangedListener { _, vw, vh ->
                                // Centre-crop the video into the square.
                                if (vw > 0 && vh > 0) {
                                    val s = maxOf(w.toFloat() / vw, h.toFloat() / vh)
                                    val m = android.graphics.Matrix()
                                    m.setScale(vw * s / w, vh * s / h, w / 2f, h / 2f)
                                    setTransform(m)
                                }
                            }
                            player.setOnCompletionListener { end() }
                            player.setOnErrorListener { _, _, _ -> end(); true }
                            player.prepare()
                            player.start()
                        }.onFailure { end() }
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MediaFrame(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val isImage = m.type == "image"
    val media = rememberMedia(m.chat, m.id, m.local, auto = Prefs.shouldAutoDownload(ctx, if (isImage) "photo" else "video"))
    val full = if (isImage) rememberImage(media.path, 900) else null
    val thumb = remember(m.id) { messageThumb(m) }
    val w = m.extra?.optInt("w") ?: 0
    val h = m.extra?.optInt("h") ?: 0
    val ratio = if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.6f, 1.8f) else 1f
    val width = 250.dp
    val height = (width / ratio).coerceIn(140.dp, 300.dp)
    Column {
        Box(Modifier.size(width, height).clip(RoundedCornerShape(Radius - 3.dp)).background(p.fill)) {
            if (full != null) {
                Image(full, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (thumb != null) {
                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(if (isImage) 3.dp else 1.dp))
            }
            if (!isImage || full == null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        media.loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                        !isImage -> Icon(Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                        media.error != null -> Icon(Icons.Rounded.Refresh, "Retry", tint = Color.White, modifier = Modifier.size(26.dp))
                        else -> Icon(Icons.Rounded.Download, "Download", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
            if (m.type != "image") {
                val secs = m.extra?.optInt("seconds") ?: 0
                Text(
                    if (m.type == "gif") "GIF" else if (secs > 0) Fmt.duration(secs) else "Video",
                    style = Type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (m.text.isBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.45f))))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Meta(m, Color.White, onDark = true)
                }
            }
        }
        if (m.text.isNotBlank()) {
            Box(Modifier.width(width)) { TextBody(m.text, meta, m.mentions) }
        }
    }
}

@Composable
private fun VoiceBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val media = rememberMedia(m.chat, m.id, m.local, auto = Prefs.shouldAutoDownload(ctx, "audio"))
    val player by VoicePlayer.state.collectAsState()
    val key = MediaState.key(m.chat, m.id)
    val current = player.key == key
    val playing = current && player.playing
    val progress = if (current && player.duration > 0) player.position.toFloat() / player.duration else 0f
    val secs = m.extra?.optInt("seconds") ?: 0
    val bars = remember(m.id) {
        waveformBars(m.extra?.optString("waveform"), 28) ?: run {
            val r = java.util.Random(m.id.hashCode().toLong())
            List(28) { 0.25f + r.nextFloat() * 0.75f }
        }
    }
    val play: (String) -> Unit = { path ->
        if (VoicePlayer.toggle(key, path) && !m.fromMe && m.type == "voice") Engine.markPlayed(m.chat, m.id)
    }
    Column(Modifier.padding(start = 8.dp, end = 9.dp, top = 8.dp, bottom = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (m.type == "voice") p.accent else p.secondaryLabel), contentAlignment = Alignment.Center) {
                Icon(if (m.type == "voice") Icons.Rounded.Mic else Icons.Outlined.Headphones, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable {
                        val path = media.path
                        if (path != null) {
                            play(path)
                        } else {
                            scope.launch { MediaState.fetch(m.chat, m.id).onSuccess { play(it) } }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    media.loading -> CircularProgressIndicator(color = p.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    playing -> Icon(Icons.Rounded.Pause, "Pause", tint = p.secondaryLabel, modifier = Modifier.size(28.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, "Play", tint = p.secondaryLabel, modifier = Modifier.size(28.dp))
                }
            }
            Row(
                Modifier
                    .height(26.dp)
                    .width(150.dp)
                    .pointerInput(key, current) {
                        detectTapGestures(onTap = { off -> if (current) VoicePlayer.seek(key, off.x / size.width) })
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bars.forEachIndexed { i, b ->
                    val played = current && i < progress * bars.size
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight(b)
                            .clip(RoundedCornerShape(50))
                            .background(if (played) p.accent else p.secondaryLabel.copy(alpha = 0.55f)),
                    )
                }
            }
        }
        Row(Modifier.width(IntrinsicSize.Max).padding(top = 2.dp)) {
            Text(
                media.error ?: Fmt.duration(if (current) player.position / 1000 else secs),
                style = Type.caption.copy(fontSize = 11.5.sp),
                color = if (media.error != null) p.danger else p.secondaryLabel,
                maxLines = 1,
                modifier = Modifier.padding(start = 44.dp).weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            meta()
        }
    }
}

@Composable
private fun DocumentBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val media = rememberMedia(m.chat, m.id, m.local, auto = Prefs.shouldAutoDownload(ctx, "doc"))
    val name = m.extra?.optString("fileName")?.ifBlank { null } ?: "Document"
    val size = m.extra?.optLong("size") ?: 0L
    val pages = m.extra?.optInt("pages") ?: 0
    val ext = name.substringAfterLast('.', "").uppercase().take(4)
    val details = listOfNotNull(
        if (pages > 0) "$pages page${if (pages == 1) "" else "s"}" else null,
        if (size > 0) Fmt.size(size) else null,
        ext.ifBlank { null },
    ).joinToString(" · ")
    Column(Modifier.padding(5.dp)) {
        Row(
            Modifier.widthIn(min = 220.dp).clip(RoundedCornerShape(12.dp)).background(p.quoteFill).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 34.dp, height = 42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                if (media.loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Outlined.Description, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(name, style = Type.subhead, color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (media.error != null) {
                    Text(media.error, style = Type.caption.copy(fontSize = 12.sp), color = p.danger, maxLines = 2)
                } else if (details.isNotEmpty()) {
                    Text(details, style = Type.caption.copy(fontSize = 12.sp), color = p.secondaryLabel)
                }
            }
            if (media.path == null && !media.loading) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.FileDownload, "Download", tint = p.secondaryLabel, modifier = Modifier.size(22.dp))
            }
        }
        if (m.text.isNotBlank()) TextBody(m.text, meta, m.mentions) else Row(Modifier.align(Alignment.End).padding(top = 3.dp, end = 4.dp)) { meta() }
    }
}

@Composable
private fun LocationBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val thumb = remember(m.id) { messageThumb(m) }
    Column {
        if (thumb != null) {
            Box(Modifier.size(250.dp, 140.dp).clip(RoundedCornerShape(Radius - 3.dp))) {
                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFFFF3B30), modifier = Modifier.align(Alignment.Center).size(34.dp))
            }
        }
        val live = m.extra?.optBoolean("live") == true
        IconBody(Icons.Outlined.LocationOn, m.text.ifBlank { if (live) "Live location" else "Location" }, if (live) "Live location" else "Shared location", meta, showIcon = thumb == null)
    }
}

@Composable
private fun IconBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    meta: @Composable () -> Unit,
    showIcon: Boolean = true,
) {
    val p = LocalPalette.current
    Column(Modifier.padding(start = 10.dp, end = 9.dp, top = 8.dp, bottom = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(p.quoteFill), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = p.secondaryLabel, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = Type.bubble.copy(fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = Type.caption.copy(fontSize = 12.sp), color = p.secondaryLabel)
            }
            Spacer(Modifier.width(12.dp))
        }
        Row(Modifier.align(Alignment.End).padding(top = 2.dp)) { meta() }
    }
}

/** False where a bubble is only a picture of itself (the long-press menu). */
val LocalBubbleInteractive = staticCompositionLocalOf { true }

/** A poll you can vote in: tap an option to vote, tap again to take it back. */
@Composable
private fun PollBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val interactive = LocalBubbleInteractive.current
    val poll = m.poll
    val names: List<String> = poll?.options?.map { it.name }
        ?: m.extra?.optJSONArray("options")?.let { a -> List(a.length()) { a.optString(it) } }
        ?: emptyList()
    val multi = poll?.multi ?: ((m.extra?.optInt("selectable", 1) ?: 1) != 1)
    val mine: Set<String> = poll?.options?.filter { it.mine }?.map { it.name }?.toSet() ?: emptySet()
    // Shown right away while the vote is on its way.
    var pending by remember(m.id, mine) { mutableStateOf<Set<String>?>(null) }
    val chosen = pending ?: mine
    val baseVotes = poll?.options?.associate { it.name to it.votes } ?: emptyMap()
    fun votesFor(name: String): Int {
        var v = baseVotes[name] ?: 0
        if (pending != null) {
            if (name in mine) v--
            if (name in chosen) v++
        }
        return v.coerceAtLeast(0)
    }
    val voters = run {
        var v = poll?.voters ?: 0
        if (pending != null) {
            if (mine.isNotEmpty()) v--
            if (chosen.isNotEmpty()) v++
        }
        v.coerceAtLeast(0)
    }
    val canVote = interactive && !m.revoked && m.status != Message.FAILED && m.status != Message.PENDING
    Column(Modifier.widthIn(min = 250.dp, max = 300.dp).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 5.dp)) {
        Text(m.text, style = Type.bubble.copy(fontWeight = FontWeight.SemiBold), color = p.label)
        Row(Modifier.padding(top = 3.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (multi) Icons.Outlined.Checklist else Icons.Outlined.RadioButtonChecked, null, tint = p.secondaryLabel, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (multi) "Select one or more" else "Select one", style = Type.caption.copy(fontSize = 12.sp), color = p.secondaryLabel)
        }
        names.forEach { name ->
            val sel = name in chosen
            val votes = votesFor(name)
            val fraction = if (voters > 0) (votes.toFloat() / voters).coerceIn(0f, 1f) else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = canVote) {
                        val next = if (multi) {
                            if (sel) chosen - name else chosen + name
                        } else {
                            if (sel) emptySet() else setOf(name)
                        }
                        pending = next
                        Engine.votePoll(m.chat, m.id, names.filter { it in next })
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceMark(sel, square = multi, size = 21.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = Type.subhead, color = p.label, modifier = Modifier.weight(1f))
                        if (votes > 0) Text("$votes", style = Type.subhead, color = p.secondaryLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(p.secondaryLabel.copy(alpha = 0.18f)),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(p.accent))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (voters) {
                    0 -> "No votes yet"
                    1 -> "1 vote"
                    else -> "$voters votes"
                },
                style = Type.caption.copy(fontSize = 12.sp),
                color = p.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            meta()
        }
    }
}

/** A shared contact card: name, number and "Message". */
@Composable
private fun ContactBody(m: Message, meta: @Composable () -> Unit) {
    val p = LocalPalette.current
    val vcard = m.extra?.optString("vcard").orEmpty()
    val number = remember(vcard) { Contacts.numberIn(vcard) }
    Column(Modifier.widthIn(min = 230.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(if (p.dark) Color(0xFF3A3A3C) else Color(0xFFD1D6DB)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, null, tint = Color.White.copy(alpha = if (p.dark) 0.6f else 0.95f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(m.text.ifBlank { "Contact" }, style = Type.bubble.copy(fontWeight = FontWeight.SemiBold), color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (number != null) Text("+$number", style = Type.caption.copy(fontSize = 12.5.sp), color = p.secondaryLabel)
            }
        }
        Row(Modifier.align(Alignment.End).padding(end = 9.dp, bottom = 4.dp)) { meta() }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.secondaryLabel.copy(alpha = 0.3f)))
        Text(
            if (number != null) "Message" else "View contact",
            style = Type.subhead.copy(fontWeight = FontWeight.Medium),
            color = p.link,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        )
    }
}

@Composable
private fun BareContent(m: Message) {
    val p = LocalPalette.current
    Column(horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
        if (m.type == "video" && m.extra?.optBoolean("round") == true) {
            // Instant video message: just the circle, no bubble (this was missing, so they showed as blank).
            RoundVideoBody(m)
        } else if (m.type == "sticker") {
            val media = rememberMedia(m.chat, m.id, m.local, auto = true)
            val path = media.path
            val animated = m.extra?.optBoolean("animated") == true
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (path != null && animated) {
                    AnimatedImage(path, Modifier.fillMaxSize())
                } else {
                    val img = rememberImage(path, 450)
                    if (img != null) {
                        Image(img, "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(
                            Modifier.size(132.dp).clip(RoundedCornerShape(24.dp)).background(p.pill.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (media.loading) {
                                CircularProgressIndicator(color = p.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.EmojiEmotions, null, tint = p.secondaryLabel, modifier = Modifier.size(42.dp))
                                    Text("Sticker", style = Type.footnote, color = p.secondaryLabel)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(m.text, style = Type.bubble.copy(fontSize = if (m.text.codePointCount(0, m.text.length) <= 2) 46.sp else 38.sp, lineHeight = 52.sp))
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(p.pill.copy(alpha = 0.85f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) { Meta(m, p.bubbleMeta) }
    }
}

@Composable
private fun ReactionPill(m: Message, modifier: Modifier) {
    val p = LocalPalette.current
    val total = m.reactions.sumOf { it.count }
    Row(
        modifier
            .border(2.dp, p.wallpaper, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (m.fromMe) p.bubbleIn else p.pill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        m.reactions.take(3).forEach { Text(it.emoji, fontSize = 14.sp) }
        if (total > 1) Text(" $total", style = Type.caption.copy(fontSize = 12.sp), color = p.secondaryLabel)
    }
}

// -----------------------------------------------------------------------------
// Text with the time tucked into the last line when it fits (WhatsApp style).

private class LayoutHolder {
    var result: TextLayoutResult? = null
}

@Composable
fun TextWithMeta(text: AnnotatedString, meta: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val holder = remember { LayoutHolder() }
    val gap = with(LocalDensity.current) { 7.dp.roundToPx() }
    Layout(
        content = {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            val hasLinks = remember(text) {
                text.getStringAnnotations("url", 0, text.length).isNotEmpty() || text.getStringAnnotations("phone", 0, text.length).isNotEmpty()
            }
            Text(
                text,
                style = Type.bubble,
                color = p.label,
                onTextLayout = { holder.result = it },
                modifier = if (!hasLinks) Modifier else Modifier.pointerInput(text) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val r = holder.result ?: return@awaitEachGesture
                        val line = r.getLineForVerticalPosition(down.position.y)
                        if (down.position.x > r.getLineRight(line) + 8f) return@awaitEachGesture
                        val off = r.getOffsetForPosition(down.position)
                        val url = text.getStringAnnotations("url", off, off).firstOrNull()?.item
                        val phone = if (url == null) text.getStringAnnotations("phone", off, off).firstOrNull()?.item else null
                        if (url == null && phone == null) return@awaitEachGesture
                        // Only a quick tap opens the link; holding goes to the message menu.
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                        if (up != null) {
                            up.consume()
                            if (url != null) runCatching { uri.openUri(url) } else PhoneTap.number = phone
                        }
                    }
                },
            )
            meta()
        },
        modifier = modifier,
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val metaP = measurables[1].measure(Constraints())
                val textP = measurables[0].measure(constraints.copy(minWidth = 0))
                val r = holder.result
                val lastRight = if (r != null && r.lineCount > 0) r.getLineRight(r.lineCount - 1).roundToInt() else textP.width
                val inline = lastRight + gap + metaP.width <= constraints.maxWidth
                val width = (if (inline) maxOf(textP.width, lastRight + gap + metaP.width) else maxOf(textP.width, metaP.width))
                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                val height = if (inline) maxOf(textP.height, metaP.height) + 2 else textP.height + metaP.height
                return layout(width, height) {
                    textP.place(0, 0)
                    metaP.place(width - metaP.width, height - metaP.height)
                }
            }

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                measurables[0].maxIntrinsicWidth(height) + gap + measurables[1].maxIntrinsicWidth(height)

            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                maxOf(measurables[0].minIntrinsicWidth(height), measurables[1].maxIntrinsicWidth(height))

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                measurables[0].maxIntrinsicHeight(width) + measurables[1].maxIntrinsicHeight(width)

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                measurables[0].minIntrinsicHeight(width)
        },
    )
}

// -----------------------------------------------------------------------------
// Shapes and helpers

/**
 * Rounded bubble with an iOS-style curled tail at the bottom corner. The tail
 * lives in a [gutter]-wide strip on the outer side so bubbles stay aligned
 * whether or not they have one.
 */
fun bubblePath(size: Size, outgoing: Boolean, tail: Boolean, gutter: Float, radius: Float): Path {
    val w = size.width
    val h = size.height
    val r = minOf(radius, h / 2f, (w - gutter) / 2f)
    val left = if (outgoing) 0f else gutter
    val right = if (outgoing) w - gutter else w
    val path = Path()
    path.addRoundRect(androidx.compose.ui.geometry.RoundRect(left, 0f, right, h, r, r))
    if (!tail) return path
    val t = Path()
    if (outgoing) {
        t.moveTo(right - r * 0.9f, h)
        t.lineTo(right - r * 0.2f, h)
        t.cubicTo(right + gutter * 0.25f, h, right + gutter * 0.85f, h - 0.5f, w, h)
        t.cubicTo(right + gutter * 0.15f, h - gutter * 0.6f, right, h - r * 0.55f, right, h - r * 1.1f)
        t.lineTo(right - r * 0.9f, h - r * 0.4f)
        t.close()
    } else {
        t.moveTo(left + r * 0.9f, h)
        t.lineTo(left + r * 0.2f, h)
        t.cubicTo(left - gutter * 0.25f, h, left - gutter * 0.85f, h - 0.5f, 0f, h)
        t.cubicTo(left - gutter * 0.15f, h - gutter * 0.6f, left, h - r * 0.55f, left, h - r * 1.1f)
        t.lineTo(left + r * 0.9f, h - r * 0.4f)
        t.close()
    }
    // Merge the tail into the bubble. Just adding the two shapes made the
    // overlap cancel out on incoming bubbles (their tail winds the other
    // way), which cut a notch into the bottom-left corner.
    return runCatching { Path.combine(PathOperation.Union, path, t) }.getOrElse {
        path.addPath(t)
        path
    }
}

private val UrlRegex = Regex("""(https?://[^\s]+|www\.[^\s]+)""")

/** Phone numbers like +44 7700 900123, 9800000000 or (020) 7946-0018. */
private val PhoneRegex = Regex("""(?<![\w/@.])\+?\d[\d \-()]{7,16}\d(?![\w@])""")

/** A tapped phone number waiting for its Chat / Call / Add to contacts sheet. */
object PhoneTap {
    var number by mutableStateOf<String?>(null)
}

/**
 * @mentions in bold app colour; a mention of you stands out on a green
 * background with white text, like WhatsApp.
 */
private fun highlightMentions(text: AnnotatedString, mentions: List<com.glasschat.engine.Mention>, accent: Color): AnnotatedString {
    if (mentions.isEmpty()) return text
    val b = AnnotatedString.Builder(text)
    val plain = text.text
    mentions.distinctBy { it.text }.forEach { m ->
        if (m.text.length < 2) return@forEach
        var at = plain.indexOf(m.text)
        while (at >= 0) {
            val style = if (m.me) {
                SpanStyle(color = Color.White, background = Color(0xFF1F7A4D), fontWeight = FontWeight.SemiBold)
            } else {
                SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
            }
            b.addStyle(style, at, at + m.text.length)
            at = plain.indexOf(m.text, at + m.text.length)
        }
    }
    return b.toAnnotatedString()
}

private fun linkify(text: String, link: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    val urls = UrlRegex.findAll(text).map { it.range }.toList()
    val phones = PhoneRegex.findAll(text).filter { m ->
        val digits = m.value.count(Char::isDigit)
        digits in 10..15 && urls.none { it.first <= m.range.last && m.range.first <= it.last }
    }.map { it.range to "phone" }
    val found = (UrlRegex.findAll(text).map { it.range to "url" } + phones).sortedBy { it.first.first }
    for ((range, kind) in found) {
        if (range.first < last) continue
        if (kind == "phone") {
            append(formatInline(text.substring(last, range.first)))
            val raw = text.substring(range)
            val digits = raw.filter(Char::isDigit)
            pushStringAnnotation("phone", if (raw.trimStart().startsWith("+")) digits else if (digits.length == 10) "91$digits" else digits)
            withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(raw) }
            pop()
            last = range.last + 1
            continue
        }
        // Leave trailing punctuation outside the link ("see example.com.").
        var url = text.substring(range)
        while (url.isNotEmpty() && url.last() in ".,!?)]};:'\"") url = url.dropLast(1)
        val end = range.first + url.length
        append(formatInline(text.substring(last, range.first)))
        val target = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
        // A plain annotation (not a clickable link) so a long press anywhere on
        // the text still opens the message menu; a quick tap opens the link.
        pushStringAnnotation("url", target)
        withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(url) }
        pop()
        last = end
    }
    append(formatInline(text.substring(last)))
}

/** WhatsApp inline formatting: *bold*, _italic_, ~strike~. */
private fun formatInline(s: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""(?<![\w*])\*([^*\n]+)\*(?![\w*])|(?<![\w_])_([^_\n]+)_(?![\w_])|(?<![\w~])~([^~\n]+)~(?![\w~])""")
    var last = 0
    for (m in regex.findAll(s)) {
        append(s.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(m.groupValues[3]) }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

/** True for messages made only of 1–3 emoji (shown large, without a bubble). */
fun isEmojiOnly(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty() || t.length > 24) return false
    var count = 0
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        i += Character.charCount(cp)
        when {
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F -> {}
            cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF || cp == 0x2764 -> count++
            cp in 0x1F1E6..0x1F1FF -> count++
            else -> return false
        }
    }
    return count in 1..6
}
