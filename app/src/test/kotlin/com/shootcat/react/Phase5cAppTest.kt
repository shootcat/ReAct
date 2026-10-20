package com.shootcat.react

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shootcat.react.level.Action
import com.shootcat.react.level.Play
import com.shootcat.react.level.perform
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.SimRunner
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
 * Phase 5c in the running app: the two levels with inhabitants, opened from the list of levels. "Bergleute": four
 * miners shut in behind a fall of rubble with firedamp, a fire of pitch in the mouth of the adit. "Winternacht": a
 * village in hard frost whose fire will not last the night. Each played to its end in the app with a solution from
 * the level tests (BergleuteTest, WinternachtTest), the actions given to the level's own play on the simulation's
 * thread, and pictured: "5c_*.png".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
class Phase5cAppTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var vm: AppViewModel

    @Before
    fun freshSettings() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("react_settings", "react_progress_v3")) app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The level [id] opened from the list, its world shown and running. */
    private fun open(scenario: ActivityScenario<MainActivity>, id: String) {
        compose.waitForIdle()
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.onNodeWithTag("levels").performClick()
        run(500)
        compose.onNodeWithTag("level_$id").performScrollTo().performClick()
        run(2500)
        compose.onNodeWithTag("level_hud").assertExists()
        assertEquals(id, vm.level.value!!.play.level.spec.id)
    }

    /** The level's play, as the simulation's thread sees it, after [actions] (played there, [seconds] at most). */
    private fun play(actions: String, seconds: Int): Play.State {
        val level = vm.level.value!!
        level.runner.speed = SimRunner.Speed.PAUSE
        val state = onSim(level.runner) {
            level.play.perform(Action.parse(actions), limitSeconds = seconds)
            level.play.state
        }
        level.runner.speed = SimRunner.Speed.NORMAL
        run(1500)
        return state
    }

    private fun medal(): String =
        compose.onNodeWithTag("medal").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""

    @Test
    fun bergleute(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        open(scenario, "bergleute")
        assertEquals("four miners", 4, vm.level.value!!.play.session.world.folk.count)
        shot("5c_bergleute_start")
        // the gold of the level: the front of the fall tipped into the burning pit, then the rest dug out
        val state = play("scoop 64 63 3 20\npour 59 64 1 30\nscoop 67 63 3 20\npour 30 20 3 20\nscoop 71 63 3 20\npour 30 20 3 20\n", 150)
        assertEquals(Play.State.WON, state)
        compose.onNodeWithTag("level_end").assertExists()
        val medal = medal()
        File("build/screenshots/5c_bergleute_medaille.txt").writeText(medal)
        shot("5c_bergleute_gerettet")
        // gold when the actions follow at once (BergleuteTest); here the level ran a moment first
        assertTrue("a medal: $medal", medal == "Gold" || medal == "Silber")
    }

    @Test
    fun winternacht(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        open(scenario, "winternacht")
        val world = vm.level.value!!.play.session.world
        assertEquals("four villagers", 4, world.folk.count)
        shot("5c_winternacht_start")
        // the two logs from behind the first house laid on the hearth beside the fire; half a minute into the night
        assertEquals(Play.State.PLAYING, play("hand 3 77\nhand 86 75\n", 30))
        shot("5c_winternacht_nacht")
        // on to the morning
        val state = play("", 210)
        assertEquals(Play.State.WON, state)
        compose.onNodeWithTag("level_end").assertExists()
        val medal = medal()
        File("build/screenshots/5c_winternacht_medaille.txt").writeText(medal)
        shot("5c_winternacht_morgen")
        assertEquals("Gold", medal)
    }

    @Test
    fun winternachtLost(): Unit = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        open(scenario, "winternacht")
        // snow from a roof on the fire: it goes out, and soon a villager lies frozen
        val state = play("scoop 20 66 3 20\npour 79 72 2 20\n", 150)
        assertEquals(Play.State.LOST, state)
        val frozen = onSim(vm.level.value!!.runner) { s -> (0 until s.world.folk.count).count { s.world.folk.frozen(it) } }
        assertTrue("a villager lies frozen", frozen >= 1)
        shot("5c_winternacht_erfroren")
        assertTrue("one froze of cold", onSim(vm.level.value!!.runner) { s -> s.world.folk.figures().any { it.cause == Folk.Cause.COLD } })
    }

    // ------------------------------------------------------------------ helpers

    private fun <T> onSim(runner: SimRunner, read: (com.shootcat.react.sim.Session) -> T): T {
        val latch = CountDownLatch(1)
        val out = AtomicReference<T>()
        runner.post {
            out.set(read(it))
            latch.countDown()
        }
        var waited = 0
        while (!latch.await(10, TimeUnit.MILLISECONDS)) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            waited += 10
            if (waited > 120_000) throw AssertionError("the simulation thread did not answer")
        }
        return out.get()
    }

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

    /** Draws the screen into a PNG; a dialog (the end of the level, a window of its own) on top of it, the screen dimmed, as on the device. */
    private fun shot(name: String) {
        compose.waitForIdle()
        val windows = compose.onAllNodes(isRoot()).fetchSemanticsNodes().map { (it.root as ViewRootForTest).view.rootView }
        val screen = windows.maxBy { it.width * it.height }
        val bitmap = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screen.draw(canvas)
        for (dialog in windows) {
            if (dialog === screen) continue
            canvas.drawColor(0x99000000.toInt())
            canvas.save()
            canvas.translate((screen.width - dialog.width) / 2f, (screen.height - dialog.height) / 2f)
            dialog.draw(canvas)
            canvas.restore()
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
