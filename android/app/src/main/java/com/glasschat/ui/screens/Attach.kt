package com.glasschat.ui.screens

import androidx.compose.material.icons.rounded.ShareLocation
import com.glasschat.ui.glass.menuGlass
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.glasschat.media.GalleryItem
import com.glasschat.media.MediaFiles
import com.glasschat.media.PendingMedia
import com.glasschat.media.PhoneGallery
import com.glasschat.media.PhoneLocation
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch
import java.util.Locale

/** What the attach panel can do. */
enum class AttachAction(val label: String, val color: Color) {
    Gallery("Gallery", Color(0xFF1E88FF)),
    Camera("Camera", Color(0xFFFF375F)),
    Location("Location", Color(0xFF1DAA61)),
    Contact("Contact", Color(0xFF0FA3D6)),
    Document("Document", Color(0xFF7F66FF)),
    Audio("Audio", Color(0xFFFF8A00)),
    Poll("Poll", Color(0xFFE5A100)),
    Sticker("Sticker", Color(0xFF20B2AA)),
    InstantVideo("Video note", Color(0xFF00A884)),
    ;

    val icon: ImageVector
        get() = when (this) {
            Gallery -> Icons.Rounded.PhotoLibrary
            Camera -> Icons.Rounded.PhotoCamera
            Location -> Icons.Rounded.LocationOn
            Contact -> Icons.Rounded.Person
            Document -> Icons.Rounded.Description
            Audio -> Icons.Rounded.Headphones
            Poll -> Icons.Rounded.Poll
            Sticker -> Icons.Rounded.EmojiEmotions
            InstantVideo -> Icons.Rounded.Videocam
        }
}

/**
 * The attach panel: a frosted sheet with the camera, your recent photos
 * (tap to pick several) and a grid of everything else you can send.
 */
@Composable
fun AttachPanel(
    onDismiss: () -> Unit,
    onAction: (AttachAction) -> Unit,
    onSendRecent: (List<PendingMedia>) -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(PhoneGallery.hasAccess(ctx)) }
    var reload by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    val selected = remember { mutableStateListOf<GalleryItem>() }
    val askGallery = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = PhoneGallery.hasAccess(ctx)
        reload++
    }
    LaunchedEffect(granted, reload) {
        if (granted) recent = PhoneGallery.recent(ctx)
    }
    // Android 14 "selected photos only": offer to pick more.
    val partial = Build.VERSION.SDK_INT >= 34 && granted &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED

    val slide = remember { Animatable(with(density) { 700.dp.toPx() }) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 380f)) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            slide.animateTo(with(density) { 700.dp.toPx() }, tween(200))
            onDismiss()
        }
    }
    fun act(a: AttachAction) {
        onDismiss()
        onAction(a)
    }

    com.glasschat.ui.glass.GlassModal(onDismiss = ::close, dim = 0.18f) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = ::close),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(30.dp)
            val dismissPx = with(density) { 140.dp.toPx() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .graphicsLayer { translationY = slide.value }
                    .menuGlass(shape)
                    .clip(shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .draggable(
                        rememberDraggableState { d -> scope.launch { slide.snapTo((slide.value + d).coerceAtLeast(0f)) } },
                        Orientation.Vertical,
                        onDragStopped = { v ->
                            if (slide.value > dismissPx || v > 2200f) close()
                            else slide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                        },
                    )
                    .padding(bottom = 18.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(p.tertiaryLabel))
                }
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(206.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "camera", span = { GridItemSpan(maxLineSpan) }) {
                        CameraTile(Modifier.width(128.dp).fillMaxHeight()) { act(AttachAction.Camera) }
                    }
                    if (!granted) {
                        item(key = "allow", span = { GridItemSpan(maxLineSpan) }) {
                            AllowPhotosTile(Modifier.width(210.dp).fillMaxHeight()) { askGallery.launch(PhoneGallery.permissions) }
                        }
                    } else {
                        items(recent, key = { it.uri.toString() }) { g ->
                            RecentTile(g, selected.indexOf(g), Modifier.width(101.dp).fillMaxHeight()) {
                                if (g in selected) selected.remove(g) else if (selected.size < 30) selected.add(g)
                            }
                        }
                        if (partial) {
                            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                                AllowPhotosTile(Modifier.width(150.dp).fillMaxHeight(), more = true) { askGallery.launch(PhoneGallery.permissions) }
                            }
                        }
                    }
                }
                AnimatedVisibility(selected.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${selected.size} selected",
                            style = Type.subhead.copy(fontWeight = FontWeight.Medium),
                            color = p.label,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Clear",
                            style = Type.subhead,
                            color = p.link,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { selected.clear() }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Next",
                            style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(p.accent)
                                .clickable {
                                    val picked = selected.map { PendingMedia(it.uri, it.video) }
                                    onDismiss()
                                    onSendRecent(picked)
                                }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, start = 6.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AttachAction.entries.filter { it != AttachAction.Camera }.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { a -> OptionTile(a, Modifier.weight(1f)) { act(a) } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionTile(a: AttachAction, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(p.label.copy(alpha = if (p.dark) 0.12f else 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            // Black & white, like WhatsApp on iPhone.
            Icon(a.icon, null, tint = p.label, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(a.label, style = Type.footnote.copy(fontSize = 12.5.sp), color = p.label, maxLines = 1)
    }
}

private fun lighten(c: Color, amount: Float) = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)

@Composable
internal fun CameraTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text("Camera", style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = Color.White)
        }
    }
}

@Composable
internal fun AllowPhotosTile(modifier: Modifier, more: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.fill)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = p.secondaryLabel, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (more) "Choose more photos" else "Show your recent photos here",
            style = Type.footnote,
            color = p.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (more) "Select" else "Allow access",
            style = Type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun RecentTile(item: GalleryItem, order: Int, modifier: Modifier, onCircle: (() -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var bmp by remember(item.uri) { mutableStateOf<ImageBitmap?>(PhoneGallery.cachedThumb(item.uri)) }
    LaunchedEffect(item.uri) {
        if (bmp == null) bmp = PhoneGallery.thumbnail(ctx, item.uri)
    }
    val chosen = order >= 0
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.fill)
            .clickable(onClick = onClick),
    ) {
        bmp?.let {
            Image(
                it,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = if (chosen) 0.9f else 1f
                    scaleX = s
                    scaleY = s
                },
            )
        }
        if (item.video) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.weight(1f))
                if (item.seconds > 0) Text(Fmt.duration(item.seconds), style = Type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .then(if (onCircle != null) Modifier.clickable(onClick = onCircle).padding(2.dp) else Modifier)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (chosen) Modifier.background(p.accent).border(1.5.dp, Color.White, CircleShape)
                    else Modifier.background(Color.Black.copy(alpha = 0.18f)).border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Text("${order + 1}", style = Type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

// -----------------------------------------------------------------------------
// Location

private enum class LocPhase { Asking, Finding, Found, Failed, Denied }

/** "Send your current location", with the accuracy and street address. */
@Composable
fun LocationSheet(onDismiss: () -> Unit, onSend: (lat: Double, lng: Double, name: String, address: String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(if (PhoneLocation.hasAccess(ctx)) LocPhase.Finding else LocPhase.Asking) }
    var attempt by remember { mutableIntStateOf(0) }
    var loc by remember { mutableStateOf<Location?>(null) }
    var address by remember { mutableStateOf("") }
    var liveInfo by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        phase = if (PhoneLocation.hasAccess(ctx)) LocPhase.Finding else LocPhase.Denied
        attempt++
    }
    LaunchedEffect(attempt) {
        if (!PhoneLocation.hasAccess(ctx)) {
            if (attempt == 0) ask.launch(PhoneLocation.permissions)
            return@LaunchedEffect
        }
        phase = LocPhase.Finding
        val l = PhoneLocation.current(ctx)
        if (l == null) {
            phase = LocPhase.Failed
        } else {
            loc = l
            phase = LocPhase.Found
            address = PhoneLocation.address(ctx, l.latitude, l.longitude)
        }
    }

    BottomCard(onDismiss, title = "Send location") {
        LocatorArt(phase == LocPhase.Found, Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 14.dp))
        Spacer(Modifier.height(14.dp))
        when (phase) {
            LocPhase.Asking, LocPhase.Finding -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = p.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(if (phase == LocPhase.Asking) "Waiting for permission…" else "Finding your location…", style = Type.body, color = p.secondaryLabel)
            }
            LocPhase.Found -> {
                val l = loc!!
                val acc = l.accuracy.toInt()
                // Share live location (as on the phone) / Send current location.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { liveInfo = !liveInfo }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ShareLocation, null, tint = p.label, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Share live location", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.label, modifier = Modifier.weight(1f))
                }
                if (liveInfo) {
                    Text(
                        "WhatsApp only lets your main phone share live location — linked devices like this app can show it " +
                            "but not send it. Start it from WhatsApp on your phone, or send your current location below.",
                        style = Type.footnote,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 78.dp, end = 20.dp, bottom = 6.dp),
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = p.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 78.dp, end = 16.dp, top = 4.dp, bottom = 4.dp))
                Text("Nearby", style = Type.footnote, color = p.secondaryLabel, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            onSend(l.latitude, l.longitude, "", address)
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.NearMe, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Send your current location", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.accentText)
                        Text(
                            if (acc > 0) "Accurate to $acc metres" else "Accuracy unknown",
                            style = Type.footnote,
                            color = p.secondaryLabel,
                        )
                    }
                }
                if (address.isNotBlank()) {
                    Text(
                        address,
                        style = Type.footnote,
                        color = p.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                    )
                }
                Row(
                    Modifier
                        .padding(start = 70.dp, top = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { MediaFiles.openMap(ctx, l.latitude, l.longitude, "") }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Map, null, tint = p.link, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        String.format(Locale.US, "Check on the map (%.5f, %.5f)", l.latitude, l.longitude),
                        style = Type.footnote,
                        color = p.link,
                    )
                }
            }
            LocPhase.Failed -> Notice(
                "Couldn't get your location. Make sure Location is turned on, then try again.",
                "Try again",
            ) { attempt++ }
            LocPhase.Denied -> Notice(
                "This app needs location access to share where you are.",
                "Open settings",
            ) { openAppSettings(ctx) }
        }
    }
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = Type.subhead, color = p.secondaryLabel, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            button,
            style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A calm "locating" picture: soft rings around a pulsing dot. */
@Composable
private fun LocatorArt(found: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "locate")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val shape = RoundedCornerShape(22.dp)
    val bgTop = if (p.dark) Color(0xFF1E2A33) else Color(0xFFE3F0FA)
    val bgBottom = if (p.dark) Color(0xFF14191E) else Color(0xFFF3F8FC)
    val blue = Color(0xFF0A84FF)
    Canvas(modifier.clip(shape).background(Brush.verticalGradient(listOf(bgTop, bgBottom)))) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val ring = if (p.dark) Color.White.copy(alpha = 0.08f) else Color(0xFF0A84FF).copy(alpha = 0.10f)
        for (i in 1..4) drawCircle(ring, radius = i * size.height / 8.5f, center = c, style = Stroke(1.dp.toPx()))
        drawLine(ring, Offset(0f, c.y), Offset(size.width, c.y), 1.dp.toPx())
        drawLine(ring, Offset(c.x, 0f), Offset(c.x, size.height), 1.dp.toPx())
        val r = 9.dp.toPx()
        if (!found) {
            drawCircle(blue.copy(alpha = (1f - pulse) * 0.35f), radius = r + pulse * size.height * 0.42f, center = c)
        } else {
            drawCircle(blue.copy(alpha = 0.18f), radius = r * 3.2f, center = c)
        }
        drawCircle(Color.White, radius = r + 3.dp.toPx(), center = c)
        drawCircle(blue, radius = r, center = c)
    }
}

/** A frosted card that slides up from the bottom (used for location). */
@Composable
private fun BottomCard(onDismiss: () -> Unit, title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    com.glasschat.ui.glass.GlassModal(onDismiss = onDismiss, dim = 0.22f) {
        val slide = remember { Animatable(600f) }
        LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 400f)) }
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
                    .graphicsLayer { translationY = slide.value }
                    .menuGlass(shape)
                    .clip(shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(bottom = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Type.title, color = p.label, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.fill).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Close", tint = p.secondaryLabel, modifier = Modifier.size(18.dp))
                    }
                }
                content()
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Polls

/** Create a poll: a question, 2–12 options and "allow multiple answers". */
@Composable
fun PollComposer(onSend: (question: String, options: List<String>, multi: Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(true) }
    val clean = options.map { it.trim() }.filter { it.isNotEmpty() }
    val duplicate = clean.size != clean.distinct().size
    val valid = question.isNotBlank() && clean.size >= 2 && !duplicate

    fun setOption(i: Int, v: String) {
        options[i] = v.take(100)
        if (i == options.lastIndex && v.isNotBlank() && options.size < 12) options.add("")
        while (options.size > 2 && options[options.lastIndex].isBlank() && options[options.lastIndex - 1].isBlank()) {
            options.removeAt(options.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.groupedBackground)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel",
                style = Type.body,
                color = p.link,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text("Create poll", style = Type.title, color = p.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(
                "Send",
                style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (valid) p.link else p.tertiaryLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = valid) { onSend(question.trim(), clean, multi) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Question")
            SectionCard {
                PollField(question, "Ask a question", Modifier.padding(16.dp)) { question = it.take(255) }
            }
            Spacer(Modifier.height(24.dp))
            SectionHeader("Options")
            SectionCard {
                options.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = p.separator, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PollField(o, if (i == options.lastIndex && i >= 2) "Add" else "Option ${i + 1}", Modifier.weight(1f).padding(vertical = 14.dp)) {
                            setOption(i, it)
                        }
                        if (options.size > 2 && o.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(p.danger)
                                    .clickable {
                                        options.removeAt(i)
                                        if (options.size < 2) options.add("")
                                        if (options.size < 12 && options.last().isNotBlank()) options.add("")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Remove, "Remove option", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Text(
                if (duplicate) "Each option needs to be different." else "You can add up to 12 options.",
                style = Type.footnote,
                color = if (duplicate) p.danger else p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            SectionCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow multiple answers", style = Type.body, color = p.label, modifier = Modifier.weight(1f))
                    IosSwitch(multi) { multi = it }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PollField(value: String, placeholder: String, modifier: Modifier, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = Type.body.copy(color = p.label),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = Type.body, color = p.tertiaryLabel)
                inner()
            }
        },
    )
}

// -----------------------------------------------------------------------------
// Contacts

/** The system contact picker, filtered to phone numbers (no contacts permission needed). */
fun contactPickIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

/** Opens "new contact" in the phone's contacts app, pre-filled. */
fun saveContact(ctx: Context, name: String, phone: String) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION)
        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(ContactsContract.Intents.Insert.NAME, name)
        .putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No contacts app: nothing to do.
    }
}

/** A tick that can be dropped into rows ("chosen"). */
@Composable
fun ChoiceMark(selected: Boolean, square: Boolean, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val p = LocalPalette.current
    val shape = if (square) RoundedCornerShape(6.dp) else CircleShape
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .then(if (selected) Modifier.background(p.accent) else Modifier.border(1.5.dp, p.secondaryLabel, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}
