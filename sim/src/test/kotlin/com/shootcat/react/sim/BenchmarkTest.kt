package com.shootcat.react.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance at 200 × 350 cells (Leitfaden V3, 4.1.7) and in the largest sandbox world (4a.5). On a desktop or CI machine;
 * the app runs the same benchmark on the phone. The report lands in build/benchmark.md.
 */
class BenchmarkTest {

    @Test
    fun `steps per second in an empty, a fully active and a busy sandbox world`() {
        val materials = MaterialTable.load()
        val results = Benchmark.runAll(materials, steps = 600)
        val render = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, Terrain.WIDTH, Terrain.HEIGHT))
        val paused = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, Terrain.WIDTH, Terrain.HEIGHT), paused = true)
        val largest = Benchmark.renderLargest(materials)
        val frame = Benchmark.frameMillis(materials)
        val device = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} Kerne"
        val report = Benchmark.report(results, render, device, paused, largest, frame)
        println(report)
        File("build").mkdirs()
        File("build/benchmark.md").writeText(report)
        val full = results.first { it.chunks == it.awakeChunks && it.name.startsWith("Voll") }
        // far above the 30 steps the phone needs, so a slowdown shows up here first
        assertTrue(full.stepsPerSecond > 60, "fully active world only reaches ${full.stepsPerSecond} steps per second")
        val living = results.first { it.name.contains("Pflanzen") }
        assertTrue(living.stepsPerSecond > 60, "fully active world with plants and animals only reaches ${living.stepsPerSecond} steps per second")
        // the largest world: 8 ms per step on the phone (4a.5); desktop and CI are slower than the phone
        val largestLiving = results.first { it.name.startsWith("Größte") && it.name.contains("Pflanzen") }
        assertTrue(largestLiving.stepsPerSecond > 20, "the largest world only reaches ${largestLiving.stepsPerSecond} steps per second")
    }
}
