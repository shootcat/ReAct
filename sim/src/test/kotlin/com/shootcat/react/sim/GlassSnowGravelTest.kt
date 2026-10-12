package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 2d (Leitfaden V3, 4a.2 and 4a.3): glass cracks when quenched, gravel lets water through, snow. */
class GlassSnowGravelTest {

    private fun avgTemp(lab: Lab, name: String): Float {
        val m = lab.id(name)
        var sum = 0f
        var n = 0
        for (i in 0 until lab.world.size) if (lab.world.mat[i].toInt() == m) {
            sum += lab.world.temp[i]
            n++
        }
        return if (n == 0) Float.NaN else sum / n
    }

    @Test
    fun `hot glass quenched with water or ice cracks into shards, glass cooling slowly stays whole`() {
        val water = Lab()
        water.rect("glass", 20, 58, 43, 61, 500f)
        water.rect("water", 20, 40, 43, 45)
        water.seconds(3f)
        water.picture("glass_quenched")
        assertTrue(water.count("glass_shards") > 20, "water on hot glass: ${water.count("glass_shards")} shards")

        val ice = Lab()
        ice.rect("glass", 20, 58, 43, 61, 500f)
        ice.rect("ice", 20, 56, 43, 57, -10f)
        ice.seconds(1f)
        assertTrue(ice.count("glass_shards") > 5, "ice on hot glass: ${ice.count("glass_shards")} shards")

        val slow = Lab()
        slow.rect("glass", 20, 58, 43, 61, 500f)
        slow.seconds(30f)
        assertEquals(96, slow.count("glass"), "cooling in the air it stays whole")
        assertTrue(avgTemp(slow, "glass") < 400f, "and it did cool: ${avgTemp(slow, "glass")}")

        val cold = Lab()
        cold.rect("glass", 20, 58, 43, 61)
        cold.rect("water", 20, 40, 43, 45)
        cold.seconds(3f)
        assertEquals(0, cold.count("glass_shards"), "cold glass in water stays whole")
    }

    @Test
    fun `glass shards melt back into glass`() {
        val lab = Lab()
        lab.rect("lava", 2, 40, 61, 61, 1300f)
        lab.rect("glass_shards", 28, 36, 35, 39)
        lab.seconds(5f)
        assertTrue(lab.count("glass") > 10, "glass again: ${lab.count("glass")}")
    }

    /** A dam of [name] across the floor, water on its left; how much water has come through to the right. */
    private fun dam(name: String, picture: String? = null): Pair<Lab, Int> {
        val lab = Lab()
        lab.rect(name, 26, 40, 37, 61)
        lab.rect("water", 2, 50, 25, 61)
        // a little sand in the water, as a stream carries it
        lab.rect("sand", 20, 59, 25, 61)
        lab.seconds(25f)
        if (picture != null) lab.picture(picture)
        return lab to lab.countIn("water", 38, 0, 61, 61)
    }

    @Test
    fun `water runs through gravel and glass shards, sand stays behind, clay and sand hold water`() {
        val (gravel, through) = dam("gravel", "gravel_dam")
        assertTrue(through > 40, "water through the gravel: $through cells")
        assertEquals(0, gravel.countIn("sand", 38, 0, 61, 61), "the sand stayed on the other side")
        val (_, shards) = dam("glass_shards")
        assertTrue(shards > 40, "water through the glass shards: $shards cells")
        val (_, clay) = dam("clay", "clay_dam")
        assertEquals(0, clay, "clay holds the water back")
        val (_, sand) = dam("sand")
        assertEquals(0, sand, "so does sand")
        // nothing is lost: what the gravel holds in its pores is counted as wet grains
        val wet = (0 until gravel.world.size).count { gravel.world.flags[it].toInt() and Flag.WET != 0 && gravel.world.mat[it].toInt() == gravel.id("gravel") }
        val soaked = (0 until gravel.world.size).count { gravel.world.flags[it].toInt() and Flag.WET != 0 && gravel.world.mat[it].toInt() == gravel.id("sand") }
        assertEquals(24 * 12 - 18, gravel.count("water") + wet + soaked, "water kept")
    }

    @Test
    fun `water seeps through a bed of gravel into a cave below`() {
        val lab = Lab()
        // a rock shelf with a gap; gravel poured over it runs through the gap and piles up in the cave until it
        // closes it, the rest lies on the shelf; then water on top
        lab.rect("rock", 2, 40, 27, 43)
        lab.rect("rock", 36, 40, 61, 43)
        lab.rect("gravel", 2, 30, 61, 39)
        lab.seconds(10f)
        lab.rect("water", 2, 20, 61, 24)
        lab.seconds(20f)
        lab.picture("gravel_bed")
        assertTrue(lab.countIn("water", 2, 44, 61, 61) > 30, "water came through into the cave: ${lab.countIn("water", 2, 44, 61, 61)}")
    }

    @Test
    fun `snow lets heat through only slowly`() {
        // a frozen slab bedded in snow or in rubble, both just below freezing
        fun warming(name: String): Float {
            val lab = Lab()
            lab.rect(name, 2, 50, 61, 61, -5f)
            lab.rect("stone", 10, 44, 53, 49, -40f)
            lab.rect(name, 2, 44, 9, 49, -5f)
            lab.rect(name, 54, 44, 61, 49, -5f)
            lab.rect(name, 2, 38, 61, 43, -5f)
            lab.seconds(5f)
            return avgTemp(lab, "stone") + 40f
        }
        val snow = warming("snow")
        val rubble = warming("rubble")
        assertTrue(snow * 3f < rubble, "the slab warms by ${snow}° under snow, by $rubble° under rubble")
    }

    @Test
    fun `snow under a heavy load turns into ice, loose snow does not`() {
        val lab = Lab()
        lab.rect("snow", 2, 54, 61, 61, -5f)
        lab.rect("stone", 10, 42, 25, 53, -5f)
        lab.seconds(60f)
        lab.picture("snow_compacted")
        val under = lab.countIn("ice", 10, 54, 25, 61)
        // away from the warm rock walls, where melt water may freeze again
        val open = lab.countIn("ice", 34, 54, 54, 61)
        assertTrue(under > 40, "ice under the stone: $under")
        assertEquals(0, open, "none under open sky")
    }

    @Test
    fun `snow on a slope slides down as an avalanche when shaken or warmed, otherwise it stays`() {
        fun slope(): Lab {
            val lab = Lab()
            // a cold mountainside, one cell down every two across, under a cover of snow, cold walls around
            lab.rect("rock", 0, 0, 1, 63, -5f)
            lab.rect("rock", 62, 0, 63, 63, -5f)
            lab.rect("rock", 0, 62, 63, 63, -5f)
            for (x in 2..61) {
                val top = 20 + (x - 2) / 2
                lab.rect("rock", x, top, x, 61, -5f)
                if (x <= 45) lab.rect("snow", x, top - 3, x, top - 1, -5f)
            }
            lab.seconds(1f)
            return lab
        }
        fun high(lab: Lab) = lab.countIn("snow", 2, 0, 24, 40)

        val calm = slope()
        val before = high(calm)
        calm.seconds(10f)
        assertTrue(high(calm) >= before - 3, "left alone it stays: ${high(calm)} of $before")

        val shaken = slope()
        shaken.rect("stone", 6, 2, 11, 6)
        shaken.seconds(10f)
        shaken.picture("avalanche")
        assertTrue(high(shaken) < before / 2, "a stone falling on it sets it off: ${high(shaken)} of $before left up high")

        val warmed = slope()
        warmed.rect("sand", 4, 8, 12, 12, 400f)
        warmed.seconds(10f)
        assertTrue(high(warmed) < before - 10, "hot sand on it sets it off: ${high(warmed)} of $before left up high")
    }
}
