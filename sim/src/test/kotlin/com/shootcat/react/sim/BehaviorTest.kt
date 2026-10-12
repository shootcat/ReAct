package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The behaviour the guide asks for (Leitfaden V3, 4.4), each played in a small lab world. Nothing here is a
 * recipe: every result comes from the general rules and the numbers in materials.json.
 */
class BehaviorTest {

    @Test
    fun `sand falls, piles up with slopes and sinks in water`() {
        val lab = Lab()
        lab.world.paint(lab.id("sand"), 20, 10, 5)
        lab.seconds(4f)
        assertEquals(0, lab.countIn("sand", 2, 0, 61, 40), "all sand has come down")
        val peak = lab.top("sand", 20)
        val side = lab.top("sand", 26)
        assertTrue(peak in 50..61 && (side == -1 || side > peak), "a heap with sloping sides, peak at $peak, side at $side")

        val pond = Lab()
        pond.rect("water", 2, 40, 61, 61)
        pond.world.paint(pond.id("sand"), 30, 10, 3)
        pond.seconds(6f)
        assertTrue(pond.countIn("sand", 2, 58, 61, 61) > 10, "the sand lies on the bottom of the pond")
        pond.picture("sand_in_water")
    }

    @Test
    fun `earth is sticky and holds a steeper slope than sand`() {
        fun width(name: String): Int {
            val lab = Lab()
            lab.rect(name, 28, 20, 35, 45)
            lab.seconds(6f)
            return (2..61).count { lab.countIn(name, it, 0, it, 61) > 0 }
        }
        val sand = width("sand")
        val earth = width("earth")
        assertTrue(earth < sand, "earth spreads to $earth columns, sand to $sand")
    }

    @Test
    fun `water flows and levels out`() {
        val lab = Lab()
        lab.rect("water", 2, 20, 15, 45)
        lab.seconds(8f)
        val tops = (2..61).map { lab.top("water", it) }
        assertTrue(tops.all { it >= 0 }, "the water reaches every column")
        assertTrue(tops.max() - tops.min() <= 1, "flat surface, tops $tops")
    }

    @Test
    fun `wood burns away in the open, leaving a little ash and a lot of smoke`() {
        val lab = Lab()
        lab.rect("wood", 20, 40, 43, 47)
        val wood = lab.count("wood")
        lab.world.paint(lab.id("fire"), 31, 50, 3)
        var smoke = 0
        repeat(30) {
            lab.seconds(1f)
            smoke = maxOf(smoke, lab.count("smoke"))
            if (it == 6) lab.picture("wood_burning")
        }
        lab.picture("wood_burnt")
        assertTrue(lab.count("wood") < wood / 10, "the log is gone: ${lab.count("wood")} of $wood left")
        assertTrue(lab.count("ash") in 1 until wood / 2, "some ash: ${lab.count("ash")}")
        assertTrue(smoke > 20, "smoke rose, at most $smoke cells at once")
    }

    @Test
    fun `fire painted onto wood sets it alight`() {
        val lab = Lab()
        lab.rect("wood", 10, 50, 53, 61)
        // a short tap with the brush centred on the surface: a finger rests a few steps; only the air gets flames
        repeat(10) {
            lab.world.paint(lab.id("fire"), 30, 50, 3, 0.8f)
            lab.world.step()
        }
        lab.seconds(2f)
        assertTrue(burning(lab) > 0 || lab.count("charcoal") + lab.count("ash") > 0, "the wood burns")
    }

    @Test
    fun `fire without air dies out, the wood turns to charcoal and smoke fills the cave`() {
        val lab = Lab()
        // a closed chamber of rock with a little air and a pile of wood
        lab.rect("rock", 15, 20, 48, 50)
        lab.rect("air", 18, 23, 45, 47)
        lab.rect("wood", 18, 38, 45, 47)
        repeat(10) {
            lab.world.paint(lab.id("fire"), 30, 38, 3, 0.8f)
            lab.world.step()
        }
        lab.seconds(20f)
        lab.picture("closed_fire")
        assertEquals(0, lab.countIn("fire", 18, 23, 45, 47), "no flame left")
        assertEquals(0, (18..45).sumOf { x -> (38..47).count { y -> lab.world.flags[lab.world.index(x, y)].toInt() and Flag.BURNING != 0 } }, "nothing glows on")
        val pile = lab.countIn("wood", 18, 23, 45, 47) + lab.countIn("charcoal", 18, 23, 45, 47)
        assertTrue(pile > 220, "the pile did not burn away, it is still there as wood or charcoal: $pile of 280")
        assertTrue(lab.countIn("charcoal", 18, 23, 45, 47) > 0, "hot wood without air chars, like in a charcoal kiln")
        assertTrue(lab.countIn("smoke", 18, 23, 45, 37) > 60, "smoke fills the room above the wood: ${lab.countIn("smoke", 18, 23, 45, 37)}")
    }

    @Test
    fun `water puts out burning wood, which becomes charcoal`() {
        val lab = Lab()
        // a log on the ground, set alight
        lab.rect("wood", 20, 56, 43, 61)
        lab.rect("wood", 20, 56, 43, 56, 400f)
        lab.seconds(3f)
        assertTrue(burning(lab) > 0, "it burns")
        // a big splash of water: it floods the floor and covers the log
        lab.rect("water", 4, 20, 59, 40)
        lab.seconds(6f)
        lab.picture("wood_doused")
        assertEquals(0, burning(lab), "the fire is out: " + burningCells(lab))
        assertTrue(lab.count("charcoal") > 0, "charred wood is charcoal")
        assertTrue(lab.count("wood") > 0, "the rest of the log is saved")
    }

    private fun burningCells(lab: Lab) = (0 until lab.world.size)
        .filter { lab.world.flags[it].toInt() and Flag.BURNING != 0 }
        .joinToString { "${lab.materials.spec(lab.world.mat[it].toInt()).id}@${it % lab.width},${it / lab.width} ${lab.world.temp[it].toInt()}°" }

    private fun burning(lab: Lab) = (0 until lab.world.size).count { lab.world.flags[it].toInt() and Flag.BURNING != 0 && lab.world.mat[it].toInt() != lab.id("fire") }

    @Test
    fun `charcoal glows a long time without a flame`() {
        val lab = Lab()
        lab.rect("charcoal", 25, 52, 38, 61)
        lab.rect("charcoal", 25, 51, 38, 51, 500f)
        lab.seconds(10f)
        val glowing = burning(lab)
        assertTrue(glowing > 5, "embers glow: $glowing")
        assertTrue(lab.count("fire") < 3, "and make (almost) no flames: ${lab.count("fire")}")
        assertTrue(lab.count("charcoal") > 100, "slowly: ${lab.count("charcoal")} left")
    }

    @Test
    fun `lava meeting water makes basalt and steam`() {
        val lab = Lab()
        lab.rect("water", 2, 45, 61, 61)
        lab.rect("lava", 25, 30, 38, 36)
        var steam = 0
        repeat(8) {
            lab.seconds(1f)
            steam = maxOf(steam, lab.count("steam"))
        }
        lab.picture("lava_water")
        assertTrue(lab.count("basalt") > 40, "basalt: ${lab.count("basalt")}")
        assertTrue(steam > 20, "steam: $steam")
    }

    @Test
    fun `lava sets wood alight and cools to basalt`() {
        val lab = Lab()
        lab.rect("wood", 10, 50, 53, 61)
        lab.rect("lava", 28, 44, 35, 49)
        lab.seconds(3f)
        assertTrue(lab.world.events[Event.IGNITE] > 0 || lab.count("fire") > 0 || lab.count("charcoal") + lab.count("ash") > 0, "the wood caught fire")
        lab.seconds(40f)
        lab.picture("lava_wood")
        assertEquals(0, lab.count("lava"), "the lava has set")
        assertTrue(lab.count("basalt") > 20)
    }

    @Test
    fun `steam rises and condenses into water`() {
        val lab = Lab(64, 96)
        lab.rect("rock", 2, 2, 61, 4)
        lab.rect("steam", 20, 80, 43, 92)
        val steam = lab.count("steam")
        lab.seconds(30f)
        lab.picture("steam_condenses")
        assertTrue(lab.count("steam") < steam / 3, "most steam has condensed: ${lab.count("steam")} of $steam left")
        assertTrue(lab.count("water") > steam / 3, "into water: ${lab.count("water")}")
    }

    @Test
    fun `sand in a lava pool turns into glass`() {
        val lab = Lab()
        lab.rect("lava", 2, 40, 61, 61, 1300f)
        lab.rect("sand", 28, 36, 35, 39)
        lab.seconds(5f)
        lab.picture("sand_glass")
        assertTrue(lab.count("glass") > 5, "glass: ${lab.count("glass")}")
    }

    @Test
    fun `heat flows through stone, and rock does not melt`() {
        val lab = Lab()
        lab.rect("stone", 2, 30, 61, 33)
        lab.rect("lava", 2, 34, 61, 61, 1250f)
        val before = lab.temp(30, 30)
        lab.seconds(3f)
        assertTrue(lab.temp(30, 30) > before + 100f, "the top of the stone got hot: ${lab.temp(30, 30)}")
        assertEquals("rock", lab.at(0, 50), "the rock frame is still there")
    }

    @Test
    fun `earth and sand soak up some water and get wet`() {
        val lab = Lab()
        lab.rect("earth", 2, 50, 61, 61)
        lab.rect("water", 20, 30, 40, 40)
        val water = lab.count("water")
        lab.seconds(8f)
        val wet = (0 until lab.world.size).count { lab.world.flags[it].toInt() and Flag.WET != 0 }
        assertTrue(wet > 20, "wet earth: $wet")
        assertTrue(lab.count("water") in 1 until water, "part of the water soaked in: ${lab.count("water")} of $water")
    }

    @Test
    fun `water washes through an earth dam`() {
        val lab = Lab(96, 64)
        // a reservoir behind a low dam of earth, water keeps coming
        lab.rect("earth", 40, 40, 47, 61)
        lab.rect("water", 2, 30, 39, 61)
        var broke = false
        repeat(60) {
            lab.world.paint(lab.id("water"), 10, 5, 3)
            lab.seconds(1f)
            if (lab.countIn("water", 50, 0, 93, 61) > 150) broke = true
        }
        lab.picture("dam")
        assertTrue(broke, "water passed the dam: ${lab.countIn("water", 50, 0, 93, 61)}")
        assertTrue(lab.countIn("earth", 48, 0, 93, 61) > 10, "and carried earth along: ${lab.countIn("earth", 48, 0, 93, 61)}")
    }

    @Test
    fun `the last grain between water and a drop gives way, two grains hold`() {
        fun lip(width: Int): Lab {
            val lab = Lab(96, 64)
            // a pool behind a rock wall with a window at its foot, stopped with sand; behind the window a
            // ledge as long as the wall is thick, then a drop
            lab.rect("rock", 2, 51, 40 + width, 61)
            lab.rect("rock", 41 + width, 57, 93, 61)
            lab.rect("rock", 40, 20, 40 + width - 1, 49)
            lab.rect("sand", 40, 50, 40 + width - 1, 50)
            lab.rect("water", 2, 30, 39, 50)
            lab.seconds(20f)
            return lab
        }
        val thin = lip(1)
        assertEquals(0, thin.countIn("sand", 40, 50, 40, 50), "the grain was pushed out")
        assertTrue(thin.countIn("water", 42, 0, 93, 61) > 200, "and the pool ran out: ${thin.countIn("water", 42, 0, 93, 61)}")
        val thick = lip(2)
        assertEquals(2, thick.countIn("sand", 40, 50, 41, 50), "two grains stay")
        assertEquals(0, thick.countIn("water", 43, 0, 93, 61), "and hold the water")
    }

    @Test
    fun `smoke rises and gathers under an overhang`() {
        val lab = Lab()
        lab.rect("rock", 10, 20, 53, 23)
        lab.rect("smoke", 25, 50, 38, 55)
        lab.seconds(3f)
        assertTrue(lab.countIn("smoke", 10, 24, 53, 30) > 30, "smoke under the roof: ${lab.countIn("smoke", 10, 24, 53, 30)}")
    }
}
