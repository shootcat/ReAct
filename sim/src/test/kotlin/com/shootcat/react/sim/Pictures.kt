package com.shootcat.react.sim

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes what the renderer makes of a world to a PNG under build/pictures, the way the app draws it: the
 * pixels scaled up without smoothing, the bodies as turned pictures of their own, the clouds, haze and glow layers
 * smoothly on top.
 */
object Pictures {
    /** The whole world, or with [area] (x0, y0, x1, y1) only that part of it. */
    fun write(world: World, backdrop: IntArray, name: String, scale: Int = 3, frame: Int = 0, area: IntArray? = null, lit: Boolean = true, ambient: Float? = null, thermal: Boolean = false): File {
        val renderer = Renderer(world, backdrop)
        renderer.lit = lit
        renderer.thermal = thermal
        if (ambient != null) renderer.ambient = ambient
        renderer.bodySprites = true
        val pixels = IntArray(world.size)
        renderer.render(pixels, frame)
        val sprites = ArrayList<BodySprite>()
        val spriteCount = renderer.renderBodies(sprites)
        val lw = renderer.layerWidth
        val lh = renderer.layerHeight
        val haze = IntArray(lw * lh)
        val light = IntArray(lw * lh)
        renderer.renderLayers(haze, light)
        val fw = renderer.flameWidth
        val fh = renderer.flameHeight
        val flames = IntArray(fw * fh)
        renderer.renderFlames(flames)
        val cw = renderer.cloudWidth
        val ch = renderer.cloudHeight
        val clouds = IntArray(cw * ch)
        renderer.renderClouds(clouds, frame)
        val cloudBlock = (Renderer.CLOUD_BLOCK * scale).toFloat()
        val flameBlock = (Renderer.FLAME_BLOCK * scale).toFloat()
        val ax = area?.get(0) ?: 0
        val ay = area?.get(1) ?: 0
        val aw = if (area != null) area[2] - area[0] + 1 else world.width
        val ah = if (area != null) area[3] - area[1] + 1 else world.height
        val img = BufferedImage(aw * scale, ah * scale, BufferedImage.TYPE_INT_RGB)
        val block = (Renderer.BLOCK * scale).toFloat()
        // the cells, then the bodies over them
        val base = IntArray(img.width * img.height)
        for (qy in 0 until img.height) for (qx in 0 until img.width) base[qy * img.width + qx] = pixels[((qy + ay * scale) / scale) * world.width + (qx + ax * scale) / scale]
        for (k in 0 until spriteCount) drawSprite(sprites[k], base, img.width, img.height, ax * scale, ay * scale, scale)
        for (qy in 0 until img.height) for (qx in 0 until img.width) {
            val px = qx + ax * scale
            val py = qy + ay * scale
            var c = base[qy * img.width + qx]
            val cl = sample(clouds, cw, ch, px / cloudBlock - 0.5f, py / cloudBlock - 0.5f)
            c = Renderer.mix(c, (cl[1].toInt() shl 16) or (cl[2].toInt() shl 8) or cl[3].toInt(), cl[0] / 255f)
            val h = sample(haze, lw, lh, px / block - 0.5f, py / block - 0.5f)
            val a = h[0] / 255f
            c = Renderer.mix(c, (h[1].toInt() shl 16) or (h[2].toInt() shl 8) or h[3].toInt(), a)
            val fl = sample(flames, fw, fh, px / flameBlock - 0.5f, py / flameBlock - 0.5f)
            c = Renderer.mix(c, (fl[1].toInt() shl 16) or (fl[2].toInt() shl 8) or fl[3].toInt(), fl[0] / 255f)
            val l = sample(light, lw, lh, px / block - 0.5f, py / block - 0.5f)
            c = Renderer.mix(c, (l[1].toInt() shl 16) or (l[2].toInt() shl 8) or l[3].toInt(), l[0] / 255f)
            img.setRGB(qx, qy, c and 0xFFFFFF)
        }
        val file = File("build/pictures/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
        return file
    }

    /**
     * A body's picture into [into] ([w] × [h], whose (0, 0) is at picture pixel ([offX], [offY])): turned about its
     * centre of weight and placed where the body is, sampled smoothly, as the app draws it.
     */
    private fun drawSprite(sp: BodySprite, into: IntArray, w: Int, h: Int, offX: Int, offY: Int, scale: Int) {
        val k = scale.toFloat() / Renderer.SPRITE_SCALE
        val cx = (sp.x + 0.5f) * scale - offX
        val cy = (sp.y + 0.5f) * scale - offY
        val cs = kotlin.math.cos(sp.angle)
        val sn = kotlin.math.sin(sp.angle)
        val reach = (kotlin.math.hypot(maxOf(sp.pivotX, sp.w - sp.pivotX), maxOf(sp.pivotY, sp.h - sp.pivotY)) * k).toInt() + 2
        for (qy in maxOf(0, (cy - reach).toInt())..minOf(h - 1, (cy + reach).toInt())) {
            for (qx in maxOf(0, (cx - reach).toInt())..minOf(w - 1, (cx + reach).toInt())) {
                // back into the picture's own frame
                val dx = qx + 0.5f - cx
                val dy = qy + 0.5f - cy
                val sx = (cs * dx + sn * dy) / k + sp.pivotX
                val sy = (-sn * dx + cs * dy) / k + sp.pivotY
                val s = sample(sp.pixels, sp.w, sp.h, sx - 0.5f, sy - 0.5f, clampEdge = false)
                if (s[0] <= 0f) continue
                val c = (s[1].toInt() shl 16) or (s[2].toInt() shl 8) or s[3].toInt()
                into[qy * w + qx] = Renderer.mix(into[qy * w + qx], c, s[0] / 255f)
            }
        }
    }

    /** Bilinear sample of an ARGB layer as (a, r, g, b), with colour weighted by alpha like premultiplied drawing. */
    private fun sample(layer: IntArray, w: Int, h: Int, fx: Float, fy: Float, clampEdge: Boolean = true): FloatArray {
        val x0 = kotlin.math.floor(fx).toInt()
        val y0 = kotlin.math.floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val out = FloatArray(4)
        for ((dx, dy, wgt) in listOf(Triple(0, 0, (1 - tx) * (1 - ty)), Triple(1, 0, tx * (1 - ty)), Triple(0, 1, (1 - tx) * ty), Triple(1, 1, tx * ty))) {
            val xx = x0 + dx
            val yy = y0 + dy
            if (!clampEdge && (xx < 0 || yy < 0 || xx >= w || yy >= h)) continue
            val c = layer[yy.coerceIn(0, h - 1) * w + xx.coerceIn(0, w - 1)]
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
