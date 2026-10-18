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

    /** Colours of the heat picture from HEAT_FROM to HEAT_TO degrees. */
    private val heatRamp = IntArray(HEAT_STEPS)

    private val lava = mt.idOrNull("lava") ?: -1
    private val fire = mt.idOrNull("fire") ?: -1
    private val smoke = mt.idOrNull("smoke") ?: -1
    private val glass = mt.idOrNull("glass") ?: -1
    private val steam = mt.idOrNull("steam") ?: -1
    private val cloud = mt.idOrNull("cloud") ?: -1
    private val grass = mt.idOrNull("grass") ?: -1
    private val fish = mt.idOrNull("fish") ?: -1
    private val bird = mt.idOrNull("bird") ?: -1
    private val firefly = mt.idOrNull("firefly") ?: -1
    private val firedamp = mt.idOrNull("firedamp") ?: -1

    /**
     * Light for drawing (2b): diffuse daylight from the whole sky (Phase 4b), so shadows are soft and fade with
     * the distance. Worked out as often as the simulation works out its own, and at once while it is paused
     * (and before the first step), so whatever is placed lights up at once.
     */
    private val light = Light(world, diffuse = true)
    private var lightStep = -1

    /** Without lighting everything is drawn at full brightness (overviews of the materials). */
    var lit = true

    /**
     * The heat picture (3b.7): every cell, air too, in the colour of its temperature, from deep blue in the
     * frost through green and yellow to red and white heat; the shapes of the materials show faintly.
     */
    var thermal = false

    /** Cells to light up, pulsing (the objects the hand could take, 3b.3), or null. */
    var highlight: BooleanArray? = null

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

    /**
     * Per column, the row from which the scenery behind is the cave wall (Phase 4b). Above it, under the open
     * sky, air, water and haze are not shaded: shadows fall on surfaces only, and do not stand as dark columns
     * in the air or in a lake; below it, in caves and under overhangs, it is as dark as the light says.
     */
    private val wallFrom = Backdrop.groundLine(world)

    /**
     * Bodies as pictures of their own (Phase 4b): [renderBodies] draws each body as an image in its own frame, to
     * be turned, smoothed and placed where it is; [render] then fills the body's cells in the grid with what is
     * around them. Off, the bodies are drawn cell by cell as before (overviews, the level list).
     */
    var bodySprites = false

    /** The colour each body cell got in the last [render], and its place in its body's own frame (for the grain). */
    private val bodyColour = IntArray(world.size)
    private val bodyLocal = IntArray(world.size)
    private val bodyStamp = IntArray(world.size)
    private var stamp = 0
    private var shownFrame = 0

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
        // incandescence (3b.7): dark red, red, orange, yellow, white, GLOW_FROM..GLOW_TO degrees
        for (k in 0 until GLOW_STEPS) glow[k] = ramp(GLOW_FROM + k * (GLOW_TO - GLOW_FROM) / (GLOW_STEPS - 1), GLOW_T, GLOW_C)
        for (k in 0 until HEAT_STEPS) heatRamp[k] = ramp(HEAT_FROM + k * (HEAT_TO - HEAT_FROM) / (HEAT_STEPS - 1), HEAT_T, HEAT_C)
    }

    /** The colour at [t] between the [colours] at the temperatures [at]. */
    private fun ramp(t: Float, at: FloatArray, colours: IntArray): Int {
        if (t <= at[0]) return colours[0] or OPAQUE
        for (k in 1 until at.size) {
            if (t <= at[k]) return mix(colours[k - 1] or OPAQUE, colours[k] or OPAQUE, (t - at[k - 1]) / (at[k] - at[k - 1]))
        }
        return colours.last() or OPAQUE
    }

    private fun heatColour(t: Float): Int = heatRamp[((t - HEAT_FROM) / (HEAT_TO - HEAT_FROM) * (HEAT_STEPS - 1)).toInt().coerceIn(0, HEAT_STEPS - 1)]

    /** The heat picture into [out] (3b.7). */
    private fun renderThermal(out: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = world.width
        val mat = world.mat
        val temp = world.temp
        val variant = world.variant
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            val m = mat[i].toInt()
            val h = heatColour(temp[i])
            out[i] = if (m == AIR || mt.kind[m] == Kind.GAS) {
                darken(h, 0.78f)
            } else {
                // the material as a faint grey relief, so its shape stays readable
                val c = palette[m * SHADES + ((variant[i].toInt() and 0xFF) and (SHADES - 1))]
                val grey = (((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)) / 10
                val edge = x > 0 && mat[i - 1].toInt() != m || i >= w && mat[i - w].toInt() != m
                mix(h, OPAQUE or (grey shl 16) or (grey shl 8) or grey, if (edge) 0.45f else 0.22f)
            }
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
    private fun computeShade(running: Boolean, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (!lit) {
            factor.fill(1f)
            shade.fill(1f)
            return
        }
        if (!running || world.step == 0 || lightStep < 0 || world.step - lightStep >= World.LIGHT_EVERY || world.step < lightStep) {
            light.compute()
            lightStep = world.step
        }
        for (b in factor.indices) {
            // the eye sees shade brighter than the bare amount of light: 1 - (1 - l)²; darkness stays dark
            val l = 1f - minOf(1f, light.shown(b))
            factor[b] = ambient + (1f - ambient) * (1f - l * l)
        }
        val w = world.width
        val lw = light.width
        val lh = light.height
        val by0 = maxOf(0, lattice(y0, lh).first)
        val by1 = minOf(lh - 1, lattice(y1, lh).first + 1)
        for (by in by0..by1) {
            val r = by * lw
            val o = by * w
            for (x in x0..x1) {
                val a = factor[r + blockX[x]]
                val b = factor[r + minOf(blockX[x] + 1, lw - 1)]
                shadeRows[o + x] = a + (b - a) * blockTx[x]
            }
        }
        for (y in y0..y1) {
            val (ly, t) = lattice(y, lh)
            val ra = ly * w
            val rb = minOf(ly + 1, lh - 1) * w
            val o = y * w
            for (x in x0..x1) {
                val a = shadeRows[ra + x]
                shade[o + x] = a + (shadeRows[rb + x] - a) * t
            }
        }
    }

    /**
     * Draws the world into [out]; [running] says the simulation is stepping, so its own light field is fresh.
     * Only the cells from (x0, y0) to (x1, y1) are drawn (the part on screen), the rest of [out] stays as it was.
     */
    fun render(out: IntArray, frame: Int, running: Boolean = false, x0: Int = 0, y0: Int = 0, x1: Int = world.width - 1, y1: Int = world.height - 1) {
        val w = world.width
        val size = world.size
        val mat = world.mat
        val temp = world.temp
        val flags = world.flags
        val variant = world.variant
        val life = world.life
        if (thermal) {
            renderThermal(out, x0, y0, x1, y1)
            return
        }
        computeShade(running, x0, y0, x1, y1)
        critterCount = 0
        shownFrame = frame
        // where each body cell lies in its body's own frame: the grain of wood goes with the body (Phase 4b)
        if (++stamp == Int.MAX_VALUE) {
            bodyStamp.fill(0)
            stamp = 1
        }
        world.bodies.each { b ->
            for (k in 0 until b.n) {
                val c = b.at[k]
                bodyLocal[c] = ((b.lx[k] - b.ox) shl 16) or (b.ly[k] - b.oy)
                bodyStamp[c] = stamp
            }
        }
        // the wind (3c.3): grass waves along it, faint streaks run through the open air
        val wn = world.windNow
        val windDir = if (wn > 0f) 1 else if (wn < 0f) -1 else 0
        val windAbs = if (wn > 0f) wn else -wn
        val windRun = frame * (1 + (windAbs * 4f).toInt()) * windDir
        val streaks = if (windAbs >= STREAK_WIND) (windAbs * STREAK_DENSITY).toInt() else 0
        // how deep in clear water the first row lies, for each column
        for (x in x0..x1) {
            var d = 0
            var j = (y0 - 1) * w + x
            while (j >= 0 && d < MAX_DEPTH && clearLiquid[mat[j].toInt()]) {
                d++
                j -= w
            }
            depth[x] = d
        }
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            val m = mat[i].toInt()
            if (clearLiquid[m]) depth[x]++ else depth[x] = 0
            val s = shade[i]
            // under the open sky the air and what is seen through it are not shaded (Phase 4b)
            val sa = if (y < wallFrom[x]) 1f else s
            if (m == AIR) {
                val bg = if (sa < 0.995f) darken(backdrop[i], maxOf(sa, backdropMin)) else backdrop[i]
                // gaps inside falling water or a flame are drawn filled, so streams and flames hold together
                val up = if (i >= w) mat[i - w].toInt() else AIR
                val down = if (i < size - w) mat[i + w].toInt() else AIR
                out[i] = if ((clearLiquid[up] || up == fire) && down == up) {
                    if (up == fire) mix(flameRamp[FLAME_STEPS / 3], bg, 0.5f) else mix(scale(palette[up * SHADES + 8], s), bg, 0.35f)
                } else if (down == grass && (variant[i + w].toInt() and 3) != 0) {
                    // blades of grass stick up into the air; in the wind a lighter wave runs over them
                    val lean = windDir != 0 && ((x * 2 - windRun) and 31) < 6 + (windAbs * 10f).toInt()
                    mix(scale(palette[grass * SHADES + (variant[i + w].toInt() and (SHADES - 1))], (if (lean) 1.4f else 1.15f) * s), bg, 0.35f)
                } else if (streaks > 0 && sa > 0.6f && streak(x - windRun, i / w, streaks)) {
                    mix(0xFFFFFFFF.toInt(), bg, 0.82f)
                } else {
                    bg
                }
                continue
            }
            val v = variant[i].toInt() and 0xFF
            var c = palette[m * SHADES + (v and (SHADES - 1))]
            val see = mt.opacity[m] < 1f || m == firefly
            val bg = if (!see) 0 else if (sa < 0.995f) darken(backdrop[i], maxOf(sa, backdropMin)) else backdrop[i]
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
                Pattern.PLANKS -> c = wood(c, i, x, i / w, v)
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
                    // polished metal: a broad diagonal shine and a bright upper edge (3b.1)
                    val y = i / w
                    c = scale(c, 0.82f + 0.45f * wave((x + y * 2) and 63))
                    if (i >= w && mat[i - w].toInt() != m) c = mix(c, 0xFFF4F8FF.toInt(), 0.45f)
                }
                Pattern.CRYSTAL -> {
                    // crystals: facets of slightly different brightness, now and then one glitters (3b.1)
                    c = scale(c, 0.9f + ((v shr 4) and 7) * 0.03f)
                    if (((v * 37 + (frame shr 2) * 11 + x * 7) and 127) == 0) c = mix(c, 0xFFFFFFFF.toInt(), 0.9f)
                    else if (((v * 37 + (frame shr 2) * 11 + x * 7) and 127) == 1) c = mix(c, 0xFFE8F4FF.toInt(), 0.5f)
                }
                Pattern.MATTE -> {
                    // soft snow: bright on top, a faint blue shadow inside the drift (3b.1)
                    var d = 0
                    var j = i - w
                    while (d < 4 && j >= 0 && mat[j].toInt() == m) {
                        d++
                        j -= w
                    }
                    c = if (d == 0) scale(c, 1.05f) else mix(c, 0xFFB8CCE4.toInt(), 0.05f * d)
                }
                Pattern.ICE -> {
                    // glassy blue with streaks; its edges are drawn after the light (3b.8)
                    val y = i / w
                    if (((x + y) and 7) == 0 || ((x + y + 1) and 7) == 0) c = scale(c, 1.1f)
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
                    out[i] = mix(scale(c, sa), bg, 1f - mt.opacity[m] * fade)
                    continue
                }
                cloud -> {
                    // clouds are drawn soft in their own layer (renderClouds, Phase 4b); here the sky behind
                    out[i] = bg
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
                // glowing by its heat: dark red, orange, yellow, white (3b.7)
                val k = ((t - GLOW_FROM) / GLOW_FULL).coerceIn(0f, 0.95f)
                c = mix(c, glowAt(t), k)
            }
            // light of its own (fire, embers, lava, glowing heat) is not darkened; the cut face of solid ground
            // is always shown, darker away from the light, while air, water and glass show the real light; ice
            // stays clearly ice even in the shade of a cave (3b.8)
            // (clear water is a volume, not a surface: under the open sky only its depth darkens it)
            val sm = if (clearLiquid[m]) sa else s
            if (sm < 0.995f && mt.glow[m] == 0f && f and Flag.BURNING == 0 && t < GLOW_FROM) {
                val floor = if (mt.pattern[m] == Pattern.ICE) ICE_SHADE else if (mt.lightPass[m] == 0f) CUT_FACE else 0f
                c = darken(c, maxOf(sm, floor))
            }
            if (mt.pattern[m] == Pattern.ICE) {
                // shining edges where the ice meets something else
                val y = i / w
                val edge = (x > 0 && mat[i - 1].toInt() != m) || (x < w - 1 && mat[i + 1].toInt() != m) ||
                    (i >= w && mat[i - w].toInt() != m) || (i < size - w && mat[i + w].toInt() != m)
                if (edge) c = mix(c, 0xFFF2FBFF.toInt(), if (((x - y) and 3) == 0) 0.75f else 0.45f)
            }
            val a = mt.opacity[m]
            out[i] = if (a >= 1f) c else if (m == glass && ((v and 15) == 0)) scale(mix(c, bg, 1f - a), 1.3f) else mix(c, bg, 1f - a)
            if (bodySprites && bodyStamp[i] == stamp) bodyColour[i] = out[i]
        }
        smoothWood(out, x0, y0, x1, y1)
        if (bodySprites) fillUnderBodies(out, x0, y0, x1, y1)
        drawCritters(out, frame)
        highlight?.let { drawHighlight(out, it, frame, x0, y0, x1, y1) }
        drawLightning(out, x0, y0, x1, y1)
    }

    /**
     * Wood (Phase 4b): a grain along the stroke it was painted with (two bits of its variant, [World.grain]),
     * gently wavy, in the body's own frame if it is a body so the grain goes with it; darker bark at its edges.
     */
    private fun wood(c0: Int, i: Int, x: Int, y: Int, v: Int): Int {
        val w = world.width
        val body = bodyStamp[i] == stamp
        val ux = if (body) bodyLocal[i] ushr 16 else x
        val uy = if (body) bodyLocal[i] and 0xFFFF else y
        val dir = (v shr 4) and 3
        // across the grain and along it
        val across = when (dir) {
            World.GRAIN_ACROSS -> ux
            World.GRAIN_FALLING -> ux - uy
            World.GRAIN_RISING -> ux + uy
            else -> uy
        }
        val along = when (dir) {
            World.GRAIN_ACROSS -> uy
            World.GRAIN_FALLING -> ux + uy
            World.GRAIN_RISING -> ux - uy
            else -> ux
        }
        // a line every few cells, shifting by one now and then along its way
        val ring = across + (((along + 1024) / 9) and 1)
        var c = when (Math.floorMod(ring, 4)) {
            0 -> scale(c0, 0.8f)
            2 -> scale(c0, 1.07f)
            else -> c0
        }
        // bark: where the wood ends
        val m = world.mat[i]
        val edge = if (body) {
            val b = world.bodies.body(world.bodyAt[i])
            b == null || !filled(b, ux - 1, uy) || !filled(b, ux + 1, uy) || !filled(b, ux, uy - 1) || !filled(b, ux, uy + 1)
        } else {
            val mat = world.mat
            (x > 0 && mat[i - 1] != m) || (x < w - 1 && mat[i + 1] != m) || (i >= w && mat[i - w] != m) || (i + w < world.size && mat[i + w] != m)
        }
        if (edge) c = mix(scale(c, 0.74f), BARK, 0.3f)
        return c
    }

    private fun filled(b: Body, x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < b.ow && y < b.oh && b.occ[y * b.ow + x]

    /**
     * Less of a staircase on slanting edges of wood (Phase 4b): an empty cell in the notch of a step (wood to one
     * side and below or above it) takes on some of the wood, and the outer corner of a step (open to one side and
     * above or below) some of what lies beyond it, so a slanting beam reads as a slant. Bodies are smoothed in
     * their own pictures.
     */
    private fun smoothWood(out: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = world.width
        val mat = world.mat
        val bodyAt = world.bodyAt
        var n = 0
        for (y in maxOf(1, y0)..minOf(world.height - 2, y1)) for (x in maxOf(1, x0)..minOf(w - 2, x1)) {
            val i = y * w + x
            val here = woodAt(i, bodyAt)
            if (!here && mat[i].toInt() != AIR) continue
            val l = woodAt(i - 1, bodyAt)
            val r = woodAt(i + 1, bodyAt)
            val u = woodAt(i - w, bodyAt)
            val d = woodAt(i + w, bodyAt)
            if (l == r || u == d) continue
            val c = if (here) {
                // the outer corner: open on the side without wood and above or below
                val side = if (l) i + 1 else i - 1
                val vert = if (u) i + w else i - w
                if (mat[side].toInt() != AIR || mat[vert].toInt() != AIR) continue
                mix(out[i], mix(out[side], out[vert], 0.5f), CORNER)
            } else {
                // the notch: wood on one side and above or below
                val side = if (l) i - 1 else i + 1
                val vert = if (u) i - w else i + w
                mix(out[i], mix(out[side], out[vert], 0.5f), NOTCH)
            }
            if (n == smoothCells.size) {
                smoothCells = smoothCells.copyOf(n * 2)
                smoothColours = smoothColours.copyOf(n * 2)
            }
            smoothCells[n] = i
            smoothColours[n] = c
            n++
        }
        for (k in 0 until n) out[smoothCells[k]] = smoothColours[k]
    }

    private var smoothCells = IntArray(256)
    private var smoothColours = IntArray(256)

    private fun woodAt(j: Int, bodyAt: IntArray): Boolean {
        val m = world.mat[j].toInt()
        return m >= 0 && mt.pattern[m] == Pattern.PLANKS && (!bodySprites || bodyAt[j] == 0)
    }

    /**
     * The cells of bodies in the grid, drawn as pictures of their own: here they take on what lies around them
     * (the water a raft floats in, the sky), so nothing of the grid shows where the turned picture lies a little
     * off it.
     */
    private fun fillUnderBodies(out: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = world.width
        val h = world.height
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            if (bodyStamp[i] != stamp) continue
            var c = 0
            var r = 1
            while (c == 0 && r <= FILL_REACH) {
                for (j in intArrayOf(i - r, i + r, i - r * w, i + r * w)) {
                    val jx = j % w
                    if (j < 0 || j >= world.size || (j == i - r && x - r < 0) || (j == i + r && x + r >= w)) continue
                    if (jx < x0 || jx > x1 || j / w < y0 || j / w > y1 || bodyStamp[j] == stamp) continue
                    c = out[j]
                    break
                }
                r++
            }
            out[i] = if (c != 0) c else backdrop[i]
        }
    }

    /** Body pictures made before, by body: unchanged bodies keep theirs. */
    private var spriteSum = LongArray(64)
    private var spritePixels = arrayOfNulls<IntArray>(64)
    private var spriteW = IntArray(64)
    private var lastX = FloatArray(64)
    private var lastY = FloatArray(64)
    private var lastAngle = FloatArray(64)
    private var lastSeen = IntArray(64)
    private var spriteRound = 0

    /**
     * Each body as a picture of its own (Phase 4b), into [out] (grown as needed), after [render]: its cells as
     * [SPRITE_SCALE] × [SPRITE_SCALE] pixels each, in the colours [render] gave them, in its own frame; the rim
     * smoothed (an edge between two cells along a slant runs as a slant), with the centre of weight as the point
     * it turns about, where the body is now and where it was in the last picture, to draw it in between. Returns
     * how many.
     */
    fun renderBodies(out: ArrayList<BodySprite>): Int {
        if (thermal) return 0
        spriteRound++
        var count = 0
        val sc = SPRITE_SCALE
        world.bodies.each { b ->
            if (b.id >= spriteSum.size) growSprites(b.id + 1)
            val sprite = if (count < out.size) out[count] else BodySprite().also { out += it }
            count++
            val cw = b.ow
            val ch = b.oh
            // the colours of its cells, in its own frame; and a sum of them, to see whether anything changed
            val col = IntArray(cw * ch)
            var sum = (cw.toLong() shl 32) xor ch.toLong()
            for (k in 0 until b.n) {
                val c = b.at[k]
                val m = world.mat[c].toInt()
                var colour = if (bodyStamp[c] == stamp) bodyColour[c] else palette[m * SHADES + (world.variant[c].toInt() and (SHADES - 1))]
                // what the hand could take, lit up and pulsing (3b.3)
                val mark = highlight
                if (mark != null && mark.size == world.size && mark[c]) colour = mix(colour, HIGHLIGHT, 0.3f + 0.25f * wave((shownFrame * 2) and 63))
                val cell = (b.ly[k] - b.oy) * cw + (b.lx[k] - b.ox)
                col[cell] = colour or OPAQUE
                sum = sum * 31 + (cell.toLong() shl 32) + colour
            }
            var pixels = spritePixels[b.id]
            if (pixels == null || spriteSum[b.id] != sum || spriteW[b.id] != cw * sc) {
                pixels = IntArray(cw * sc * ch * sc)
                drawSprite(b, col, cw, ch, pixels)
                spritePixels[b.id] = pixels
                spriteSum[b.id] = sum
                spriteW[b.id] = cw * sc
            }
            sprite.id = b.id
            sprite.w = cw * sc
            sprite.h = ch * sc
            sprite.pixels = pixels
            sprite.pivotX = (b.comX - b.ox + 0.5f) * sc
            sprite.pivotY = (b.comY - b.oy + 0.5f) * sc
            sprite.x = b.px
            sprite.y = b.py
            val angle = Math.atan2(b.sn.toDouble(), b.cs.toDouble()).toFloat() + b.spin
            // where it was in the picture before (a new body: where it is), the turn taken the short way round
            if (lastSeen[b.id] != spriteRound - 1) {
                lastX[b.id] = b.px
                lastY[b.id] = b.py
                lastAngle[b.id] = angle
            }
            var last = lastAngle[b.id]
            while (angle - last > Math.PI) last += (2 * Math.PI).toFloat()
            while (last - angle > Math.PI) last -= (2 * Math.PI).toFloat()
            sprite.lastX = lastX[b.id]
            sprite.lastY = lastY[b.id]
            sprite.lastAngle = last
            sprite.angle = angle
            lastX[b.id] = b.px
            lastY[b.id] = b.py
            lastAngle[b.id] = angle
            lastSeen[b.id] = spriteRound
        }
        return count
    }

    private fun growSprites(n: Int) {
        val s = maxOf(n, spriteSum.size * 2)
        spriteSum = spriteSum.copyOf(s)
        spritePixels = spritePixels.copyOf(s)
        spriteW = spriteW.copyOf(s)
        lastX = lastX.copyOf(s)
        lastY = lastY.copyOf(s)
        lastAngle = lastAngle.copyOf(s)
        lastSeen = lastSeen.copyOf(s)
    }

    /**
     * One body's picture: each pixel takes the colour of the nearest cell of the body, and is as solid as the
     * cells around it are, blended between their centres: inside it is solid, along an edge it fades over a third
     * of a cell, and where two cells meet only at a corner the edge runs straight across the corner.
     */
    private fun drawSprite(b: Body, col: IntArray, cw: Int, ch: Int, pixels: IntArray) {
        val sc = SPRITE_SCALE
        val pw = cw * sc
        for (sy in 0 until ch * sc) {
            val fy = (sy + 0.5f) / sc - 0.5f
            val cy = kotlin.math.floor(fy).toInt()
            val ty = fy - cy
            for (sx in 0 until pw) {
                val fx = (sx + 0.5f) / sc - 0.5f
                val cx = kotlin.math.floor(fx).toInt()
                val tx = fx - cx
                val a00 = if (filled(b, cx, cy)) 1f else 0f
                val a10 = if (filled(b, cx + 1, cy)) 1f else 0f
                val a01 = if (filled(b, cx, cy + 1)) 1f else 0f
                val a11 = if (filled(b, cx + 1, cy + 1)) 1f else 0f
                val o = (a00 * (1 - tx) + a10 * tx) * (1 - ty) + (a01 * (1 - tx) + a11 * tx) * ty
                if (o <= SPRITE_EDGE - SPRITE_SOFT) continue
                val alpha = ((o - (SPRITE_EDGE - SPRITE_SOFT)) / (2 * SPRITE_SOFT)).coerceIn(0f, 1f)
                // the colour of the cell the pixel lies in, or of the nearest filled one
                val ix = sx / sc
                val iy = sy / sc
                var c = if (filled(b, ix, iy)) col[iy * cw + ix] else 0
                if (c == 0) {
                    var best = -1f
                    for ((dx, dy, wgt) in arrayOf(Triple(0, 0, (1 - tx) * (1 - ty)), Triple(1, 0, tx * (1 - ty)), Triple(0, 1, (1 - tx) * ty), Triple(1, 1, tx * ty))) {
                        if (filled(b, cx + dx, cy + dy) && wgt > best) {
                            best = wgt
                            c = col[(cy + dy) * cw + cx + dx]
                        }
                    }
                }
                if (c == 0) continue
                pixels[sy * pw + sx] = ((alpha * 255f).toInt() shl 24) or (c and 0xFFFFFF)
            }
        }
    }

    /** A faint streak of wind through the air at (x, y): short dashes, few of them, [density] of 1024. */
    private fun streak(x: Int, y: Int, density: Int): Boolean {
        val cell = (x shr 3) * 73856093 xor y * 19349663
        return ((cell ushr 7) and 1023) < density && (x and 7) < 5
    }

    /** The stroke of lightning and the flash it gives (3c.4). */
    private fun drawLightning(out: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = world.width
        val f = world.flash
        if (f > 0) {
            val k = f * FLASH
            for (y in y0..y1) for (x in x0..x1) out[y * w + x] = mix(out[y * w + x], 0xFFF4F2FF.toInt(), k)
        }
        if (world.boltAge <= 0) return
        for (n in 0 until world.boltLength) {
            val x = world.boltX[n]
            val y = world.boltY[n]
            if (y < y0 || y > y1) continue
            for (dx in -1..1) {
                val xx = x + dx
                if (xx < x0 || xx > x1) continue
                val i = y * w + xx
                out[i] = if (dx == 0) BOLT_CORE else mix(BOLT_EDGE, out[i], 0.55f)
            }
        }
    }

    /** What the hand could take, lit up and pulsing, its outline brightest (3b.3). */
    private fun drawHighlight(out: IntArray, mark: BooleanArray, frame: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (mark.size != world.size) return
        val w = world.width
        val pulse = 0.5f + 0.5f * wave((frame * 2) and 63)
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            if (!mark[i]) continue
            val edge = (x == 0 || !mark[i - 1]) || (x == w - 1 || !mark[i + 1]) || (i < w || !mark[i - w]) || (i >= world.size - w || !mark[i + w])
            out[i] = mix(out[i], HIGHLIGHT, if (edge) 0.45f + 0.3f * pulse else 0.12f + 0.12f * pulse)
        }
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
        if (thermal) {
            haze.fill(0)
            light.fill(0)
            return
        }
        val w = world.width
        val mat = world.mat
        val temp = world.temp
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
                    // glowing heat lights up its surroundings too (3b.7)
                    else if (temp[i] > GLOW_HALO && mt.kind[m] != Kind.GAS) lightSum[b] += minOf(0.6f, (temp[i] - GLOW_HALO) / 1000f)
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

    /** Width and height of the cloud layer, in blocks of [CLOUD_BLOCK] × [CLOUD_BLOCK] cells. */
    val cloudWidth = (world.width + CLOUD_BLOCK - 1) / CLOUD_BLOCK
    val cloudHeight = (world.height + CLOUD_BLOCK - 1) / CLOUD_BLOCK
    private val cloudDensity = FloatArray(cloudWidth * cloudHeight)
    private val cloudTmp = FloatArray(cloudWidth * cloudHeight)
    private val cloudCells = IntArray(cloudWidth)
    private val billows = Noise(CLOUD_NOISE_SEED)

    /**
     * The clouds (Phase 4b), drawn soft over the pixels, smoothly scaled: not cell by cell but as the density of
     * their cells, blurred, with billowing rims from noise that slowly changes. White on top, greyer towards the
     * underside the more cloud lies above; only a deep bank, a thundercloud, has a dark belly. One pixel per
     * block of [CLOUD_BLOCK] × [CLOUD_BLOCK] cells; the cloud cells themselves are drawn as the sky behind.
     */
    fun renderClouds(out: IntArray, frame: Int) {
        out.fill(0)
        if (thermal || cloud < 0) return
        val w = world.width
        val mat = world.mat
        val cw = cloudWidth
        val ch = cloudHeight
        val cell = 1f / (CLOUD_BLOCK * CLOUD_BLOCK)
        cloudDensity.fill(0f)
        cloudCells.fill(0)
        var top = ch
        var bottom = -1
        for (y in 0 until world.height) {
            val row = (y / CLOUD_BLOCK) * cw
            var i = y * w
            for (x in 0 until w) {
                if (mat[i].toInt() == cloud) {
                    val bx = x / CLOUD_BLOCK
                    cloudDensity[row + bx] += cell
                    cloudCells[bx]++
                    val by = y / CLOUD_BLOCK
                    if (by < top) top = by
                    if (by > bottom) bottom = by
                }
                i++
            }
        }
        if (bottom < 0) return
        val y0 = maxOf(0, top - CLOUD_REACH)
        val y1 = minOf(ch - 1, bottom + CLOUD_REACH)
        repeat(2) { blur(cloudDensity, cloudTmp, cw, y0, y1, CLOUD_BLUR) }
        val drift = frame * CLOUD_CHURN
        val flash = world.flash * FLASH * 6f
        for (bx in 0 until cw) {
            // how deep the cloud above goes: the light gets greyer through it; a deep bank is a thundercloud
            val storm = ((cloudCells[bx] / CLOUD_BLOCK.toFloat() - STORM_FROM) / STORM_RANGE).coerceIn(0f, 1f)
            val floor = CLOUD_GREY + (CLOUD_STORM - CLOUD_GREY) * storm
            var above = 0f
            for (by in y0..y1) {
                val b = by * cw + bx
                val d = cloudDensity[b]
                above += d
                if (d < 0.02f) continue
                val n = billows.fbm(bx * CLOUD_SCALE + drift, by * CLOUD_SCALE * 1.4f, 2)
                val v = d * CLOUD_GAIN + (n - 0.5f) * CLOUD_FRAY
                if (v <= CLOUD_EDGE) continue
                val a = ((v - CLOUD_EDGE) / CLOUD_SOFT).coerceIn(0f, 1f)
                val alpha = a * a * (3f - 2f * a) * CLOUD_ALPHA
                var bright = (1f - above * CLOUD_SHADOW).coerceAtLeast(floor) + (n - 0.5f) * 0.1f
                bright = bright.coerceIn(0f, 1.04f)
                var c = mix(scale(CLOUD_TOP, bright), CLOUD_BELLY, ((1f - bright) * 0.35f).coerceAtLeast(0f))
                if (flash > 0f) c = mix(c, 0xFFFFFFFF.toInt(), minOf(1f, flash))
                out[b] = ((alpha * 255f).toInt() shl 24) or (c and 0xFFFFFF)
            }
        }
    }

    /** A box blur of radius [r] over rows [y0]..[y1] of [a] ([w] wide), horizontal then vertical, in place. */
    private fun blur(a: FloatArray, tmp: FloatArray, w: Int, y0: Int, y1: Int, r: Int) {
        val k = 1f / (2 * r + 1)
        for (y in y0..y1) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (dx in -r..r) sum += a[row + (x + dx).coerceIn(0, w - 1)]
                tmp[row + x] = sum * k
            }
        }
        for (y in y0..y1) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (dy in -r..r) sum += tmp[(y + dy).coerceIn(y0, y1) * w + x]
                a[row + x] = sum * k
            }
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
        if (thermal) {
            out.fill(0)
            return
        }
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

        /** Clear water is drawn darker with depth down to this many cells. */
        private const val MAX_DEPTH = 48

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
        private const val GLOW_TO = 1500f

        /** Above GLOW_FROM by this much, a hot thing shows nothing but its glow. */
        private const val GLOW_FULL = 350f

        /** Hotter than this, a thing lights up its surroundings a little. */
        private const val GLOW_HALO = 600f

        /** Incandescence: temperatures and colours (3b.7). */
        private val GLOW_T = floatArrayOf(450f, 600f, 750f, 900f, 1100f, 1300f, 1500f)
        private val GLOW_C = intArrayOf(0x3a0800, 0x8a1c04, 0xd84a0c, 0xff8a1e, 0xffc35a, 0xffeab0, 0xfffaf2)

        /** The heat picture: temperatures and colours from frost to white heat (3b.7). */
        private const val HEAT_STEPS = 256
        private const val HEAT_FROM = -30f
        private const val HEAT_TO = 1400f
        private val HEAT_T = floatArrayOf(-30f, 0f, 20f, 60f, 150f, 400f, 800f, 1400f)
        private val HEAT_C = intArrayOf(0x1c1a6e, 0x2f6fd0, 0x1f8a8a, 0x3fae4a, 0xd6d03a, 0xf08a24, 0xe02a1a, 0xfff2e4)

        /**
         * The soft clouds (Phase 4b): blocks of cells per pixel; blur radius in blocks and how far it reaches;
         * how strongly the blurred density counts, how much the noise frays the rims, where the cloud begins
         * and over how much it becomes solid, how opaque it gets; the noise: its scale per block and how fast it
         * churns per frame.
         */
        const val CLOUD_BLOCK = 2
        private const val CLOUD_BLUR = 2
        private const val CLOUD_REACH = 6
        private const val CLOUD_GAIN = 1.7f
        private const val CLOUD_FRAY = 0.6f
        private const val CLOUD_EDGE = 0.2f
        private const val CLOUD_SOFT = 0.35f
        private const val CLOUD_ALPHA = 0.95f
        private const val CLOUD_SCALE = 0.32f
        private const val CLOUD_CHURN = 0.004f
        private const val CLOUD_NOISE_SEED = 97L

        /**
         * Cloud colours: the sunlit top, the tint of the underside; how much greyer per block of cloud above, the
         * greyest an ordinary cloud gets and the darkest a thundercloud does; a column with more than STORM_FROM
         * cells of cloud starts to be a thundercloud and is one fully STORM_RANGE cells later.
         */
        private const val CLOUD_TOP = 0xFFF6F8FB.toInt()
        private const val CLOUD_BELLY = 0xFF7E8898.toInt()
        private const val CLOUD_SHADOW = 0.07f
        private const val CLOUD_GREY = 0.74f
        private const val CLOUD_STORM = 0.4f
        private const val STORM_FROM = 9f
        private const val STORM_RANGE = 8f

        /** Wind streaks from this wind on, so many of 1024 cells per unit of wind (3c.3). */
        private const val STREAK_WIND = 0.15f
        private const val STREAK_DENSITY = 40f

        /** Lightning: its core, its violet edge, how much the flash lightens the picture at its start. */
        private const val BOLT_CORE = 0xFFFFFFFF.toInt()
        private const val BOLT_EDGE = 0xFFC8BCFF.toInt()
        private const val FLASH = 0.045f

        /** Body pictures (Phase 4b): pixels per cell; where the rim lies between empty (0) and filled (1), and how softly. */
        const val SPRITE_SCALE = 4
        private const val SPRITE_EDGE = 0.5f
        private const val SPRITE_SOFT = 0.15f

        /** How far a body cell in the grid looks for something around it to take on (Phase 4b). */
        private const val FILL_REACH = 3

        /** Wood (Phase 4b): the colour of its bark, and how much of the wood the notch of a step takes on. */
        private const val BARK = 0xFF4A3020.toInt()
        private const val NOTCH = 0.5f
        private const val CORNER = 0.4f

        /** Ice in the dark is never darker than this (3b.8). */
        private const val ICE_SHADE = 0.72f

        /** The colour things the hand could take are lit up in (3b.3). */
        private const val HIGHLIGHT = 0xFFFFE27A.toInt()
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

    /** The ground line is the middle of the ground over this many columns to either side. */
    private const val GROUND_SPAN = 3

    fun of(world: World, seed: Long = Terrain.SEED): IntArray {
        val w = world.width
        val h = world.height
        val n = Noise(seed + 7)
        val top = groundLine(world)
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

    /**
     * Per column, the row where the ground begins for the scenery (Phase 4b): above it the sky is seen, below it
     * the cave wall. The first ground from the top: rock, anchored level ground, or loose ground lying on
     * something; not water, drops, stones, bodies, things in the air or wood standing on it, which would leave a
     * dark column of cave wall behind them in the sky (and behind a lake) once they moved; evened out over a few
     * columns, so a thin spike of rock does not draw one either.
     */
    fun groundLine(world: World): IntArray {
        val w = world.width
        val h = world.height
        val mt = world.materials
        val first = IntArray(w) { x ->
            (0 until h).firstOrNull { y ->
                val i = y * w + x
                val m = world.mat[i].toInt()
                val k = mt.kind[m]
                // rock and the level's anchored ground; loose ground (earth, sand) where it lies on something
                when {
                    m == AIR || k == Kind.GAS || k == Kind.LIQUID || mt.flammable[m] || world.bodyOf(i) != 0 -> false
                    k == Kind.STATIC || world.flags[i].toInt() and Flag.ANCHORED != 0 -> true
                    k == Kind.POWDER -> y == h - 1 || world.mat[i + w].toInt() != AIR
                    else -> false
                }
            } ?: h
        }
        return IntArray(w) { x ->
            val near = IntArray(2 * GROUND_SPAN + 1) { first[(x + it - GROUND_SPAN).coerceIn(0, w - 1)] }
            near.sort()
            near[GROUND_SPAN]
        }
    }
}

/**
 * A body drawn as a picture of its own (Phase 4b, [Renderer.renderBodies]): [w] × [h] pixels, [Renderer.SPRITE_SCALE]
 * per cell, ARGB; its centre of weight at ([pivotX], [pivotY]) in the picture. Where it is now ([x], [y]: the
 * centre of weight in cells, cell centres at whole numbers; [angle] in radians, clockwise on the screen) and where
 * it was in the picture before ([lastX], [lastY], [lastAngle]), to draw it in between. [pixels] is never changed
 * once handed out: a changed body gets new ones.
 */
class BodySprite {
    var id = 0
    var w = 0
    var h = 0
    var pixels = IntArray(0)
    var pivotX = 0f
    var pivotY = 0f
    var x = 0f
    var y = 0f
    var angle = 0f
    var lastX = 0f
    var lastY = 0f
    var lastAngle = 0f
}
