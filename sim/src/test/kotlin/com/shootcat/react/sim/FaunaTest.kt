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
}
