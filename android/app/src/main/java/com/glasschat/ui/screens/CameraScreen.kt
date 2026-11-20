package com.glasschat.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.glasschat.media.GalleryItem
import com.glasschat.media.MediaPrep
import com.glasschat.media.PendingMedia
import com.glasschat.media.PhoneGallery
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.delay
import java.io.File

/**
 * Full-screen camera, WhatsApp style: live preview, flash, front/back,
 * Photo and Video modes, a strip of your latest photos to pick from (swipe it
 * up for the whole gallery), and a button for the system photo picker.
 * Everything you take or pick goes to [onMedia] (then the send preview).
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraCapture(onMedia: (List<PendingMedia>) -> Unit, onClose: () -> Unit, allowVideo: Boolean = true) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)

    fun has(perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    var cameraOk by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    var galleryOk by remember { mutableStateOf(PhoneGallery.hasAccess(ctx)) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraOk = has(Manifest.permission.CAMERA)
        galleryOk = PhoneGallery.hasAccess(ctx)
        asked = true
    }
    LaunchedEffect(Unit) {
        val want = buildList {
            if (!cameraOk) add(Manifest.permission.CAMERA)
            if (!galleryOk) addAll(PhoneGallery.permissions)
            if (allowVideo && !has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        }
        if (want.isNotEmpty()) ask.launch(want.toTypedArray()) else asked = true
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) onMedia(uris.map { PendingMedia(it, MediaPrep.isVideo(ctx, it)) })
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or if (allowVideo) CameraController.VIDEO_CAPTURE else 0)
        }
    }
    DisposableEffect(cameraOk, lifecycleOwner) {
        if (cameraOk && lifecycleOwner != null) runCatching { controller.bindToLifecycle(lifecycleOwner) }
        onDispose { runCatching { controller.unbind() } }
    }

    var video by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var front by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var recordSecs by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    // Photos ticked in the strip (shared with the full gallery).
    val picked = remember { mutableStateListOf<GalleryItem>() }
    LaunchedEffect(galleryOk) { if (galleryOk) recent = PhoneGallery.recent(ctx, 120) }
    LaunchedEffect(recording) {
        while (recording != null) {
            recordSecs = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
            delay(250)
        }
    }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    fun takePhoto() {
        if (busy) return
        busy = true
        val file = MediaPrep.cameraFile(ctx)
        runCatching {
            controller.imageCaptureFlashMode = flash
            controller.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(ctx),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        busy = false
                        onMedia(listOf(PendingMedia(Uri.fromFile(file), false)))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                        error = "Couldn't take the photo: ${exception.message}"
                    }
                },
            )
        }.onFailure {
            busy = false
            error = "Camera isn't ready yet"
        }
    }

    fun toggleRecording() {
        val r = recording
        if (r != null) {
            r.stop()
            recording = null
            return
        }
        val file = File(ctx.cacheDir, "camera_${System.currentTimeMillis()}.mp4")
        val audio = if (has(Manifest.permission.RECORD_AUDIO)) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        runCatching {
            controller.enableTorch(flash == ImageCapture.FLASH_MODE_ON)
            recordStart = System.currentTimeMillis()
            recording = controller.startRecording(
                FileOutputOptions.Builder(file).build(),
                audio,
                ContextCompat.getMainExecutor(ctx),
            ) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    controller.enableTorch(false)
                    recording = null
                    if (!event.hasError() || file.length() > 0) {
                        onMedia(listOf(PendingMedia(Uri.fromFile(file), true)))
                    } else {
                        error = "Couldn't record the video"
                    }
                }
            }
        }.onFailure { error = "Camera isn't ready yet" }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraOk) {
            AndroidView(
                factory = { c ->
                    PreviewView(c).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 150.dp)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)),
            )
        } else if (asked) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Allow camera access to take photos and videos.", style = Type.body, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Allow camera",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                        .clickable { if (asked && !cameraOk) openAppSettings(ctx) else ask.launch(arrayOf(Manifest.permission.CAMERA)) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
        }

        // Top: close and flash.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Rounded.Close, "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (recording != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF3B30)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.duration(recordSecs), style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            }
            RoundButton(
                when (flash) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                    else -> Icons.Rounded.FlashOff
                },
                "Flash",
            ) {
                flash = when (flash) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> if (video) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }
        }

        // Bottom: recent photos strip, then controls, then the mode switch.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (picked.isNotEmpty() && recording == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${picked.size} selected",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Next (${picked.size})",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(LocalPalette.current.accent)
                            .clickable { onMedia(picked.map { PendingMedia(it.uri, it.video) }) }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
            if (recent.isNotEmpty() && recording == null) {
                // Handle: tap or swipe up for the whole gallery.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) expanded = true } }
                        .clickable(remember { MutableInteractionSource() }, indication = null) { expanded = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) expanded = true } },
                ) {
                    items(recent.take(40), key = { it.uri.toString() }) { g ->
                        val toggle = { if (g in picked) picked.remove(g) else if (picked.size < 30) picked.add(g) }
                        RecentTile(g, picked.indexOf(g), Modifier.width(84.dp).height(84.dp), onCircle = { toggle() }) {
                            // Tap: send this one; once some are ticked, tap adds/removes.
                            if (picked.isEmpty()) onMedia(listOf(PendingMedia(g.uri, g.video))) else toggle()
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Rounded.PhotoLibrary, "Gallery", big = true) {
                    runCatching { systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                }
                Spacer(Modifier.weight(1f))
                Shutter(video = video, recording = recording != null, enabled = cameraOk && !busy) {
                    if (video) toggleRecording() else takePhoto()
                }
                Spacer(Modifier.weight(1f))
                RoundButton(Icons.Rounded.FlipCameraAndroid, "Switch camera", big = true) {
                    if (recording == null) {
                        front = !front
                        runCatching {
                            controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    }
                }
            }
            if (allowVideo && recording == null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModePill("Video", video) { video = true; if (flash == ImageCapture.FLASH_MODE_AUTO) flash = ImageCapture.FLASH_MODE_OFF }
                    Spacer(Modifier.width(6.dp))
                    ModePill("Photo", !video) { video = false }
                }
            }
        }

        error?.let {
            LaunchedEffect(it) {
                delay(2500)
                error = null
            }
            Text(
                it,
                style = Type.subhead,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(14.dp),
            )
        }

        AnimatedVisibility(
            expanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            GalleryGrid(recent, picked, onClose = { expanded = false }, onDone = { onMedia(it) })
        }
    }
}

/** The whole recent gallery, pick up to 10. */
@Composable
private fun GalleryGrid(
    items: List<GalleryItem>,
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<GalleryItem>,
    onClose: () -> Unit,
    onDone: (List<PendingMedia>) -> Unit,
) {
    val p = LocalPalette.current
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Rounded.Close, "Close", onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text("Recents", style = Type.title.copy(fontWeight = FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                Text(
                    "Next (${selected.size})",
                    style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                        .clickable { onDone(selected.map { PendingMedia(it.uri, it.video) }) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(2.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            items(items, key = { it.uri.toString() }) { g ->
                RecentTile(g, selected.indexOf(g), Modifier.aspectRatio(1f).padding(1.5.dp)) {
                    if (g in selected) selected.remove(g) else if (selected.size < 30) selected.add(g)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 56.dp else 46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (big) 28.dp else 24.dp))
    }
}

@Composable
private fun Shutter(video: Boolean, recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val inner by animateDpAsState(if (recording) 30.dp else 62.dp, label = "shutter")
    Box(
        Modifier
            .size(80.dp)
            .border(5.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(if (recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(if (video) Color(0xFFFF3B30) else Color.White),
        )
    }
}

@Composable
private fun ModePill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Type.body.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
