package com.shootcat.react

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.shootcat.react.data.Progress
import com.shootcat.react.data.ProgressStore
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.ui.GameUiState
import com.shootcat.react.ui.GameViewModel
import com.shootcat.react.ui.level.Viewport
import org.junit.Before
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Starts the real app on the JVM and plays levels of all four worlds through the UI like a player
 * would: in live mode every move sets the world in motion right away, undo and redo step through
 * the history.
 * Screenshots of every screen end up in app/build/screenshots (and in each CI release).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    /** Robolectric keeps the preferences between tests: every test starts without progress. */
    @Before
    fun clearProgress() {
        ProgressStore(RuntimeEnvironment.getApplication()).clear()
    }

    /** Starts the app; [completed] levels are set directly in the view model's state. */
    private fun launch(completed: Set<String> = emptySet()): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        if (completed.isNotEmpty()) {
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[GameViewModel::class.java]
                val field = GameViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(viewModel) as MutableStateFlow<GameUiState>
                state.update { it.copy(progress = Progress(completed = completed)) }
            }
        }
        compose.waitForIdle()
        return scenario
    }

    /** The main levels 1..[upTo] of [world]. */
    private fun mains(world: Int, upTo: Int = 20) = (1..upTo).map { "w${world}_%02d".format(it) }

    @Test
    fun playFirstLevelsLive(): Unit = launch().use {
        compose.onNodeWithText("REACT").assertExists()
        shot("01_titel")
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        // Only the first world is open at the start.
        compose.onNodeWithTag("world_1").assertIsEnabled()
        compose.onNodeWithTag("world_2").assertIsNotEnabled()
        shot("02_welten")
        openWorld(1)
        shot("03_weltkarte")

        // Level 1: no start button – every move counts immediately.
        openLevel("w1_01")
        compose.onNodeWithContentDescription("Ziel").assertExists()
        compose.onNodeWithContentDescription("Start").assertDoesNotExist()
        compose.onNodeWithContentDescription("Rückgängig").assertIsNotEnabled()
        shot("04_level1_start")

        // A harmless move, undone and redone.
        moveOnBoard("w1_01", from = 2 to 3, to = 3 to 3)
        advance(millis = 600)
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsNotEnabled()

        // Next to the ice: it melts at once, the water douses the fire and presses the button.
        moveOnBoard("w1_01", from = 3 to 3, to = 5 to 3)
        advance(millis = 300)
        shot("05_level1_live")
        advance(millis = 2500)
        compose.onNodeWithText("Geschafft").assertExists()
        compose.onNodeWithText("Standard").assertExists()
        shot("06_geschafft")

        compose.onNodeWithText("Nochmal").performClick()
        advance(millis = 300)
        compose.onNodeWithText("Geschafft").assertDoesNotExist()
        // The reset itself can be undone.
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled()

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hitze + Eis → Wasser").assertExists()
        compose.onNodeWithText("Wasser + Feuer → Dampf").assertExists()
        shot("07_entdeckungen")

        // Back to the level, then to the map and into level 2.
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        openLevel("w1_02")
        shot("08_level2_start")

        // Level 2: the fire falls down the slope into the ice, the water runs down to the button.
        moveOnBoard("w1_02", from = 6 to 3, to = 11 to 2)
        advance(millis = 1000)
        shot("09_level2_live")
        advance(millis = 4000)
        compose.onNodeWithText("Geschafft").assertExists()

        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gewicht").assertExists()
        shot("10_level3_start")

        // Level 3: the stone falls through the hole onto the plate.
        moveOnBoard("w1_03", from = 2 to 2, to = 4 to 2)
        advance(millis = 3000)
        compose.onNodeWithText("Geschafft").assertExists()
        shot("11_level3_geloest")

        leaveToMap()
        shot("12_weltkarte_fortschritt")

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tempo").assertExists()
        shot("13_einstellungen")
    }

    @Test
    fun allWorlds(): Unit = launch(completed = (mains(1) + mains(2) + mains(3) + mains(4, upTo = 6)).toSet()).use {
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        for (world in 1..4) compose.onNodeWithTag("world_$world").assertIsEnabled()
        shot("20_welten_offen")

        // World 1 with the bonus level unlocked.
        openWorld(1)
        compose.onNodeWithTag("level_w1_bonus").assertIsEnabled()
        shot("21_welt1_bonus")
        backToWorlds()

        // World 2 – pressure: the metal rod carries the heat into the boiler, the steam lifts the piston.
        openWorld(2)
        shot("22_welt2_karte")
        openLevel("w2_01")
        shot("23_w2_kessel")
        moveOnBoard("w2_01", from = 1 to 5, to = 4 to 5)
        advance(millis = 1200)
        shot("24_w2_kessel_dampf")
        advance(millis = 2500)
        compose.onNodeWithText("Geschafft").assertExists()
        leaveToMap()
        backToWorlds()

        // World 3 – electricity: the battery powers the cable, the lamp lights up.
        openWorld(3)
        shot("25_welt3_karte")
        openLevel("w3_01")
        moveOnBoard("w3_01", from = 2 to 2, to = 6 to 2)
        advance(millis = 1500)
        compose.onNodeWithText("Geschafft").assertExists()
        leaveToMap()

        // The steam turbine: fire under the metal, the steam spins the turbine and powers the lamp.
        openLevel("w3_08")
        moveOnBoard("w3_08", from = 1 to 6, to = 4 to 6)
        advance(millis = 1200)
        shot("26_w3_turbine")
        advance(millis = 2500)
        compose.onNodeWithText("Geschafft").assertExists()
        shot("27_w3_turbine_geloest")
        leaveToMap()
        backToWorlds()

        // World 4 – volcano: lava, oil and sand.
        openWorld(4)
        shot("28_welt4_karte")
        openLevel("w4_01")
        shot("29_w4_lava")
        moveOnBoard("w4_01", from = 3 to 2, to = 9 to 1)
        advance(millis = 2500)
        compose.onNodeWithText("Geschafft").assertExists()
        leaveToMap()

        openLevel("w4_03")
        moveOnBoard("w4_03", from = 2 to 2, to = 1 to 3)
        advance(millis = 2000)
        shot("30_w4_oel_brennt")
        advance(millis = 6000)
        compose.onNodeWithText("Geschafft").assertExists()
        leaveToMap()

        openLevel("w4_04")
        moveOnBoard("w4_04", from = 7 to 3, to = 5 to 3)
        advance(millis = 1500)
        shot("31_w4_sand_rutscht")
        advance(millis = 4000)
        compose.onNodeWithText("Geschafft").assertExists()
        leaveToMap()

        // Later levels: just a look at the boards.
        for (id in listOf("w4_06", "w4_07")) {
            openLevel(id)
            shot("32_${id}_start")
            compose.onNodeWithTag("back").performClick()
            compose.waitForIdle()
        }
        backToWorlds()

        compose.onNodeWithContentDescription("Entdeckungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("discovery_list").performScrollToNode(hasText("Strom + Lampe → Lampe an"))
        compose.onNodeWithText("Strom + Lampe → Lampe an").assertExists()
        shot("33_entdeckungen_welten")
    }

    private fun openWorld(number: Int) {
        compose.onNodeWithTag("world_$number").performScrollTo()
        compose.onNodeWithTag("world_$number").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("level_w${number}_01").assertExists("world $number did not open")
    }

    /** Closes the completion card and goes back from the level to the world map. */
    private fun leaveToMap() {
        compose.onNodeWithText("Nochmal").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Rückgängig").assertDoesNotExist()
    }

    private fun backToWorlds() {
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("world_1").assertExists()
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
        val catalog = LevelLoader.parseCatalog(File(dir, "elements.json").readText())
        val number = levelId.removePrefix("w").substringBefore('_').toInt()
        val world = LevelLoader.parseWorld(File(dir, "world_%02d.json".format(number)).readText(), catalog)
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
