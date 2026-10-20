package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.Flag
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 7, "Winternacht" (Phase 5c): a village on a ledge high in the mountains, a night of hard frost (-15 °C). Four
 * villagers, two houses with snow on their roofs, and in the square a low stone hearth with a fire pit: a row of wood
 * burning on a bed of charcoal, which glows on from the top down for about a minute, not the night. Wood is scarce: two
 * logs behind the first house. Up the slope a seep of cold pitch and the charcoal burner's heap. Bucket and hand.
 *
 * Nobody may freeze before morning (keep_warm without a temperature: lost as soon as a villager faints of cold). The
 * villagers warm themselves at the fire on their own, and freezing they light firewood lying near a flame (5b), never
 * from nothing. The heat of a fire goes into the stones of the hearth and the rock of the ledge and keeps the square
 * warm for a while after it is out.
 *
 * The night is 180 s. Measured over when the logs come (0 to 40 s after the start) and where they are put down, the
 * ways below win in most nights, not in all: the villagers walk where their rules take them, and how warm the ground
 * stays after the fire depends on it (see docs/PHASE5C.md).
 */
class WinternachtTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("winternacht"), mt)

    /** What happened in a night: the result, when it was lost (or 0), the most cold any villager had. */
    private class Night(val play: Play, val result: Result?, val lostAt: Float, val maxCold: Int)

    /** Plays [script] through the night; keeps a picture of the end. */
    private fun night(name: String, script: String): Night {
        val play = play()
        val w = play.session.world
        val backdrop = Backdrop.of(w)
        var maxCold = 0
        val check = play.session.onStep
        play.session.onStep = { s ->
            check?.invoke(s)
            if (w.step % 30 == 0) for (k in 0 until w.folk.count) maxCold = maxOf(maxCold, w.folk.figures()[k].cold)
        }
        val result = play.perform(Action.parse(script), limitSeconds = 210)
        Pictures.write(w, backdrop, "level7_$name", scale = 3)
        val lostAt = if (play.state == Play.State.LOST) w.step / 60f else 0f
        println("$name: ${play.state}, ${result ?: "lost at $lostAt s"}, most cold $maxCold of ${Folk.COLD_MAX}, " +
            "kindled ${w.folk.did[Folk.Did.KINDLE]}")
        return Night(play, result, lostAt, maxCold)
    }

    private fun burning(play: Play): Int {
        val w = play.session.world
        var n = 0
        for (y in 66..77) for (x in 70..88) if (w.flags[y * w.width + x].toInt() and Flag.BURNING != 0) n++
        return n
    }

    /** The two logs from behind the first house, laid on the hearth beside the fire, a cell apart from it. */
    private val logs = "hand 3 77\nhand 86 75\n"

    @Test
    fun `the level is as painted, and without help the village freezes before morning`() {
        val play = play()
        val w = play.session.world
        Pictures.write(w, Backdrop.of(w), "level7_start", scale = 3)
        assertEquals(4, w.folk.count, "four villagers")
        assertEquals(-15f, w.ambient)
        repeat(60) { play.session.tick() }
        assertTrue(burning(play) > 0, "the fire on the hearth burns")
        val run = night("ohne", "")
        assertNull(run.result)
        assertEquals(Play.State.LOST, run.play.state)
        assertTrue(run.lostAt in 60f..170f, "the fire and the warm stones last a while, not the night: ${run.lostAt} s")
        assertTrue(run.play.goals[0].failed(run.play.session.world), "a villager lies frozen")
    }

    @Test
    fun `the logs laid on the hearth beside the fire catch from it, the village stays warm, gold`() {
        val run = night("holz", logs)
        assertNotNull(run.result)
        assertEquals(Medal.GOLD, run.result!!.medal)
        assertTrue(run.maxCold < Folk.COLD_MAX, "nobody froze: ${run.maxCold}")
    }

    @Test
    fun `the logs put down on the ground beside the hearth, freezing villagers light them themselves`() {
        val run = night("holz_boden", "hand 3 77\nhand 64 77\n")
        assertNotNull(run.result)
        assertTrue(run.play.session.world.folk.did[Folk.Did.KINDLE] >= 1, "a villager kindled the logs at the flame")
    }

    @Test
    fun `the logs beside the fire, and later charcoal from the burner's heap on the hearth, silver`() {
        val run = night("holz_kohle", logs + "wait 600\nscoop 176 57 3 20\npour 72 74 1 30\n")
        assertNotNull(run.result)
        assertEquals(Medal.SILVER, run.result!!.medal)
    }

    @Test
    fun `snow from a roof in the fire pit does not help, the village freezes before morning`() {
        // (the snow wets the burning wood on top; the charcoal under it glows on, the night is lost as without help)
        val run = night("schnee", "scoop 20 66 3 20\npour 79 72 2 20\n")
        assertEquals(Play.State.LOST, run.play.state)
        assertTrue(run.lostAt < 170f, "${run.lostAt} s")
    }

    @Test
    fun `a bucket of cold charcoal in the fire pit smothers it, the village freezes sooner than without help`() {
        val run = night("kohle", "scoop 176 57 3 20\npour 79 72 1 30\n")
        assertEquals(Play.State.LOST, run.play.state)
        assertTrue(run.lostAt < 100f, "${run.lostAt} s")
    }

    @Test
    fun `the logs dropped on top of the fire pit smother it`() {
        val run = night("holz_drauf", "hand 3 77\nhand 80 75\n")
        assertEquals(Play.State.LOST, run.play.state)
    }

    @Test
    fun `pitch on the fire burns long, but its black smoke drives the villagers from it`() {
        val run = night("teer", "scoop 167 65 3 20\npour 79 73 1 30\n")
        assertEquals(Play.State.LOST, run.play.state)
    }

    @Test
    fun `the logs brought when the fire pit is out, nobody lights them`() {
        val run = night("holz_zu_spaet", "wait 3600\n$logs")
        assertEquals(Play.State.LOST, run.play.state)
        val w = run.play.session.world
        val wood = mt.id("wood")
        var logsLeft = 0
        for (i in 0 until w.size) if (w.mat[i].toInt() == wood && w.flags[i].toInt() and Flag.ANCHORED == 0) logsLeft++
        assertTrue(logsLeft >= 8, "no flame left to light them at, they lie there: $logsLeft cells of wood")
    }

    @Test
    fun `the same actions give the same night`() {
        val a = play().also { it.perform(Action.parse(logs), limitSeconds = 30) }
        val b = play().also { it.perform(Action.parse(logs), limitSeconds = 30) }
        assertEquals(a.session.world.hash(), b.session.world.hash())
    }
}
