package com.shootcat.react

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Starts the real app on the JVM and plays all beta levels through the UI like a player would.
 * Screenshots of every screen end up in app/build/screenshots (and in each CI release).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun playAllBetaLevelsThroughTheUi() {
        compose.onNodeWithText("REACT").assertExists()
        shot("01_weltkarte")

        // Level 0: fire next to the ice on the button.
        compose.onNodeWithTag("level_level_00").performClick()
        compose.onNodeWithText("Ziel: Tür offen").assertExists()
        shot("02_level0_aufbau")
        moveOnBoard(columns = 8, from = 2 to 4, to = 5 to 4)
        compose.onNodeWithText("Verschoben: 1 Objekt").assertExists()

        compose.onNodeWithText("Start").performClick()
        advance(millis = 500)
        shot("03_level0_simulation")
        advance(millis = 3000)
        compose.onNodeWithText("Level geschafft!").assertExists()
        compose.onNodeWithText("Standard-Weg").assertExists()
        shot("04_level_geschafft", compose.onAllNodes(isRoot()).onLast())

        compose.onNodeWithText("Weiter experimentieren").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Schritt 2 / 2").assertExists()
        shot("05_level0_zeitleiste")

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Feuer + Eis → Wasser").assertExists()
        shot("06_entdeckungen")

        // Back to the level, then to the map and into level 1.
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("level_level_01").performClick()
        compose.waitForIdle()
        shot("07_level1_aufbau")

        // Level 1: melt the ice on the ledge from the left, the water finds the button.
        moveOnBoard(columns = 10, from = 4 to 5, to = 1 to 2)
        compose.onNodeWithText("Start").performClick()
        advance(millis = 6000)
        compose.onNodeWithText("Level geschafft!").assertExists()
        shot("08_level1_geloest", compose.onAllNodes(isRoot()).onFirst())

        compose.onNodeWithText("Nächstes Level").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gewicht").assertExists()
        shot("09_level2_aufbau")

        // Level 2: melt the dam – the water alone is heavy enough (System-Override).
        moveOnBoard(columns = 13, from = 3 to 2, to = 8 to 2)
        compose.onNodeWithText("Start").performClick()
        advance(millis = 2500)
        shot("10_level2_simulation")
        advance(millis = 6000)
        compose.onNodeWithText("Level geschafft!").assertExists()
        compose.onNodeWithText("System-Override").assertExists()
        shot("11_level2_geloest", compose.onAllNodes(isRoot()).onFirst())

        compose.onNodeWithText("Zur Weltkarte").performClick()
        compose.waitForIdle()
        shot("12_weltkarte_fortschritt")
    }

    /** Tap-to-select an object, then tap the target cell. */
    private fun moveOnBoard(columns: Int, from: Pair<Int, Int>, to: Pair<Int, Int>) {
        for ((x, y) in listOf(from, to)) {
            compose.onNodeWithTag("board").performTouchInput {
                val cell = width / columns.toFloat()
                click(Offset(cell * (x + 0.5f), cell * (y + 0.5f)))
            }
            compose.waitForIdle()
        }
    }

    /** Lets coroutine delays (playback, toasts) and animations run for [millis] of virtual time. */
    private fun advance(millis: Long) {
        var left = millis
        while (left > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            left -= 100
        }
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
