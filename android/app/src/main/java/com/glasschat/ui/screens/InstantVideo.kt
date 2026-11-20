package com.glasschat.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.delay
import java.io.File

/** Longest instant video, like WhatsApp. */
private const val MAX_SECONDS = 60

/**
 * Records an instant (round) video message with the front camera: recording
 * starts right away, tap the send button to stop and send, × to throw it away.
 * [onRecorded] gets the finished file and its length in seconds.
 */
@SuppressLint("MissingPermission")
@Composable
fun InstantVideoRecorder(onRecorded: (File, Int) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    fun has(perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    var cameraOk by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraOk = has(Manifest.permission.CAMERA)
        asked = true
    }
    LaunchedEffect(Unit) {
        val want = buildList {
            if (!cameraOk) add(Manifest.permission.CAMERA)
            if (!has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        }
        if (want.isNotEmpty()) ask.launch(want.toTypedArray()) else asked = true
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            // Small, like WhatsApp's video notes (keeps sending quick).
            runCatching { videoCaptureQualitySelector = QualitySelector.from(Quality.SD, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)) }
        }
    }
    DisposableEffect(cameraOk, lifecycleOwner) {
        if (cameraOk && lifecycleOwner != null) runCatching { controller.bindToLifecycle(lifecycleOwner) }
        onDispose { runCatching { controller.unbind() } }
    }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var front by remember { mutableStateOf(true) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val file = remember { File(ctx.cacheDir, "ptv_${System.currentTimeMillis()}.mp4") }

    fun start() {
        if (recording != null) return
        val audio = if (has(Manifest.permission.RECORD_AUDIO)) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        runCatching {
            started = System.currentTimeMillis()
            recording = controller.startRecording(
                FileOutputOptions.Builder(file).build(),
                audio,
                ContextCompat.getMainExecutor(ctx),
            ) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    val secs = ((System.currentTimeMillis() - started + 500) / 1000).toInt().coerceIn(1, MAX_SECONDS)
                    when {
                        discard -> file.delete()
                        file.length() > 0 -> onRecorded(file, secs)
                        else -> error = "Couldn't record the video"
                    }
                }
            }
        }.onFailure { error = "Camera isn't ready yet" }
    }

    fun stop(send: Boolean) {
        discard = !send
        val r = recording
        if (r != null) r.stop() else if (!send) onClose()
    }

    BackHandler { stop(false); onClose() }

    // Start once the camera is up (it needs a moment after binding).
    LaunchedEffect(cameraOk, attempt) {
        if (!cameraOk) return@LaunchedEffect
        delay(700)
        start()
    }
    LaunchedEffect(recording) {
        while (recording != null) {
            elapsedMs = System.currentTimeMillis() - started
            if (elapsedMs >= MAX_SECONDS * 1000L) {
                stop(true)
                break
            }
            delay(50)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            discard = discard || recording != null
            recording?.stop()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                if (cameraOk) {
                    AndroidView(
                        factory = { c ->
                            PreviewView(c).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                this.controller = controller
                            }
                        },
                        modifier = Modifier.size(280.dp).clip(CircleShape),
                    )
                } else {
                    Box(Modifier.size(280.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                        Text(
                            if (asked) "Allow camera access\nin Android settings" else "",
                            style = Type.subhead,
                            color = Color.White,
                        )
                    }
                }
                // Progress ring: fills up over the minute.
                val progress = (elapsedMs / (MAX_SECONDS * 1000f)).coerceIn(0f, 1f)
                Canvas(Modifier.size(300.dp)) {
                    val w = 5.dp.toPx()
                    drawArc(
                        Color.White.copy(alpha = 0.18f), 0f, 360f, false,
                        topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w),
                    )
                    drawArc(
                        p.accent, -90f, 360f * progress, false,
                        topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recording != null) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    error ?: if (recording != null) Fmt.duration((elapsedMs / 1000).toInt()) + " / 1:00" else "Starting camera…",
                    style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = if (error != null) Color(0xFFFF6B6B) else Color.White,
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTool(Icons.Rounded.Close, "Discard", Color.White.copy(alpha = 0.16f)) {
                stop(false)
                onClose()
            }
            RoundTool(Icons.Rounded.FlipCameraAndroid, "Switch camera", Color.White.copy(alpha = 0.16f)) {
                // Switching cameras ends the clip on most phones, so only before recording, or restart.
                front = !front
                val wasRecording = recording != null
                if (wasRecording) {
                    discard = true
                    recording?.stop()
                }
                controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (wasRecording) {
                    elapsedMs = 0
                    attempt++
                }
            }
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (recording != null) p.accent else Color.Gray)
                    .clickable(enabled = recording != null) { stop(true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun RoundTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** Send spec for a recorded instant video. */
fun instantVideoSpec(ctx: android.content.Context, file: File, seconds: Int): org.json.JSONObject? = runCatching {
    com.glasschat.media.MediaPrep.video(ctx, Uri.fromFile(file), "")
        .put("kind", "ptv")
        .put("seconds", seconds)
}.getOrNull()
