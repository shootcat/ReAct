package com.shootcat.react

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.engine.LiveSimulation
import com.shootcat.react.engine.RuleEngine
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.ui.level.Board
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.ReactTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Plays the small water scenes in src/test/resources/scenes the way the app does (one simulation step
 * every 300 ms, each step animated) and saves the board in motion and at rest: a stone in a full pond,
 * floating wood, a waterfall over a rock edge, water running over a stone, sea water under fresh water
 * and lava meeting water. The screenshots end up in app/build/screenshots next to the app's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WaterScenesTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private class Shown(val state: GameState, val previous: GameState?, val progress: Float)

    private val levels = File("src/main/assets/levels")
    private val scenes = File("src/test/resources/scenes")

    @Test
    fun waterScenes(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        val catalog = LevelLoader.parseCatalog(File(levels, "elements.json").readText())
        val world = LevelLoader.parseWorld(File(levels, "world_01.json").readText(), catalog)
        val lines = File(scenes, "scenes.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue("six water scenes", lines.size >= 6)
        for ((index, line) in lines.withIndex()) {
            val (name, move, moments) = line.split(Regex("\\s+"))
            val level = LevelLoader.parseLevel(File(scenes, "$name.json").readText(), world)
            val sim = LiveSimulation(level, RuleEngine(catalog.types, level.rules, wind = level.wind))
            val (id, at) = move.split("@")
            val (x, y) = at.split(",").map { it.toInt() }
            val states = mutableListOf(sim.start().state)
            var run = sim.move(sim.start(), id, Position(x, y)) ?: error("$name: move $move not allowed")
            states += run.state
            while (run.active && states.size < 300) {
                run = sim.step(run).run
                if (run.state != states.last()) states += run.state
            }

            fun shownAt(ms: Long): Shown {
                if (ms < 100) return Shown(states[1], null, 1f)
                val k = ((ms - 100) / 300).toInt() + 2
                if (k > states.size - 1) return Shown(states.last(), states[states.size - 2], 1f)
                return Shown(states[k], states[k - 1], ((ms - 100) % 300) / 300f)
            }

            val shown = mutableStateOf(Shown(states[0], null, 1f))
            // The test steps time itself: the water animates on this clock, not on the (paused) frame clock.
            val clock = mutableStateOf(0L)
            scenario.onActivity { activity ->
                activity.setContent {
                    ReactTheme {
                        Box(Modifier.fillMaxSize().background(Palette.background)) {
                            val s = shown.value
                            Board(
                                level = level.copy(goals = emptyList()),
                                state = s.state,
                                previous = s.previous,
                                progress = s.progress,
                                types = catalog.types,
                                interactive = false,
                                overload = false,
                                goalsMet = emptyList(),
                                previewDrop = { _, _ -> null },
                                onMove = { _, _ -> },
                                onBounce = {},
                                modifier = Modifier.fillMaxSize(),
                                clockNanos = clock.value,
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(500)

            val inMotion = moments.split(",").map { it.toLong() }
            val atRest = 100L + states.size * 300L + 800L
            val captures = (inMotion.map { it to "bewegung" } + (atRest to "ruhe")).toMap()
            var ms = 0L
            while (ms <= atRest) {
                compose.runOnIdle {
                    shown.value = shownAt(ms)
                    clock.value = (ms + 1_000) * 1_000_000L
                }
                compose.mainClock.advanceTimeBy(FRAME)
                captures[ms]?.let { label ->
                    val suffix = if (label == "bewegung" && inMotion.size > 1) "${label}_$ms" else label
                    shot("%02d_wasser_%s_%s".format(40 + index, name, suffix))
                }
                ms += FRAME
            }
        }
    }

    /** Draws the window into a PNG. */
    private fun shot(name: String) {
        compose.waitForIdle()
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view.rootView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** One animation frame of virtual time; capture moments are multiples of it. */
        const val FRAME = 50L
    }
}
