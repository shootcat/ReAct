package com.shootcat.react.sim

/**
 * Measures how many steps per second the simulation manages (Leitfaden V3, 4.1.7 and Phase 1): in an
 * empty world, in a world in which every chunk is awake and full of moving, burning and boiling material,
 * and in the sandbox start with fires going. The same code runs in the unit tests on a desktop and in the
 * app on the phone, so the numbers can be compared.
 */
object Benchmark {

    data class Result(
        val name: String,
        val steps: Int,
        val stepsPerSecond: Double,
        val msPerStep: Double,
        val awakeChunks: Int,
        val chunks: Int,
        /** Of that, structures and bodies (Phase 4); how many bodies there were on average, how many moved. */
        val bodyMs: Double = 0.0,
        val bodies: Int = 0,
        val movingBodies: Int = 0,
    )

    fun emptyWorld(materials: MaterialTable, width: Int, height: Int): World = World(width, height, materials, 1L)

    /**
     * Every chunk is updated in every step, whatever happens in it, and the world is a churning mix: sand
     * and water fall and flow, lava boils water, wood burns, smoke and steam rise.
     */
    fun fullWorld(materials: MaterialTable, width: Int, height: Int): World {
        val world = World(width, height, materials, 2L)
        val n = Noise(3L)
        val sand = materials.id("sand")
        val water = materials.id("water")
        val lava = materials.id("lava")
        val wood = materials.id("wood")
        val fire = materials.id("fire")
        val steam = materials.id("steam")
        val earth = materials.id("earth")
        for (y in 0 until height) for (x in 0 until width) {
            val r = n.hash(x, y)
            val m = when {
                r < 0.22f -> sand
                r < 0.44f -> water
                r < 0.50f -> earth
                r < 0.53f -> lava
                r < 0.58f -> wood
                r < 0.62f -> fire
                r < 0.66f -> steam
                else -> MaterialTable.AIR
            }
            if (m != MaterialTable.AIR) world.set(x, y, m)
        }
        world.keepAllAwake = true
        return world
    }

    /**
     * Like [fullWorld], every chunk awake, with plants and animals mixed in (2b): grass, seeds, moss,
     * leaves and vines grow and burn, fish, fireflies, worms and birds move, and the light is worked out.
     */
    fun livingWorld(materials: MaterialTable, width: Int, height: Int): World {
        val world = World(width, height, materials, 4L)
        val n = Noise(5L)
        val table = listOf(
            0.18f to "sand", 0.36f to "water", 0.42f to "earth", 0.44f to "lava", 0.47f to "wood", 0.50f to "fire",
            0.52f to "steam", 0.56f to "grass", 0.58f to "seed", 0.60f to "moss", 0.62f to "leaves", 0.63f to "vine",
            0.645f to "fish", 0.66f to "firefly", 0.67f to "worm", 0.68f to "bird",
        ).map { (p, id) -> p to materials.id(id) }
        for (y in 0 until height) for (x in 0 until width) {
            val r = n.hash(x, y)
            val m = table.firstOrNull { r < it.first }?.second ?: continue
            world.set(x, y, m)
            if (r < 0.42f && r >= 0.36f) world.setWet(x, y, true)
        }
        world.keepAllAwake = true
        return world
    }

    fun sandboxWorld(materials: MaterialTable, width: Int, height: Int): World {
        val world = World(width, height, materials, Terrain.SEED)
        Terrain.sandbox(world)
        val fire = materials.id("fire")
        val water = materials.id("water")
        val sand = materials.id("sand")
        // light the bridge and the logs, pour sand and water from above
        for (x in 0 until width step 12) world.paint(fire, x, height / 2 - 30, 3)
        for (k in 0 until maxOf(1, width / 200)) {
            world.paint(water, width * (4 * k + 1) / (4 * maxOf(1, width / 200)), 30, 12)
            world.paint(sand, width * (4 * k + 3) / (4 * maxOf(1, width / 200)), 30, 12)
        }
        return world
    }

    /**
     * The busy meadow with weather (3c): clouds at the usual height, a fresh wind with gusts and a
     * thunderstorm over the middle, so rain falls, the wind carries smoke and lightning strikes.
     */
    fun weatherWorld(materials: MaterialTable, width: Int, height: Int): World {
        val world = sandboxWorld(materials, width, height)
        world.setWeather(cloudBase = (height * 0.16f).toInt(), wind = 0.5f, gust = 0.2f)
        world.callStorm(width / 2)
        return world
    }

    /**
     * Like [livingWorld], every chunk awake, and on top [count] loose things (Phase 4): boulders, planks, logs,
     * bars of metal, panes of glass, blocks of ice, dropped from the upper half into the churning world below,
     * where they fall, tip, roll, float, sink, burn and break.
     */
    fun bodyWorld(materials: MaterialTable, width: Int, height: Int, count: Int = 150): World {
        val world = livingWorld(materials, width, height)
        val n = Noise(6L)
        val kinds = listOf("stone", "wood", "deadwood", "metal", "glass", "ice", "brick")
        for (k in 0 until count) {
            val m = materials.id(kinds[k % kinds.size])
            val cx = 6 + (n.hash(k, 1) * (width - 12)).toInt()
            val cy = 6 + (n.hash(k, 2) * (height / 2 - 12)).toInt()
            val big = 2 + (n.hash(k, 3) * 4).toInt()
            if (k % 3 == 0) {
                for (y in cy - big..cy + big) for (x in cx - big..cx + big) if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= big * big + big) world.set(x, y, m)
            } else {
                for (y in cy until cy + 2) for (x in cx - big * 2..cx + big * 2) world.set(x, y, m)
            }
        }
        world.keepAllAwake = true
        return world
    }

    /** Measures [steps] steps after [warmup] steps, so the just-in-time compiler has settled (on the phone as well). */
    fun measure(name: String, world: World, steps: Int, warmup: Int = 300): Result {
        world.run(warmup)
        var awake = 0
        var bodies = 0L
        var moving = 0L
        val b0 = world.bodyNanos
        val t0 = System.nanoTime()
        repeat(steps) {
            world.step()
            awake += world.awakeChunks
            bodies += world.bodyCount
            moving += world.awakeBodies
        }
        val ns = System.nanoTime() - t0
        val secs = ns / 1e9
        return Result(
            name, steps, steps / secs, ns / 1e6 / steps, awake / steps, world.chunksX * world.chunksY,
            (world.bodyNanos - b0) / 1e6 / steps, (bodies / steps).toInt(), (moving / steps).toInt(),
        )
    }

    /**
     * Milliseconds to turn the world into a picture once, while it runs (with the light the simulation has
     * worked out) or, with [paused], working out the light for the picture itself.
     */
    fun renderMillis(world: World, frames: Int = 60, paused: Boolean = false, viewWidth: Int = world.width): Double {
        val renderer = Renderer(world, IntArray(world.size))
        val out = IntArray(world.size)
        if (world.step == 0) world.step()
        val x0 = (world.width - viewWidth) / 2
        val x1 = x0 + viewWidth - 1
        repeat(60) { renderer.render(out, it, !paused, x0, 0, x1, world.height - 1) }
        val t0 = System.nanoTime()
        repeat(frames) { renderer.render(out, it, !paused, x0, 0, x1, world.height - 1) }
        return (System.nanoTime() - t0) / 1e6 / frames
    }

    /**
     * The measurements of the report: the worlds of 200 × 350 cells measured since phase 1, for comparison,
     * and the largest sandbox world ([WorldSize.LARGE]), for which 8 ms per step is the limit (4a.5).
     */
    fun runAll(materials: MaterialTable, steps: Int = 600): List<Result> {
        val w = Terrain.WIDTH
        val h = Terrain.HEIGHT
        val big = WorldSize.LARGE
        val size = "${big.width} × ${big.height}"
        return listOf(
            measure("Leere Welt", emptyWorld(materials, w, h), steps),
            measure("Voll aktive Welt (alle Chunks wach)", fullWorld(materials, w, h), steps),
            measure("Voll aktive Welt mit Pflanzen, Tieren und Licht", livingWorld(materials, w, h), steps),
            measure("Spielplatz mit Feuer, Sand und Wasser", sandboxWorld(materials, w, h), steps),
            measure("Größte Welt $size, voll aktiv", fullWorld(materials, big.width, big.height), steps),
            measure("Größte Welt $size, voll aktiv mit Pflanzen, Tieren und Licht", livingWorld(materials, big.width, big.height), steps),
            measure("Größte Welt $size, Wiese mit Feuer, Sand und Wasser", sandboxWorld(materials, big.width, big.height), steps),
            measure("Größte Welt $size, dieselbe Wiese mit Wind, Wolken und Gewitter", weatherWorld(materials, big.width, big.height), steps),
            measure("Größte Welt $size, voll aktiv belebt, dazu 150 Körper", bodyWorld(materials, big.width, big.height), steps),
        )
    }

    /** Milliseconds per picture of the largest world: all of it, and the part a phone shows (a third of its width). */
    fun renderLargest(materials: MaterialTable): Pair<Double, Double> {
        val big = WorldSize.LARGE
        val all = renderMillis(sandboxWorld(materials, big.width, big.height))
        val part = renderMillis(sandboxWorld(materials, big.width, big.height), viewWidth = big.width / 3)
        return all to part
    }

    fun report(results: List<Result>, renderMs: Double?, device: String, pausedRenderMs: Double? = null, largest: Pair<Double, Double>? = null): String = buildString {
        appendLine("Gerät: $device")
        for (r in results) {
            appendLine(
                "%s: %.0f Schritte/s (%.2f ms pro Schritt, %d von %d Chunks wach, %d Schritte gemessen; Körper %.2f ms, %d Körper, %d bewegt)"
                    .format(r.name, r.stepsPerSecond, r.msPerStep, r.awakeChunks, r.chunks, r.steps, r.bodyMs, r.bodies, r.movingBodies),
            )
        }
        if (renderMs != null) appendLine("Bild erzeugen: %.2f ms".format(renderMs))
        if (pausedRenderMs != null) appendLine("Bild erzeugen in der Pause (eigenes Licht): %.2f ms".format(pausedRenderMs))
        if (largest != null) {
            appendLine("Bild der größten Welt, ganz: %.2f ms".format(largest.first))
            appendLine("Bild der größten Welt, sichtbarer Ausschnitt (ein Drittel): %.2f ms".format(largest.second))
        }
    }
}
