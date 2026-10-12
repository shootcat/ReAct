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

    private val lava = mt.idOrNull("lava") ?: -1
    private val fire = mt.idOrNull("fire") ?: -1
    private val smoke = mt.idOrNull("smoke") ?: -1
    private val glass = mt.idOrNull("glass") ?: -1
    private val steam = mt.idOrNull("steam") ?: -1
    private val grass = mt.idOrNull("grass") ?: -1
    private val fish = mt.idOrNull("fish") ?: -1
    private val bird = mt.idOrNull("bird") ?: -1
    private val firefly = mt.idOrNull("firefly") ?: -1
    private val firedamp = mt.idOrNull("firedamp") ?: -1

    /**
     * Light for drawing (2b). While the world runs, the field the simulation works out every few steps is
     * used; while it is paused (and before the first step) the picture works out its own, so whatever is
     * placed lights up at once.
     */
    private val ownLight = Light(world)
    private var light = ownLight

    /** Without lighting everything is drawn at full brightness (overviews of the materials). */
    var lit = true

    /** Brightness in total darkness; little scenes in the discovery book lift it so a dark cave still shows. */
    var ambient = AMBIENT
        set(value) {
            field = value
            backdropMin = maxOf(BACKDROP_MIN, value)
        }
    private var backdropMin = BACKDROP_MIN

    /** Brightness per block of the light field, and per cell, smoothly in between. */
    private val factor = FloatArray(light.width * light.height)
    private val shadeRows = FloatArray(light.height * world.width)
    private val shade = FloatArray(world.size)
    private val blockX = IntArray(world.width) { x -> lattice(x, light.width).first }
    private val blockTx = FloatArray(world.width) { x -> lattice(x, light.width).second }

    /** Fish and birds seen while drawing: tails and wings are added afterwards. */
    private var critters = IntArray(64)
    private var critterCount = 0

    /** Glowing melts drawn by their temperature: lava, molten metal. */
    private val glowingLiquid = BooleanArray(mt.count) { mt.kind[it] == Kind.LIQUID && mt.emitsLight[it] > 0f }

    /** Clear liquids drawn with depth and a moving surface edge: water and salt water. */
    private val clearLiquid = BooleanArray(mt.count) { mt.kind[it] == Kind.LIQUID && mt.emitsLight[it] == 0f && mt.opacity[it] < 1f }

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

    /** Works out how bright each cell is from the light field: dark caves, lit surroundings of fire. */
    private fun computeShade(running: Boolean) {
        if (!lit) {
            factor.fill(1f)
            shade.fill(1f)
            return
        }
        light = if (running && world.step > 0) world.light else ownLight.also { it.compute() }
        for (b in factor.indices) {
            // the eye sees shade brighter than the bare amount of light: 1 - (1 - l)²; darkness stays dark
            val l = 1f - minOf(1f, light.shown(b))
            factor[b] = ambient + (1f - ambient) * (1f - l * l)
        }
        val w = world.width
        val lw = light.width
        val lh = light.height
        for (by in 0 until lh) {
            val r = by * lw
            val o = by * w
            for (x in 0 until w) {
                val a = factor[r + blockX[x]]
                val b = factor[r + minOf(blockX[x] + 1, lw - 1)]
                shadeRows[o + x] = a + (b - a) * blockTx[x]
            }
        }
        for (y in 0 until world.height) {
            val (y0, t) = lattice(y, lh)
            val ra = y0 * w
            val rb = minOf(y0 + 1, lh - 1) * w
            val o = y * w
            for (x in 0 until w) {
                val a = shadeRows[ra + x]
                shade[o + x] = a + (shadeRows[rb + x] - a) * t
            }
        }
    }

    /** Draws the world into [out]; [running] says the simulation is stepping, so its own light field is fresh. */
    fun render(out: IntArray, frame: Int, running: Boolean = false) {
        val w = world.width
        val size = world.size
        val mat = world.mat
        val temp = world.temp
        val flags = world.flags
        val variant = world.variant
        val life = world.life
        computeShade(running)
        critterCount = 0
        depth.fill(0)
        var x = -1
        for (i in 0 until size) {
            x++
            if (x == w) x = 0
            val m = mat[i].toInt()
            if (clearLiquid[m]) depth[x]++ else depth[x] = 0
            val s = shade[i]
            if (m == AIR) {
                val bg = if (s < 0.995f) darken(backdrop[i], maxOf(s, backdropMin)) else backdrop[i]
                // gaps inside falling water or a flame are drawn filled, so streams and flames hold together
                val up = if (i >= w) mat[i - w].toInt() else AIR
                val down = if (i < size - w) mat[i + w].toInt() else AIR
                out[i] = if ((clearLiquid[up] || up == fire) && down == up) {
                    if (up == fire) mix(flameRamp[FLAME_STEPS / 3], bg, 0.5f) else mix(scale(palette[up * SHADES + 8], s), bg, 0.35f)
                } else if (down == grass && (variant[i + w].toInt() and 3) != 0) {
                    // blades of grass stick up into the air
                    mix(scale(palette[grass * SHADES + (variant[i + w].toInt() and (SHADES - 1))], 1.15f * s), bg, 0.35f)
                } else {
                    bg
                }
                continue
            }
            val v = variant[i].toInt() and 0xFF
            var c = palette[m * SHADES + (v and (SHADES - 1))]
            val see = mt.opacity[m] < 1f || m == firefly
            val bg = if (!see) 0 else if (s < 0.995f) darken(backdrop[i], maxOf(s, backdropMin)) else backdrop[i]
            val f = flags[i].toInt()
            val t = temp[i]
            if (clearLiquid[m]) {
                // a light, gently moving edge at the surface; darker with depth
                val d = depth[x]
                c = when {
                    d == 1 -> scale(c, 1.32f + 0.12f * wave((x * 3 + (frame shr 1)) and 63))
                    d == 2 -> scale(c, 1.12f)
                    else -> scale(c, 1f - minOf(d, 48) * 0.005f)
                }
            } else when (mt.pattern[m]) {
                Pattern.PLANKS -> {
                    // grain along the planks
                    val y = i / w
                    if (((y + (x shr 3)) and 3) == 0) c = scale(c, 0.82f)
                }
                Pattern.BRICK -> {
                    // bricks of 6 × 3 cells in staggered rows, light mortar between them
                    val y = i / w
                    val row = y / 3
                    val bx = (x + (if (row and 1 == 0) 0 else 3)) % 6
                    if (y % 3 == 2 || bx == 5) c = mix(c, 0xFFB9AFA4.toInt(), 0.55f)
                }
                Pattern.MASONRY -> {
                    // larger blocks with dark joints
                    val y = i / w
                    val row = y / 5
                    val bx = (x + (if (row and 1 == 0) 0 else 4)) % 9
                    if (y % 5 == 4 || bx == 8) c = scale(c, 0.72f)
                }
                Pattern.SHEEN -> {
                    // brushed metal: a soft diagonal shine
                    val y = i / w
                    c = scale(c, 0.9f + 0.25f * wave((x + y * 2) and 63))
                }
                Pattern.PORES -> {
                    // porous basalt: gas bubbles of one to three cells, uneven tone; it goes with the cells
                    val left = x > 0 && mat[i - 1].toInt() == m && (variant[i - 1].toInt() and 15) == 0
                    val up = i >= w && mat[i - w].toInt() == m && (variant[i - w].toInt() and 15) == 0
                    c = when {
                        (v and 15) < 2 -> scale(c, 0.45f)
                        left || up -> scale(c, 0.55f)
                        else -> scale(c, 0.86f + ((v shr 4) and 7) * 0.04f)
                    }
                }
            }
            if (mt.speckle[m] > 0f && (v ushr 4) < (mt.speckle[m] * 16f).toInt()) {
                // inclusions that catch the light (ore), some of them glinting
                c = mt.speckleColor[m]
                if (((v * 31 + frame) and 127) == 0) c = scale(c, 1.35f)
            }
            if (glowingLiquid[m]) {
                // orange-red melt with brighter veins; darker as it cools towards setting
                c = mix(glowAt(minOf(t, 1150f) - 120f + ((v and 31) - 16) * 9f), c, if (m == lava) 0.3f else 0.7f)
                if (((v + frame) and 63) == 0) c = scale(c, 1.25f)
            }
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
                    out[i] = mix(c, bg, if (n <= 1) 0.3f else 0.05f)
                    continue
                }
                smoke -> {
                    val fade = (life[i] / 90f).coerceIn(0f, 1f)
                    out[i] = mix(scale(c, s), bg, 1f - mt.opacity[m] * fade)
                    continue
                }
                firedamp -> {
                    // hardly to be seen: the air over it shimmers a little
                    val shimmer = 0.04f + 0.1f * wave((x * 5 + (i / w) * 3 + frame * 2) and 63)
                    out[i] = mix(scale(bg, 1.06f), bg, 1f - shimmer)
                    continue
                }
                firefly -> {
                    // a soft blinking glow
                    out[i] = if (((v * 7 + frame) and 63) < 44) c else mix(c, bg, 0.55f)
                    continue
                }
                fish, bird -> {
                    if (critterCount == critters.size) critters = critters.copyOf(critterCount * 2)
                    critters[critterCount++] = i
                }
            }
            if (f and Flag.WET != 0) c = scale(c, 0.68f)
            if (f and Flag.BURNING != 0) {
                // glowing embers under the flames: dark char with orange-red glow that flickers
                val flick = ((v * 7 + frame * 3) and 31) / 31f
                c = mix(glowAt(maxOf(t, 650f) + flick * 300f), scale(c, 0.35f), 0.08f + 0.4f * (1f - flick))
            } else if (t > GLOW_FROM && !glowingLiquid[m] && mt.kind[m] != Kind.GAS) {
                val k = ((t - GLOW_FROM) / 650f).coerceIn(0f, 0.9f)
                c = mix(glowAt(t), c, k)
            }
            // light of its own (fire, embers, lava, glowing heat) is not darkened; the cut face of solid ground
            // is always shown, darker away from the light, while air, water and glass show the real light
            if (s < 0.995f && mt.glow[m] == 0f && f and Flag.BURNING == 0 && t < GLOW_FROM) {
                c = darken(c, if (mt.lightPass[m] == 0f && s < CUT_FACE) CUT_FACE else s)
            }
            val a = mt.opacity[m]
            out[i] = if (a >= 1f) c else if (m == glass && ((v and 15) == 0)) scale(mix(c, bg, 1f - a), 1.3f) else mix(c, bg, 1f - a)
        }
        drawCritters(out, frame)
    }

    /** A tail behind each fish, wings on each bird (flapping while it flies), drawn into the air or water around them. */
    private fun drawCritters(out: IntArray, frame: Int) {
        val w = world.width
        val mat = world.mat
        val flags = world.flags
        for (k in 0 until critterCount) {
            val i = critters[k]
            val x = i % w
            val m = mat[i].toInt()
            val f = flags[i].toInt()
            val c = out[i]
            if (m == fish) {
                val j = if (f and Flag.MARK != 0) i - 1 else i + 1
                val tx = if (f and Flag.MARK != 0) x - 1 else x + 1
                if (tx in 0 until w && clearLiquid[mat[j].toInt()]) out[j] = mix(scale(c, 0.8f), out[j], 0.35f)
            } else if (f and Flag.MARK != 0) {
                // flying: wings up and down in turn
                val up = ((frame shr 2) + i) and 1 == 0
                val row = if (up) i - w else i
                if (row >= 0) {
                    if (x > 0 && mat[row - 1].toInt() == AIR) out[row - 1] = c
                    if (x < w - 1 && mat[row + 1].toInt() == AIR) out[row + 1] = c
                }
            } else if (i >= w && mat[i - w].toInt() == AIR) {
                // sitting: a head above the body
                out[i - w] = scale(c, 1.25f)
            }
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
        val animal = mt.animal
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
                    // the warm glow of fire and heat; fireflies light up their surroundings through the light field only
                    if (e > 0f && !animal[m] && (!mt.flammable[m] || mt.kind[m] == Kind.GAS || flags[i].toInt() and Flag.BURNING != 0)) lightSum[b] += e
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
                // smoke in a dark cave is dark too
                val lx = minOf((b % layerWidth) * 2 + 1, this.light.width - 1)
                val ly = minOf((b / layerWidth) * 2 + 1, this.light.height - 1)
                val c = scale(mix(smokeColor or OPAQUE, steamColor or OPAQUE, st / total), factor[ly * this.light.width + lx])
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
        /** Brightness in total darkness: an unlit cave is dim, never a black hole (4a.1.4). */
        private const val AMBIENT = 0.25f

        /** The least brightness of the scenery behind the world (the cave wall) in the dark. */
        private const val BACKDROP_MIN = 0.7f

        /** The least brightness of solid ground in the dark, so the ground stays readable as a cut. */
        private const val CUT_FACE = 0.58f

        /** Where cell [p] lies between the centres of blocks of 2 cells: the block left of it and how far on. */
        private fun lattice(p: Int, blocks: Int): Pair<Int, Float> {
            val f = (p - 0.5f) / 2f
            if (f <= 0f) return 0 to 0f
            val b = f.toInt()
            return if (b >= blocks - 1) (blocks - 1) to 0f else b to (f - b)
        }

        const val SHADES = 16
        private const val FLAME_STEPS = 32
        const val BLOCK = 4
        const val FLAME_BLOCK = 2
        private const val GLOW_STEPS = 128
        private const val GLOW_FROM = 450f
        private const val GLOW_TO = 1400f
        const val OPAQUE = 0xFF000000.toInt()

        /** [c] darkened to [f] (0..1) of its brightness. */
        fun darken(c: Int, f: Float): Int {
            val k = (f * 256f).toInt()
            val r = (((c shr 16) and 0xFF) * k) shr 8
            val g = (((c shr 8) and 0xFF) * k) shr 8
            val b = ((c and 0xFF) * k) shr 8
            return OPAQUE or (r shl 16) or (g shl 8) or b
        }

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
                        Renderer.scale(0x463a32 or Renderer.OPAQUE, (0.75f + 0.5f * tex) * (1f - 0.25f * depth))
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
