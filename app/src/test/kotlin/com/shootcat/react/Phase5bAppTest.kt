package com.shootcat.react

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shootcat.react.data.Settings
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.Kind
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.World
import com.shootcat.react.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 5b in the running app: inhabitants put down with the tool on the empty field, what they lack over their heads,
 * and what they do of their own. In frost (-20 °C) they freeze, kindle a log at a burning post and warm themselves at
 * it; a thirsty one drinks from a pond; one in smoke runs out of it. Pictures "5b_*.png", each a row of moments with
 * what the figures do written under it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Phase5bAppTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var vm: AppViewModel
    private val world: World get() = vm.world.value!!.session.world
    private val mt: MaterialTable get() = world.materials

    @Before
    fun freshSettings() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("react_settings", "react_progress_v3")) app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The empty field in the sandbox, the tool for inhabitants in hand. */
    private fun open(scenario: ActivityScenario<MainActivity>) {
        compose.waitForIdle()
        scenario.onActivity {
            vm = ViewModelProvider(it)[AppViewModel::class.java]
            vm.updateSettings(Settings(template = "EMPTY", worldSize = "MEDIUM"))
        }
        compose.onNodeWithText("Spielplatz").performClick()
        run(500)
        vm.world.value!!.watch = null
        steps(30)
        compose.onNodeWithTag("category_Werkzeug").performScrollTo().performClick()
        compose.onNodeWithTag("material_Bewohner").performScrollTo().performClick()
    }

    @Test
    fun frostKindlingAndWarmth(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        open(scenario)
        val g = ground(240)
        // hard frost, a log on the ground and a burning post (set up on the simulation's thread)
        onSim { s ->
            s.setWeather(s.world.cloudBase, 0f, 0f, false, -20f)
            val w = s.world
            val wood = mt.id("wood")
            for (y in g - 2 until g) for (x in LOG_X0..LOG_X0 + 2) w.set(x, y, wood, -20f)
            for (y in g - 10 until g) for (x in POST_X..POST_X + 1) w.set(x, y, wood, 400f)
        }
        // two inhabitants, a tap each; already cold (as after four seconds of this frost), so that the post still burns
        // when they freeze (FolkNeedsTest measures how fast the cold comes)
        for (x in listOf(200, 212)) {
            tap(x, ground(x) - 6)
            steps(4)
        }
        assertEquals("two taps, two inhabitants", 2, onSim { it.world.folk.count })
        onSim { s -> for (id in 1..2) s.world.folk.setNeeds(id, cold = Folk.COLD_SHOW - 100) }
        val area = intArrayOf(186, g - 26, 270, g + 3)
        val shots = ArrayList<Pair<Bitmap, String>>()
        shots += shot(area) to "0 s: ${say()}"
        // waits until each of these is to be seen, and takes a picture of it
        val start = world.step
        val moments: List<Pair<String, (World) -> Boolean>> = listOf(
            Pair("friert") { w: World -> (0 until w.folk.count).any { w.folk.needs(it) and Folk.Need.COLD != 0 } },
            Pair("zündet an") { w: World -> (0 until w.folk.count).any { w.folk.state(it) == Folk.KINDLE } },
            Pair("wärmt sich") { w: World -> (0 until w.folk.count).any { w.folk.state(it) == Folk.WARM } },
        )
        for ((what, test) in moments) {
            val at = until(60 * 40) { test(it) }
            println("5B frost: '$what' after ${(world.step - start) / 60f} s")
            assertTrue("'$what' to be seen within 40 s", at)
            shots += shot(area) to "${"%.1f".format((world.step - start) / 60f)} s: ${say()}"
        }
        // and at last warm again
        val warm = until(60 * 40) { w -> w.folk.did[Folk.Did.WARMED] > 0 }
        shots += shot(area) to "${"%.1f".format((world.step - start) / 60f)} s: ${say()}"
        val did = onSim { it.world.folk.did.copyOf() }
        println("5B frost: froze ${did[Folk.Did.COLD]}, kindled ${did[Folk.Did.KINDLE]}, warmed up ${did[Folk.Did.WARMED]}, fainted ${did[Folk.Did.FAINT]}")
        assertTrue("warm again", warm)
        strip("5b_frost_feuer", "Frost −20 °C: frieren (Schneeflocke), zünden den Holzklotz am brennenden Pfahl an, wärmen sich", shots)
    }

    @Test
    fun thirstAndSmoke(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        open(scenario)
        val g = ground(255)
        // a pond dug two cells deep, and a cloud of smoke on the left (set up on the simulation's thread)
        onSim { s ->
            val w = s.world
            val water = mt.id("water")
            val smoke = mt.id("smoke")
            for (y in g until g + 2) for (x in POND_X0..POND_X0 + 15) w.set(x, y, water)
            for (y in g - 30 until g) for (x in SMOKE_X0..SMOKE_X0 + 24) if (w.material(x, y) == MaterialTable.AIR) w.set(x, y, smoke)
        }
        tap(232, ground(232) - 6)
        steps(2)
        tap(SMOKE_X0 + 12, ground(SMOKE_X0 + 12) - 6)
        steps(2)
        // the first is thirsty (as after two minutes without water)
        onSim { it.world.folk.setNeeds(1, thirst = Folk.THIRSTY + 100) }
        assertEquals(2, onSim { it.world.folk.count })
        val area = intArrayOf(SMOKE_X0 - 8, g - 26, POND_X0 + 22, g + 4)
        val shots = ArrayList<Pair<Bitmap, String>>()
        val start = world.step
        for (t in listOf(0, 1, 2, 4)) {
            if (t > 0) steps(start + t * 60 - world.step)
            shots += shot(area) to "$t s: ${say()}"
        }
        val did = onSim { it.world.folk.did.copyOf() }
        val figures = onSim { it.world.folk.figures() }
        println("5B thirst and smoke: drank ${did[Folk.Did.DRINK]}, choked ${did[Folk.Did.CHOKE]}, ran ${did[Folk.Did.FLEE]}; " + figures.joinToString { "${it.x},${it.y} thirst ${it.thirst} breath ${it.breath} faints ${it.faints}" })
        assertTrue("the thirsty one drank", did[Folk.Did.DRINK] >= 1)
        assertTrue("the one in the smoke lacked air", did[Folk.Did.CHOKE] >= 1)
        assertTrue("nobody fainted", figures.all { it.faints == 0 })
        strip("5b_durst_rauch", "Durstig (Tropfen) trinkt am Teich; im Rauch (Wolke) Atemnot, läuft hinaus", shots)
    }

    // ------------------------------------------------------------------ helpers

    /** What the figures do, in words. */
    private fun say(): String = onSim { s ->
        val w = s.world
        (0 until w.folk.count).joinToString("; ") { k ->
            val what = when (w.folk.state(k)) {
                Folk.WALK -> "geht"
                Folk.FALL -> "fällt"
                Folk.SWIM -> "schwimmt"
                Folk.SINK -> "geht unter"
                Folk.FAINT -> "ohnmächtig"
                Folk.WARM -> "wärmt sich"
                Folk.KINDLE -> "zündet an"
                else -> "kommt zurück"
            }
            val n = w.folk.needs(k)
            val lacks = listOfNotNull(
                if (n and Folk.Need.COLD != 0) "friert" else null,
                if (n and Folk.Need.THIRST != 0) "durstig" else null,
                if (n and Folk.Need.AIR != 0) "Atemnot" else null,
            )
            what + if (lacks.isEmpty()) "" else " (" + lacks.joinToString(", ") + ")"
        }
    }

    /** Runs until [what] holds, at most [max] steps; whether it came. */
    private fun until(max: Int, what: (World) -> Boolean): Boolean {
        val end = world.step + max
        while (world.step < end) {
            if (onSim { what(it.world) }) return true
            steps(6)
        }
        return onSim { what(it.world) }
    }

    private fun ground(x: Int): Int = onSim { s ->
        val w = s.world
        var y = 0
        while (y < w.height) {
            val m = w.material(x, y)
            if (m != MaterialTable.AIR && mt.kind[m] != Kind.GAS && mt.kind[m] != Kind.LIQUID && !mt.plant[m] && mt.organism[m] == 0) break
            y++
        }
        y
    }

    private fun tap(x: Int, y: Int) {
        compose.onNodeWithTag("world").performTouchInput {
            down(cell(x, y))
            up()
        }
        run(80)
    }

    private fun androidx.compose.ui.test.TouchInjectionScope.cell(x: Int, y: Int): Offset {
        val s = height / world.height.toFloat()
        val ox = (width - world.width * s) / 2f
        return Offset(ox + (x + 0.5f) * s, (y + 0.5f) * s)
    }

    private fun steps(n: Int) {
        val target = world.step + n
        var waited = 0
        while (world.step < target && waited < n * 200 + 10_000) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            compose.mainClock.advanceTimeBy(10)
            waited += 10
        }
        run(60)
    }

    private fun run(millis: Long) {
        var left = millis
        while (left > 0) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            compose.mainClock.advanceTimeBy(20)
            left -= 20
        }
        compose.waitForIdle()
    }

    private fun <T> onSim(read: (Session) -> T): T {
        val latch = CountDownLatch(1)
        val out = AtomicReference<T>()
        vm.world.value!!.post {
            out.set(read(it))
            latch.countDown()
        }
        var waited = 0
        while (!latch.await(10, TimeUnit.MILLISECONDS)) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            waited += 10
            if (waited > 10_000) throw AssertionError("the simulation thread did not answer")
        }
        return out.get()
    }

    /**
     * The screen cut to world cells area (x0, y0, x1, y1), three times as large, with the simulation paused while the
     * picture is taken, so that the picture and the words under it show the same step.
     */
    private fun shot(area: IntArray): Bitmap {
        val runner = vm.world.value!!
        runner.speed = SimRunner.Speed.PAUSE
        onSim { }
        run(200)
        compose.waitForIdle()
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view.rootView
        val screen = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(screen))
        val r = compose.onNodeWithTag("world").fetchSemanticsNode().boundsInWindow
        val s = r.height / world.height
        val ox = r.left + (r.width - world.width * s) / 2f
        val left = (ox + area[0] * s).toInt().coerceIn(r.left.toInt(), r.right.toInt() - 1)
        val top = (r.top + area[1] * s).toInt().coerceIn(r.top.toInt(), r.bottom.toInt() - 1)
        val right = (ox + (area[2] + 1) * s).toInt().coerceIn(left + 1, r.right.toInt())
        val bottom = (r.top + (area[3] + 1) * s).toInt().coerceIn(top + 1, r.bottom.toInt())
        val out = Bitmap.createBitmap((right - left) * 3, (bottom - top) * 3, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(screen, Rect(left, top, right, bottom), Rect(0, 0, out.width, out.height), null)
        runner.speed = SimRunner.Speed.NORMAL
        return out
    }

    /** The pictures side by side with their words under them, as "[name].png", and each alone as "[name]_k.png". */
    private fun strip(name: String, title: String, shots: List<Pair<Bitmap, String>>) {
        val dir = File("build/screenshots").apply { mkdirs() }
        val pw = shots.maxOf { it.first.width }
        val ph = shots.maxOf { it.first.height }
        val label = 44
        val gap = 12
        val out = Bitmap.createBitmap(pw * shots.size + gap * (shots.size - 1), ph + label * 3, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(16, 20, 26))
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
        }
        val small = Paint(paint).apply { textSize = 24f }
        c.drawText(title, 12f, 32f, paint)
        for ((k, s) in shots.withIndex()) {
            c.drawBitmap(s.first, null, Rect(k * (pw + gap), label, k * (pw + gap) + s.first.width, label + s.first.height), null)
            c.drawText(s.second.substringBefore(": "), k * (pw + gap) + 12f, label + ph + 34f, paint)
            c.drawText(s.second.substringAfter(": "), k * (pw + gap) + 12f, label + ph + 74f, small)
            File(dir, "${name}_$k.png").outputStream().use { f -> s.first.compress(Bitmap.CompressFormat.PNG, 100, f) }
        }
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("5B $name: " + shots.joinToString(" | ") { it.second })
    }

    private companion object {
        const val LOG_X0 = 226
        const val POST_X = 244
        const val POND_X0 = 245
        const val SMOKE_X0 = 170
    }
}
