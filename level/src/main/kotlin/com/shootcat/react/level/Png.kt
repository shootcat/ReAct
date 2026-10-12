package com.shootcat.react.level

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/** An image as ARGB pixels, row by row. */
class Picture(val width: Int, val height: Int, val argb: IntArray) {
    operator fun get(x: Int, y: Int): Int = argb[y * width + x]

    /** The colour without alpha, 0xRRGGBB. */
    fun rgb(x: Int, y: Int): Int = argb[y * width + x] and 0xFFFFFF
}

/**
 * Reads PNG files without any platform library, so the same level files load on the phone and in the tests
 * (Leitfaden V3, 5.1: the ground is a painted PNG). Grey, RGB and palette images with or without alpha, all bit
 * depths up to 8 per channel, not interlaced: what painting programs write.
 */
object Png {

    fun read(bytes: ByteArray): Picture {
        require(bytes.size > 8 && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }) { "not a PNG file" }
        var pos = 8
        var width = 0
        var height = 0
        var depth = 0
        var type = 0
        var palette = IntArray(0)
        var alpha = IntArray(0)
        val data = ByteArrayOutputStream()
        while (pos + 8 <= bytes.size) {
            val length = int(bytes, pos)
            val name = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val at = pos + 8
            when (name) {
                "IHDR" -> {
                    width = int(bytes, at)
                    height = int(bytes, at + 4)
                    depth = bytes[at + 8].toInt() and 0xFF
                    type = bytes[at + 9].toInt() and 0xFF
                    require(bytes[at + 12].toInt() == 0) { "interlaced PNG files are not supported" }
                    require(depth in intArrayOf(1, 2, 4, 8)) { "PNG bit depth $depth is not supported" }
                }
                "PLTE" -> palette = IntArray(length / 3) { k ->
                    val r = bytes[at + 3 * k].toInt() and 0xFF
                    val g = bytes[at + 3 * k + 1].toInt() and 0xFF
                    val b = bytes[at + 3 * k + 2].toInt() and 0xFF
                    (r shl 16) or (g shl 8) or b
                }
                "tRNS" -> alpha = IntArray(length) { bytes[at + it].toInt() and 0xFF }
                "IDAT" -> data.write(bytes, at, length)
                "IEND" -> break
            }
            pos = at + length + 4
        }
        val channels = when (type) {
            0 -> 1
            2 -> 3
            3 -> 1
            4 -> 2
            6 -> 4
            else -> error("PNG colour type $type is not supported")
        }
        val bits = channels * depth
        val stride = (width * bits + 7) / 8
        val raw = inflate(data.toByteArray(), (stride + 1) * height)
        val bpp = maxOf(1, bits / 8)
        val rows = unfilter(raw, stride, height, bpp)
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * stride
            for (x in 0 until width) {
                fun sample(c: Int): Int {
                    if (depth == 8) return rows[row + x * channels + c].toInt() and 0xFF
                    val bit = x * depth
                    val b = rows[row + bit / 8].toInt() and 0xFF
                    return (b shr (8 - depth - bit % 8)) and ((1 shl depth) - 1)
                }
                out[y * width + x] = when (type) {
                    0 -> {
                        val v = sample(0) * 255 / ((1 shl depth) - 1)
                        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                    }
                    2 -> (0xFF shl 24) or (sample(0) shl 16) or (sample(1) shl 8) or sample(2)
                    3 -> {
                        val k = sample(0)
                        val a = if (k < alpha.size) alpha[k] else 0xFF
                        (a shl 24) or palette[k]
                    }
                    4 -> (sample(1) shl 24) or (sample(0) shl 16) or (sample(0) shl 8) or sample(0)
                    else -> (sample(3) shl 24) or (sample(0) shl 16) or (sample(1) shl 8) or sample(2)
                }
            }
        }
        return Picture(width, height, out)
    }

    private fun inflate(data: ByteArray, size: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArray(size)
        var n = 0
        while (n < size && !inflater.finished()) {
            val got = inflater.inflate(out, n, size - n)
            if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            n += got
        }
        inflater.end()
        require(n == size) { "PNG image data is cut short" }
        return out
    }

    /** Undoes the filter in front of each row (None, Sub, Up, Average, Paeth). */
    private fun unfilter(raw: ByteArray, stride: Int, height: Int, bpp: Int): ByteArray {
        val out = ByteArray(stride * height)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            val src = y * (stride + 1) + 1
            val dst = y * stride
            for (x in 0 until stride) {
                val v = raw[src + x].toInt() and 0xFF
                val a = if (x >= bpp) out[dst + x - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) out[dst - stride + x].toInt() and 0xFF else 0
                val c = if (x >= bpp && y > 0) out[dst - stride + x - bpp].toInt() and 0xFF else 0
                val r = when (filter) {
                    0 -> v
                    1 -> v + a
                    2 -> v + b
                    3 -> v + (a + b) / 2
                    4 -> v + paeth(a, b, c)
                    else -> error("unknown PNG filter $filter")
                }
                out[dst + x] = r.toByte()
            }
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun int(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
}
