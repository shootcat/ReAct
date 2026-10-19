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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shootcat.react.data.Settings
import com.shootcat.react.sim.Kind
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Seen
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.World
import com.shootcat.react.ui.AppViewModel
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
import kotlin.math.abs

/**
 * The proof of Phase 4c from the running app: each case played with the finger in the sandbox as on the device,
 * and pictures of the screen at 0, 1, 2 and 3 seconds of the world's own time (steps, 60 to the second), cut to
 * where it happens and put side by side in one picture per case ("4c_…png" in every CI release). What the world
 * did is written beside them ("4c_nachweis.txt").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Phase4cEvidenceTest {

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

    // ------------------------------------------------------------------ 1. bodies tip over

    /** A stick drawn slanting up from flat ground, the finger resting on its end for half a second before it lets go. */
    @Test
    fun slantingStickFalls(): Unit = sandbox("EMPTY") {
        val x = 240
        val gy = surface(x)
        pick("Bauen", "Holz", brush = 2)
        stroke(listOf(x - 6 to gy - 2, x + 6 to gy - 14), restMillis = 500)
        val stick = bodyNear(x, gy - 8)
        film("4c_1a_stab_schraeg", "Schräg gemalter Stab auf ebenem Boden", x - 22, gy - 26, x + 22, gy + 4) {
            val b = bodies().firstOrNull { it.id == stick }
            if (b == null) "kein Körper" else "Winkel %.0f°, %s".format(b.angle, if (b.asleep) "liegt still" else "bewegt sich")
        }
        assertTrue("a body was made", stick != 0)
        val b = bodies().first { it.id == stick }
        assertTrue("the slanting stick fell flat: turned ${b.angle}°", abs(b.angle) > 35f && b.asleep)
    }

    /** An upright stick, then a second one drawn leaning towards it, which falls against it: the push. */
    @Test
    fun uprightStickPushedFalls(): Unit = sandbox("EMPTY") {
        val x = 240
        val gy = surface(x)
        pick("Bauen", "Holz", brush = 2)
        stroke(listOf(x to gy - 2, x to gy - 18), restMillis = 200)
        steps(60)
        val upright = bodyNear(x, gy - 10)
        val before = bodies().firstOrNull { it.id == upright }
        println("4C upright before the push: ${before?.angle}")
        stroke(listOf(x - 15 to gy - 2, x - 6 to gy - 16), restMillis = 0)
        film("4c_1b_stab_senkrecht_stoss", "Senkrechter Stab, ein zweiter fällt dagegen", x - 26, gy - 26, x + 26, gy + 4) {
            val b = bodies().firstOrNull { it.id == upright }
            if (b == null) "kein Körper" else "Winkel des senkrechten Stabs %.0f°, %s".format(b.angle, if (b.asleep) "liegt still" else "bewegt sich")
        }
        assertTrue("the upright stick stood: ${before?.angle}°", before != null && abs(before.angle) < 3f)
        assertTrue("a body was made", upright != 0)
        val b = bodies().first { it.id == upright }
        assertTrue("the pushed stick fell over: ${b.angle}°", abs(b.angle) > 60f)
    }

    /** A wedge of stone drawn on the field in one stroke, a log drawn level over its slope. */
    @Test
    fun logOnSlopeFalls(): Unit = sandbox("EMPTY") {
        val x = 240
        val gy = surface(x)
        val x0 = x - 30
        pick("Bauen", "Stein", brush = 3)
        // along the ground, up the high end, down its slope, and through the middle
        stroke(listOf(x0 + 3 to gy - 4, x - 3 to gy - 4, x - 3 to gy - 18, x0 + 3 to gy - 4, x - 3 to gy - 11, x0 + 14 to gy - 9), restMillis = 0)
        steps(60)
        val lx = x - 14
        val ly = (lx - 7..lx + 7).minOf { surface(it) } - 2
        pick("Bauen", "Holz", brush = 2)
        stroke(listOf(lx - 7 to ly, lx + 7 to ly), restMillis = 200)
        val log = bodyNear(lx, ly)
        film("4c_1c_stamm_schraege", "Stamm über einer gemalten Steinrampe", lx - 22, ly - 16, lx + 24, gy + 4) {
            val b = bodies().firstOrNull { it.id == log }
            if (b == null) "kein Körper" else "Winkel %.0f°, %s".format(b.angle, if (b.asleep) "liegt still" else "bewegt sich")
        }
        assertTrue("a body was made", log != 0)
        val b = bodies().first { it.id == log }
        assertTrue("the log tipped onto the slope: ${b.angle}°", abs(b.angle) > 15f)
    }

    /** A lone tree on the flat top of the caves; one ember against its foot burns the trunk through. */
    @Test
    fun burntTreeFalls(): Unit = sandbox("CAVES") {
        val x = TREE_X
        val foot = TREE_FOOT
        assertTrue("a tree stands there", mt.spec(world.material(x, foot)).id == "trunk")
        println("4C around the foot of the tree: " + ((foot - 2)..(foot + 1)).joinToString(" / ") { y -> ((x - 3)..(x + 3)).joinToString(",") { mt.spec(world.material(it, y)).id.take(4) } })
        pick("Hitze", "Glut", brush = 1)
        compose.onNodeWithTag("world").performTouchInput {
            down(cell(x - 1, foot))
            up()
        }
        // until the trunk is burnt through and the tree comes loose
        var waited = 0
        while (plantBodyNear(x, foot) == 0 && waited < 60 * 40) {
            steps(10)
            waited += 10
        }
        val tree = plantBodyNear(x, foot)
        println("4C the tree came loose after ${waited / 60f} s: " + bodies().filter { mt.plant[it.material] }.joinToString { "#${it.id} ${mt.spec(it.material).id} ${it.cells} at %.0f,%.0f".format(it.x, it.y) })
        assertTrue("the trunk burnt through within ${waited / 60} s", tree != 0)
        // where the tree is: the middle of its trunk and leaves, and its top (a body made anew after it fell has a new
        // frame and reads 0°, so its turn tells nothing; where its cells are does)
        val upright = treeAt(x, foot)
        var last = upright
        film("4c_1d_baum_durchgebrannt", "Baum, Stamm am Fuß durchgebrannt (0 s: der Stamm ist durch)", x - 26, foot - 26, x + 26, foot + 6) {
            last = treeAt(x, foot)
            "Krone bei %.0f,%.0f, Spitze Reihe %d (stehend %.0f,%.0f, %d)".format(last[0], last[1], last[2].toInt(), upright[0], upright[1], upright[2].toInt())
        }
        assertTrue(
            "the tree lies: its middle from %.0f,%.0f to %.0f,%.0f, its top from row %.0f to %.0f".format(upright[0], upright[1], last[0], last[1], upright[2], last[2]),
            abs(last[0] - upright[0]) > 4f && last[2] - upright[2] >= 4f,
        )
    }

    /** The middle (x, y) of the trunk and leaf cells around a tree with its foot at ([x], [foot]), and its highest row. */
    private fun treeAt(x: Int, foot: Int): FloatArray {
        val trunk = mt.id("trunk")
        val leaves = mt.id("leaves")
        return onSim { w ->
            var n = 0
            var sx = 0f
            var sy = 0f
            var top = Int.MAX_VALUE
            for (y in maxOf(0, foot - 30)..minOf(w.height - 1, foot + 4)) for (xx in maxOf(0, x - 25)..minOf(w.width - 1, x + 25)) {
                val m = w.material(xx, y)
                if (m != trunk && m != leaves) continue
                n++
                sx += xx
                sy += y
                if (y < top) top = y
            }
            if (n == 0) floatArrayOf(0f, 0f, 0f) else floatArrayOf(sx / n, sy / n, top.toFloat())
        }
    }

    // ------------------------------------------------------------------ 2. rain and snow

    /**
     * A cloud painted into the sky of the meadow rains. Over the sand on its right: a cloud this deep is a thundercloud
     * (3c), and lightning out of it would set the trees and the wood further left on fire and hide the rain in smoke.
     * Landscape, so that the whole width of the meadow is on screen.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun paintedCloudRains(): Unit = sandbox("MEADOW", quiet = true) {
        val base = world.cloudBase
        val ground = (SKY_X0..SKY_X1).maxOf { surface(it) }
        val fell = Fell("water")
        paintCloud(base)
        var seen = 0
        film(
            "4c_2a_wolke_regen", "Gemalte Wolke, 20 °C: Regen", SKY_X0 - 15, base - 16, SKY_X1 + 15, ground + 3,
            seconds = listOf(0, 1, 2, 3, 10), zoom = 3,
        ) {
            val air = falling("water", base, SKY_X0 - 15, SKY_X1 + 15, ground)
            seen = maxOf(seen, air)
            fell.note() + ", %d Tropfen in der Luft".format(air)
        }
        assertTrue("it rained: ${fell.total} drops in 10 s, at most $seen in the air at once", fell.total >= 40 && seen >= 3)
        cloudDebugShot("4c_2a_wolkenansicht", base)
    }

    /** Frost from the weather controls: the painted cloud snows, flakes drifting down (the same place as the rain). */
    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun coldCloudSnows(): Unit = sandbox("MEADOW", quiet = true) {
        println("4C weather before: clouds from row ${world.cloudBase}, ${world.ambient} °C; the controls start from ${vm.world.value!!.stats.weather}")
        openWeather()
        compose.onNodeWithTag("weather_temp").performSemanticsAction(SemanticsActions.SetProgress) { it(-10f) }
        compose.waitForIdle()
        // the weather controls as they are set, a picture of its own (the dialog is a window of its own)
        val panel = (compose.onNodeWithTag("weather_panel").fetchSemanticsNode().root as ViewRootForTest).view.rootView
        val controls = Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888)
        panel.draw(Canvas(controls))
        File(dir(), "4c_2b_wetterregler.png").outputStream().use { controls.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // (in landscape the dialog is taller than the screen: its button is pressed by what it does, not by where it is)
        compose.onNodeWithTag("weather_apply").performSemanticsAction(SemanticsActions.OnClick)
        steps(120)
        println("4C weather after: clouds from row ${world.cloudBase}, ${world.ambient} °C, the controls ${if (compose.onAllNodesWithTag("weather_panel").fetchSemanticsNodes().isEmpty()) "closed" else "still open"}")
        assertTrue("the weather controls kept the clouds and set the frost: row ${world.cloudBase}, ${world.ambient} °C", world.cloudBase >= 0 && world.ambient < 0f)
        val base = world.cloudBase
        val ground = (SKY_X0..SKY_X1).maxOf { surface(it) }
        val fell = Fell("snow")
        paintCloud(base)
        var seen = 0
        var rain = 0
        film(
            "4c_2b_wolke_schnee", "Gemalte Wolke bei −10 °C: Schnee", SKY_X0 - 15, base - 16, SKY_X1 + 15, ground + 3,
            seconds = listOf(0, 1, 2, 3, 10), zoom = 3,
        ) {
            val air = falling("snow", base, SKY_X0 - 15, SKY_X1 + 15, ground)
            val drops = falling("water", base, SKY_X0 - 15, SKY_X1 + 15, ground)
            seen = maxOf(seen, air)
            rain = maxOf(rain, drops)
            fell.note() + ", %d Flocken in der Luft, %d Tropfen".format(air, drops)
        }
        assertTrue("it snowed: ${fell.total} flakes in 10 s, at most $seen in the air at once", fell.total >= 40 && seen >= 10)
        assertTrue("no rain in frost: $rain drops", rain == 0)
        cloudDebugShot("4c_2b_wolkenansicht", base)
    }

    /**
     * Lava poured into the sea of the coast: the sea boils, the steam rises into clouds, and they rain. The first picture
     * is from before the lava: no cloud in the sky.
     */
    @Test
    fun seaBoiledByLavaRains(): Unit = sandbox("COAST", quiet = true) {
        val base = world.cloudBase
        var shore = 0
        while (shore < world.width && (0 until world.height).none { world.material(shore, it) == mt.id("saltwater") }) shore++
        var sea = 0
        while (world.material(shore + 8, sea) != mt.id("saltwater")) sea++
        val area = intArrayOf(shore - 40, base - 18, shore + 60, sea + 6)
        val before = crop(screen(), area[0], area[1], area[2], area[3]) to "Dampf %d, Wolke %d".format(world.count("steam"), world.count("cloud"))
        val fell = Fell("water")
        // lava poured along the sea and back, a finger drawn over it
        pick("Hitze", "Lava", brush = 4)
        stroke(listOf(shore + 6 to sea - 8, shore + 50 to sea - 8, shore + 6 to sea - 8), restMillis = 0)
        film(
            "4c_2c_meer_lava_regen", "Lava ins Meer: Dampf, Wolken, Regen", area[0], area[1], area[2], area[3],
            seconds = listOf(0, 1, 2, 3, 10, 20), before = before,
        ) {
            "Dampf %d, Wolke %d, ".format(world.count("steam"), world.count("cloud")) + fell.note()
        }
        assertTrue("the steam made clouds that rained: ${fell.total} drops in 20 s", fell.total >= 30)
    }

    /**
     * Rain ([what] water) or snow ([what] snow) fallen out of the clouds since this was made, as the world counts it
     * (the discoveries are off: they would clear the counts), and per second of the world's time since the last note.
     */
    private inner class Fell(what: String) {
        private val kind = if (what == "snow") Seen.SNOWFALL else Seen.RAIN
        private val start = count()
        private var lastCount = start
        private var lastStep = world.step
        var total = 0
            private set

        private fun count(): Int = onSim { it.seen[kind * mt.count + mt.id("cloud")] }

        fun note(): String {
            val now = count()
            val step = world.step
            total = now - start
            val rate = if (step > lastStep) (now - lastCount) * 60f / (step - lastStep) else 0f
            lastCount = now
            lastStep = step
            val what = if (kind == Seen.SNOWFALL) "Flocken" else "Tropfen"
            return "%d %s gefallen (%.0f je s)".format(total, what, rate)
        }
    }

    /**
     * Cells of [what] falling through open air under the clouds (air above and below them), in columns [x0]..[x1] down
     * to row [y1] (by default everywhere).
     */
    private fun falling(what: String, base: Int, x0: Int = 0, x1: Int = world.width - 1, y1: Int = world.height - 2): Int {
        val m = mt.id(what)
        return onSim { w ->
            var n = 0
            for (y in base + 1..minOf(y1, w.height - 2)) for (x in maxOf(x0, 0)..minOf(x1, w.width - 1)) {
                val i = y * w.width + x
                if (w.mat[i].toInt() == m && w.mat[i - w.width].toInt() == MaterialTable.AIR && w.mat[i + w.width].toInt() == MaterialTable.AIR) n++
            }
            n
        }
    }

    // ------------------------------------------------------------------ playing

    /**
     * The sandbox with a new world from [template] (medium size). The settings an earlier test left behind (in build
     * 111 the caves of the tree test and the performance figures over the world) are set back to the
     * defaults first. [quiet]: no discoveries, their banners would lie over the sky.
     */
    private fun sandbox(template: String, quiet: Boolean = false, play: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            scenario.onActivity {
                vm = ViewModelProvider(it)[AppViewModel::class.java]
                vm.updateSettings(Settings(template = template, worldSize = "MEDIUM"))
            }
            compose.onNodeWithText("Spielplatz").performClick()
            run(500)
            if (quiet) vm.world.value!!.watch = null
            println("4C world $template ${world.width} × ${world.height}, clouds from row ${world.cloudBase}")
            steps(60)
            play()
        }
    }

    /** A material from the bar, and the brush size (1 to 5: a single cell, radius 1, 3, 6, 10). */
    private fun pick(category: String, material: String, brush: Int) {
        compose.onNodeWithTag("category_$category").performScrollTo().performClick()
        compose.onNodeWithTag("material_$material").performScrollTo().performClick()
        compose.onNodeWithTag("brush_shape").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pinsel $brush").performClick()
        compose.waitForIdle()
    }

    /**
     * A finger drawn through the cells [points], resting [restMillis] at the end. The first [FAST] cells go quickly, as a
     * finger starts a stroke: slower, the app would take the still finger for a long press that asks what is there (3b.1).
     * Then a cell every couple of steps.
     */
    private fun stroke(points: List<Pair<Int, Int>>, restMillis: Long) {
        val path = ArrayList<Pair<Int, Int>>()
        path += points.first()
        for ((a, b) in points.zipWithNext()) {
            val n = maxOf(abs(b.first - a.first), abs(b.second - a.second))
            for (k in 1..n) path += (a.first + (b.first - a.first) * k / n) to (a.second + (b.second - a.second) * k / n)
        }
        compose.onNodeWithTag("world").performTouchInput {
            down(cell(path[0].first, path[0].second))
            for (k in 1 until minOf(FAST, path.size)) moveTo(cell(path[k].first, path[k].second))
        }
        run(40)
        for (k in FAST until path.size) {
            compose.onNodeWithTag("world").performTouchInput { moveTo(cell(path[k].first, path[k].second)) }
            run(35)
        }
        if (restMillis > 0) run(restMillis)
        compose.onNodeWithTag("world").performTouchInput { up() }
    }

    private fun paintCloud(base: Int) {
        pick("Flüssig", "Wolke", brush = 4)
        stroke(listOf(SKY_X0 to base - 4, SKY_X1 to base - 4), restMillis = 0)
    }

    /** The screen position of world cell (x, y): the world fills the height of its view, centred. */
    private fun androidx.compose.ui.test.TouchInjectionScope.cell(x: Int, y: Int): Offset {
        val s = height / world.height.toFloat()
        val ox = (width - world.width * s) / 2f
        return Offset(ox + (x + 0.5f) * s, (y + 0.5f) * s)
    }

    /** Lets the world run [n] steps of its own (the simulation thread runs on the wall clock; the screen follows). */
    private fun steps(n: Int) {
        val target = world.step + n
        var waited = 0
        while (world.step < target && waited < n * 200 + 10_000) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            compose.mainClock.advanceTimeBy(10)
            waited += 10
        }
        // the frame of that moment onto the screen
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

    private fun surface(x: Int): Int {
        for (y in 0 until world.height) {
            val m = world.material(x, y)
            if (m != MaterialTable.AIR && mt.kind[m] != Kind.GAS && mt.kind[m] != Kind.LIQUID && !mt.plant[m]) return y
        }
        return world.height
    }

    /** The biggest body (of wood) around (x, y), waiting a little for it to be found; 0 if there is none. */
    private fun bodyNear(x: Int, y: Int): Int {
        val wood = mt.id("wood")
        repeat(20) {
            val b = bodies().filter { it.material == wood && abs(it.x - x) < 12 && abs(it.y - y) < 14 && it.cells > 5 }.maxByOrNull { it.cells }
            if (b != null) return b.id
            steps(2)
        }
        println("4C no body near $x, $y: " + bodies().joinToString { "#${it.id} ${it.cells} at %.0f,%.0f".format(it.x, it.y) })
        return 0
    }

    /** The bodies as the simulation thread sees them between two steps. */
    private fun bodies(): List<World.BodyState> = onSim { it.bodyStates() }

    /** Reads the world on the simulation thread, between two steps. */
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

    private fun plantBodyNear(x: Int, y: Int): Int =
        bodies().filter { mt.plant[it.material] && it.cells > 50 && abs(it.x - x) < 15 && abs(it.y - y) < 30 }.maxByOrNull { it.cells }?.id ?: 0

    private fun World.count(id: String): Int {
        val m = mt.id(id)
        return onSim { w ->
            var n = 0
            for (i in 0 until w.size) if (w.mat[i].toInt() == m) n++
            n
        }
    }

    // ------------------------------------------------------------------ pictures

    /**
     * Pictures of the screen at [seconds] of world time from now, cut to cells ([x0], [y0])–([x1], [y1]) and
     * enlarged [zoom] times, side by side in one picture [name] with the time and [state] under each; each also on its
     * own. [before]: a picture taken before, and what was there, put first ("vorher"). For each picture the world is
     * paused: the picture and what is written under it are of the same step.
     */
    private fun film(
        name: String, title: String, x0: Int, y0: Int, x1: Int, y1: Int,
        seconds: List<Int> = listOf(0, 1, 2, 3), zoom: Int = 2, before: Pair<Bitmap, String>? = null, state: () -> String,
    ) {
        val start = world.step
        val panels = ArrayList<Bitmap>()
        val labels = ArrayList<String>()
        val notes = ArrayList<String>()
        if (before != null) {
            panels += before.first
            labels += "vorher"
            notes += "vorher: ${before.second}"
        }
        val runner = vm.world.value!!
        for (t in seconds) {
            val target = start + t * 60
            if (world.step < target) steps(target - world.step)
            runner.speed = SimRunner.Speed.PAUSE
            onSim { }
            run(200)
            val note = state()
            val at = world.step - start
            val crop = crop(screen(), x0, y0, x1, y1, zoom)
            runner.speed = SimRunner.Speed.NORMAL
            notes += "${t} s (Schritt $at): $note"
            labels += "$t s"
            File(dir(), "${name}_${t}s.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            panels += crop
        }
        val pw = panels.maxOf { it.width }
        val ph = panels.maxOf { it.height }
        val label = 44
        val strip = Bitmap.createBitmap(pw * panels.size + GAP * (panels.size - 1), ph + label * 3, Bitmap.Config.ARGB_8888)
        val c = Canvas(strip)
        c.drawColor(Color.rgb(16, 20, 26))
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }
        val small = Paint(paint).apply { textSize = 20f }
        c.drawText(title, 12f, 32f, paint)
        for ((k, p) in panels.withIndex()) {
            c.drawBitmap(p, null, Rect(k * (pw + GAP), label, k * (pw + GAP) + pw, label + ph), null)
            c.drawText(labels[k], k * (pw + GAP) + 12f, label + ph + 32f, paint)
            c.drawText(notes[k].substringAfter(": "), k * (pw + GAP) + 12f, label + ph + 68f, small)
        }
        File(dir(), "$name.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val text = "$name – $title\n" + notes.joinToString("\n") { "  $it" } + "\n"
        File(dir(), "4c_nachweis.txt").appendText(text)
        println("4C $text")
    }

    /** The developer view of the clouds switched on, one picture, and off again. */
    private fun cloudDebugShot(name: String, base: Int) {
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Wolkenansicht an").performClick()
        steps(30)
        compose.onNodeWithTag("cloud_debug").assertExists()
        val screen = screen()
        File(dir(), "$name.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir(), "${name}_ausschnitt.png").outputStream().use {
            crop(screen, SKY_X0 - 25, base - 18, SKY_X1 + 25, base + 30, zoom = 3).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.onNodeWithText("Wolkenansicht aus").performClick()
        compose.waitForIdle()
    }

    private fun screen(): Bitmap {
        compose.waitForIdle()
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view.rootView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** The part of [screen] that shows world cells ([x0], [y0])–([x1], [y1]), [zoom] times as large. */
    private fun crop(screen: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, zoom: Int = 2): Bitmap {
        val node = compose.onNodeWithTag("world").fetchSemanticsNode()
        val r = node.boundsInWindow
        val s = r.height / world.height
        val ox = r.left + (r.width - world.width * s) / 2f
        val oy = r.top
        val left = (ox + x0 * s).toInt().coerceIn(r.left.toInt(), r.right.toInt() - 1)
        val top = (oy + y0 * s).toInt().coerceIn(r.top.toInt(), r.bottom.toInt() - 1)
        val right = (ox + (x1 + 1) * s).toInt().coerceIn(left + 1, r.right.toInt())
        val bottom = (oy + (y1 + 1) * s).toInt().coerceIn(top + 1, r.bottom.toInt())
        val out = Bitmap.createBitmap((right - left) * zoom, (bottom - top) * zoom, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(screen, Rect(left, top, right, bottom), Rect(0, 0, out.width, out.height), null)
        return out
    }

    private fun dir() = File("build/screenshots").apply { mkdirs() }

    private fun openWeather() {
        compose.onNodeWithContentDescription("Menü").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wetter …").performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val WIPE = 60f

        /** Cells a stroke starts with in one quick move. */
        const val FAST = 12
        const val GAP = 8

        /** Where the clouds are painted over the meadow (medium world): over the sand on its right. */
        const val SKY_X0 = 330
        const val SKY_X1 = 380

        /** The lone tree on the flat top of the caves (Template.CAVES, medium world): its trunk and the cell at its foot. */
        const val TREE_X = 291
        const val TREE_FOOT = 31
    }
}
