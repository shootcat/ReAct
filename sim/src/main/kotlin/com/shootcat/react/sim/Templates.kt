package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Sizes of the sandbox world (Leitfaden V3, 4a.5): two to three screen widths across. */
enum class WorldSize(val label: String, val width: Int, val height: Int) {
    SMALL("Klein", 320, 240),
    MEDIUM("Mittel", 480, 300),
    LARGE("Groß", 600, 340),
}

/** The landscapes a sandbox can start from (Leitfaden V3, 4a.5). */
enum class Template(val label: String) {
    MEADOW("Wiese mit Teich"),
    EMPTY("Leeres Feld"),
    ISLAND("Insel"),
    COAST("Küste"),
    MOUNTAIN("Berg mit Schnee"),
    VOLCANO("Vulkan"),
    CAVES("Höhlensystem"),
    ;

    /** Builds the landscape into [world], whatever its size. */
    fun build(world: World, seed: Long = Terrain.SEED) {
        when (this) {
            MEADOW -> Terrain.sandbox(world, seed)
            EMPTY -> Templates.empty(world, seed)
            ISLAND -> Templates.island(world, seed)
            COAST -> Templates.coast(world, seed)
            MOUNTAIN -> Templates.mountain(world, seed)
            VOLCANO -> Templates.volcano(world, seed)
            CAVES -> Templates.caves(world, seed)
        }
        // clouds form above the landscape (3c.1): high over the meadow, lower round the snowy mountain, where
        // its peaks reach into them; no wind to begin with
        world.setWeather(cloudBase = (world.height * cloudHeight).toInt().coerceAtLeast(World.CLOUD_BAND + 2))
        world.wakeAll()
    }

    /** Where the clouds form, as a share of the world's height from the top. */
    private val cloudHeight: Float
        get() = when (this) {
            MOUNTAIN -> 0.2f
            CAVES -> 0.1f
            else -> 0.16f
        }
}

/**
 * The landscapes besides the meadow. Each is drawn for any size: heights as shares of the world height,
 * widths as shares of its width, with noise so nothing looks cut out of boxes (Leitfaden V3, 2.6).
 */
internal object Templates {

    private class Kit(val world: World, seed: Long, val t: Float = world.ambient) {
        val mt = world.materials
        val w = world.width
        val h = world.height
        val n = Noise(seed)
        val air = MaterialTable.AIR
        fun id(name: String) = mt.id(name)
        fun put(x: Int, y: Int, m: Int) {
            if (world.inside(x, y)) world.set(x, y, m, if (mt.hasSpawnTemp[m]) Float.NaN else t)
        }
        fun at(x: Int, y: Int) = if (world.inside(x, y)) world.material(x, y) else -1

        /**
         * Ground below [surface]: a soil of [soil] (or what [top] says, from the depth into the ground) over
         * rock with boulders of stone and thin veins of ore.
         */
        fun ground(surface: IntArray, soil: (Int, Int) -> Int?) {
            val rock = id("rock")
            val stone = id("stone")
            val ore = id("ore")
            for (x in 0 until w) {
                val top = surface[x].coerceIn(0, h)
                val depth = (h * 0.035f + h * 0.03f * n.fbm(x / 20f, 3.3f)).toInt()
                for (y in top until h) {
                    val d = y - top
                    val m = when {
                        d < depth -> soil(x, d) ?: rock
                        d < depth + 10 && n.fbm(x / 9f, y / 9f, 2) > 0.74f -> stone
                        d > depth + 14 && abs(n.fbm(x / 34f + 7f, y / 22f, 3) - 0.5f) < 0.016f -> ore
                        else -> rock
                    }
                    put(x, y, m)
                }
            }
        }

        /** Water of [m] in all air below [level] down to the ground, between x0 and x1. */
        fun flood(m: Int, level: Int, x0: Int = 0, x1: Int = w - 1) {
            for (x in max(0, x0)..min(w - 1, x1)) for (y in max(0, level) until h) {
                if (world.material(x, y) != air) break
                put(x, y, m)
            }
        }

        /** Grass on bare earth under the open sky. */
        fun grass(surface: IntArray) {
            val earth = id("earth")
            val grass = id("grass")
            for (x in 0 until w) {
                val y = surface[x]
                if (at(x, y) == earth && at(x, y - 1) == air) put(x, y, grass)
            }
        }

        /** Trees (and now and then a bush) on grass, about every [gap] cells, where there is room. */
        fun trees(surface: IntArray, from: Int, to: Int, gap: Int) {
            val grass = id("grass")
            var x = from + (gap * n.hash(from, 11)).toInt() / 2
            while (x < to) {
                if (x in 2 until w - 2 && at(x, surface[x]) == grass && at(x, surface[x] - 1) == air) {
                    world.plantTree(x, surface[x] - 1, tree = n.hash(x, 13) < 0.75f)
                }
                x += (gap * (0.7f + 0.6f * n.hash(x, 17))).toInt()
            }
        }

        /** Birds sitting on whatever is highest in a few columns. */
        fun birds(xs: List<Int>) {
            val bird = id("bird")
            for (x in xs) {
                if (x !in 0 until w) continue
                var y = 0
                while (y < h - 1 && world.material(x, y + 1) == air) y++
                if (y > 0 && world.material(x, y) == air) put(x, y, bird)
            }
        }

        /** A hollow in the rock around (cx, cy) with radii rx, ry, ragged at the edge; [fill] below [level], air above. */
        fun hollow(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int = air, level: Float = Float.MAX_VALUE) {
            for (y in max(0, (cy - ry * 1.5f).toInt())..min(h - 1, (cy + ry * 1.5f).toInt())) {
                for (x in max(0, (cx - rx * 1.5f).toInt())..min(w - 1, (cx + rx * 1.5f).toInt())) {
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val r = dx * dx + dy * dy + (n.fbm(x / 9f, y / 9f + 30f, 3) - 0.5f) * 0.6f
                    if (r < 1f) put(x, y, if (y > level) fill else air)
                }
            }
        }

        /** Rock around a lava pocket is hot too, out to [reach] times its radii, so the lava stays liquid for a while. */
        fun heatAround(cx: Float, cy: Float, rx: Float, ry: Float, core: Float, reach: Float = 2f) {
            for (y in max(0, (cy - ry * reach).toInt())..min(h - 1, (cy + ry * reach).toInt())) {
                for (x in max(0, (cx - rx * reach).toInt())..min(w - 1, (cx + rx * reach).toInt())) {
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val r = sqrt(dx * dx + dy * dy)
                    if (r < reach && mt.kind[world.material(x, y)] == Kind.STATIC) {
                        world.setTemperature(x, y, max(world.temp[world.index(x, y)], core - (r - 1f).coerceAtLeast(0f) * 700f))
                    }
                }
            }
        }
    }

    /** A flat field: grass on a layer of earth over rock, and nothing else. */
    fun empty(world: World, seed: Long) {
        val k = Kit(world, seed)
        val surface = IntArray(k.w) { x -> (k.h * 0.72f + (k.n.fbm(x / 40f, 1.5f, 2) - 0.5f) * 3f).toInt() }
        val earth = k.id("earth")
        k.ground(surface) { _, _ -> earth }
        k.grass(surface)
    }

    /** An island in the sea: a hill of rock with earth and trees on top, sandy beaches, salt water all round. */
    fun island(world: World, seed: Long) {
        val k = Kit(world, seed)
        val w = k.w
        val h = k.h
        val sea = (h * 0.55f).toInt()
        val floor = h * 0.84f
        val peak = h * 0.3f
        val cx = w * 0.5f
        val half = w * 0.26f
        val surface = IntArray(w) { x ->
            val d = abs(x - cx) / half
            val bump = if (d < 1f) (1f - d * d).let { it * sqrt(it) } else 0f
            val rough = (k.n.fbm(x / 24f, 0.7f, 4) - 0.5f) * h * 0.05f
            (floor - (floor - peak) * bump + rough * bump + (k.n.fbm(x / 50f, 4.4f, 2) - 0.5f) * h * 0.04f).toInt()
        }
        val sand = k.id("sand")
        val earth = k.id("earth")
        k.ground(surface) { x, d ->
            val top = surface[x]
            when {
                top > sea - h * 0.04f -> sand
                d < 3 && top > sea - h * 0.07f -> sand
                else -> earth
            }
        }
        k.flood(k.id("saltwater"), sea)
        k.grass(surface)
        k.trees(surface, (cx - half * 0.7f).toInt(), (cx + half * 0.7f).toInt(), (w * 0.08f).toInt())
        k.birds(listOf((cx - half * 0.3f).toInt(), (cx + half * 0.2f).toInt()))
    }

    /** A coast: hills with trees on the left, a cliff, a sandy beach and the sea on the right. */
    fun coast(world: World, seed: Long) {
        val k = Kit(world, seed)
        val w = k.w
        val h = k.h
        val sea = (h * 0.56f).toInt()
        val cliff = w * 0.5f
        val surface = IntArray(w) { x ->
            val u = x / cliff
            val land = h * 0.36f + (k.n.fbm(x / 30f, 2.1f, 4) - 0.5f) * h * 0.12f
            val beachTop = h * 0.52f
            val seaFloor = h * 0.86f
            val y = when {
                u < 0.94f -> land
                u < 1.06f -> land + (beachTop - land) * ((u - 0.94f) / 0.12f)
                else -> {
                    val t = ((x - cliff * 1.06f) / (w - cliff * 1.06f)).coerceIn(0f, 1f)
                    beachTop + (seaFloor - beachTop) * min(1f, t * 1.8f)
                }
            }
            (y + (k.n.fbm(x / 12f, 9.1f, 2) - 0.5f) * h * 0.02f).toInt()
        }
        val sand = k.id("sand")
        val earth = k.id("earth")
        val gravel = k.id("gravel")
        k.ground(surface) { x, d ->
            when {
                x > cliff * 1.04f -> if (d > 6 && k.n.hash(x, d) < 0.15f) gravel else sand
                x > cliff * 0.94f -> null
                else -> earth
            }
        }
        k.flood(k.id("saltwater"), sea, (cliff * 1.04f).toInt())
        k.grass(surface)
        k.trees(surface, (w * 0.04f).toInt(), (cliff * 0.88f).toInt(), (w * 0.09f).toInt())
        // a small cave in the cliff
        k.hollow(cliff * 0.96f, h * 0.48f, w * 0.025f, h * 0.035f)
        k.birds(listOf((w * 0.2f).toInt(), (w * 0.38f).toInt()))
    }

    /**
     * A mountain with a cap of snow in the cold (the air is a few degrees below freezing): rock, scree at its
     * foot, snow above the snow line, a frozen pond in the valley, a few trees down below.
     */
    fun mountain(world: World, seed: Long) {
        world.setAmbient(COLD)
        val k = Kit(world, seed)
        val w = k.w
        val h = k.h
        val peakX = w * 0.58f
        val peakY = h * 0.12f
        val base = h * 0.8f
        // a main peak and a lower one on its left shoulder, with ridges and gullies
        fun cone(x: Float, px: Float, py: Float, half: Float): Float = py + (base - py) * min(1f, abs(x - px) / half)
        val surface = IntArray(w) { x ->
            val fx = x.toFloat()
            val y = min(cone(fx, peakX, peakY, w * 0.4f), cone(fx, w * 0.3f, h * 0.3f, w * 0.22f))
            val ridges = (k.n.fbm(fx / 26f, 3.7f, 3) - 0.5f) * h * 0.22f + (k.n.fbm(fx / 6f, 8.1f, 2) - 0.5f) * h * 0.03f
            (y + ridges * min(1f, (base - y) / (h * 0.15f))).toInt().coerceIn((h * 0.08f).toInt(), (h * 0.85f).toInt())
        }
        // a hollow in the valley on the left for a frozen pond
        val pondX = (w * 0.1f).toInt()
        for (x in 0 until w) {
            val d = abs(x - pondX) / (w * 0.06f)
            if (d < 1f) surface[x] += (h * 0.05f * (1f - d * d)).toInt()
        }
        val earth = k.id("earth")
        val rubble = k.id("rubble")
        val gravel = k.id("gravel")
        val snowLine = h * 0.56f
        k.ground(surface) { x, d ->
            val top = surface[x]
            when {
                top < snowLine -> null
                top < base - h * 0.06f -> if (d < 3) (if (k.n.hash(x, 7) < 0.5f) rubble else gravel) else null
                else -> earth
            }
        }
        // ice in the hollow, snow above the snow line, deeper towards the top
        val ice = k.id("ice")
        val pondLevel = surface[pondX] - (h * 0.035f).toInt()
        for (x in max(0, pondX - (w * 0.06f).toInt())..min(w - 1, pondX + (w * 0.06f).toInt())) {
            for (y in pondLevel until h) {
                if (k.at(x, y) != k.air) break
                k.world.set(x, y, ice, COLD - 4f)
            }
        }
        val snow = k.id("snow")
        for (x in 0 until w) {
            val top = surface[x]
            if (top > snowLine) continue
            val thick = (3 + 12 * (snowLine - top) / snowLine + 3 * k.n.fbm(x / 8f, 5.5f, 2)).toInt()
            for (y in top - thick until top) if (k.at(x, y) == k.air) k.world.set(x, y, snow, COLD)
        }
        k.grass(surface)
        k.trees(surface, (w * 0.18f).toInt(), (w * 0.3f).toInt(), (w * 0.05f).toInt())
        k.trees(surface, (w * 0.86f).toInt(), (w * 0.98f).toInt(), (w * 0.05f).toInt())
    }

    /**
     * A volcano: a cone of rock with basalt on its flanks, a crater with lava, a chimney down to a hot magma
     * chamber, land on the left and the sea on the right.
     */
    fun volcano(world: World, seed: Long) {
        val k = Kit(world, seed)
        val w = k.w
        val h = k.h
        val cx = w * 0.45f
        val peak = h * 0.2f
        val base = h * 0.66f
        val half = w * 0.3f
        val craterHalf = w * 0.045f
        val surface = IntArray(w) { x ->
            val d = abs(x - cx) / half
            var y = if (d < 1f) peak + (base - peak) * d * (0.8f + 0.2f * d) else base + (d - 1f) * h * 0.05f
            if (abs(x - cx) < craterHalf) y += h * 0.07f * (1f - (abs(x - cx) / craterHalf).let { it * it })
            val seaSide = x > w * 0.8f
            if (seaSide) y = max(y, h * 0.66f + (x - w * 0.8f) / (w * 0.2f) * h * 0.2f)
            (y + (k.n.fbm(x / 14f, 8.8f, 3) - 0.5f) * h * 0.03f).toInt().coerceIn(1, h - 4)
        }
        val basalt = k.id("basalt")
        val earth = k.id("earth")
        val sand = k.id("sand")
        val ash = k.id("ash")
        k.ground(surface) { x, d ->
            val inCone = abs(x - cx) < half
            when {
                x > w * 0.78f -> sand
                inCone && d < 2 && k.n.hash(x, 23) < 0.25f -> ash
                inCone -> basalt
                else -> earth
            }
        }
        // the magma chamber, the chimney up to the crater, lava standing in the crater
        val lava = k.id("lava")
        val chamberY = h * 0.8f
        k.hollow(cx, chamberY, w * 0.1f, h * 0.07f, lava, chamberY - h * 0.05f)
        for (y in surface[cx.toInt()] until chamberY.toInt()) {
            val wob = ((k.n.fbm(y / 12f, 4.2f, 2) - 0.5f) * 6f).toInt()
            for (x in cx.toInt() - 2 + wob..cx.toInt() + 2 + wob) k.put(x, y, lava)
        }
        k.hollow(cx, chamberY, w * 0.1f, h * 0.07f, lava, chamberY - h * 0.08f)
        val craterFloor = surface[cx.toInt()]
        val rim = min(surface[(cx - craterHalf).toInt()], surface[(cx + craterHalf).toInt()])
        val lavaTop = craterFloor - (craterFloor - rim) / 2
        for (x in (cx - craterHalf).toInt()..(cx + craterHalf).toInt()) {
            for (y in lavaTop until h) {
                if (k.at(x, y) != k.air) break
                k.put(x, y, lava)
            }
        }
        k.heatAround(cx, chamberY, w * 0.1f, h * 0.07f, 1050f)
        k.heatAround(cx, craterFloor.toFloat(), craterHalf, h * 0.04f, 1000f)
        k.flood(k.id("saltwater"), (h * 0.7f).toInt(), (w * 0.8f).toInt())
        k.grass(surface)
        k.trees(surface, (w * 0.02f).toInt(), (cx - half - w * 0.02f).toInt(), (w * 0.06f).toInt())
    }

    /**
     * A cave system: a thin skin of earth under the sky, then rock full of winding tunnels and chambers, with
     * pools, gravel, ore, a crack giving off firedamp that gathers under a ceiling, moss and fireflies in the
     * dark, and lava deep down.
     */
    fun caves(world: World, seed: Long) {
        val k = Kit(world, seed)
        val w = k.w
        val h = k.h
        val surface = IntArray(w) { x -> (h * 0.12f + (k.n.fbm(x / 30f, 1.9f, 3) - 0.5f) * h * 0.06f).toInt() }
        val earth = k.id("earth")
        val gravel = k.id("gravel")
        k.ground(surface) { _, _ -> earth }
        // winding tunnels below the soil
        val top = (h * 0.2f).toInt()
        for (y in top until h - 4) for (x in 2 until w - 2) {
            val a = k.n.fbm(x / 46f, y / 24f, 3)
            val b = k.n.fbm(x / 30f + 50f, y / 40f, 3)
            if (abs(a - 0.5f) < 0.028f || abs(b - 0.5f) < 0.02f) k.put(x, y, k.air)
        }
        // chambers: one with a pool, one dark with moss and fireflies, one with the gas crack, one with gravel
        val water = k.id("water")
        val chambers = listOf(
            floatArrayOf(0.18f, 0.42f, 0.08f, 0.07f),
            floatArrayOf(0.5f, 0.35f, 0.07f, 0.06f),
            floatArrayOf(0.8f, 0.48f, 0.09f, 0.08f),
            floatArrayOf(0.32f, 0.7f, 0.1f, 0.07f),
            floatArrayOf(0.68f, 0.78f, 0.08f, 0.06f),
        )
        for ((i, c) in chambers.withIndex()) {
            val cx = w * c[0]
            val cy = h * c[1]
            val rx = w * c[2]
            val ry = h * c[3]
            k.hollow(cx, cy, rx, ry, if (i == 0 || i == 3) water else k.air, cy + ry * 0.35f)
        }
        // a way down from the surface into the first chamber
        val shaftX = (w * 0.24f).toInt()
        for (y in surface[shaftX] - 1 until (h * 0.4f).toInt()) {
            val wob = ((k.n.fbm(y / 10f, 2.4f, 2) - 0.5f) * 8f).toInt()
            for (x in shaftX - 2 + wob..shaftX + 2 + wob) if (k.at(x, y) != -1) k.put(x, y, k.air)
        }
        // gravel heaped in the last chamber
        val gx = w * 0.68f
        val gy = h * 0.78f
        for (x in (gx - w * 0.05f).toInt()..(gx + w * 0.05f).toInt()) {
            var y = gy.toInt()
            while (y < h - 1 && k.at(x, y + 1) == k.air) y++
            val pile = (h * 0.03f * (1f - abs(x - gx) / (w * 0.05f))).toInt()
            for (p in 0 until pile) if (k.at(x, y - p) == k.air) k.put(x, y - p, gravel)
        }
        // the crack with firedamp in the right chamber
        val vcx = w * 0.8f
        val vcy = h * 0.48f
        var floorY = vcy.toInt()
        while (floorY < h - 1 && k.at(vcx.toInt(), floorY + 1) == k.air) floorY++
        for (x in vcx.toInt() - 2..vcx.toInt() + 2) k.put(x, floorY + 1, k.id("gas_vent"))
        val damp = k.id("firedamp")
        for (y in (vcy - h * 0.09f).toInt()..(vcy - h * 0.04f).toInt()) {
            for (x in (vcx - w * 0.06f).toInt()..(vcx + w * 0.06f).toInt()) if (k.at(x, y) == k.air && k.n.hash(x, y) < 0.5f) k.put(x, y, damp)
        }
        // moss and fireflies in the dark middle chamber
        val moss = k.id("moss")
        val rock = k.id("rock")
        val mcx = w * 0.5f
        val mcy = h * 0.35f
        for (y in (mcy - h * 0.07f).toInt()..(mcy + h * 0.07f).toInt()) for (x in (mcx - w * 0.08f).toInt()..(mcx + w * 0.08f).toInt()) {
            if (k.at(x, y) != k.air) continue
            if ((k.at(x - 1, y) == rock || k.at(x + 1, y) == rock || k.at(x, y + 1) == rock) && k.n.hash(x, y) < 0.6f) k.put(x, y, moss)
        }
        val firefly = k.id("firefly")
        for (i in 0 until 8) {
            val x = (mcx + (i - 4) * w * 0.012f).toInt()
            val y = (mcy - h * 0.02f + (i % 3) * 2).toInt()
            if (k.at(x, y) == k.air) k.put(x, y, firefly)
        }
        // lava deep down on the left, sealed off from the tunnels by solid rock all round
        val lx = w * 0.12f
        val ly = h * 0.86f
        for (y in (ly - h * 0.16f).toInt()..min(h - 1, (ly + h * 0.16f).toInt())) for (x in max(0, (lx - w * 0.17f).toInt())..min(w - 1, (lx + w * 0.17f).toInt())) {
            val dx = (x - lx) / (w * 0.16f)
            val dy = (y - ly) / (h * 0.15f)
            if (dx * dx + dy * dy < 1f) k.put(x, y, rock)
        }
        k.hollow(lx, ly, w * 0.06f, h * 0.04f, k.id("lava"), ly - h * 0.01f)
        k.heatAround(lx, ly, w * 0.06f, h * 0.04f, 1050f, reach = 1.4f)
        k.grass(surface)
        k.trees(surface, (w * 0.05f).toInt(), (w * 0.95f).toInt(), (w * 0.12f).toInt())
    }

    /** The air of the snowy mountain. */
    const val COLD = -6f
}
