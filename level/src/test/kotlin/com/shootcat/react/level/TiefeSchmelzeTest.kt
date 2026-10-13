package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 3, "Tiefe Schmelze" (Leitfaden V3, 5.4): a mine in tuff. In the gallery a heap of salt, a little sand,
 * a hollow with a seep spring, rubble, ore rubble and a glowing vent (lava over magma). In its floor the shaft
 * to the deep chamber, stopped with ice. In the chamber a sump under the shaft and a bed of sand, beside it a
 * pocket of lava over magma behind a tunnel stopped with earth. The tools only reach what is open to the sky:
 * until the ice is gone, the deep chamber is out of reach.
 */
class TiefeSchmelzeTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("tiefe_schmelze"), mt)

    private fun count(play: Play, name: String, x0: Int = 0, y0: Int = 0, x1: Int = 189, y1: Int = 159): Int {
        val m = mt.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (play.session.world.material(x, y) == m) n++
        return n
    }

    /** Two loads of salt onto the ice in the shaft: it melts, the water runs down into the sump. */
    private val salt = "scoop 50 43 6 20\npour 84 42 3 20\nscoop 60 43 6 20\npour 84 40 3 20\nwait 300\n"

    /** The earth out of the tunnel to the lava pocket, three loads tipped into the corner of the chamber. */
    private val dig = "scoop 107 132 6 20\npour 60 124 6 30\nscoop 111 131 6 20\npour 60 124 6 30\nscoop 115 131 6 20\npour 60 124 6 30\nwait 120\n"

    private fun solve(name: String, script: String, limit: Int = 480): Pair<Play, Result?> {
        val play = play()
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level3_$name", scale = 3)
        println("$name: ${play.state}, ${result ?: "${play.session.world.step / 60f} s, progress ${play.progress.joinToString()}"}")
        return play to result
    }

    @Test
    fun `the deep chamber is out of reach while the ice holds, and the ice holds`() {
        val play = play()
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level3_start", scale = 3)
        play.perform(Action.parse("wait 30\nscoop 100 136 6 20"), limitSeconds = 0)
        assertEquals(0, play.session.bucketAmount, "the sand in the chamber")
        assertEquals(Session.OUT_OF_REACH, play.session.notice)
        play.perform(Action.parse("scoop 107 132 6 20"), limitSeconds = 0)
        assertEquals(0, play.session.bucketAmount, "the earth in the tunnel")
        // the hand cannot pull the plug out: it is wedged in the shaft
        play.perform(Action.parse("hand 84 46"), limitSeconds = 0)
        assertNull(play.session.held)
        assertTrue("verankert" in play.session.notice || play.session.notice == Session.OUT_OF_REACH, play.session.notice)
        // three minutes later the ice still closes the shaft: the deep chamber is out of reach
        play.perform(emptyList(), limitSeconds = 200)
        assertTrue(!play.session.inspect(100, 132).reachable, "the chamber is still shut off after 200 s")
        // the vent's heat creeps through the tuff: after about four minutes a thin channel has thawed along the
        // shaft wall (3b: measured 240 s, the same before the warm air); most of the ice is still there
        play.perform(emptyList(), limitSeconds = 100)
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level3_idle", scale = 3)
        assertEquals(Play.State.PLAYING, play.state)
        assertTrue(count(play, "ice") > 100, "${count(play, "ice")} ice")
        assertEquals(0, count(play, "glass"))
    }

    @Test
    fun `waiting until the vent has thawed a way, then the lava, is done without a medal`() {
        // the lazy way: no salt, four minutes of waiting, then the tunnel dug open and sand on the lava
        val dig = "scoop 107 132 6 20\npour 60 124 6 30\nscoop 111 131 6 20\npour 60 124 6 30\nscoop 115 131 6 20\npour 60 124 6 30\nwait 120\n"
        val (_, r) = solve("warten", "wait 14400\n" + dig + "scoop 98 135 6 20\npour 114 129 1 40\nwait 120\n", limit = 600)
        val result = assertNotNull(r)
        assertEquals(Medal.NONE, result.medal, "$result")
    }

    @Test
    fun `shards - glass made on the vent, broken with water from the spring, carried down in the bucket, gold`() {
        val script = "wait 30\n" + salt +
            "scoop 93 44 6 20\npour 35 42 1 20\nwait 180\n" +   // sand onto the vent, it turns to glass
            "scoop 71 47 3 20\npour 35 43 3 30\nwait 120\n" +   // water from the spring onto the hot glass: it cracks
            "scoop 35 45 3 20\npour 75 125 3 30\n"               // the shards into the chamber
        val (play, r) = solve("scherben", script)
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(10, result.actions)
        assertTrue(count(play, "glass_shards", 52, 112, 116, 150) >= 5)
    }

    @Test
    fun `lava - the tunnel dug open, sand from the bed poured onto the lava at its mouth, silver`() {
        val (play, r) = solve("lava", "wait 30\n" + salt + dig + "scoop 98 135 6 20\npour 114 129 1 40\nwait 120\n")
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(12, result.actions)
        assertTrue(count(play, "glass", 52, 112, 116, 150) >= 5)
    }

    @Test
    fun `ore - ore rubble on the lava gives molten metal that runs out along the floor and melts the sand, silver`() {
        val (play, r) = solve("erz", "wait 30\n" + salt + dig + "scoop 123 44 6 20\npour 114 129 1 40\nwait 300\n".repeat(2))
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(14, result.actions)
        assertTrue(count(play, "metal") + count(play, "molten_metal") > 0, "the metal")
    }

    @Test
    fun `water on the lava at the mouth only makes a crust, the lava from the pocket breaks through`() {
        // fresh water from the top of the sump onto the lava as it comes out of the tunnel: a crust of basalt
        // forms, but the magma behind the tunnel keeps the lava coming, and sand poured on it still melts.
        // (Phase 3 had this as a trap; it held only by chance, with salt water from deeper in the sump: 3b)
        val (play, r) = solve("wasser_auf_lava", "wait 30\n" + salt + dig + "scoop 75 137 6 20\npour 112 129 3 30\nwait 120\nscoop 98 135 6 20\npour 114 129 1 40\nwait 120\n", limit = 90)
        assertTrue(count(play, "basalt", 100, 118, 124, 144) > 5, "a crust at the mouth: ${count(play, "basalt", 100, 118, 124, 144)}")
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(14, result.actions, "two more than the lava way")
    }

    @Test
    fun `glass on the vent is solid and hot, neither the bucket nor the hand takes it`() {
        val play = play()
        play.perform(Action.parse("wait 30\nscoop 93 44 6 20\npour 35 42 1 20\nwait 180\nscoop 35 44 1 20"), limitSeconds = 0)
        assertTrue(count(play, "glass", 26, 36, 44, 50) > 5, "glass on the vent")
        assertEquals(0, play.session.bucketAmount, "the bucket takes no glass")
        play.perform(Action.parse("hand 35 44"), limitSeconds = 0)
        assertNull(play.session.held, play.session.notice)
    }
}
