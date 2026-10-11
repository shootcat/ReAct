package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Leitfaden V3, 3.1: solid material forms structures. One that touches anchored ground (rock) stands, one
 * that does not falls as a whole, without turning, until it lies on something.
 */
class StructureTest {

    /** The cells of [name] as a set of (x, y). */
    private fun shape(lab: Lab, name: String): Set<Pair<Int, Int>> {
        val m = lab.id(name)
        val out = HashSet<Pair<Int, Int>>()
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == m) out += x to y
        return out
    }

    @Test
    fun `a loose block of wood falls as a whole and keeps its shape`() {
        val lab = Lab()
        lab.rect("wood", 20, 10, 29, 14)
        lab.rect("wood", 24, 15, 25, 18)
        val before = shape(lab, "wood")
        lab.seconds(2f)
        val after = shape(lab, "wood")
        assertEquals(before.size, after.size)
        val dy = after.minOf { it.second } - before.minOf { it.second }
        assertTrue(dy > 30, "it fell, by $dy")
        assertEquals(before.map { it.first to it.second + dy }.toSet(), after, "same shape, only lower")
        assertEquals(61, after.maxOf { it.second }, "and lies on the floor")
    }

    @Test
    fun `wood that touches rock stands`() {
        val lab = Lab()
        // a shelf sticking out of the left wall
        lab.rect("wood", 2, 20, 20, 21)
        lab.seconds(2f)
        assertEquals((2..20).map { it to 20 }.toSet() + (2..20).map { it to 21 }, shape(lab, "wood"))
    }

    @Test
    fun `a bridge falls when its post is gone`() {
        val lab = Lab()
        // a deck held only by a post that stands on the rock floor
        lab.rect("wood", 20, 30, 43, 31)
        lab.rect("wood", 30, 32, 32, 61)
        lab.seconds(1f)
        assertEquals(30, shape(lab, "wood").minOf { it.second }, "standing")
        // most of the post is gone: the deck and the stump hanging from it fall onto what is left
        for (y in 36..58) for (x in 30..32) lab.world.erase(x, y, 0)
        lab.seconds(2f)
        assertTrue(shape(lab, "wood").minOf { it.second } > 40, "the deck came down")
    }

    @Test
    fun `burning through the post brings the deck down`() {
        val lab = Lab()
        lab.rect("wood", 20, 30, 43, 31)
        lab.rect("wood", 30, 32, 32, 61)
        // a fire at the foot of the post
        lab.rect("wood", 29, 55, 33, 56, 600f)
        var fell = false
        repeat(40) {
            lab.seconds(1f)
            if (it == 3) lab.picture("bridge_burning")
            if (!fell && shape(lab, "wood").none { it.second == 30 }) {
                fell = true
                lab.picture("bridge_fallen")
            }
        }
        assertTrue(fell, "the deck fell once the post had burnt through")
    }

    @Test
    fun `a wooden raft floats, a stone block sinks`() {
        val lab = Lab()
        lab.rect("water", 2, 40, 61, 61)
        lab.rect("wood", 10, 20, 25, 22)
        lab.rect("stone", 40, 20, 46, 24)
        lab.seconds(4f)
        val wood = shape(lab, "wood")
        assertEquals(39, wood.maxOf { it.second }, "the raft lies on the water")
        assertEquals(61, shape(lab, "stone").maxOf { it.second }, "the stone lies on the bottom")
        val soaked = (0 until lab.world.size).count { lab.world.flags[it].toInt() and Flag.WET != 0 }
        assertEquals(22 * 60, lab.count("water") + soaked, "the stone pushed the water aside; only what the wood soaked up is gone")
    }

    @Test
    fun `rubble falls grain by grain, a stone wall stands on its own`() {
        val lab = Lab()
        lab.rect("rubble", 10, 20, 13, 40)
        lab.rect("stone", 40, 41, 43, 61)
        lab.seconds(3f)
        assertTrue(lab.countIn("rubble", 10, 20, 13, 40) < 10, "the rubble has come down")
        assertEquals(84, lab.countIn("stone", 40, 41, 43, 61), "the wall on the floor is unchanged")
    }
}
