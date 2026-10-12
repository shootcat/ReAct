package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Animals (Leitfaden V3, 4.2): small and with simple rules. */
class FaunaTest {

    private fun Lab.find(name: String): Pair<Int, Int>? {
        val m = id(name)
        for (y in 0 until height) for (x in 0 until width) if (world.material(x, y) == m) return x to y
        return null
    }

    @Test
    fun `fish swim about in fresh water`() {
        val lab = Lab()
        lab.rect("water", 2, 36, 61, 61)
        lab.world.set(30, 50, lab.id("fish"))
        val seen = HashSet<Pair<Int, Int>>()
        repeat(20) {
            lab.seconds(0.5f)
            val at = lab.find("fish")
            assertTrue(at != null, "the fish lives")
            assertTrue(at.second >= 36, "it stays in the water: row ${at.second}")
            seen += at
        }
        assertTrue(seen.size > 8, "it moved around: ${seen.size} places")
        assertEquals(0, lab.count("dead_fish"))
    }

    @Test
    fun `fish die in salt water, in hot water and on land`() {
        val salt = Lab()
        salt.rect("saltwater", 2, 36, 61, 61)
        salt.world.set(30, 50, salt.id("fish"))
        salt.seconds(1f)
        assertEquals(0, salt.count("fish"), "in salt water")

        val hot = Lab()
        hot.rect("water", 2, 36, 61, 61, 60f)
        hot.world.set(30, 50, hot.id("fish"))
        hot.seconds(1f)
        assertEquals(0, hot.count("fish"), "in hot water")

        val land = Lab()
        land.world.set(30, 40, land.id("fish"))
        land.seconds(2f)
        assertEquals(1, land.count("fish"), "it lasts a moment out of the water")
        land.seconds(4f)
        assertEquals(0, land.count("fish"), "on land")
        assertEquals(1, land.count("dead_fish"), "a dead fish is left")
    }

    @Test
    fun `fireflies glow and keep to the dark`() {
        val lab = Lab()
        // the left half lies under a roof of rock, the right half is open to the sky
        lab.rect("rock", 2, 18, 31, 21)
        for (k in 0 until 12) lab.world.set(30 + k % 4, 30 + k / 4 * 4, lab.id("firefly"))
        lab.seconds(60f)
        val dark = lab.countIn("firefly", 2, 22, 31, 61)
        val bright = lab.countIn("firefly", 32, 0, 61, 61) + lab.countIn("firefly", 2, 0, 31, 17)
        assertEquals(12, dark + bright, "they all live")
        assertTrue(dark >= 9, "most are in the dark: $dark in the dark, $bright in daylight")
    }

    @Test
    fun `worms loosen the earth so that water seeps in`() {
        fun puddle(worms: Int): Int {
            val lab = Lab()
            lab.rect("earth", 2, 30, 61, 61)
            for (k in 0 until worms) lab.world.set(6 + k * 8, 33 + k % 3, lab.id("worm"))
            lab.seconds(30f)
            lab.rect("water", 2, 26, 61, 29)
            lab.seconds(30f)
            return lab.count("water")
        }
        val without = puddle(0)
        val with = puddle(7)
        assertTrue(with < without - 20, "water left on top: $with with worms, $without without")
    }

    @Test
    fun `birds fly away from fire and smoke`() {
        fun perch(fire: Boolean): Int {
            val lab = Lab()
            lab.rect("stone", 28, 40, 33, 61)
            lab.world.set(30, 39, lab.id("bird"))
            if (fire) {
                lab.rect("wood", 44, 56, 52, 61)
                lab.world.paint(lab.id("fire"), 48, 54, 2)
            }
            lab.seconds(6f)
            return lab.count("bird")
        }
        assertEquals(1, perch(false), "without danger it stays")
        assertEquals(0, perch(true), "with fire nearby it flies off and away")
    }

    @Test
    fun `a tap puts down one animal, a stroke only a few, well apart`() {
        val materials = Lab.materials
        val s = Session(materials, 64, 64, 4L) { w ->
            val rock = materials.id("rock")
            for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
            for (y in 40..61) for (x in 0..31) w.set(x, y, materials.id("water"))
            for (y in 40..61) for (x in 32..63) w.set(x, y, materials.id("earth"))
        }
        val fish = materials.id("fish")
        val worm = materials.id("worm")
        // a big brush, a tap just above the water: one fish, and it goes into the water
        s.press(15, 38, fish, 8)
        repeat(30) { s.tick() }
        s.release()
        assertEquals(1, s.world.count(fish), "one tap, one fish")
        val at = (0 until s.world.size).first { s.world.mat[it].toInt() == fish }
        assertTrue(at / 64 >= 40, "it is in the water")
        // a worm tapped onto the earth goes into it
        s.press(48, 39, worm, 8)
        s.release()
        assertEquals(1, s.world.count(worm))
        assertTrue((0 until s.world.size).first { s.world.mat[it].toInt() == worm } / 64 >= 40, "the worm is in the earth")
        // a stroke across the whole width
        val birds = materials.id("bird")
        s.press(2, 10, birds, 6)
        for (x in 3..61) s.drag(x, 10)
        s.release()
        val put = (0 until s.world.size).filter { s.world.mat[it].toInt() == birds }.map { it % 64 }.sorted()
        assertTrue(put.size in 4..6, "a few birds along the stroke: $put")
        assertTrue(put.zipWithNext().all { (a, b) -> b - a >= Session.ANIMAL_SPACING }, "well apart: $put")
    }

    @Test
    fun `fish on land make no heap, they flop about one by one, die and rot away`() {
        val lab = Lab()
        // a heap of fish on the rock floor, as if tipped out of a net
        lab.rect("fish", 28, 55, 33, 61)
        val heap = lab.count("fish")
        lab.seconds(2f)
        lab.picture("fish_on_land")
        var top = 61
        for (y in 0 until 62) for (x in 0 until 64) if (lab.at(x, y) == "fish") top = minOf(top, y)
        assertTrue(top >= 58, "the heap has come apart: highest fish in row $top")
        assertTrue(lab.countIn("fish", 2, 0, 27, 61) + lab.countIn("fish", 34, 0, 61, 61) > heap / 3, "they flopped off to the sides")
        lab.seconds(4f)
        assertEquals(0, lab.count("fish"), "out of the water they die")
        assertEquals(heap, lab.count("dead_fish"))
        lab.seconds(60f)
        assertEquals(0, lab.count("dead_fish"), "and rot away within a minute")
    }
}
