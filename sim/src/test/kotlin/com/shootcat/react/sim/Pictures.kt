package com.shootcat.react.sim

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes what the renderer makes of a world to a PNG under build/pictures, the way the app draws it: the
 * pixels scaled up without smoothing, the haze and glow layers smoothly on top.
 */
object Pictures {
    fun write(world: World, backdrop: IntArray, name: String, scale: Int = 3, frame: Int = 0): File {
        val renderer = Renderer(world, backdrop)
        val pixels = IntArray(world.size)
        renderer.render(pixels, frame)
        val lw = renderer.layerWidth
        val lh = renderer.layerHeight
        val haze = IntArray(lw * lh)
        val light = IntArray(lw * lh)
        renderer.renderLayers(haze, light)
        val img = BufferedImage(world.width * scale, world.height * scale, BufferedImage.TYPE_INT_RGB)
        val block = (Renderer.BLOCK * scale).toFloat()
        for (py in 0 until img.height) for (px in 0 until img.width) {
            var c = pixels[(py / scale) * world.width + px / scale]
            val h = sample(haze, lw, lh, px / block - 0.5f, py / block - 0.5f)
            val a = h[0] / 255f
            c = Renderer.mix(c, (h[1].toInt() shl 16) or (h[2].toInt() shl 8) or h[3].toInt(), a)
            val l = sample(light, lw, lh, px / block - 0.5f, py / block - 0.5f)
            c = Renderer.mix(c, (l[1].toInt() shl 16) or (l[2].toInt() shl 8) or l[3].toInt(), l[0] / 255f)
            img.setRGB(px, py, c and 0xFFFFFF)
        }
        val file = File("build/pictures/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
        return file
    }

    /** Bilinear sample of an ARGB layer as (a, r, g, b), with colour weighted by alpha like premultiplied drawing. */
    private fun sample(layer: IntArray, w: Int, h: Int, fx: Float, fy: Float): FloatArray {
        val x0 = kotlin.math.floor(fx).toInt()
        val y0 = kotlin.math.floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val out = FloatArray(4)
        for ((dx, dy, wgt) in listOf(Triple(0, 0, (1 - tx) * (1 - ty)), Triple(1, 0, tx * (1 - ty)), Triple(0, 1, (1 - tx) * ty), Triple(1, 1, tx * ty))) {
            val c = layer[(y0 + dy).coerceIn(0, h - 1) * w + (x0 + dx).coerceIn(0, w - 1)]
            val a = (c ushr 24) * wgt
            out[0] += a
            out[1] += ((c shr 16) and 0xFF) * a
            out[2] += ((c shr 8) and 0xFF) * a
            out[3] += (c and 0xFF) * a
        }
        if (out[0] > 0f) for (k in 1..3) out[k] /= out[0]
        return out
    }
}
