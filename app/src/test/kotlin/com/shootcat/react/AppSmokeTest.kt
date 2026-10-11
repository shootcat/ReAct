package com.shootcat.react

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.shootcat.react.sim.Terrain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import kotlin.math.min

/**
 * Starts the real app on the JVM: the title, the sandbox with a little play (fire, water, lava, pause,
 * undo, zoom), the in-app measurement and the settings. Screenshots end up in app/build/screenshots and in
 * every CI release.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun titleSandboxAndSettings(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("REACT").assertExists()
        run(600)
        shot("01_titel")

        compose.onNodeWithText("Spielplatz").performClick()
        run(1500)
        shot("02_spielplatz")

        // fire on the log pile and on the bridge
        compose.onNodeWithTag("material_Feuer").performClick()
        paint(36, 112, holdMillis = 400)
        paint(100, 150, holdMillis = 400)
        run(4000)
        shot("03_feuer")

        // pause, pour lava onto the bridge and water onto the hill, then let it run
        compose.onNodeWithTag("speed_Pause").performClick()
        compose.onNodeWithTag("material_Lava").performScrollTo().performClick()
        paint(115, 125, holdMillis = 200)
        compose.onNodeWithTag("material_Wasser").performScrollTo().performClick()
        stroke(20, 70, 60, 70)
        run(300)
        shot("04_pause_mit_lava_und_wasser")
        compose.onNodeWithTag("speed_Normal").performClick()
        run(4000)
        shot("05_lava_trifft_wasser")

        // a stroke taken back
        compose.onNodeWithTag("material_Sand").performScrollTo().performClick()
        stroke(140, 40, 190, 40)
        run(500)
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        run(500)

        // two fingers zoom in on the pond
        compose.onNodeWithTag("world").performTouchInput { pinch(cellToOffset(100, 170), 3f) }
        run(800)
        shot("06_zoom")

        // the performance overlay and the in-app measurement
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Leistungsanzeige an").performClick()
        run(1500)
        compose.onNodeWithTag("performance").assertExists()
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Leistung messen").performClick()
        var waited = 0
        while (compose.onAllNodesWithTag("benchmark").fetchSemanticsNodes().isEmpty() && waited < 180_000) {
            run(500)
            waited += 500
        }
        compose.onNodeWithTag("benchmark").assertExists()
        shot("07_messung", compose.onNodeWithTag("benchmark"))
        File("build/screenshots/benchmark_robolectric.txt").writeText(
            compose.onNodeWithTag("benchmark").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Text)?.joinToString("\n") { it.text } ?: "",
        )
        compose.onNodeWithText("Schließen").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Leistungsanzeige").assertExists()
        shot("08_einstellungen")
    }

    /** The screen position of cell (x, y) inside the world view, as the view fits the world in. */
    private fun TouchInjectionScope.cellToOffset(x: Int, y: Int): Offset {
        val s = min(width / Terrain.WIDTH.toFloat(), height / Terrain.HEIGHT.toFloat())
        val ox = (width - Terrain.WIDTH * s) / 2f
        val oy = (height - Terrain.HEIGHT * s) / 2f
        return Offset(ox + (x + 0.5f) * s, oy + (y + 0.5f) * s)
    }

    /** Puts a finger down on cell (x, y) and keeps it there for a while: the brush keeps pouring. */
    private fun paint(x: Int, y: Int, holdMillis: Long) {
        compose.onNodeWithTag("world").performTouchInput { down(cellToOffset(x, y)) }
        run(holdMillis)
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(100)
    }

    /** A finger drawn from cell (x0, y0) to (x1, y1). */
    private fun stroke(x0: Int, y0: Int, x1: Int, y1: Int) {
        compose.onNodeWithTag("world").performTouchInput { down(cellToOffset(x0, y0)) }
        run(100)
        for (k in 1..10) {
            compose.onNodeWithTag("world").performTouchInput { moveTo(cellToOffset(x0 + (x1 - x0) * k / 10, y0 + (y1 - y0) * k / 10)) }
            run(50)
        }
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(100)
    }

    private fun TouchInjectionScope.pinch(center: Offset, factor: Float) {
        val a0 = center - Offset(40f, 0f)
        val b0 = center + Offset(40f, 0f)
        down(0, a0)
        down(1, b0)
        for (k in 1..10) {
            val d = 40f * (1f + (factor - 1f) * k / 10f)
            moveTo(0, center - Offset(d, 0f))
            moveTo(1, center + Offset(d, 0f))
            advanceEventTime(20)
        }
        up(0)
        up(1)
    }

    /**
     * Lets [millis] pass in real time: the simulation thread runs on the wall clock, while the main looper
     * and the Compose clock are moved along so the screen picks up its pictures.
     */
    private fun run(millis: Long) {
        var left = millis
        while (left > 0) {
            Thread.sleep(25)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
            compose.mainClock.advanceTimeBy(25)
            left -= 25
        }
        compose.waitForIdle()
    }

    /** Draws the window that holds [node] (screen or dialog) into a PNG. */
    private fun shot(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        compose.waitForIdle()
        val view = (node.fetchSemanticsNode().root as ViewRootForTest).view.rootView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
