package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 5, "Regenmacher" (3c): a dry field with a few tufts of grass and some seeds at the foot of a cliff, the
 * sea on the other side, salt water. Between them a hot spring: a dry basin of stone over magma. The wind blows
 * from the sea to the cliff. Only the bucket.
 *
 * Salt water poured on the field kills what grows there and leaves a crust of salt. Boiled in the hot basin
 * it leaves its salt behind; the steam rises, turns into cloud where clouds form, the wind carries it to the
 * cliff, it piles up there and rains fresh water onto the field (3c.1 to 3c.3).
 */
class RegenmacherTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("regenmacher"), mt)

    private fun count(play: Play, name: String, x0: Int = 0, y0: Int = 0, x1: Int = 219, y1: Int = 129): Int {
        val m = mt.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (play.session.world.material(x, y) == m) n++
        return n
    }

    private fun plants(play: Play) = (play.goals[0] as Goal.Grow).count(play.session.world)

    /** One bucket of salt water from the sea into the hot basin. */
    private val load = "scoop 30 96 6 20\npour 78 82 3 20\n"

    /** Plays [script] to [limit] seconds and keeps a picture of the end. */
    private fun solve(name: String, script: String, limit: Int = 300): Pair<Play, Result?> {
        val play = play()
        val backdrop = Backdrop.of(play.session.world)
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(play.session.world, backdrop, "level5_$name", scale = 3)
        println("$name: ${play.state}, ${result ?: "${play.session.world.step / 60f} s"}, ${plants(play)} plants, ${count(play, "cloud")} cloud, ${count(play, "salt")} salt")
        return play to result
    }

    @Test
    fun `the level is as painted, and without help nothing grows, nothing boils, nothing burns`() {
        val play = play()
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level5_start", scale = 3)
        assertEquals(15, plants(play), "three tufts of grass, six seeds")
        assertEquals(0, count(play, "water"), "no fresh water anywhere")
        assertTrue(count(play, "saltwater") > 500, "the sea: ${count(play, "saltwater")}")
        // seven minutes: the magma heats the basin, but the clefts keep its heat from the sea and the field
        val (end, r) = solve("ohne", "", limit = 420)
        assertNull(r)
        assertEquals(15, plants(end), "nothing grew and nothing died")
        assertEquals(0, count(end, "cloud"), "no cloud")
        assertEquals(0, count(end, "salt"), "the sea did not boil at its shore")
    }

    @Test
    fun `three buckets into the hot basin, the cloud rains at the cliff, the field turns green, gold`() {
        val (play, r) = solve("drei_eimer", "wait 30\n" + load.repeat(3))
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(6, result.actions)
        assertTrue(count(play, "salt", 70, 70, 86, 90) > 15, "the salt stayed in the basin")
        assertEquals(0, count(play, "salt", 140, 60, 186, 90), "the rain was fresh")
    }

    @Test
    fun `six buckets make cloud faster, but cost more actions, silver`() {
        val (_, r) = solve("sechs_eimer", "wait 30\n" + load.repeat(6))
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(12, result.actions)
    }

    @Test
    fun `salt water poured on the hot ground beside the basin boils slowly, bronze`() {
        val (_, r) = solve("tuff", "wait 30\n" + "scoop 30 96 6 20\npour 66 80 3 20\n".repeat(3))
        assertEquals(Medal.BRONZE, assertNotNull(r).medal)
    }

    @Test
    fun `one bucket makes too little rain`() {
        val (play, r) = solve("ein_eimer", "wait 30\n$load", limit = 420)
        assertNull(r)
        assertTrue(plants(play) in 16..39, "it rained a little: ${plants(play)} plants")
    }

    @Test
    fun `salt water on the field kills what grows there, and later rain does not bring it back`() {
        val (play, r) = solve("salz_aufs_feld", "wait 30\nscoop 30 96 6 20\npour 160 80 6 20\n" + load.repeat(3))
        assertNull(r)
        assertTrue(plants(play) < 10, "fewer than the 15 at the start: ${plants(play)} plants")
        assertTrue(count(play, "salt", 140, 60, 186, 90) > 10, "a crust of salt on the field: ${count(play, "salt", 140, 60, 186, 90)}")
    }

    @Test
    fun `the same actions grow the same field`() {
        val a = solve("gleich_a", "wait 30\n" + load.repeat(3)).first
        val b = solve("gleich_b", "wait 30\n" + load.repeat(3)).first
        assertEquals(a.session.world.hash(), b.session.world.hash())
        assertEquals(a.result, b.result)
    }
}
