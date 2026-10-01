package com.shootcat.react

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.ui.level.Viewport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Starts the real app on the JVM and plays levels through the UI like a player would: in live mode
 * every move sets the world in motion right away, undo and redo step through the history.
 * Screenshots of every screen end up in app/build/screenshots (and in each CI release).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    /** Starts the app; [unlockAll] first stores progress with every level unlocked. */
    private fun launch(unlockAll: Boolean = false): ActivityScenario<MainActivity> {
        if (unlockAll) {
            val levels = (0 until 12).map { "level_%02d".format(it) }.toSet()
            RuntimeEnvironment.getApplication()
                .getSharedPreferences("react_progress", Context.MODE_PRIVATE)
                .edit().putStringSet("completed", levels).commit()
        }
        return ActivityScenario.launch(MainActivity::class.java).also { compose.waitForIdle() }
    }

    @Test
    fun playFirstLevelsLive(): Unit = launch().use {
        compose.onNodeWithText("REACT").assertExists()
        shot("01_titel")
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        shot("02_weltkarte")

        // Level 0: no start button – every move counts immediately.
        compose.onNodeWithTag("level_level_00").performClick()
        compose.onNodeWithContentDescription("Ziel").assertExists()
        compose.onNodeWithContentDescription("Start").assertDoesNotExist()
        compose.onNodeWithContentDescription("Rückgängig").assertIsNotEnabled()
        shot("03_level0_start")

        // A harmless move, undone and redone.
        moveOnBoard("level_00", from = 2 to 4, to = 3 to 4)
        advance(millis = 600)
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsNotEnabled()

        // Next to the ice: it melts at once, the water runs onto the button.
        moveOnBoard("level_00", from = 3 to 4, to = 5 to 4)
        advance(millis = 400)
        shot("04_level0_live")
        advance(millis = 3000)
        compose.onNodeWithText("Geschafft").assertExists()
        compose.onNodeWithText("Standard").assertExists()
        shot("05_geschafft")

        compose.onNodeWithText("Nochmal").performClick()
        advance(millis = 300)
        compose.onNodeWithText("Geschafft").assertDoesNotExist()
        // The reset itself can be undone.
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled()
        shot("06_level0_reset")

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hitze + Eis → Wasser").assertExists()
        compose.onNodeWithText("Wasser + Feuer → Dampf").assertExists()
        shot("07_entdeckungen")

        // Back to the level, then to the map and into level 1.
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("level_level_01").performClick()
        compose.waitForIdle()
        shot("08_level1_start")

        // Level 1: melt the ice on the ledge from the left, the water finds the button.
        moveOnBoard("level_01", from = 4 to 5, to = 1 to 2)
        advance(millis = 6000)
        compose.onNodeWithText("Geschafft").assertExists()
        shot("09_level1_geloest")

        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gewicht").assertExists()
        shot("10_level2_start")

        // Level 2: drop the fire into the shaft – it melts the dam on its way down (System-Override).
        moveOnBoard("level_02", from = 4 to 2, to = 5 to 2)
        advance(millis = 1500)
        shot("11_level2_live")
        advance(millis = 6000)
        compose.onNodeWithText("Geschafft").assertExists()
        compose.onNodeWithText("Override").assertExists()
        shot("12_level2_geloest")

        compose.onNodeWithText("Nochmal").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        shot("13_weltkarte_fortschritt")

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tempo").assertExists()
        shot("14_einstellungen")
    }

    @Test
    fun newMaterials(): Unit = launch(unlockAll = true).use {
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("level_level_12").assertIsEnabled()

        // Heat conduction: the fire on the metal rod melts the ice in the closed chamber.
        shot("19_weltkarte_alles_offen")
        openLevel("level_10")
        shot("20_level10_waermeleiter")
        moveOnBoard("level_10", from = 2 to 2, to = 4 to 2)
        advance(millis = 1500)
        shot("21_level10_heiss")
        advance(millis = 4000)
        compose.onNodeWithText("Geschafft").assertExists()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()

        // Burning wood: the beam burns away and the stone drops onto the plate.
        compose.onNodeWithText("Brandschneise").assertExists()
        shot("22_level11_brandschneise")
        moveOnBoard("level_11", from = 1 to 2, to = 3 to 2)
        advance(millis = 2800)
        shot("23_level11_brennt")
        advance(millis = 6000)
        compose.onNodeWithText("Geschafft").assertExists()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()

        // Steam pressure: boiling water in the closed chamber pushes the gate away.
        compose.onNodeWithText("Überdruck").assertExists()
        shot("24_level12_ueberdruck")
        moveOnBoard("level_12", from = 2 to 2, to = 3 to 2)
        advance(millis = 2000)
        shot("25_level12_dampf")
        advance(millis = 4000)
        compose.onNodeWithText("Geschafft").assertExists()
        shot("26_level12_geloest")

        // Back on the map, the floating wood level.
        compose.onNodeWithText("Karte").performClick()
        compose.waitForIdle()
        openLevel("level_08")
        moveOnBoard("level_08", from = 2 to 2, to = 6 to 2)
        advance(millis = 1000)
        shot("27_level08_holz_schwimmt")
    }

    private fun openLevel(id: String) {
        compose.onNodeWithTag("level_$id").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("level_$id").assertIsEnabled().performClick()
        compose.waitForIdle()
        // Only the level screen has the history dock.
        compose.onNodeWithContentDescription("Rückgängig").assertExists("level $id did not open")
    }

    /** Tap-to-select an object, then tap the target cell (grid coordinates of the level file). */
    private fun moveOnBoard(levelId: String, from: Pair<Int, Int>, to: Pair<Int, Int>) {
        val view = viewport(levelId)
        for ((x, y) in listOf(from, to)) {
            compose.onNodeWithTag("board").performTouchInput {
                val cell = width / view.width
                click(Offset((x + 0.5f - view.left) * cell, (y + 0.5f - view.top) * cell))
            }
            compose.waitForIdle()
        }
    }

    private fun viewport(levelId: String): Viewport {
        val dir = File("src/main/assets/levels")
        val world = LevelLoader.parseWorld(File(dir, "world_01.json").readText())
        val level = LevelLoader.parseLevel(File(dir, "$levelId.json").readText(), world)
        return Viewport.of(level.initialState())
    }

    /** Lets coroutine delays (simulation steps, toasts) and animations run for [millis] of virtual time. */
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
