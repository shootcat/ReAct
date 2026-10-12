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

    /** Cells of [name] with nothing under them: neither more of it nor anything that carries it. */
    private fun hanging(lab: Lab, name: String): Int {
        val m = lab.id(name)
        var n = 0
        for (y in 0 until lab.height - 1) for (x in 0 until lab.width) {
            if (lab.world.material(x, y) != m) continue
            val below = lab.world.material(x, y + 1)
            if (below == MaterialTable.AIR || lab.materials.soft[below]) n++
        }
        return n
    }

    /** The 4-connected pieces of [name]. */
    private fun pieces(lab: Lab, name: String): List<Set<Pair<Int, Int>>> {
        val left = shape(lab, name).toMutableSet()
        val out = ArrayList<Set<Pair<Int, Int>>>()
        while (left.isNotEmpty()) {
            val piece = HashSet<Pair<Int, Int>>()
            val stack = ArrayDeque(listOf(left.first()))
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                if (!left.remove(c)) continue
                piece += c
                val (x, y) = c
                stack += listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            }
            out += piece
        }
        return out
    }

    /** True if every column of [name] reaches down onto something: the lowest cell of the shape rests. */
    private fun lies(lab: Lab, name: String): Boolean {
        val cells = shape(lab, name)
        if (cells.isEmpty()) return false
        return cells.any { (x, y) -> (x to y + 1) !in cells && lab.world.material(x, y + 1) != MaterialTable.AIR && !lab.materials.soft[lab.world.material(x, y + 1)] }
    }

    @Test
    fun `a block painted in the air lies on the ground after a few steps`() {
        // Leitfaden V3, 4a.1.1: painted with the brush while the world runs, as in the sandbox
        for (name in listOf("metal", "glass", "ice", "brick", "stone", "wood")) {
            val m = Lab.materials.id(name)
            val session = Session(Lab.materials, 64, 64, 5L) { w ->
                val rock = Lab.materials.id("rock")
                for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
                for (y in 0..61) for (x in listOf(0, 1, 62, 63)) w.set(x, y, rock)
            }
            session.press(30, 12, m, 3)
            session.drag(36, 12)
            session.release()
            repeat(45) { session.tick() }
            var lowest = 0
            var count = 0
            for (y in 0 until 64) for (x in 0 until 64) if (session.world.material(x, y) == m) { lowest = maxOf(lowest, y); count++ }
            assertTrue(count > 30, "$name painted: $count")
            assertEquals(61, lowest, "$name lies on the floor after 45 steps")
        }
    }

    @Test
    fun `a block resting on a corner tips over the edge and comes down`() {
        val lab = Lab()
        // a post of dead wood on the floor; a block of metal put on it with only its corner
        lab.rect("deadwood", 30, 40, 31, 61)
        lab.rect("metal", 31, 34, 40, 39)
        lab.seconds(2f)
        assertEquals(0, hanging(lab, "metal"), "nothing of the block hangs in the air")
        assertTrue(shape(lab, "metal").maxOf { it.second } == 61, "the block slid off and lies on the floor")
        assertEquals(44, lab.count("deadwood"), "the post still stands")

        // a block whose weight is over the post stays on it
        val held = Lab()
        held.rect("deadwood", 30, 40, 33, 61)
        held.rect("metal", 28, 36, 35, 39)
        held.seconds(2f)
        assertEquals(39, shape(held, "metal").maxOf { it.second }, "balanced on the post")
    }

    @Test
    fun `leaves carry nothing, a block in a tree crown falls through it`() {
        val lab = Lab(64, 80)
        lab.rect("earth", 2, 66, 61, 77)
        lab.world.plantTree(31, 65, tree = true)
        lab.seconds(1f)
        val leaves = lab.count("leaves")
        assertTrue(leaves > 20, "a crown: $leaves leaves")
        // metal painted into the crown beside the trunk and onto the top of it
        val metal = lab.id("metal")
        var top = 80
        var left = 64
        for (y in 0 until 80) for (x in 0 until 64) if (lab.at(x, y) == "leaves") { top = minOf(top, y); left = minOf(left, x) }
        lab.world.paint(metal, left + 2, top + 4, 1)
        lab.world.paint(metal, 31, top - 4, 1)
        val before = shape(lab, "metal").maxOf { it.second }
        lab.seconds(3f)
        lab.picture("crown_metal")
        for (piece in pieces(lab, "metal")) {
            val under = piece.map { (x, y) -> x to y + 1 }.filter { it !in piece }.map { lab.at(it.first, it.second) }.filter { it != "air" }
            assertTrue(under.any { it != "leaves" && it != "vine" }, "a piece of metal rests on $under")
        }
        assertTrue(shape(lab, "metal").maxOf { it.second } > before + 3, "the metal came down")
        assertTrue(lab.count("leaves") < leaves, "it tore leaves off on the way")
    }

    @Test
    fun `built material and plants are separate structures`() {
        val lab = Lab(64, 80)
        lab.rect("earth", 2, 66, 61, 77)
        // a dead trunk standing on the earth, and a plank laid against it that touches nothing else
        lab.rect("deadwood", 30, 50, 31, 65)
        lab.rect("wood", 32, 52, 40, 52)
        lab.seconds(3f)
        assertEquals(65, shape(lab, "wood").maxOf { it.second }, "the plank fell to the ground")
        assertEquals((30..31).flatMap { x -> (50..65).map { x to it } }.toSet(), shape(lab, "deadwood"), "the trunk still stands")
    }
}
