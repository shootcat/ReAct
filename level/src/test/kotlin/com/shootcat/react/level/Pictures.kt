package com.shootcat.react.level

import com.shootcat.react.sim.BodySprite
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.Renderer
import com.shootcat.react.sim.World
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
    fun write(world: World, backdrop: IntArray, name: String, scale: Int = 3, frame: Int = 0, area: IntArray? = null, lit: Boolean = true, ambient: Float? = null): File {
        val renderer = Renderer(world, backdrop)
        renderer.lit = lit
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
        drawFolk(world, img, ax, ay, scale)
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

    /**
     * The inhabitants (Phase 5) as little figures in cells, as the app draws them more finely: legs, tunic, head; lying
     * when fainted; the village as a hut with a flag. What they lack shows as a square over the head (blue cold, cyan
     * thirst, grey air).
     */
    private fun drawFolk(world: World, img: BufferedImage, ax: Int, ay: Int, scale: Int) {
        fun cell(x: Int, y: Int, c: Int, x0: Float = 0f, x1: Float = 1f, y0: Float = 0f, y1: Float = 1f) {
            for (qy in ((y - ay + y0) * scale).toInt() until ((y - ay + y1) * scale).toInt()) for (qx in ((x - ax + x0) * scale).toInt() until ((x - ax + x1) * scale).toInt()) {
                if (qx in 0 until img.width && qy in 0 until img.height) img.setRGB(qx, qy, c and 0xFFFFFF)
            }
        }
        val folk = world.folk
        if (folk.homeX >= 0) {
            val hx = folk.homeX
            val hy = folk.homeY
            for (y in hy - 2..hy) for (x in hx - 2..hx + 2) cell(x, y, 0xB07A48)
            for (x in hx - 3..hx + 3) cell(x, hy - 3, 0xB23A2E, y0 = 0.5f)
            for (x in hx - 1..hx + 1) cell(x, hy - 3, 0xB23A2E)
            cell(hx, hy, 0x5A3A22, 0.2f, 0.8f, 0.2f)
            for (y in hy - 6..hy) cell(hx + 4, y, 0x6B6B6B, 0.4f, 0.6f)
            cell(hx + 5, hy - 6, 0xF2A93B, 0f, 1f, 0f, 0.6f)
        }
        val tunics = intArrayOf(0x2F80ED, 0xE0533D, 0x27AE60, 0xF2A93B, 0x9B51E0, 0x00A3A3)
        for (k in 0 until folk.count) {
            val x = folk.x(k)
            val y = folk.y(k)
            val tunic = tunics[(folk.id(k) - 1).mod(tunics.size)]
            when (folk.state(k)) {
                Folk.FAINT -> {
                    val d = folk.dir(k)
                    cell(x - d, y, 0x3D3A4B, y0 = 0.4f)
                    cell(x, y, tunic, y0 = 0.3f)
                    cell(x + d, y, 0xF2C29B, 0.1f, 0.9f, 0.2f)
                    cell(x + d, y - 1, 0xFFE066, 0.3f, 0.7f, 0.5f, 0.9f)
                }
                Folk.SWIM, Folk.SINK -> cell(x, y - 3, 0xF2C29B, 0.1f, 0.9f)
                else -> {
                    cell(x, y - 3, 0xF2C29B, 0.1f, 0.9f)
                    cell(x, y - 3, 0x5A3A22, 0.1f, 0.9f, 0f, 0.35f)
                    cell(x, y - 2, tunic, 0.05f, 0.95f)
                    cell(x, y - 1, tunic, 0.05f, 0.95f, 0f, 0.6f)
                    cell(x, y - 1, 0x3D3A4B, 0.15f, 0.85f, 0.6f)
                    cell(x, y, 0x3D3A4B, 0.1f, 0.4f)
                    cell(x, y, 0x3D3A4B, 0.6f, 0.9f)
                }
            }
            val needs = folk.needs(k)
            val icons = listOfNotNull(
                if (needs and Folk.Need.COLD != 0) 0x5FA8FF else null,
                if (needs and Folk.Need.THIRST != 0) 0x11B4D8 else null,
                if (needs and Folk.Need.AIR != 0) 0x6E6A66 else null,
            )
            for ((j, c) in icons.withIndex()) {
                val ix = x + j * 2 - (icons.size - 1)
                cell(ix, y - 6, 0xFFFFFF)
                cell(ix, y - 6, c, 0.2f, 0.8f, 0.2f, 0.8f)
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
