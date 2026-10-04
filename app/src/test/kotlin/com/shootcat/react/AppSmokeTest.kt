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
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Starts the real app on the JVM and plays the first levels of the exam world through the UI like a
 * player would: every move sets the world in motion right away, undo and redo step through the history,
 * and once the tasks hold the "Aufgabe erfüllt" card appears. Then every level is opened once.
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

    @Test
    fun playFirstLevelsLive(): Unit = launch().use {
        compose.onNodeWithText("REACT").assertExists()
        shot("01_titel")
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        // A single world: "Spielen" opens its map right away, only the first level is open.
        compose.onNodeWithTag("level_p_01").assertIsEnabled()
        compose.onNodeWithTag("level_p_02").assertIsNotEnabled()
        shot("02_pruefung_karte")

        // p_01: the tasks are on top, every move counts at once.
        openLevel("p_01")
        compose.onNodeWithContentDescription("Aufgaben").assertExists()
        compose.onNodeWithText("Lass den Samen keimen").assertExists()
        compose.onNodeWithTag("task_0").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").assertIsNotEnabled()
        shot("03_umweg_start")

        // A harmless move onto a placement field (the flame drops to the floor), undone and redone.
        moveOnBoard("p_01", from = 1 to 8, to = 6 to 9)
        advance(millis = 600)
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        advance(millis = 300)
        compose.onNodeWithContentDescription("Wiederholen").assertIsEnabled().performClick()
        // Redo goes back to just after the drop: the flame falls to the floor once more.
        advance(millis = 1200)
        compose.onNodeWithContentDescription("Wiederholen").assertIsNotEnabled()

        // Both flames become a big fire; laid on the wall by the pond it boils the water into a cloud.
        moveOnBoard("p_01", from = 6 to 11, to = 2 to 8)
        advance(millis = 600)
        moveOnBoard("p_01", from = 2 to 8, to = 4 to 9)
        advance(millis = 3000)
        shot("04_umweg_dampf")
        awaitText("Aufgabe erfüllt")
        shot("05_aufgabe_erfuellt")

        compose.onNodeWithText("Nochmal").performClick()
        advance(millis = 300)
        compose.onNodeWithText("Aufgabe erfüllt").assertDoesNotExist()
        // Starting over can be undone as well.
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled()

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Flamme + Flamme → Großes Feuer").assertExists()
        shot("06_entdeckungen")

        // Back to the level, to the map, and into p_02.
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        openLevel("p_02")
        shot("07_zu_wenig_start")

        // p_02: one puddle into the hollow, the other onto it, then all of it to the big fire.
        moveOnBoard("p_02", from = 2 to 5, to = 5 to 8)
        advance(millis = 1500)
        moveOnBoard("p_02", from = 8 to 5, to = 5 to 8)
        advance(millis = 1500)
        shot("08_zu_wenig_gesammelt")
        moveOnBoard("p_02", from = 5 to 8, to = 8 to 11)
        awaitText("Aufgabe erfüllt")

        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Verdrängung").assertExists()
        shot("09_verdraengung_start")

        // p_03: the pumice shields the heat, then both stones lift the pond into the hollow.
        moveOnBoard("p_03", from = 2 to 4, to = 8 to 7)
        advance(millis = 900)
        moveOnBoard("p_03", from = 0 to 4, to = 3 to 7)
        // The stone sinks through the pond for a dozen steps before the next one follows.
        advance(millis = 4500)
        moveOnBoard("p_03", from = 1 to 4, to = 4 to 7)
        advance(millis = 900)
        shot("10_verdraengung_ueberlauf")
        awaitText("Aufgabe erfüllt")

        leaveToMap()
        shot("11_pruefung_fortschritt")

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tempo").assertExists()
        shot("12_einstellungen")
    }

    @Test
    fun everyLevelOpens(): Unit = launch(completed = levelIds().toSet()).use {
        compose.onNodeWithText("Spielen").performClick()
        compose.waitForIdle()
        shot("20_pruefung_alle_offen")
        for ((i, id) in levelIds().withIndex()) {
            openLevel(id)
            shot("%02d_%s_start".format(21 + i, id))
            compose.onNodeWithTag("back").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Entdeckungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("discovery_list").assertExists()
        shot("39_entdeckungen")
    }

    /** The levels of the exam world, in order. */
    private fun levelIds(): List<String> {
        val dir = File("src/main/assets/levels")
        val catalog = LevelLoader.parseCatalog(File(dir, "elements.json").readText())
        return LevelLoader.parseWorld(File(dir, "world_01.json").readText(), catalog).levelIds
    }

    /** Closes the completion card and goes back from the level to the world's map. */
    private fun leaveToMap() {
        compose.onNodeWithText("Nochmal").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Rückgängig").assertDoesNotExist()
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
        val world = dir.listFiles { f -> f.name.startsWith("world_") }!!.sorted()
            .map { LevelLoader.parseWorld(it.readText(), catalog) }
            .first { levelId in it.allLevelIds }
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
