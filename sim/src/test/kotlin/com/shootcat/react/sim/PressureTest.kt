package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 2d (Leitfaden V3, 4a.4): pressure in closed spaces, bursting walls, explosions, firedamp. */
class PressureTest {

    private val materials = Lab.materials

    /** A box of [wall] standing on the rock floor, inside x 22..41, y 44..57, with a window of [window] in its right side. */
    private fun box(wall: String, window: String? = null): Lab {
        val lab = Lab()
        lab.rect(wall, 20, 42, 43, 61)
        lab.rect("air", 22, 44, 41, 59)
        if (window != null) lab.rect(window, 42, 48, 43, 53)
        return lab
    }

    @Test
    fun `walls withstand pressure in the order glass, clay, ice, wood, brick, stone, metal, and rock never breaks`() {
        val order = listOf("glass", "clay", "ice", "wood", "brick", "stone", "metal").map { materials.strength[materials.id(it)] }
        assertEquals(order.sorted(), order, "strengths: $order")
        assertEquals(Float.POSITIVE_INFINITY, materials.strength[materials.id("rock")])
    }

    @Test
    fun `steam shut in builds up pressure and bursts the weakest wall`() {
        val lab = box("stone", "glass")
        lab.rect("steam", 22, 44, 41, 59, 160f)
        val stone = lab.count("stone")
        lab.world.step()
        lab.world.run(World.PRESSURE_EVERY)
        lab.seconds(1f)
        lab.picture("steam_burst")
        assertTrue(lab.count("glass") < 12, "the glass window burst: ${lab.count("glass")} of 12 left")
        assertTrue(lab.count("glass_shards") > 0, "into shards")
        assertEquals(stone, lab.count("stone"), "the stone held")
        assertTrue(lab.world.events[Event.EXPLODE] > 0)

        // plain air at the same warmth does nothing
        val calm = box("stone", "glass")
        calm.rect("air", 22, 44, 41, 59, 20f)
        calm.seconds(2f)
        assertEquals(12, calm.count("glass"))
    }

    @Test
    fun `the pressure is worked out for each closed region of gas`() {
        val lab = box("stone")
        lab.rect("steam", 22, 44, 41, 51, 150f)
        lab.world.step()
        val inside = lab.world.pressureAt(30, 55)
        assertTrue(inside > 2f, "steam in a closed box: $inside")
        assertEquals(1f, lab.world.pressureAt(30, 20), "outside, in the open")
    }

    @Test
    fun `water that gets into a closed cave with lava flashes into steam and blows it open`() {
        fun cave(roof: String?): Lab {
            val lab = Lab()
            lab.rect("stone", 18, 40, 45, 61)
            lab.rect("lava", 20, 54, 43, 59, 1200f)
            lab.rect("air", 20, 44, 43, 53)
            if (roof == null) lab.rect("air", 20, 40, 43, 43) else lab.rect(roof, 20, 40, 43, 43)
            // water that has leaked in, just above the lava
            lab.rect("water", 24, 46, 39, 49)
            return lab
        }
        val shut = cave("glass")
        shut.seconds(2f)
        shut.picture("steam_explosion")
        assertTrue(shut.world.events[Event.EXPLODE] > 0, "a steam explosion")
        assertTrue(shut.count("glass") < 96, "the glass roof burst: ${shut.count("glass")}")

        val open = cave(null)
        open.seconds(2f)
        assertEquals(0, open.world.events[Event.EXPLODE], "in the open the steam just goes up")
        assertTrue(open.world.events[Event.BOIL] > 20, "and boils off quickly")
    }

    @Test
    fun `firedamp rises, gathers under the roof of a cave and goes off at a flame`() {
        val lab = Lab()
        lab.rect("rock", 2, 20, 61, 23)
        lab.rect("firedamp", 20, 48, 43, 55)
        val gas = lab.count("firedamp")
        lab.seconds(6f)
        val high = lab.countIn("firedamp", 2, 24, 61, 33)
        assertTrue(high > gas / 2, "it gathers under the roof: $high of $gas")
        // a glass pane up there, and a torch held up into the gas
        lab.rect("glass", 50, 34, 55, 35)
        lab.rect("fire", 30, 28, 31, 29)
        lab.seconds(3f)
        lab.picture("firedamp")
        assertTrue(lab.world.events[Event.EXPLODE] > 0, "it went off")
        assertTrue(lab.count("firedamp") < gas / 10, "all at once: ${lab.count("firedamp")} left")
    }

    @Test
    fun `a crack in a mine gives off firedamp`() {
        val lab = Lab()
        lab.rect("rock", 2, 20, 61, 23)
        lab.rect("gas_vent", 30, 60, 33, 61)
        lab.seconds(30f)
        assertTrue(lab.count("firedamp") > 5, "firedamp: ${lab.count("firedamp")}")
    }

    @Test
    fun `a spring gives water while it lies open, under water or buried it rests`() {
        // in a rock face the water runs off, the spring stays open
        val open = Lab()
        open.rect("rock", 2, 20, 20, 61)
        open.rect("spring", 20, 30, 20, 45)
        open.seconds(10f)
        assertTrue(open.count("water") > 30, "water from the spring: ${open.count("water")}")

        val buried = Lab()
        buried.rect("rock", 2, 50, 61, 61)
        buried.rect("spring", 28, 50, 35, 51)
        buried.rect("earth", 2, 40, 61, 49)
        buried.seconds(5f)
        assertEquals(0, buried.count("water"), "under earth")

        // in a basin the water covers the spring, and then it rests: a pool is fed only as far as its springs lie open
        val basin = Lab()
        basin.rect("rock", 2, 50, 61, 61)
        basin.rect("rock", 24, 40, 25, 49)
        basin.rect("rock", 38, 40, 39, 49)
        basin.rect("spring", 26, 49, 37, 49)
        basin.seconds(10f)
        val covered = basin.count("water")
        assertTrue(covered in 12..40, "a layer over the spring: $covered")
        basin.seconds(10f)
        assertEquals(covered, basin.count("water"), "covered by water, the spring rests")
    }

    @Test
    fun `an explosion throws sand and water away, breaks glass, kindles wood and sets snow sliding`() {
        val lab = Lab()
        lab.rect("sand", 24, 52, 39, 61)
        lab.rect("water", 2, 56, 15, 61)
        lab.rect("glass", 44, 54, 47, 61)
        lab.rect("wood", 16, 58, 19, 61)
        lab.rect("snow", 52, 58, 59, 61, -5f)
        val near = lab.countIn("sand", 24, 52, 39, 61)
        lab.world.explode(31, 56, 12f, 16)
        lab.world.step()
        assertTrue(lab.countIn("sand", 24, 52, 39, 61) < near * 2 / 3, "sand thrown off: ${lab.countIn("sand", 24, 52, 39, 61)} of $near left")
        assertTrue(lab.count("glass") < 32, "glass broken: ${lab.count("glass")}")
        lab.seconds(1f)
        assertTrue(lab.world.events[Event.IGNITE] > 0, "the wood caught fire")
        assertTrue((0 until lab.world.size).any { lab.world.mat[it].toInt() == lab.id("snow") && lab.world.flags[it].toInt() and Flag.MARK != 0 } ||
            lab.countIn("snow", 52, 58, 59, 61) < 32, "the snow came loose")
    }
}
