package com.glasschat.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Records a voice note as a standard Ogg Opus file, the way WhatsApp's own
 * apps do: microphone → Android's Opus encoder → a carefully written Ogg
 * container (correct headers, granule positions and end-of-stream page).
 *
 * Android's built-in MediaRecorder "OGG" output plays here but some WhatsApp
 * apps reject it ("something is wrong with the audio file"); this writer
 * avoids that.
 */
class OpusRecorder(private val out: File) {
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile var failed: String? = null
        private set
    /** Peak level every ~100 ms, for the waveform. */
    val levels = ArrayList<Int>()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val codec = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS) }.getOrNull() ?: return false
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME_BYTES * 8))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            codec.release()
            return false
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            rec.startRecording()
        } catch (t: Throwable) {
            Log.w(TAG, "can't start", t)
            runCatching { rec.release() }
            runCatching { codec.release() }
            return false
        }
        running = true
        thread = Thread({ loop(rec, codec) }, "opus-recorder").apply { start() }
        return true
    }

    /** Stops and finishes the file. Returns false if nothing usable was written. */
    fun stop(): Boolean {
        running = false
        thread?.join(3_000)
        thread = null
        return failed == null && out.length() > 200
    }

    private fun loop(rec: AudioRecord, codec: MediaCodec) {
        val pcm = ByteArray(FRAME_BYTES)
        var samples = 0L
        var peak = 0
        var peakSamples = 0
        var eosSent = false
        val info = MediaCodec.BufferInfo()
        try {
            FileOutputStream(out).use { fos ->
                val ogg = OggOpusWriter(fos)
                var headersWritten = false
                var preSkip = 312
                var done = false
                while (!done) {
                    // Feed microphone audio (or end-of-stream once stopped).
                    if (!eosSent) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            buf.clear()
                            if (!running) {
                                codec.queueInputBuffer(inIdx, 0, 0, samples * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eosSent = true
                            } else {
                                val n = rec.read(pcm, 0, minOf(pcm.size, buf.remaining()))
                                if (n > 0) {
                                    buf.put(pcm, 0, n)
                                    codec.queueInputBuffer(inIdx, 0, n, samples * 1_000_000L / RATE, 0)
                                    // Waveform: peak level per 100 ms.
                                    var i = 0
                                    while (i + 1 < n) {
                                        val v = abs(((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt())
                                        if (v > peak) peak = v
                                        i += 2
                                    }
                                    samples += n / 2
                                    peakSamples += n / 2
                                    if (peakSamples >= RATE / 10) {
                                        synchronized(levels) { levels.add(peak) }
                                        peak = 0
                                        peakSamples = 0
                                    }
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, 0, samples * 1_000_000L / RATE, 0)
                                }
                            }
                        }
                    }
                    // Collect encoded Opus packets.
                    while (true) {
                        val outIdx = codec.dequeueOutputBuffer(info, if (eosSent) 10_000 else 0)
                        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            codec.outputFormat.getByteBuffer("csd-1")?.let { b ->
                                // Codec delay in nanoseconds → samples to skip at 48 kHz.
                                val ns = b.duplicate().order(ByteOrder.LITTLE_ENDIAN).long
                                if (ns in 1..100_000_000L) preSkip = (ns * RATE / 1_000_000_000L).toInt()
                            }
                            continue
                        }
                        if (outIdx < 0) break
                        val ob = codec.getOutputBuffer(outIdx)!!
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && info.size > 0) {
                            if (!headersWritten) {
                                ogg.writeHeaders(preSkip)
                                headersWritten = true
                            }
                            val packet = ByteArray(info.size)
                            ob.position(info.offset)
                            ob.get(packet)
                            ogg.addPacket(packet)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            done = true
                            break
                        }
                    }
                }
                if (!headersWritten) ogg.writeHeaders(preSkip)
                ogg.finish()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "recording failed", t)
            failed = t.message ?: "recording failed"
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    companion object {
        private const val TAG = "OpusRecorder"
        const val RATE = 48_000
        private const val FRAME_BYTES = 960 * 2 // 20 ms of 16-bit mono

        /** True if this phone has an Opus encoder (Android 10+ normally does). */
        fun available(): Boolean = runCatching {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).release()
        }.isSuccess
    }
}

/** Minimal Ogg container for one mono Opus stream (RFC 3533 + RFC 7845). */
private class OggOpusWriter(private val os: OutputStream) {
    private val serial = (System.nanoTime() and 0x7fffffff).toInt()
    private var seq = 0
    private var granule = 0L
    private val pending = ArrayList<ByteArray>()
    private var pendingSegments = 0
    private var preSkip = 312

    fun writeHeaders(preSkip: Int) {
        this.preSkip = preSkip
        val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1) // version
            put(1) // channels
            putShort(preSkip.toShort())
            putInt(OpusRecorder.RATE)
            putShort(0) // output gain
            put(0) // mapping family
        }.array()
        writePage(listOf(head), 0L, FLAG_BOS)
        val vendor = "glasschat".toByteArray(Charsets.US_ASCII)
        val tags = ByteBuffer.allocate(8 + 4 + vendor.size + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusTags".toByteArray(Charsets.US_ASCII))
            putInt(vendor.size)
            put(vendor)
            putInt(0)
        }.array()
        writePage(listOf(tags), 0L, 0)
        granule = preSkip.toLong()
    }

    fun addPacket(p: ByteArray) {
        val segs = p.size / 255 + 1
        if (pendingSegments + segs > 255 || pending.size >= 50) flush(0)
        pending.add(p)
        pendingSegments += segs
        granule += samplesIn(p)
    }

    fun finish() {
        if (pending.isEmpty()) {
            // An empty end page so players see the end of the stream.
            writePage(emptyList(), granule, FLAG_EOS)
        } else {
            flush(FLAG_EOS)
        }
        os.flush()
    }

    private fun flush(flags: Int) {
        if (pending.isEmpty()) return
        writePage(pending.toList(), granule, flags)
        pending.clear()
        pendingSegments = 0
    }

    private fun writePage(packets: List<ByteArray>, gran: Long, flags: Int) {
        val lacing = ArrayList<Int>()
        for (p in packets) {
            var n = p.size
            while (n >= 255) {
                lacing.add(255)
                n -= 255
            }
            lacing.add(n)
        }
        val bodySize = packets.sumOf { it.size }
        val page = ByteBuffer.allocate(27 + lacing.size + bodySize).order(ByteOrder.LITTLE_ENDIAN)
        page.put("OggS".toByteArray(Charsets.US_ASCII))
        page.put(0)
        page.put(flags.toByte())
        page.putLong(gran)
        page.putInt(serial)
        page.putInt(seq++)
        page.putInt(0) // CRC, filled in below
        page.put(lacing.size.toByte())
        lacing.forEach { page.put(it.toByte()) }
        packets.forEach { page.put(it) }
        val bytes = page.array()
        val crc = crc(bytes)
        bytes[22] = (crc and 0xff).toByte()
        bytes[23] = ((crc ushr 8) and 0xff).toByte()
        bytes[24] = ((crc ushr 16) and 0xff).toByte()
        bytes[25] = ((crc ushr 24) and 0xff).toByte()
        os.write(bytes)
    }

    companion object {
        const val FLAG_BOS = 0x02
        const val FLAG_EOS = 0x04

        private val table = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if ((r and 0x80000000.toInt()) != 0) (r shl 1) xor 0x04c11db7 else r shl 1 }
            r
        }

        fun crc(data: ByteArray): Int {
            var c = 0
            for (b in data) c = (c shl 8) xor table[((c ushr 24) xor (b.toInt() and 0xff)) and 0xff]
            return c
        }

        /** Samples (at 48 kHz) in one Opus packet, from its TOC byte (RFC 6716 §3.1). */
        fun samplesIn(p: ByteArray): Int {
            if (p.isEmpty()) return 0
            val toc = p[0].toInt() and 0xff
            val config = toc shr 3
            val frame = when {
                config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3]
                config < 16 -> if ((config and 1) == 0) 480 else 960
                else -> intArrayOf(120, 240, 480, 960)[config and 3]
            }
            val frames = when (toc and 3) {
                0 -> 1
                1, 2 -> 2
                else -> if (p.size > 1) p[1].toInt() and 0x3f else 1
            }
            return frame * frames
        }
    }
}
