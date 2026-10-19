package com.shootcat.react

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
 * Phase 5a in the running app: the village and the inhabitants put down with the tools of the sandbox (one tap, one
 * inhabitant; a stroke too puts down only one), and pictures of them walking on the meadow ("5a_*.png").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Phase5aAppTest {

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

    @Test
    fun villageAndInhabitantsByTap(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        compose.waitForIdle()
        scenario.onActivity {
            vm = ViewModelProvider(it)[AppViewModel::class.java]
            vm.updateSettings(Settings(template = "MEADOW", worldSize = "MEDIUM"))
        }
        compose.onNodeWithText("Spielplatz").performClick()
        run(500)
        vm.world.value!!.watch = null
        steps(60)
        compose.onNodeWithTag("category_Werkzeug").performScrollTo().performClick()
        // the village on the sand on the right, then five inhabitants, a tap each, on the meadow to the left of it
        compose.onNodeWithTag("material_Dorf").performScrollTo().performClick()
        tap(VILLAGE_X, ground(VILLAGE_X) - 2)
        steps(10)
        compose.onNodeWithTag("material_Bewohner").performScrollTo().performClick()
        for (x in FIGURES) {
            tap(x, ground(x) - 8)
            steps(6)
        }
        steps(30)
        val placed = onSim { it.folk.count }
        println("5A after five taps: $placed inhabitants, the village at ${onSim { it.folk.homeX }},${onSim { it.folk.homeY }}")
        assertEquals("one tap, one inhabitant", FIGURES.size, placed)
        // a stroke with the tool puts down one, not one per cell
        compose.onNodeWithTag("world").performTouchInput {
            down(cell(180, ground(180) - 8))
            for (k in 1..20) moveTo(cell(180 + k, ground(180) - 8))
        }
        run(100)
        compose.onNodeWithTag("world").performTouchInput { up() }
        steps(10)
        assertEquals("a stroke puts down one", FIGURES.size + 1, onSim { it.folk.count })
        val area = intArrayOf(160, ground(VILLAGE_X) - 40, 320, ground(VILLAGE_X) + 6)
        shot("5a_bewohner_0s", area)
        steps(60 * 5)
        shot("5a_bewohner_5s", area)
        steps(60 * 10)
        shot("5a_bewohner_15s", area)
        val figures = onSim { it.folk.figures() }
        println("5A after 15 s: " + figures.joinToString { "${it.x},${it.y} state ${it.state}${if (it.home) " im Dorf" else ""}" })
        assertTrue("they walk about on the meadow: " + figures.joinToString { "${it.x},${it.y}" }, figures.all { it.state == Folk.WALK || it.state == Folk.BACK || it.state == Folk.FALL })
    }

    // ------------------------------------------------------------------ helpers

    private fun ground(x: Int): Int = onSim { w ->
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

    private fun <T> onSim(read: (World) -> T): T {
        val latch = CountDownLatch(1)
        val out = AtomicReference<T>()
        vm.world.value!!.post {
            out.set(read(it.world))
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

    /** The screen cut to world cells area (x0, y0, x1, y1), three times as large. */
    private fun shot(name: String, area: IntArray) {
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
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val VILLAGE_X = 305
        val FIGURES = listOf(185, 200, 215, 230, 245)
    }
}
