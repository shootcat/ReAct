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

    fun sandboxWorld(materials: MaterialTable, width: Int, height: Int): World {
        val world = World(width, height, materials, Terrain.SEED)
        Terrain.sandbox(world)
        val fire = materials.id("fire")
        val water = materials.id("water")
        val sand = materials.id("sand")
        // light the bridge and the logs, pour sand and water from above
        for (x in 0 until width step 12) world.paint(fire, x, height / 2 - 30, 3)
        world.paint(water, width / 4, 30, 12)
        world.paint(sand, width * 3 / 4, 30, 12)
        return world
    }

    fun measure(name: String, world: World, steps: Int, warmup: Int = 60): Result {
        world.run(warmup)
        var awake = 0
        val t0 = System.nanoTime()
        repeat(steps) {
            world.step()
            awake += world.awakeChunks
        }
        val ns = System.nanoTime() - t0
        val secs = ns / 1e9
        return Result(name, steps, steps / secs, ns / 1e6 / steps, awake / steps, world.chunksX * world.chunksY)
    }

    /** Milliseconds to turn the world into a picture once. */
    fun renderMillis(world: World, frames: Int = 60): Double {
        val renderer = Renderer(world, IntArray(world.size))
        val out = IntArray(world.size)
        repeat(10) { renderer.render(out, it) }
        val t0 = System.nanoTime()
        repeat(frames) { renderer.render(out, it) }
        return (System.nanoTime() - t0) / 1e6 / frames
    }

    fun runAll(materials: MaterialTable, width: Int = Terrain.WIDTH, height: Int = Terrain.HEIGHT, steps: Int = 600): List<Result> = listOf(
        measure("Leere Welt", emptyWorld(materials, width, height), steps),
        measure("Leere Welt, alle Chunks wach", emptyWorld(materials, width, height).also { it.keepAllAwake = true }, steps),
        measure("Voll aktive Welt (alle Chunks wach)", fullWorld(materials, width, height), steps),
        measure("Spielplatz mit Feuer, Sand und Wasser", sandboxWorld(materials, width, height), steps),
    )

    fun report(results: List<Result>, renderMs: Double?, device: String): String = buildString {
        appendLine("Gerät: $device")
        for (r in results) {
            appendLine(
                "%s: %.0f Schritte/s (%.2f ms pro Schritt, %d von %d Chunks wach, %d Schritte gemessen)"
                    .format(r.name, r.stepsPerSecond, r.msPerStep, r.awakeChunks, r.chunks, r.steps),
            )
        }
        if (renderMs != null) appendLine("Bild erzeugen: %.2f ms".format(renderMs))
    }
}
