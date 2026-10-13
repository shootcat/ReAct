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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shootcat.react.sim.WorldSize
import org.junit.Before
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

    /**
     * Every test starts from the default settings: the stored ones outlive a test here, and a folded bar or
     * the volcano left behind by one would be the start of the next.
     */
    @Before
    fun freshSettings() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("react_settings", "react_progress_v3")) app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

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
        category("Hitze").performScrollTo().performClick()
        compose.onNodeWithTag("material_Feuer").performClick()
        paint(36, 112, holdMillis = 400)
        paint(100, 150, holdMillis = 400)
        run(4000)
        shot("03_feuer")

        // pause, pour lava onto the bridge and water onto the hill, then let it run
        compose.onNodeWithTag("speed_Pause").performClick()
        compose.onNodeWithTag("material_Lava").performScrollTo().performClick()
        paint(115, 125, holdMillis = 200)
        category("Flüssig").performScrollTo().performClick()
        compose.onNodeWithTag("material_Wasser").performScrollTo().performClick()
        stroke(20, 70, 60, 70)
        run(300)
        shot("04_pause_mit_lava_und_wasser")
        compose.onNodeWithTag("speed_Normal").performClick()
        run(4000)
        shot("05_lava_trifft_wasser")

        // a stroke taken back
        category("Lose").performScrollTo().performClick()
        compose.onNodeWithTag("material_Sand").performScrollTo().performClick()
        stroke(140, 40, 190, 40)
        run(500)
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        run(500)

        // building in the pause: a brick frame and a wooden plank across it, with the preview while dragging
        compose.onNodeWithTag("speed_Pause").performClick()
        category("Bauen").performScrollTo().performClick()
        compose.onNodeWithTag("material_Ziegel").performScrollTo().performClick()
        brushMenu()
        compose.onNodeWithTag("mode_Rahmen").performClick()
        drag(150, 60, 185, 95, finish = false)
        shot("10_bauen_vorschau")
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(200)
        compose.onNodeWithTag("material_Holz").performScrollTo().performClick()
        brushMenu()
        compose.onNodeWithTag("mode_Linie").performClick()
        drag(140, 58, 195, 58)
        brushMenu()
        compose.onNodeWithTag("mode_Pinsel").performClick()
        compose.onNodeWithTag("speed_Normal").performClick()
        run(1500)
        shot("11_gebaut")

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

    /** Plants, animals and light (2b): the living start world, seeds and fireflies placed, the dark grotto up close. */
    @Test
    fun lifeAndLight(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(2000)
        shot("12_leben")
        category("Leben").performScrollTo().performClick()
        compose.onNodeWithTag("material_Samen").performScrollTo().performClick()
        brushMenu()
        compose.onNodeWithContentDescription("Pinsel 1").performClick()
        paint(70, 140, holdMillis = 100)
        compose.onNodeWithTag("material_Glühwürmchen").performScrollTo().performClick()
        paint(44, 205, holdMillis = 100)
        run(1500)
        // two fingers zoom in on the grotto under the hill: dark, with fireflies and moss
        compose.onNodeWithTag("world").performTouchInput { pinch(cellToOffset(44, 212), 4f) }
        run(1500)
        shot("13_hoehle")
    }

    /**
     * Procuring and the discovery book (4.3): only the bucket, the hand and the store; a log picked up and
     * made into planks, built with; water scooped from the pond and poured on the hill; then the book.
     */
    @Test
    fun procureAndBook(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(1500)
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Modus: nur Beschaffen").performClick()
        run(500)
        category("Werkzeug").performScrollTo().performClick()
        compose.onNodeWithTag("material_Hand").performClick()
        // the fallen log on the dune
        compose.onNodeWithTag("world").performTouchInput {
            down(cellToOffset(156, 151))
            up()
        }
        run(500)
        shot("14_hand")
        compose.onNodeWithTag("material_Zu Brettern").performClick()
        run(500)
        category("Lager").performScrollTo().performClick()
        compose.onNodeWithTag("material_Holz").performClick()
        brushMenu()
        compose.onNodeWithTag("mode_Linie").performClick()
        drag(118, 120, 150, 120)
        run(1500)
        shot("15_lager_gebaut")
        // the bucket: water from the pond onto the hill
        brushMenu()
        compose.onNodeWithTag("mode_Pinsel").performClick()
        category("Werkzeug").performScrollTo().performClick()
        compose.onNodeWithTag("material_Schöpfen").performClick()
        paint(100, 172, holdMillis = 400)
        compose.onNodeWithTag("material_Gießen").performClick()
        paint(40, 95, holdMillis = 800, wiggle = true)
        run(1500)
        shot("16_eimer")
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Entdeckungsbuch").performClick()
        run(500)
        shot("17_buch")
        compose.onNodeWithTag("book").performScrollToNode(hasTestTag("entry_firefly_shine"))
        compose.onNodeWithTag("entry_firefly_shine").performClick()
        run(2000)
        shot("18_buch_szene")
    }

    /** The supply bar with large system font, scrolled half way: no label may run into another (3.4). */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun materialBarWithLargeFont(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(500)
        compose.onNodeWithTag("material_bar").performTouchInput {
            down(Offset(width * 0.9f, height / 2f))
            for (k in 1..10) {
                moveTo(Offset(width * (0.9f - 0.05f * k), height / 2f))
                advanceEventTime(16)
            }
            up()
        }
        run(500)
        shot("09_leiste_grosse_schrift")
    }

    /** Landscape (4a.5): the world beside a column of controls; a new world from a landscape; the column folded away. */
    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeNewWorldAndFold(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(1500)
        shot("19_querformat")
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Neue Welt …").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("template_VOLCANO").performScrollTo().performClick()
        compose.onNodeWithTag("size_MEDIUM").performScrollTo().performClick()
        shot("20_neue_welt", compose.onNodeWithTag("create_world"))
        compose.onNodeWithTag("create_world").performClick()
        run(2500)
        shot("21_vulkan_quer")
        compose.onNodeWithTag("fold").performClick()
        run(800)
        compose.onNodeWithTag("current_tool").assertExists()
        shot("22_leiste_eingeklappt_quer")
        restoreDefaults()
    }

    /** Portrait (4a.5): the snowy mountain and the caves, the bar folded to one slim row and out again. */
    @Test
    fun templatesAndFoldedBar(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(800)
        for ((template, name) in listOf("MOUNTAIN" to "23_berg", "CAVES" to "24_hoehlen", "ISLAND" to "25_insel", "COAST" to "26_kueste")) {
            compose.onNodeWithContentDescription("Menü").performClick()
            compose.onNodeWithText("Neue Welt …").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("template_$template").performScrollTo().performClick()
            compose.onNodeWithTag("create_world").performClick()
            run(2000)
            shot(name)
        }
        compose.onNodeWithTag("fold").performClick()
        run(500)
        compose.onNodeWithTag("current_tool").assertExists()
        shot("27_leiste_eingeklappt")
        compose.onNodeWithTag("unfold").performClick()
        run(300)
        compose.onNodeWithTag("material_bar").assertExists()
        // the corner: speed and the shape of the brush; the menu is a window of its own
        brushMenu()
        shot("28_pinselform", compose.onNodeWithTag("mode_Pinsel"))
        compose.onNodeWithTag("mode_Pinsel").performClick()
        restoreDefaults()
    }

    /**
     * The levels (Phase 3): the list, the first level solved with a single bucket out of the earth in the
     * window (the pool runs out over the ledge into the pond), the medal, on to the second level, and back to
     * the list with the medal on the first.
     */
    @Test
    fun levelSolvedWithMedal(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithTag("levels").performClick()
        run(500)
        shot("29_levelauswahl")
        compose.onNodeWithTag("level_trockener_teich").performClick()
        run(1500)
        compose.onNodeWithTag("level_hud").assertExists()
        shot("30_level1")
        // the level begins with all of it on screen; one bucket out of the middle of the earth in the window
        // of the rock wall (brush 3, the bucket is the first tool). Not its left edge: after a second and a
        // half the pool has seeped in there, and an empty bucket takes what lies under the finger, water.
        // A short touch (3b): it scoops for as long as the finger was down; held still longer it would ask
        // what is there instead.
        compose.onNodeWithTag("world").performTouchInput { down(levelCell(52, 46, 220, 140)) }
        run(400)
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(700)
        compose.onNodeWithTag("speed_Schnell").performClick()
        var waited = 0
        while (compose.onAllNodesWithTag("level_end").fetchSemanticsNodes().isEmpty() && waited < 120_000) {
            run(500)
            waited += 500
        }
        if (compose.onAllNodesWithTag("level_end").fetchSemanticsNodes().isEmpty()) {
            shot("31_level1_nicht_geloest")
            throw AssertionError("Level 1 nicht gelöst. Auf dem Bildschirm:\n" + compose.onAllNodes(isRoot()).printToString(Int.MAX_VALUE))
        }
        shot("31_level1_medaille", compose.onNodeWithTag("level_end"))
        // one bucket out of the window is the gold of this level (TrockenerTeichTest: 31 to 38 s, 1 action)
        val medal = compose.onNodeWithTag("medal").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""
        File("build/screenshots/level1_medaille.txt").writeText(medal)
        if (medal != "Gold") throw AssertionError("Level 1 mit einem Eimer aus dem Fenster: $medal statt Gold")
        compose.onNodeWithTag("level_next").performClick()
        run(1500)
        shot("32_level2")
        compose.onNodeWithTag("back").performClick()
        run(500)
        compose.onNodeWithTag("level_list").assertExists()
        shot("33_auswahl_mit_medaille")
    }

    /** A level in landscape: the goal beside the world; the bucket cannot reach the deep chamber behind the ice. */
    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun levelInLandscapeOutOfReach(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithTag("levels").performClick()
        run(500)
        compose.onNodeWithTag("level_tiefe_schmelze").performScrollTo().performClick()
        run(1500)
        compose.onNodeWithTag("world").performTouchInput {
            down(levelCell(100, 136, 190, 160))
            up()
        }
        run(700)
        compose.onNodeWithTag("notice").assertExists()
        shot("34_level3_quer_nicht_erreichbar")
    }

    /**
     * Phase 3b: a finger held still asks what is there (and is no action), the heat picture over the whole
     * world, with the hand everything it can take lights up, and zoomed far in the world stays in its box.
     */
    @Test
    fun infoHeatAndHand(): Unit = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForIdle()
        compose.onNodeWithText("Spielplatz").performClick()
        run(1500)
        // lava onto the bridge, so the heat picture has something to show
        category("Hitze").performScrollTo().performClick()
        compose.onNodeWithTag("material_Lava").performScrollTo().performClick()
        paint(115, 125, holdMillis = 300)
        run(1500)
        // a finger held still on the pond
        compose.onNodeWithTag("world").performTouchInput { down(cellToOffset(100, 172)) }
        run(800)
        compose.onNodeWithTag("info").assertExists()
        shot("35_info_wasser")
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(300)
        compose.onNodeWithTag("world").performTouchInput { down(cellToOffset(115, 126)) }
        run(800)
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(200)
        compose.onNodeWithTag("info").assertExists()
        shot("36_info_lava")
        compose.onNodeWithTag("thermal").performClick()
        run(800)
        shot("37_waermebild")
        compose.onNodeWithTag("thermal").performClick()
        run(300)
        // only the bucket and the hand: with the hand, everything it can take lights up
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Modus: nur Beschaffen").performClick()
        run(500)
        category("Werkzeug").performScrollTo().performClick()
        compose.onNodeWithTag("material_Hand").performClick()
        run(1500)
        shot("38_hand_greifbar")
        // far in: the world stays in its box and never covers the controls
        compose.onNodeWithTag("world").performTouchInput { pinch(cellToOffset(100, 170), 8f) }
        run(800)
        shot("39_zoom_im_rahmen")
        compose.onNodeWithTag("material_bar").assertExists()
    }

    /**
     * The screen position of cell (x, y) of a level of [w] × [h] cells, with the whole level on screen
     * (zoomed out as far as it goes, or small enough to fit): centred, as large as the view allows.
     */
    private fun TouchInjectionScope.levelCell(x: Int, y: Int, w: Int, h: Int): Offset {
        val s = minOf(width / w.toFloat(), height / h.toFloat())
        val ox = (width - w * s) / 2f
        val oy = (height - h * s) / 2f
        return Offset(ox + (x + 0.5f) * s, oy + (y + 0.5f) * s)
    }

    /**
     * The folded bar and the landscape of a new world are kept for the next start; a test that changes them
     * puts them back, so the next test begins with the meadow and the open bar whatever ran before it.
     */
    private fun restoreDefaults() {
        if (compose.onAllNodesWithTag("unfold").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("unfold").performClick()
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Neue Welt …").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("template_MEADOW").performScrollTo().performClick()
        compose.onNodeWithTag("size_MEDIUM").performScrollTo().performClick()
        compose.onNodeWithTag("create_world").performClick()
        run(300)
    }

    /**
     * The screen position of cell (x, y) of the start landscape as it was drawn for 200 × 350 cells: in the
     * wide meadow it lies unstretched in the middle, and the view fills its height, centred (4a.5).
     */
    private fun TouchInjectionScope.cellToOffset(x: Int, y: Int): Offset {
        val world = WorldSize.MEDIUM
        val k = world.height / 350f
        val left = ((world.width - 200f * k) / 2f).toInt()
        val cx = left + (x * k).toInt()
        val cy = (y * k).toInt()
        val s = height / world.height.toFloat()
        val ox = (width - world.width * s) / 2f
        return Offset(ox + (cx + 0.5f) * s, (cy + 0.5f) * s)
    }

    /** A tab of the bar; if it is missing, the failure shows everything on the screen. */
    private fun category(name: String): SemanticsNodeInteraction {
        if (compose.onAllNodesWithTag("category_$name").fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("Reiter $name fehlt. Auf dem Bildschirm:\n" + compose.onAllNodes(isRoot()).printToString(Int.MAX_VALUE))
        }
        return compose.onNodeWithTag("category_$name")
    }

    /** Opens the shapes and sizes of the brush in the corner of the world. */
    private fun brushMenu() {
        compose.onNodeWithTag("brush_shape").performClick()
        compose.waitForIdle()
    }

    /**
     * Puts a finger down on cell (x, y) for a while: the brush pours for as long. A finger held still longer
     * than a moment asks what is there instead (3b.1), so for a long pour it [wiggle]s first: a stroke goes on
     * pouring where the finger rests.
     */
    private fun paint(x: Int, y: Int, holdMillis: Long, wiggle: Boolean = false) {
        compose.onNodeWithTag("world").performTouchInput {
            down(cellToOffset(x, y))
            if (wiggle) {
                moveBy(Offset(0f, 40f))
                moveBy(Offset(0f, -40f))
            }
        }
        run(holdMillis)
        compose.onNodeWithTag("world").performTouchInput { up() }
        run(100)
    }

    /** A shape dragged from cell (x0, y0) to (x1, y1); with [finish] false the finger stays down. */
    private fun drag(x0: Int, y0: Int, x1: Int, y1: Int, finish: Boolean = true) {
        compose.onNodeWithTag("world").performTouchInput { down(cellToOffset(x0, y0)) }
        run(50)
        for (k in 1..6) {
            compose.onNodeWithTag("world").performTouchInput { moveTo(cellToOffset(x0 + (x1 - x0) * k / 6, y0 + (y1 - y0) * k / 6)) }
            run(30)
        }
        if (finish) {
            compose.onNodeWithTag("world").performTouchInput { up() }
            run(200)
        }
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
