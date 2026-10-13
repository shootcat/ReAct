package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4, "Gewitterfeuer" (3c): a thunderstorm over a valley, a woodpile on a plateau of rock, and no fire
 * anywhere. Left a ridge of rock and a hill with a tall tree, the highest things under the cloud: the first
 * stroke sets the tree alight, then the lightning goes to the ridge. Under an overhang at the end of the
 * plateau old fence posts of metal, one long, three short. Only the hand.
 *
 * Lightning seeks metal and runs down it to its foot (3c.4): a post stood on the woodpile kindles it; a post
 * stood in the rock beside it takes the lightning away and keeps it, too hot to touch; one in the sand
 * makes glass. The storm lasts a little over two minutes, its rain wets the wood.
 */
class GewitterfeuerTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("gewitterfeuer"), mt)

    private fun count(play: Play, name: String, x0: Int = 0, y0: Int = 0, x1: Int = 199, y1: Int = 129): Int {
        val m = mt.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (play.session.world.material(x, y) == m) n++
        return n
    }

    /** Where each stroke of lightning went: (second, x, y, material). */
    private class Strike(val second: Float, val x: Int, val y: Int, val material: String)

    /** Plays [script] to [limit] seconds, noting every stroke, and keeps a picture of the end. */
    private fun solve(name: String, script: String, limit: Int = 60): Triple<Play, Result?, List<Strike>> {
        val play = play()
        val w = play.session.world
        // the backdrop as the app makes it, at the start: later, every falling drop of rain would darken the sky under it
        val backdrop = Backdrop.of(w)
        val strikes = ArrayList<Strike>()
        val check = play.session.onStep
        play.session.onStep = { s ->
            check?.invoke(s)
            if (w.boltAge == BOLT_NEW) {
                val x = w.boltX[w.boltLength - 1]
                val y = w.boltY[w.boltLength - 1]
                strikes += Strike(w.step / 60f, x, y, mt.spec(w.material(x, y)).id)
            }
        }
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(w, backdrop, "level4_$name", scale = 3)
        println("$name: ${play.state}, ${result ?: "${w.step / 60f} s"}, ${strikes.size} strokes: " +
            strikes.take(8).joinToString { "%.1f s %s at %d,%d".format(it.second, it.material, it.x, it.y) })
        return Triple(play, result, strikes)
    }

    @Test
    fun `the level is as painted, and without help the lightning strikes the tree and the ridge, never the woodpile`() {
        val play = play()
        Pictures.write(play.session.world, Backdrop.of(play.session.world), "level4_start", scale = 3)
        assertEquals(72, count(play, "wood"), "nine logs")
        assertEquals(15 + 3 * 7, count(play, "metal"), "one long post, three short")
        assertTrue(count(play, "cloud") > 2500, "the thunder cloud: ${count(play, "cloud")}")
        val (end, r, strikes) = solve("ohne", "", limit = 180)
        assertNull(r)
        assertTrue(strikes.size > 30, "a thunderstorm: ${strikes.size} strokes")
        assertEquals("leaves", strikes.first().material, "the first stroke into the crown of the tree")
        assertTrue(strikes.all { it.x < 100 }, "all on the ridge and the hill: ${strikes.filter { it.x >= 100 }.map { it.x }}")
        assertEquals(72, count(end, "wood"), "the woodpile is untouched")
        assertTrue(strikes.last().second < 160f, "the storm has passed: last stroke at ${strikes.last().second} s")
    }

    @Test
    fun `the long post stood on the woodpile, gold`() {
        val (_, r, strikes) = solve("lang", "wait 30\nhand 142 79\nhand 172 72")
        val result = assertNotNull(r)
        assertEquals(Medal.GOLD, result.medal)
        assertEquals(2, result.actions)
        assertTrue(strikes.any { it.material == "metal" && it.x == 172 }, "it struck the post")
    }

    @Test
    fun `any post on the woodpile will do, a short one too, gold`() {
        val (_, r, _) = solve("kurz", "wait 30\nhand 145 79\nhand 172 72")
        assertEquals(Medal.GOLD, assertNotNull(r).medal)
    }

    @Test
    fun `the long post stood in the rock beside the pile takes the lightning, then two short ones stacked on the pile reach higher, silver`() {
        val script = "wait 30\nhand 142 79\nhand 185 79\nwait 300\nhand 145 79\nhand 172 72\nhand 148 79\nhand 172 62"
        val (_, r, strikes) = solve("geerdet_dann_hoeher", script)
        val result = assertNotNull(r)
        assertEquals(Medal.SILVER, result.medal)
        assertEquals(6, result.actions)
        assertTrue(strikes.take(3).count { it.x == 185 } >= 1, "first the post in the rock: ${strikes.map { it.x }}")
    }

    @Test
    fun `a post stood on the pile late, when the rain has wetted it a little, still kindles it, bronze`() {
        val (_, r, _) = solve("spaet", "wait 6000\nhand 142 79\nhand 172 72", limit = 150)
        val result = assertNotNull(r)
        assertEquals(Medal.BRONZE, result.medal)
    }

    @Test
    fun `a post in the rock beside the pile takes every stroke, and is too hot to take back`() {
        val (play, r, strikes) = solve("geerdet", "wait 30\nhand 142 79\nhand 185 79\nwait 900\nhand 185 70")
        assertNull(r)
        assertEquals(72, count(play, "wood"), "nothing burnt")
        assertTrue(strikes.count { it.x == 185 } > 10, "the lightning goes into the post: ${strikes.map { it.x }}")
        assertEquals("Zu heiß zum Anfassen", play.session.notice)
    }

    @Test
    fun `a post in the heap of sand makes glass, and kindles nothing`() {
        val (play, r, strikes) = solve("sand", "wait 30\nhand 142 79\nhand 192 74")
        assertNull(r)
        assertTrue(strikes.count { it.x == 192 } > 10, "the lightning goes into the post: ${strikes.map { it.x }}")
        assertTrue(count(play, "glass", 186, 70, 198, 90) >= 3, "a fulgurite under it")
        assertEquals(72, count(play, "wood"))
    }

    @Test
    fun `a post on the ridge is a lightning rod up there, the woodpile stays cold`() {
        val (play, r, strikes) = solve("grat", "wait 30\nhand 142 79\nhand 16 44")
        assertNull(r)
        assertTrue(strikes.drop(1).all { it.x in 10..22 }, "${strikes.map { it.x }}")
        assertEquals(72, count(play, "wood"))
    }

    @Test
    fun `the same actions bring the same strokes in the same places`() {
        val a = solve("gleich_a", "wait 30\nhand 145 79\nhand 172 72\nhand 148 79\nhand 172 62").third
        val b = solve("gleich_b", "wait 30\nhand 145 79\nhand 172 72\nhand 148 79\nhand 172 62").third
        assertEquals(a.map { Triple(it.second, it.x, it.y) }, b.map { Triple(it.second, it.x, it.y) })
    }

    private companion object {
        /** A bolt is shown for eight steps; on the step it struck it has all of them left. */
        const val BOLT_NEW = 8
    }
}
