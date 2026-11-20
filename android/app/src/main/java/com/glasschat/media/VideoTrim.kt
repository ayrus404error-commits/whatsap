package com.glasschat.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts a video to [startMs]..[endMs] without re-encoding (fast, no quality
 * loss). The cut starts on the nearest key frame at or before [startMs], like
 * most phone editors do.
 */
object VideoTrim {
    private const val TAG = "VideoTrim"

    fun trim(src: File, out: File, startMs: Long, endMs: Long): Boolean {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ex.setDataSource(src.absolutePath)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = HashMap<Int, Int>()
            var maxSize = 1 shl 20
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                ex.selectTrack(i)
                map[i] = muxer.addTrack(f)
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = maxOf(maxSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (map.isEmpty()) return false
            // Keep the phone's rotation so the video isn't sideways.
            runCatching {
                val r = MediaMetadataRetriever()
                r.setDataSource(src.absolutePath)
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                r.release()
                muxer.setOrientationHint(rot)
            }
            muxer.start()
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val firstUs = ex.sampleTime.coerceAtLeast(0)
            val buf = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = ex.sampleTrackIndex
                if (track < 0) break
                val t = ex.sampleTime
                if (t > endUs) {
                    // Other tracks may still have samples before the end.
                    if (!ex.advance()) break
                    continue
                }
                info.offset = 0
                info.size = ex.readSampleData(buf, 0)
                if (info.size < 0) break
                info.presentationTimeUs = (t - firstUs).coerceAtLeast(0)
                info.flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                map[track]?.let { muxer.writeSampleData(it, buf, info) }
                if (!ex.advance()) break
            }
            muxer.stop()
            return out.length() > 0
        } catch (t: Throwable) {
            Log.w(TAG, "trim failed", t)
            return false
        } finally {
            runCatching { muxer?.release() }
            runCatching { ex.release() }
        }
    }

    /** Length of a video in ms (0 if unknown). */
    fun durationMs(path: String): Long = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    }.getOrDefault(0L)
}
