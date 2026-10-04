package com.shootcat.react

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.ui.GameUiState
import com.shootcat.react.ui.GameViewModel
import com.shootcat.react.ui.level.BoardLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Starts the real app on the JVM and plays levels of all four worlds through the UI like a player
 * would: every move sets the world in motion right away, undo and redo step through the history, and
 * once the tasks hold the "Aufgabe erfüllt" card appears.
 * Screenshots of every screen end up in app/build/screenshots (and in each CI release).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    /**
     * Starts the app with exactly the [completed] levels, set directly in the view model's state
     * (stored progress can survive from an earlier test).
     */
    private fun launch(completed: Set<String> = emptySet()): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[GameViewModel::class.java]
            val field = GameViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(viewModel) as MutableStateFlow<GameUiState>
            state.update { it.copy(progress = Progress(completed = completed)) }
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
        // Only the forest is open at the start.
        compose.onNodeWithTag("world_1").assertIsEnabled()
        compose.onNodeWithTag("world_2").assertIsNotEnabled()
        shot("02_weltkarte")
        openWorld(1)
        shot("03_wald")

        // Level 1: the tasks are on top, every move counts at once.
        openLevel("w1_01")
        compose.onNodeWithContentDescription("Aufgaben").assertExists()
        compose.onNodeWithText("Fülle die Mulde mit Wasser").assertExists()
        compose.onNodeWithTag("task_1").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").assertIsNotEnabled()
        shot("04_tauwetter_start")

        // A harmless move, undone and redone.
        moveOnBoard("w1_01", from = 1 to 11, to = 2 to 11)
        advance(millis = 600)
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsNotEnabled()

        // Next to the ice: it melts, the water runs down into the hollow – and the tree stays green.
        moveOnBoard("w1_01", from = 2 to 11, to = 7 to 9)
        advance(millis = 900)
        shot("05_tauwetter_schmilzt")
        awaitText("Aufgabe erfüllt")
        // The optional task (the tree stays green) is listed on the card as well.
        compose.onAllNodesWithText("Der Baum bleibt grün").assertCountEquals(2)
        shot("06_aufgabe_erfuellt")

        compose.onNodeWithText("Nochmal").performClick()
        advance(millis = 300)
        compose.onNodeWithText("Aufgabe erfüllt").assertDoesNotExist()
        // Starting over can be undone as well.
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled()

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hitze + Eis → Wasser").assertExists()
        shot("07_entdeckungen")

        // Back to the level, to the map, and into level 2.
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        openLevel("w1_02")
        shot("08_keimling_start")

        // Level 2: the flame melts the ice from the side, the water reaches the seed – the flame does not.
        moveOnBoard("w1_02", from = 9 to 14, to = 5 to 11)
        advance(millis = 900)
        shot("09_keimling_live")
        awaitText("Aufgabe erfüllt")

        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Steinwurf").assertExists()
        shot("10_steinwurf_start")

        // Level 3: the stone sinks into the pond, the water spills over to the seed.
        moveOnBoard("w1_03", from = 1 to 10, to = 2 to 12)
        advance(millis = 900)
        shot("11_steinwurf_ueberlauf")
        awaitText("Aufgabe erfüllt")

        leaveToMap()
        shot("12_wald_fortschritt")

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tempo").assertExists()
        shot("13_einstellungen")
    }

    @Test
    fun allWorlds(): Unit = launch(completed = (mains(1) + mains(2) + mains(3) + mains(4, upTo = 19)).toSet()).use {
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        for (world in 1..4) compose.onNodeWithTag("world_$world").assertIsEnabled()
        shot("20_weltkarte_offen")

        // The forest with its bonus level open.
        openWorld(1)
        compose.onNodeWithTag("level_w1_bonus").assertIsEnabled()
        shot("21_wald_bonus")
        backToWorlds()

        // Coast: the stone in the sea makes it spill over the fire on the beach.
        openWorld(2)
        shot("22_kueste")
        openLevel("w2_01")
        shot("23_brandung_start")
        moveOnBoard("w2_01", from = 1 to 7, to = 3 to 9)
        advance(millis = 1200)
        shot("24_brandung_flut")
        awaitText("Aufgabe erfüllt")
        leaveToMap()
        backToWorlds()

        // Volcano: ice dropped onto the lava cools it to stone.
        openWorld(3)
        shot("25_vulkan")
        openLevel("w3_01")
        moveOnBoard("w3_01", from = 1 to 7, to = 5 to 11)
        advance(millis = 900)
        shot("26_erste_glut")
        awaitText("Aufgabe erfüllt")
        leaveToMap()
        backToWorlds()

        // Frost: the overflow freezes at the crystal into a loose block of ice – carried to the big fire.
        openWorld(4)
        shot("27_frost")
        openLevel("w4_02")
        shot("28_eisblock_start")
        moveOnBoard("w4_02", from = 1 to 5, to = 2 to 7)
        advance(millis = 7000)
        shot("29_eisblock_gefroren")
        moveOnBoard("w4_02", from = 5 to 10, to = 7 to 12)
        advance(millis = 900)
        shot("30_eisblock_loescht")
        awaitText("Aufgabe erfüllt")
        leaveToMap()

        // Steam from a doused flame thaws the plug, the water reaches the seed.
        openLevel("w4_06")
        moveOnBoard("w4_06", from = 8 to 9, to = 3 to 11)
        advance(millis = 1500)
        shot("31_dampftuer")
        awaitText("Aufgabe erfüllt")
        leaveToMap()

        // Later levels: a look at the boards.
        for (id in listOf("w4_14", "w4_20")) {
            openLevel(id)
            shot("32_${id}_start")
            compose.onNodeWithTag("back").performClick()
            compose.waitForIdle()
        }
        backToWorlds()

        compose.onNodeWithContentDescription("Entdeckungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("discovery_list").performScrollToNode(hasText("WELT 4 · FROST"))
        compose.onNodeWithText("WELT 4 · FROST").assertExists()
        shot("33_entdeckungen_frost")
    }

    private fun openWorld(number: Int) {
        compose.onNodeWithTag("world_$number").performScrollTo()
        compose.onNodeWithTag("world_$number").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("level_w${number}_01").assertExists("world $number did not open")
    }

    /** Closes the completion card and goes back from the level to the world's map. */
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

    /** Tap an object to pick it up, then tap the target cell (grid coordinates of the level file). */
    private fun moveOnBoard(levelId: String, from: Pair<Int, Int>, to: Pair<Int, Int>) {
        val (columns, rows) = gridOf(levelId)
        for ((x, y) in listOf(from, to)) {
            compose.onNodeWithTag("board").performTouchInput {
                val layout = BoardLayout.of(width.toFloat(), height.toFloat(), columns, rows)
                click(Offset(layout.origin.x + (x + 0.5f) * layout.cell, layout.origin.y + (y + 0.5f) * layout.cell))
            }
            compose.waitForIdle()
        }
    }

    private fun gridOf(levelId: String): Pair<Int, Int> {
        val dir = File("src/main/assets/levels")
        val catalog = LevelLoader.parseCatalog(File(dir, "elements.json").readText())
        val number = levelId.removePrefix("w").substringBefore('_').toInt()
        val world = LevelLoader.parseWorld(File(dir, "world_%02d.json".format(number)).readText(), catalog)
        val level = LevelLoader.parseLevel(File(dir, "$levelId.json").readText(), world)
        return level.width to level.height
    }

    /** Lets the world run until [text] shows up (at most [maxMillis] of virtual time). */
    private fun awaitText(text: String, maxMillis: Long = 20_000) {
        var waited = 0L
        while (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() && waited < maxMillis) {
            advance(millis = 200)
            waited += 200
        }
        compose.onNodeWithText(text).assertExists("'$text' did not appear within $maxMillis ms")
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
