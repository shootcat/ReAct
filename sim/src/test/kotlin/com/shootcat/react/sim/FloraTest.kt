package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plants (Leitfaden V3, 4.2): they grow slowly with water and light, burn well, hold the ground, die of salt and heat. */
class FloraTest {

    /** Earth from row 50 down, with a strip of grass in the middle; the left half of the earth is moist. */
    private fun meadow(lab: Lab) {
        lab.rect("earth", 2, 50, lab.width - 3, 61)
        for (y in 50..52) for (x in 2..31) lab.world.setWet(x, y, true)
        for (x in 30..33) lab.world.set(x, 50, lab.id("grass"))
    }

    @Test
    fun `grass spreads over moist earth in the light, not over dry earth or in the dark`() {
        val lab = Lab()
        meadow(lab)
        lab.seconds(60f)
        val moist = lab.countIn("grass", 2, 50, 29, 50)
        val dry = lab.countIn("grass", 34, 50, 61, 50)
        assertTrue(moist >= 8, "grass on the moist side: $moist cells")
        assertEquals(0, dry, "no grass on the dry side")

        val cave = Lab()
        meadow(cave)
        cave.rect("rock", 2, 18, 61, 21)
        cave.seconds(60f)
        assertEquals(4, cave.count("grass"), "in the dark it does not grow")
    }

    @Test
    fun `moss grows in a dark, damp cave and not in the light`() {
        fun grotto(dark: Boolean): Int {
            val lab = Lab()
            if (dark) lab.rect("rock", 2, 18, 61, 21)
            lab.rect("water", 2, 56, 61, 61)
            lab.world.set(2, 55, lab.id("moss"))
            lab.world.set(61, 55, lab.id("moss"))
            lab.seconds(90f)
            return lab.count("moss")
        }
        val dark = grotto(true)
        val light = grotto(false)
        assertTrue(dark >= 10, "moss in the dark cave: $dark cells")
        assertEquals(2, light, "in daylight it does not grow")
    }

    @Test
    fun `a vine grows towards water, hanging from the rock it grew on`() {
        val lab = Lab()
        // a ledge of rock on the left, a basin of water down on the right
        lab.rect("rock", 2, 33, 14, 37)
        lab.rect("rock", 34, 56, 35, 61)
        lab.rect("water", 36, 57, 61, 61)
        lab.world.set(15, 35, lab.id("vine"))
        lab.seconds(120f)
        val vine = lab.id("vine")
        val water = lab.id("water")
        var reached = false
        for (y in 1..62) for (x in 1..62) {
            if (lab.world.material(x, y) != vine) continue
            if (lab.world.material(x + 1, y) == water || lab.world.material(x, y + 1) == water || lab.world.material(x - 1, y) == water) reached = true
        }
        lab.picture("vine")
        assertTrue(reached, "the vine reached the water (${lab.count("vine")} cells)")
        assertEquals("vine", lab.at(15, 35), "it still hangs on the ledge as one structure")
    }

    @Test
    fun `a seed on moist earth in the light grows into a bush or a tree`() {
        val lab = Lab(64, 80)
        lab.rect("earth", 2, 66, 61, 77)
        for (y in 66..68) for (x in 2..61) lab.world.setWet(x, y, true)
        lab.world.set(31, 64, lab.id("seed"))
        lab.seconds(120f)
        assertTrue(lab.count("trunk") >= 2, "a trunk: ${lab.count("trunk")}")
        assertTrue(lab.count("leaves") >= 12, "leaves: ${lab.count("leaves")}")
        lab.picture("seed_grown")

        val dark = Lab(64, 80)
        dark.rect("earth", 2, 66, 61, 77)
        for (y in 66..68) for (x in 2..61) dark.world.setWet(x, y, true)
        dark.rect("rock", 2, 20, 61, 23)
        dark.world.set(31, 64, dark.id("seed"))
        dark.seconds(120f)
        assertEquals(0, dark.count("trunk"), "no light, no tree")

        val dry = Lab(64, 80)
        dry.rect("earth", 2, 66, 61, 77)
        dry.world.set(31, 64, dry.id("seed"))
        dry.seconds(120f)
        assertEquals(0, dry.count("trunk"), "no water, no tree")
    }

    /** The lowest row of the trunk, and the highest row of the tree. */
    private fun extent(lab: Lab): Pair<Int, Int> {
        var top = Int.MAX_VALUE
        for (y in 0 until lab.height) for (x in 0 until lab.width) {
            val id = lab.at(x, y)
            if ((id == "trunk" || id == "leaves") && y < top) top = y
        }
        return lab.top("trunk", 31) to top
    }

    @Test
    fun `a tree is a structure, when its trunk burns through the top falls`() {
        val lab = Lab(64, 80)
        lab.rect("earth", 2, 66, 61, 77)
        lab.world.plantTree(31, 65, tree = true)
        lab.seconds(1f)
        val (_, top) = extent(lab)
        assertTrue(lab.count("trunk") > 10 && lab.count("leaves") > 20, "a full-grown tree: ${lab.count("trunk")} trunk, ${lab.count("leaves")} leaves")
        lab.picture("tree")
        // heat the foot of the trunk until it burns through
        for (y in 62..65) lab.world.setTemperature(31, y, 650f)
        lab.seconds(12f)
        lab.picture("tree_burnt_through")
        val (_, after) = extent(lab)
        assertTrue(after == Int.MAX_VALUE || after > top + 3, "the top came down: highest part from row $top to $after")
    }

    @Test
    fun `a tree is one structure, cut at its foot it falls as a whole`() {
        val lab = Lab(64, 80)
        lab.rect("earth", 2, 66, 61, 77)
        lab.world.plantTree(31, 65, tree = true)
        lab.seconds(1f)
        val (_, top) = extent(lab)
        val leaves = lab.count("leaves")
        val trunk = lab.count("trunk")
        lab.world.erase(31, 62, 0)
        lab.world.erase(31, 63, 0)
        lab.seconds(3f)
        lab.picture("tree_cut")
        val (_, after) = extent(lab)
        assertTrue(after > top + 1, "the top came down: highest part from row $top to $after")
        assertEquals(leaves, lab.count("leaves"), "it fell whole, with every leaf")
        assertEquals(trunk - 2, lab.count("trunk"), "and every piece of trunk")
    }

    @Test
    fun `plants die in salt water and in heat`() {
        // grass in three fields: salt water on the left, hot sand on the right, nothing in the middle
        val lab = Lab()
        lab.rect("earth", 2, 50, 61, 61)
        for (x in 2..61) lab.world.set(x, 49, lab.id("grass"))
        lab.rect("rock", 27, 38, 27, 49)
        lab.rect("rock", 38, 38, 38, 49)
        lab.rect("saltwater", 2, 45, 26, 48)
        lab.rect("sand", 39, 46, 61, 48, 400f)
        lab.seconds(12f)
        val salted = lab.countIn("grass", 2, 40, 26, 50)
        // next to the walls the rock passes on some of the heat, so only the middle counts
        val untouched = lab.countIn("grass", 29, 40, 36, 50)
        val heated = lab.countIn("grass", 39, 40, 61, 50)
        assertTrue(salted < 8, "grass under salt water died: $salted of 25 left")
        assertTrue(heated < 4, "grass under hot sand died: $heated of 23 left")
        assertEquals(8, untouched, "the rest lives")

        val tree = Lab(64, 80)
        tree.rect("earth", 2, 66, 61, 77)
        tree.world.plantTree(31, 65, tree = true)
        tree.rect("rock", 20, 62, 21, 65)
        tree.rect("rock", 41, 62, 42, 65)
        for (y in 62..65) for (x in 22..40) if (tree.at(x, y) == "air") tree.world.set(x, y, tree.id("saltwater"))
        tree.seconds(60f)
        assertTrue(tree.count("deadwood") > 3, "the tree in salt water dies from below: ${tree.count("deadwood")} dead wood")
    }

    @Test
    fun `roots hold wet earth together`() {
        // a steep bank of wet earth, bare or covered in grass on top and on its side
        fun bank(grass: Boolean): Int {
            val lab = Lab()
            for (y in 36..61) for (x in 2..20) {
                val turf = grass && (y == 36 || x == 20)
                lab.world.set(x, y, lab.id(if (turf) "grass" else "earth"))
                lab.world.setWet(x, y, true)
            }
            lab.seconds(8f)
            var reach = 0
            for (y in 36..61) for (x in 2..61) if (lab.at(x, y) == "earth" || lab.at(x, y) == "grass") reach = maxOf(reach, x)
            return reach
        }
        val bare = bank(false)
        val rooted = bank(true)
        assertTrue(rooted <= 21, "the grassy bank keeps its shape: reaches x = $rooted")
        assertTrue(bare > 24, "the bare wet bank slumps: reaches x = $bare")
    }
}
