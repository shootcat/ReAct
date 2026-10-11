package com.shootcat.react.sim

import com.shootcat.react.sim.MaterialTable.Companion.AIR

/**
 * Turns the world into ARGB pixels, one per cell (Leitfaden V3, 4.6): the app scales them up without
 * smoothing. Each particle keeps the shade it was born with, so surfaces look grainy and natural; hot
 * material glows, burning material flickers, water, glass, smoke and steam let the background through.
 */
class Renderer(private val world: World, private val backdrop: IntArray) {

    private val mt = world.materials
    private val palette = IntArray(mt.count * SHADES)
    private val glow = IntArray(GLOW_STEPS)

    private val water = mt.idOrNull("water") ?: -1
    private val lava = mt.idOrNull("lava") ?: -1
    private val fire = mt.idOrNull("fire") ?: -1
    private val wood = mt.idOrNull("wood") ?: -1
    private val smoke = mt.idOrNull("smoke") ?: -1
    private val glass = mt.idOrNull("glass") ?: -1
    private val steam = mt.idOrNull("steam") ?: -1
    private val charcoal = mt.idOrNull("charcoal") ?: -1

    init {
        require(backdrop.size == world.size)
        for (m in 0 until mt.count) {
            val c = mt.color[m]
            val v = mt.colorVariance[m]
            for (k in 0 until SHADES) {
                val f = 1f + (k / (SHADES - 1f) - 0.5f) * 2f * v
                palette[m * SHADES + k] = scale(c, f)
            }
        }
        // incandescence from dark red to yellow-white, GLOW_FROM..GLOW_TO degrees
        val stops = intArrayOf(0x3a0800, 0x8a1c04, 0xd84a0c, 0xff8a1e, 0xffc35a, 0xffeab0)
        for (k in 0 until GLOW_STEPS) {
            val p = k / (GLOW_STEPS - 1f) * (stops.size - 1)
            val a = p.toInt().coerceAtMost(stops.size - 2)
            glow[k] = mix(stops[a] or OPAQUE, stops[a + 1] or OPAQUE, p - a)
        }
    }

    /** How many water cells lie above the current one, per column, while drawing top to bottom. */
    private val depth = IntArray(world.width)

    /** Flame colours from the cool red rim to the yellow-white core (3.2). */
    private val flameRamp = IntArray(FLAME_STEPS).also { ramp ->
        val stops = intArrayOf(0x8e1606, 0xd23a12, 0xff6a14, 0xff9a26, 0xffc848, 0xffe596)
        for (k in ramp.indices) {
            val p = k / (FLAME_STEPS - 1f) * (stops.size - 1)
            val a = p.toInt().coerceAtMost(stops.size - 2)
            ramp[k] = mix(stops[a] or OPAQUE, stops[a + 1] or OPAQUE, p - a)
        }
    }

    fun render(out: IntArray, frame: Int) {
        val w = world.width
        val size = world.size
        val mat = world.mat
        val temp = world.temp
        val flags = world.flags
        val variant = world.variant
        val life = world.life
        depth.fill(0)
        var x = -1
        for (i in 0 until size) {
            x++
            if (x == w) x = 0
            val m = mat[i].toInt()
            if (m == water) depth[x]++ else depth[x] = 0
            if (m == AIR) {
                // gaps inside falling water or a flame are drawn filled, so streams and flames hold together
                val up = if (i >= w) mat[i - w].toInt() else AIR
                out[i] = if ((up == water || up == fire) && i < size - w && mat[i + w].toInt() == up) {
                    if (up == water) mix(palette[water * SHADES + 8], backdrop[i], 0.35f) else mix(flameRamp[FLAME_STEPS / 3], backdrop[i], 0.5f)
                } else {
                    backdrop[i]
                }
                continue
            }
            val v = variant[i].toInt() and 0xFF
            var c = palette[m * SHADES + (v and (SHADES - 1))]
            val f = flags[i].toInt()
            val t = temp[i]
            when (m) {
                fire -> {
                    // a flame body: hot and yellow inside, orange, red at the rim, flickering, cooler as it dies
                    var n = 0
                    if (x > 0 && mat[i - 1].toInt() == fire) n++
                    if (x < w - 1 && mat[i + 1].toInt() == fire) n++
                    if (i >= w && mat[i - w].toInt() == fire) n++
                    if (i < size - w && mat[i + w].toInt() == fire) n++
                    val age = (life[i] / mt.burnSteps[m].toFloat()).coerceIn(0f, 1.3f)
                    val flick = ((v * 13 + frame * 5) and 15) / 15f
                    val heat = (n * 0.17f + age * 0.35f + flick * 0.22f).coerceIn(0f, 1f)
                    c = flameRamp[(heat * (FLAME_STEPS - 1)).toInt()]
                    out[i] = mix(c, backdrop[i], if (n <= 1) 0.3f else 0.05f)
                    continue
                }
                lava -> {
                    // orange-red melt with brighter veins; darker as it cools towards setting
                    c = mix(glowAt(minOf(t, 1150f) - 120f + ((v and 31) - 16) * 9f), c, 0.3f)
                    if (((v + frame) and 63) == 0) c = scale(c, 1.25f)
                }
                water -> {
                    // a light, gently moving edge at the surface; darker with depth
                    val d = depth[x]
                    c = when {
                        d == 1 -> scale(c, 1.32f + 0.12f * wave((x * 3 + (frame shr 1)) and 63))
                        d == 2 -> scale(c, 1.12f)
                        else -> scale(c, 1f - minOf(d, 48) * 0.009f)
                    }
                }
                wood -> {
                    // grain along the planks
                    val y = i / w
                    val x = i - y * w
                    if (((y + (x shr 3)) and 3) == 0) c = scale(c, 0.82f)
                }
                smoke -> {
                    val fade = (life[i] / 90f).coerceIn(0f, 1f)
                    out[i] = mix(c, backdrop[i], 1f - mt.opacity[m] * fade)
                    continue
                }
            }
            if (f and Flag.WET != 0) c = scale(c, 0.68f)
            if (f and Flag.BURNING != 0) {
                // glowing embers under the flames: dark char with orange-red glow that flickers
                val flick = ((v * 7 + frame * 3) and 31) / 31f
                c = mix(glowAt(maxOf(t, 650f) + flick * 300f), scale(c, 0.35f), 0.2f + 0.45f * (1f - flick))
            } else if (t > GLOW_FROM && m != lava && mt.kind[m] != Kind.GAS) {
                val k = ((t - GLOW_FROM) / 650f).coerceIn(0f, 0.9f)
                c = mix(glowAt(t), c, k)
            }
            val a = mt.opacity[m]
            out[i] = if (a >= 1f) c else if (m == glass && ((v and 15) == 0)) scale(mix(c, backdrop[i], 1f - a), 1.3f) else mix(c, backdrop[i], 1f - a)
        }
    }

    /** Width and height of the soft layers, in blocks of [BLOCK] × [BLOCK] cells. */
    val layerWidth = (world.width + BLOCK - 1) / BLOCK
    val layerHeight = (world.height + BLOCK - 1) / BLOCK
    private val smokeSum = FloatArray(layerWidth * layerHeight)
    private val steamSum = FloatArray(layerWidth * layerHeight)
    private val lightSum = FloatArray(layerWidth * layerHeight)
    private val tmp = FloatArray(layerWidth * layerHeight)
    private val smokeColor = mt.idOrNull("smoke")?.let { mt.color[it] } ?: 0x4d4a48
    private val steamColor = mt.idOrNull("steam")?.let { mt.color[it] } ?: 0xdde6ee

    /**
     * The soft layers drawn over the pixels, smoothly scaled and blended normally: [haze] is smoke and steam
     * as clouds, [light] the warm glow around fire, embers and lava. One pixel per block of cells.
     */
    fun renderLayers(haze: IntArray, light: IntArray) {
        val w = world.width
        val mat = world.mat
        val flags = world.flags
        val life = world.life
        smokeSum.fill(0f)
        steamSum.fill(0f)
        lightSum.fill(0f)
        for (y in 0 until world.height) {
            val row = (y / BLOCK) * layerWidth
            var i = y * w
            for (x in 0 until w) {
                val m = mat[i].toInt()
                if (m != AIR) {
                    val b = row + x / BLOCK
                    if (m == smoke) smokeSum[b] += (life[i] / 90f).coerceAtMost(1f)
                    else if (m == steam) steamSum[b] += 1f
                    val e = mt.emitsLight[m]
                    if (e > 0f && (m != charcoal || flags[i].toInt() and Flag.BURNING != 0)) lightSum[b] += e
                    else if (flags[i].toInt() and Flag.BURNING != 0) lightSum[b] += 0.5f
                }
                i++
            }
        }
        // spread without averaging: a single flame lights up the blocks around it, a lava lake a wide area
        spread(smokeSum)
        spread(steamSum)
        spread(lightSum)
        spread(lightSum)
        for (b in 0 until layerWidth * layerHeight) {
            val sm = smokeSum[b]
            val st = steamSum[b]
            val total = sm + st
            haze[b] = if (total <= 0.2f) 0 else {
                val c = mix(smokeColor or OPAQUE, steamColor or OPAQUE, st / total)
                val a = (minOf(0.8f, total * 0.03f) * 255f).toInt()
                (a shl 24) or (c and 0xFFFFFF)
            }
            val l = 1f - 1f / (1f + lightSum[b] * 0.02f)
            light[b] = if (l <= 0.01f) 0 else ((l * 0.42f * 255f).toInt() shl 24) or 0xff8c32
        }
    }

    /** Width and height of the flame layer, in blocks of [FLAME_BLOCK] × [FLAME_BLOCK] cells. */
    val flameWidth = (world.width + FLAME_BLOCK - 1) / FLAME_BLOCK
    val flameHeight = (world.height + FLAME_BLOCK - 1) / FLAME_BLOCK
    private val flameSum = FloatArray(flameWidth * flameHeight)
    private val flameTmp = FloatArray(flameWidth * flameHeight)

    /**
     * The flame body (3.2), drawn smoothly over the pixels: where flames are dense they merge into one
     * shape, yellow where they are thickest and youngest, orange, then red at the rim. Burning wood and
     * embers below add to it. One pixel per block of [FLAME_BLOCK] × [FLAME_BLOCK] cells.
     */
    fun renderFlames(out: IntArray) {
        val w = world.width
        val mat = world.mat
        val flags = world.flags
        val life = world.life
        flameSum.fill(0f)
        val burnSteps = if (fire >= 0) mt.burnSteps[fire].toFloat() else 1f
        for (y in 0 until world.height) {
            val row = (y / FLAME_BLOCK) * flameWidth
            var i = y * w
            for (x in 0 until w) {
                val m = mat[i].toInt()
                if (m == fire) flameSum[row + x / FLAME_BLOCK] += 0.5f + 0.5f * (life[i] / burnSteps).coerceAtMost(1f)
                else if (m != AIR && flags[i].toInt() and Flag.BURNING != 0) flameSum[row + x / FLAME_BLOCK] += 0.35f
                i++
            }
        }
        spread(flameSum, flameWidth, flameHeight, flameTmp)
        for (b in out.indices) {
            val f = flameSum[b]
            out[b] = if (f < 0.6f) 0 else {
                val c = flameRamp[((f - 0.6f) / 9f * (FLAME_STEPS - 1)).toInt().coerceIn(0, FLAME_STEPS - 1)]
                val alpha = minOf(0.85f, (f - 0.6f) * 0.2f)
                ((alpha * 255f).toInt() shl 24) or (c and 0xFFFFFF)
            }
        }
    }

    /** Sum over the 3 × 3 neighbourhood of each block, in place. */
    private fun spread(a: FloatArray, w: Int = layerWidth, h: Int = layerHeight, tmp: FloatArray = this.tmp) {
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (dx in -1..1) sum += a[y * w + (x + dx).coerceIn(0, w - 1)]
            tmp[y * w + x] = sum
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (dy in -1..1) sum += tmp[(y + dy).coerceIn(0, h - 1) * w + x]
            a[y * w + x] = sum
        }
    }

    /** A triangle wave 0..1..0 over 0..63, for the shimmer of the water surface. */
    private fun wave(p: Int): Float = (if (p < 32) p else 63 - p) / 31f

    private fun glowAt(t: Float): Int {
        val k = ((t - GLOW_FROM) / (GLOW_TO - GLOW_FROM) * (GLOW_STEPS - 1)).toInt().coerceIn(0, GLOW_STEPS - 1)
        return glow[k]
    }

    companion object {
        const val SHADES = 16
        private const val FLAME_STEPS = 32
        const val BLOCK = 4
        const val FLAME_BLOCK = 2
        private const val GLOW_STEPS = 128
        private const val GLOW_FROM = 450f
        private const val GLOW_TO = 1400f
        const val OPAQUE = 0xFF000000.toInt()

        fun scale(c: Int, f: Float): Int {
            val r = (((c shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
            return OPAQUE or (r shl 16) or (g shl 8) or b
        }

        /** [a] with [k] (0..1) of [b] mixed in. */
        fun mix(a: Int, b: Int, k: Float): Int {
            val j = 1f - k
            val r = (((a shr 16) and 0xFF) * j + ((b shr 16) and 0xFF) * k).toInt()
            val g = (((a shr 8) and 0xFF) * j + ((b shr 8) and 0xFF) * k).toInt()
            val bl = ((a and 0xFF) * j + (b and 0xFF) * k).toInt()
            return OPAQUE or (r shl 16) or (g shl 8) or bl
        }
    }
}

/**
 * The picture behind the world (4.6.4): sky with distant hills above the ground of the start world, a dark
 * cave wall below it. It stays as it is when the player digs or fills: it is scenery, not material.
 */
object Backdrop {

    fun of(world: World, seed: Long = Terrain.SEED): IntArray {
        val w = world.width
        val h = world.height
        val n = Noise(seed + 7)
        // the ground line: wooden things standing on it are not part of the scenery
        val mt = world.materials
        val ground = BooleanArray(mt.count) { it != AIR && mt.kind[it] != Kind.GAS && !mt.flammable[it] }
        val top = IntArray(w) { x -> (0 until h).firstOrNull { ground[world.material(x, it)] } ?: h }
        val horizon = top.average().toFloat()
        val out = IntArray(world.size)
        val skyTop = 0x6fa6d6
        val skyLow = 0xd6e8f0
        val far = 0x9fb9c4
        val near = 0x86a39a
        for (x in 0 until w) {
            val farLine = (horizon - 40f + (n.fbm(x / 40f, 1f, 3) - 0.5f) * 70f).toInt()
            val nearLine = (horizon - 8f + (n.fbm(x / 25f, 5f, 3) - 0.5f) * 40f).toInt()
            for (y in 0 until h) {
                val i = y * w + x
                out[i] = when {
                    y >= top[x] -> {
                        // cave wall: dark rock with soft patches, darker in the depth
                        val depth = ((y - top[x]) / 120f).coerceIn(0f, 1f)
                        val tex = n.fbm(x / 9f, y / 9f, 3)
                        Renderer.scale(0x3a302a or Renderer.OPAQUE, (0.75f + 0.5f * tex) * (1f - 0.35f * depth))
                    }
                    y >= nearLine -> Renderer.mix(near or Renderer.OPAQUE, skyLow or Renderer.OPAQUE, 0.25f)
                    y >= farLine -> Renderer.mix(far or Renderer.OPAQUE, skyLow or Renderer.OPAQUE, 0.35f)
                    else -> Renderer.mix(skyTop or Renderer.OPAQUE, skyLow or Renderer.OPAQUE, (y / horizon).coerceIn(0f, 1f))
                }
            }
        }
        return out
    }
}
