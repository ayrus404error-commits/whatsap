package com.glasschat.ui.screens

import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.foundation.combinedClickable
import com.glasschat.ui.components.Fmt
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCut
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.toMutableStateList
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasschat.media.PendingMedia
import com.glasschat.media.loadUriImage
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

/** Preview of picked photos/videos with a caption box, before sending. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SendPreview(
    original: List<PendingMedia>,
    target: String,
    onSend: (caption: String, items: List<PendingMedia>) -> Unit,
    onCancel: () -> Unit,
    /** Show the "1" (view once) button — for chats, not status. */
    allowViewOnce: Boolean = false,
    /** Hold the send button to send later (scheduled). */
    onSchedule: ((caption: String, items: List<PendingMedia>) -> Unit)? = null,
    /** ⋮ menu: send as documents in original quality. */
    onSendAsFiles: ((caption: String, items: List<PendingMedia>) -> Unit)? = null,
    /** ⋮ menu: send several one by one instead of as an album. */
    onSendUngrouped: ((caption: String, items: List<PendingMedia>) -> Unit)? = null,
) {
    BackHandler(onBack = onCancel)
    val p = LocalPalette.current
    // Photos can be edited (crop, draw, text, emoji): edits replace them here.
    val items = remember(original) { original.toMutableStateList() }
    var index by remember(original) { mutableIntStateOf(0) }
    var caption by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var editTool by remember { mutableStateOf("") }
    var trimming by remember { mutableStateOf(false) }
    var viewOnce by remember(original) { mutableStateOf(false) }
    var sendMenu by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]
    Box(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        PreviewImage(current, 2048, Modifier.fillMaxSize(), ContentScale.Fit)
        if (current.video) {
            Box(
                Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Cancel", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            if (onSendAsFiles != null || (onSendUngrouped != null && items.size > 1)) {
                Box(
                    Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { sendMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, "Send options", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            if (items.size > 1) {
                Text("${index + 1} of ${items.size}", style = Type.subhead, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            if (current.video) {
                Box(
                    Modifier.padding(start = 6.dp).height(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { trimming = true }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ContentCut, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (current.trimEndMs > current.trimStartMs) Fmt.duration(((current.trimEndMs - current.trimStartMs + 500) / 1000).toInt()) else "Trim", style = Type.subhead, color = Color.White)
                    }
                }
            }
            if (!current.video) {
                // Crop · blur · emoji · text · draw, all in the photo editor.
                listOf(
                    Icons.Rounded.Crop to "Crop",
                    Icons.Rounded.BlurOn to "Blur",
                    Icons.Rounded.EmojiEmotions to "Emoji",
                    Icons.Rounded.TextFields to "Text",
                    Icons.Rounded.Draw to "Draw",
                ).forEach { (icon, label) ->
                    Box(
                        Modifier.padding(start = 6.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable {
                            editTool = label.lowercase()
                            editing = true
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, label, tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    itemsIndexed(items) { i, item ->
                        PreviewImage(
                            item,
                            200,
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.dp, if (i == index) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { index = i },
                            ContentScale.Crop,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .frosted(shape, Color(0xCC2C2C2E), 0.4f)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (caption.isEmpty()) Text("Add a caption…", style = Type.body, color = Color.White.copy(alpha = 0.5f))
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it.take(1024) },
                        textStyle = Type.body.copy(color = Color.White),
                        cursorBrush = SolidColor(p.accent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (allowViewOnce && items.size == 1) {
                    Spacer(Modifier.width(8.dp))
                    // "1": the person can open it once.
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (viewOnce) p.accent else Color.Black.copy(alpha = 0.45f))
                            .border(1.5.dp, Color.White.copy(alpha = if (viewOnce) 0f else 0.8f), CircleShape)
                            .clickable { viewOnce = !viewOnce },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("1", style = Type.rowTitle.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(p.accent).combinedClickable(
                        onLongClick = onSchedule?.let { f -> { f(caption, items.toList()) } },
                    ) {
                        onSend(caption, items.toList().map { if (viewOnce && items.size == 1) it.copy(viewOnce = true) else it })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            if (viewOnce && items.size == 1) {
                Text(
                    "View once: they can open it one time, then it disappears",
                    style = Type.footnote,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (target.isNotBlank()) {
                Text(
                    "To: $target",
                    style = Type.footnote.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (sendMenu) {
            val n = items.size
            com.glasschat.ui.components.ActionSheet(
                title = if (n > 1) "$n selected" else null,
                actions = buildList {
                    onSendAsFiles?.let { f ->
                        add(com.glasschat.ui.components.SheetAction(if (n > 1) "Send as files" else "Send as file", Icons.Outlined.InsertDriveFile) {
                            sendMenu = false
                            f(caption, items.toList())
                        })
                    }
                    if (n > 1) onSendUngrouped?.let { f ->
                        add(com.glasschat.ui.components.SheetAction("Send without grouping", Icons.Outlined.GridOff) {
                            sendMenu = false
                            f(caption, items.toList())
                        })
                    }
                },
                onDismiss = { sendMenu = false },
            )
        }
        if (trimming && current.video) {
            VideoTrimmer(
                current,
                onDone = { edited ->
                    items[index.coerceIn(0, items.lastIndex)] = edited
                    trimming = false
                },
                onCancel = { trimming = false },
            )
        }
        if (editing) {
            ImageEditor(
                current,
                onDone = { edited ->
                    items[index.coerceIn(0, items.lastIndex)] = edited
                    editing = false
                },
                onCancel = { editing = false },
                initialTool = editTool,
            )
        }
    }
}

@Composable
private fun PreviewImage(item: PendingMedia, maxPx: Int, modifier: Modifier, scale: ContentScale) {
    val ctx = LocalContext.current
    var bmp by remember(item.uri, maxPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.uri, maxPx) {
        bmp = if (item.video) videoFrame(ctx, item.uri) else loadUriImage(ctx, item.uri, maxPx)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = scale, modifier = Modifier.fillMaxSize())
        } else if (maxPx > 500) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
    }
}
