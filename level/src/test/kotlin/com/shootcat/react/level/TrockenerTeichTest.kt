package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 1, "Trockener Teich" (Leitfaden V3, 5.4): the pond down on the right is dry. Up on the left a pool
 * fed by springs stands behind a rock wall whose window is stopped with earth; from the pool a channel runs
 * to a shaft, stopped with earth on a log; under the log embers glow on ash. On the right bank springs run
 * into a hollow that drains through gravel into a cave. Each way to fill the pond is played here as a list
 * of actions, with a picture of how it ended.
 */
class TrockenerTeichTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("trockener_teich"), mt)

    private fun count(play: Play, name: String, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val m = mt.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (play.session.world.material(x, y) == m) n++
        return n
    }

    /** Plays [script], lets the world run on up to [limit] seconds, and keeps a picture of the end. */
    private fun solve(name: String, script: String, limit: Int = 300): Pair<Play, Result?> {
        val play = play()
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level1_$name", scale = 3)
        println("$name: ${play.state}, ${result ?: "${play.session.world.step / 60f} s, progress ${play.progress.joinToString()}"}")
        return play to result
    }

    @Test
    fun `the level is as painted, the pond is dry, the pool full`() {
        val play = play()
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level1_start", scale = 3)
        assertEquals(0, count(play, "water", 150, 98, 196, 111), "the pond")
        assertTrue(count(play, "water", 16, 36, 45, 62) > 600, "the pool")
        assertEquals(18, count(play, "trunk", 74, 65, 82, 66), "the log")
        assertEquals(4, count(play, "ember", 74, 67, 82, 71), "the embers")
        assertEquals(Play.State.PLAYING, play.state)
        assertEquals(1, play.goals.size)
    }

    @Test
    fun `fire - the branch onto the embers burns the log away, the shaft opens, gold`() {
        val (_, r) = solve("feuer", "wait 60\nhand 91 72\nhand 77 68")
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(2, result.actions)
    }

    @Test
    fun `digging - one bucket out of the earth in the window, the pool runs out over the ledge, gold`() {
        val (_, r) = solve("graben", "wait 60\nscoop 48 45 3 20")
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(1, result.actions)
    }

    @Test
    fun `washing - three buckets of water poured onto the earth in the window wash it out, silver`() {
        val (_, r) = solve("anspuelen", "wait 60\n" + "scoop 30 58 6 20\npour 52 40 1 30\n".repeat(3))
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(6, result.actions)
    }

    @Test
    fun `channel - earth from the hill fills the hollow on the bank, its springs run over into the pond, silver`() {
        val (_, r) = solve("rinne", "wait 60\nscoop 7 16 3 20\npour 202 86 1 30")
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(2, result.actions)
    }

    @Test
    fun `carrying - the bucket from the pool to the pond, sixteen times, bronze`() {
        val (_, r) = solve("tragen", "wait 30\n" + "scoop 30 58 6 20\npour 172 95 3 30\n".repeat(16))
        val result = assertNotNull(r)
        assertEquals(Medal.BRONZE, result.medal)
        assertTrue(result.actions > 20, "${result.actions} actions")
    }

    @Test
    fun `the log is wedged between the rocks, the hand cannot lift it`() {
        val play = play()
        play.perform(Action.parse("wait 60\nhand 78 65"), limitSeconds = 0)
        assertNull(play.session.held)
        assertTrue("verankert" in play.session.notice, play.session.notice)
    }

    @Test
    fun `water on the embers puts them out, then the branch does not catch`() {
        val (play, r) = solve("glut_geloescht", "wait 60\nscoop 30 58 6 20\npour 77 69 1 30\nwait 120\nhand 91 72\nhand 77 68", limit = 60)
        assertNull(r)
        assertEquals(0, count(play, "ember", 74, 67, 82, 71), "no embers left")
        assertTrue(count(play, "charcoal", 74, 67, 82, 71) > 0, "only charcoal")
        assertEquals(18, count(play, "trunk", 74, 65, 82, 66), "the log is whole")
    }

    @Test
    fun `waiting alone does not fill the pond in time for a medal`() {
        val (play, r) = solve("warten", "", limit = 300)
        assertNull(r)
        assertEquals(Play.State.PLAYING, play.state)
    }
}
