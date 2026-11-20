package com.glasschat.media

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Moves an MP4's index ("moov") in front of its media data, like
 * qt-faststart. Phones write it at the end; WhatsApp streams video notes
 * while downloading and can't play them without the index first.
 */
object Faststart {
    private const val TAG = "Faststart"

    private data class Box(val type: String, val start: Long, val size: Long)

    private fun topLevel(f: RandomAccessFile): List<Box>? {
        val out = ArrayList<Box>()
        var pos = 0L
        val len = f.length()
        val head = ByteArray(16)
        while (pos + 8 <= len) {
            f.seek(pos)
            f.readFully(head, 0, 8)
            var size = ByteBuffer.wrap(head, 0, 4).int.toLong() and 0xffffffffL
            val type = String(head, 4, 4, Charsets.US_ASCII)
            if (size == 1L) {
                f.readFully(head, 8, 8)
                size = ByteBuffer.wrap(head, 8, 8).long
            } else if (size == 0L) {
                size = len - pos
            }
            if (size < 8 || pos + size > len) return null
            out.add(Box(type, pos, size))
            pos += size
        }
        return out
    }

    /** Rewrites [src] into [dst] with moov first. Returns false if nothing was needed or it couldn't. */
    fun apply(src: File, dst: File): Boolean = runCatching {
        RandomAccessFile(src, "r").use { f ->
            val boxes = topLevel(f) ?: return false
            val moov = boxes.firstOrNull { it.type == "moov" } ?: return false
            val mdatIndex = boxes.indexOfFirst { it.type == "mdat" }
            if (mdatIndex < 0 || boxes.indexOf(moov) < mdatIndex) return false // already fine
            if (moov.size > 64L shl 20) return false
            val moovBytes = ByteArray(moov.size.toInt())
            f.seek(moov.start)
            f.readFully(moovBytes)
            if (!shiftOffsets(ByteBuffer.wrap(moovBytes), 0, moovBytes.size, moov.size)) return false
            dst.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                fun copy(b: Box) {
                    f.seek(b.start)
                    var left = b.size
                    while (left > 0) {
                        val n = f.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) error("short read")
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
                boxes.take(mdatIndex).filter { it.type != "moov" }.forEach(::copy)
                out.write(moovBytes)
                boxes.drop(mdatIndex).filter { it.type != "moov" }.forEach(::copy)
            }
            true
        }
    }.onFailure { Log.w(TAG, "faststart failed", it) }.getOrDefault(false)

    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "mvex")

    /** Adds [delta] to every chunk offset (stco / co64) inside the boxes in [from, to). */
    private fun shiftOffsets(b: ByteBuffer, from: Int, to: Int, delta: Long): Boolean {
        var pos = from
        while (pos + 8 <= to) {
            val size = b.getInt(pos).toLong() and 0xffffffffL
            val type = String(ByteArray(4) { b.get(pos + 4 + it) }, Charsets.US_ASCII)
            if (size < 8 || pos + size > to) return false
            val end = (pos + size).toInt()
            when {
                type == "moov" && pos == from && from == 0 -> if (!shiftOffsets(b, pos + 8, end, delta)) return false
                type in containers -> if (!shiftOffsets(b, pos + 8, end, delta)) return false
                type == "stco" -> {
                    val n = b.getInt(pos + 12)
                    for (i in 0 until n) {
                        val at = pos + 16 + i * 4
                        val v = (b.getInt(at).toLong() and 0xffffffffL) + delta
                        if (v > 0xffffffffL) return false
                        b.putInt(at, v.toInt())
                    }
                }
                type == "co64" -> {
                    val n = b.getInt(pos + 12)
                    for (i in 0 until n) {
                        val at = pos + 16 + i * 8
                        b.putLong(at, b.getLong(at) + delta)
                    }
                }
            }
            pos = end
        }
        return true
    }
}
