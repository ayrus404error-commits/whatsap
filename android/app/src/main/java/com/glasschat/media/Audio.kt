package com.glasschat.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** Plays one voice note / audio file at a time. Call from the main thread. */
object VoicePlayer {
    data class State(val key: String? = null, val playing: Boolean = false, val position: Int = 0, val duration: Int = 0)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val main = MainScope()
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Plays [path] for [key], or pauses/resumes if it's already the current one. */
    fun toggle(key: String, path: String): Boolean {
        val s = _state.value
        val p = player
        if (s.key == key && p != null) {
            if (p.isPlaying) {
                p.pause()
                _state.value = s.copy(playing = false)
            } else {
                p.start()
                _state.value = s.copy(playing = true)
                tick()
            }
            return false
        }
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(path)
            mp.prepare()
        } catch (t: Throwable) {
            Log.w("VoicePlayer", "can't play $path", t)
            mp.release()
            return false
        }
        mp.setOnCompletionListener { done ->
            if (player === done) {
                player = null
                ticker?.cancel()
                _state.value = State()
            }
            done.release()
        }
        player = mp
        mp.start()
        _state.value = State(key, true, 0, mp.duration)
        tick()
        return true
    }

    fun seek(key: String, fraction: Float) {
        val p = player ?: return
        if (_state.value.key != key) return
        val to = (fraction.coerceIn(0f, 1f) * p.duration).toInt()
        p.seekTo(to)
        _state.update { it.copy(position = to) }
    }

    fun stop() {
        ticker?.cancel()
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        _state.value = State()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = main.launch {
            while (isActive) {
                val p = player ?: break
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                _state.update { it.copy(position = pos) }
                delay(120)
            }
        }
    }
}

/** A finished voice recording, ready to send. */
class Recording(val file: File, val seconds: Int, val waveform: String)

/** Records a voice note as Ogg/Opus, the format WhatsApp uses. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = ArrayList<Int>()
    private var sampler: Job? = null
    private val main = MainScope()
    private var opus: OpusRecorder? = null

    val isRecording: Boolean get() = rec != null || opus != null

    fun elapsedMs(): Long = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start(): Boolean {
        if (isRecording) return true
        val f = File(ctx.cacheDir, "voice-${System.currentTimeMillis()}.ogg")
        // Preferred: our own Ogg Opus writer, which every WhatsApp app accepts.
        val o = OpusRecorder(f)
        if (o.start()) {
            opus = o
            file = f
            startedAt = SystemClock.elapsedRealtime()
            levels.clear()
            return true
        }
        f.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startedAt = SystemClock.elapsedRealtime()
            levels.clear()
            sampler = main.launch {
                while (isActive) {
                    levels.add(runCatching { r.maxAmplitude }.getOrDefault(0))
                    delay(100)
                }
            }
            true
        } catch (t: Throwable) {
            Log.w("VoiceRecorder", "can't record", t)
            r.release()
            f.delete()
            false
        }
    }

    /** Stops recording. Returns the recording, or null if [keep] is false or it was too short. */
    fun stop(keep: Boolean): Recording? {
        sampler?.cancel()
        opus?.let { o ->
            opus = null
            val ms = SystemClock.elapsedRealtime() - startedAt
            val ok = o.stop()
            val f = file ?: return null
            file = null
            levels.clear()
            synchronized(o.levels) { levels.addAll(o.levels) }
            if (!keep || !ok || ms < 800 || f.length() == 0L) {
                f.delete()
                return null
            }
            return Recording(f, ((ms + 500) / 1000).toInt().coerceAtLeast(1), waveform())
        }
        val r = rec ?: return null
        rec = null
        val ms = SystemClock.elapsedRealtime() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file ?: return null
        file = null
        if (!keep || !ok || ms < 800 || f.length() == 0L) {
            f.delete()
            return null
        }
        return Recording(f, ((ms + 500) / 1000).toInt().coerceAtLeast(1), waveform())
    }

    /** 64 bars, 0–100, like WhatsApp's voice note waveform. */
    private fun waveform(): String {
        if (levels.isEmpty()) return ""
        val peak = (levels.maxOrNull() ?: 1).coerceAtLeast(1)
        val out = ByteArray(64) { i ->
            val from = i * levels.size / 64
            val to = maxOf(from + 1, (i + 1) * levels.size / 64).coerceAtMost(levels.size)
            val avg = levels.subList(from.coerceAtMost(levels.size - 1), to).average()
            (avg / peak * 100).roundToInt().coerceIn(0, 100).toByte()
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}

/** Decodes a base64 waveform into 0..1 bar heights. */
fun waveformBars(b64: String?, count: Int): List<Float>? {
    if (b64.isNullOrEmpty()) return null
    val raw = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
    if (raw.isEmpty()) return null
    return List(count) { i ->
        val v = raw[(i * raw.size / count).coerceAtMost(raw.size - 1)].toInt() and 0xff
        (v.coerceAtMost(100) / 100f).coerceIn(0.12f, 1f)
    }
}

