package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2, "Signalfeuer" (Leitfaden V3, 5.4): on the summit a woodpile under a hood of snow, three cells
 * thick. On the left a volcano with a lava lake over magma, in the middle the sea, at the foot of the
 * mountain a small beach with little sand. A bucket that holds one load, no hand.
 *
 * Fire cannot be carried, heat can: sand poured on the lava, taken up again after a moment, is hot enough
 * to kindle wood. Hot sand on snow only melts it, the cold water takes the heat. So the snow has to go
 * first, or melt where its water runs off.
 */
class SignalfeuerTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("signalfeuer"), mt)

    private fun count(play: Play, name: String, x0: Int = 0, y0: Int = 0, x1: Int = 179, y1: Int = 109): Int {
        val m = mt.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (play.session.world.material(x, y) == m) n++
        return n
    }

    /** Sand from the beach onto the lava, a moment later back into the bucket, and onto the pile at ([x], [y]). */
    private fun hotSand(x: Int, y: Int, wait: Int = 90) = "scoop 112 68 6 20\npour 29 62 1 20\nwait $wait\nscoop 29 66 3 20\npour $x $y 1 30\n"

    /** Plays [script], lets the world run on up to [limit] seconds, and keeps a picture of the end. */
    private fun solve(name: String, script: String, limit: Int = 300): Pair<Play, Result?> {
        val play = play()
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level2_$name", scale = 3)
        println("$name: ${play.state}, ${result ?: "${play.session.world.step / 60f} s, progress ${play.progress.joinToString()}"}")
        return play to result
    }

    @Test
    fun `the level is as painted, and nothing happens by itself`() {
        val play = play()
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level2_start", scale = 3)
        assertEquals(80, count(play, "wood"))
        assertTrue(count(play, "snow") > 80, "the hood of snow")
        assertTrue(count(play, "sand") in 60..90, "a little sand: ${count(play, "sand")}")
        val lava = count(play, "lava")
        // five minutes later the lake is still liquid (the magma keeps it hot), the snow still lies, the
        // heat of the volcano has not crept across the sea to the summit
        play.perform(emptyList(), limitSeconds = 300)
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level2_idle", scale = 3)
        assertEquals(Play.State.PLAYING, play.state)
        assertTrue(count(play, "lava") >= lava, "the lake")
        assertTrue(count(play, "snow", 125, 0, 170, 40) > 80, "the snow on the summit")
        assertEquals(0, count(play, "water"), "no meltwater, no steam from the sea")
    }

    @Test
    fun `melting - hot sand beside the pile melts the snow there, the water runs down the slope, the second load kindles, gold`() {
        val (_, r) = solve("schmelzen", "wait 60\n" + hotSand(136, 18) + "wait 300\n" + hotSand(136, 18))
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(8, result.actions)
    }

    @Test
    fun `into the sea - the snow carried off in four loads, then one load of hot sand, silver`() {
        // the hood in three loads, the last of the snow on the right slope in the fourth
        val snow = listOf(145 to 14, 138 to 20, 152 to 20, 158 to 26).joinToString("") { (x, y) -> "scoop $x $y 6 20\npour 86 60 3 20\n" }
        val (play, r) = solve("ins_meer", "wait 60\n" + snow + hotSand(137, 18))
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(12, result.actions)
        assertTrue(count(play, "snow", 125, 0, 170, 40) < 5, "the summit is bare")
    }

    @Test
    fun `salt water - three loads from the sea melt the snow without heat, then hot sand, silver`() {
        val salt = listOf(145 to 9, 138 to 14, 152 to 14).joinToString("") { (x, y) -> "scoop 86 80 3 20\npour $x $y 3 20\n" }
        val (_, r) = solve("salzwasser", "wait 60\n" + salt + "wait 300\n" + hotSand(136, 18))
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(10, result.actions)
    }

    @Test
    fun `hot sand on top of the hood only melts snow into water, again and again`() {
        val (play, r) = solve("oben_drauf", "wait 60\n" + hotSand(145, 10) + "wait 300\n" + hotSand(145, 10), limit = 60)
        assertNull(r)
        assertEquals(80, count(play, "wood"), "nothing burnt")
        assertTrue(count(play, "snow", 125, 0, 170, 40) < 70, "the snow melted: ${count(play, "snow", 125, 0, 170, 40)} left")
    }

    @Test
    fun `one load of hot sand beside the pile only melts the snow there`() {
        val (play, r) = solve("einmal_seitlich", "wait 60\n" + hotSand(136, 18), limit = 60)
        assertNull(r)
        assertEquals(80, count(play, "wood"), "nothing burnt")
    }

    @Test
    fun `sand left on the lava too long has turned to glass and kindles nothing`() {
        val (play, r) = solve("zu_lange", "wait 60\n" + hotSand(136, 18, wait = 600) + "wait 300\n" + hotSand(136, 18, wait = 600), limit = 60)
        assertNull(r)
        assertTrue(count(play, "glass", 10, 50, 50, 75) > 20, "glass in the crater: ${count(play, "glass", 10, 50, 50, 75)}")
    }

    @Test
    fun `snow tipped into the crater sets the lava, the only heat is covered`() {
        val snow = listOf(145 to 14, 138 to 20).joinToString("") { (x, y) -> "scoop $x $y 6 20\npour 29 60 3 20\n" }
        val (play, r) = solve("krater", "wait 60\n" + snow + "wait 1800\n" + hotSand(136, 18) + "wait 300\n" + hotSand(136, 18), limit = 90)
        assertNull(r)
        // since Phase 4 pieces of the crust are bodies and some sink into the lava still liquid (basalt is
        // heavier): 153 cells of basalt in the lake instead of 166, 96 of lava instead of 83
        println("crater: ${count(play, "basalt", 14, 60, 44, 76)} basalt, ${count(play, "lava", 14, 60, 44, 76)} lava")
        assertTrue(count(play, "basalt", 14, 60, 44, 76) > 150, "a crust of basalt over the lake: ${count(play, "basalt", 14, 60, 44, 76)}")
        assertEquals(80, count(play, "wood"))
    }

    @Test
    fun `there is no hand in this level`() {
        val play = play()
        val e = runCatching { play.perform(Action.parse("hand 145 16")) }.exceptionOrNull()
        assertTrue(e is IllegalStateException, "$e")
    }
}
