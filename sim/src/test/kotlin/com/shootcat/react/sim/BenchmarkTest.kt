package com.shootcat.react.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance at the target size of 200 × 350 cells (Leitfaden V3, 4.1.7). On a desktop or CI machine;
 * the app runs the same benchmark on the phone. The report lands in build/benchmark.md.
 */
class BenchmarkTest {

    @Test
    fun `steps per second in an empty, a fully active and a busy sandbox world`() {
        val materials = MaterialTable.load()
        val results = Benchmark.runAll(materials, steps = 600)
        val render = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, Terrain.WIDTH, Terrain.HEIGHT))
        val device = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} Kerne"
        val report = Benchmark.report(results, render, device)
        println(report)
        File("build").mkdirs()
        File("build/benchmark.md").writeText(report)
        val full = results.first { it.chunks == it.awakeChunks && it.name.startsWith("Voll") }
        // far above the 30 steps the phone needs, so a slowdown shows up here first
        assertTrue(full.stepsPerSecond > 60, "fully active world only reaches ${full.stepsPerSecond} steps per second")
    }
}
