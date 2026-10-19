package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4c, rain and snow: a column of cloud rains out of its underside once it holds [World.RAIN_DEPTH] cells
 * (the threshold), the harder the denser it is; in frost it snows; the rain takes its water out of the cloud; a
 * thunderstorm draws in moist air and keeps its clouds deep while it lasts.
 */
class RainTest {

    private val mt = Lab.materials

    private fun lab(ambient: Float = 20f): Lab {
        val lab = Lab(96, 80)
        lab.world.setAmbient(ambient)
        lab.world.setWeather(cloudBase = 20)
        lab.rect("rock", 0, 74, 95, 79)
        return lab
    }

    private fun drops(lab: Lab) = lab.world.seen[Seen.RAIN * mt.count + mt.id("cloud")]
    private fun flakes(lab: Lab) = lab.world.seen[Seen.SNOWFALL * mt.count + mt.id("cloud")]

    @Test
    fun `a dense cloud rains within seconds, a thin one does not rain at all`() {
        val dense = lab()
        dense.rect("cloud", 20, 10, 75, 20)
        dense.world.wakeAll()
        dense.seconds(3f)
        val thin = lab()
        thin.rect("cloud", 20, 18, 75, 20)
        thin.world.wakeAll()
        thin.seconds(3f)
        println("in 3 s: a cloud 11 deep ${drops(dense)} drops, 3 deep ${drops(thin)}")
        assertTrue(drops(dense) > 30, "the dense cloud rains: ${drops(dense)} drops in 3 s")
        assertEquals(0, drops(thin), "a cloud thinner than the threshold does not rain")
    }

    @Test
    fun `the denser the cloud, the harder it rains`() {
        fun rain(depth: Int): Int {
            val lab = lab()
            lab.rect("cloud", 20, 21 - depth, 75, 20)
            lab.world.wakeAll()
            lab.seconds(2f)
            return drops(lab)
        }
        val five = rain(5)
        val eight = rain(8)
        val twelve = rain(12)
        println("drops in 2 s by depth: 5 → $five, 8 → $eight, 12 → $twelve")
        assertTrue(five < eight && eight < twelve, "$five < $eight < $twelve")
    }

    @Test
    fun `in frost a cloud snows, and a cloud painted into frost is as cold as the air`() {
        val lab = lab(ambient = -10f)
        lab.world.paint(mt.id("cloud"), 48, 14, 6)
        assertEquals(-10f, lab.world.temp[lab.world.index(48, 14)], 0.01f, "painted at the temperature of the air")
        lab.rect("cloud", 20, 10, 75, 20, -10f)
        lab.world.wakeAll()
        lab.seconds(3f)
        println("in frost: ${flakes(lab)} flakes, ${drops(lab)} drops, ${lab.count("snow")} snow")
        assertTrue(flakes(lab) > 30, "it snows: ${flakes(lab)}")
        assertEquals(0, drops(lab), "no rain in frost")
    }

    @Test
    fun `snow out of a cloud drifts down slowly, rain and a lump of snow fall as before`() {
        /** Steps from the first flake or drop out of the cloud until it lies on the floor (row 73), 52 rows below. */
        fun fallTime(lab: Lab, name: String, kind: Int): Int {
            var born = -1
            for (n in 1..3000) {
                lab.world.run(1)
                if (born < 0 && lab.world.seen[kind * mt.count + mt.id("cloud")] > 0) born = n
                if (born >= 0 && lab.countIn(name, 0, 73, 95, 73) > 0) return n - born
            }
            return -1
        }
        val cold = lab(ambient = -10f)
        cold.rect("cloud", 20, 10, 75, 20, -10f)
        cold.world.wakeAll()
        val flake = fallTime(cold, "snow", Seen.SNOWFALL)
        val warm = lab()
        warm.rect("cloud", 20, 10, 75, 20)
        warm.world.wakeAll()
        val drop = fallTime(warm, "water", Seen.RAIN)
        val lump = lab(ambient = -10f)
        lump.rect("snow", 28, 17, 32, 21, -10f)
        lump.world.wakeAll()
        var heap = 0
        while (lump.countIn("snow", 0, 73, 95, 73) == 0 && heap < 3000) {
            lump.world.run(1)
            heap++
        }
        println("52 rows down: a flake out of the cloud in $flake steps, a drop in $drop, a lump of snow in $heap")
        assertTrue(flake > 3 * heap, "the flake drifts down far slower than the lump: $flake against $heap steps")
        assertTrue(drop < 40 && flake > 3 * drop, "the drop falls fast, the flake slowly: $drop and $flake steps")
    }

    @Test
    fun `rain takes its water out of the cloud, it makes none`() {
        val lab = lab()
        lab.rect("cloud", 20, 8, 75, 20)
        lab.world.wakeAll()
        val before = lab.count("cloud")
        lab.seconds(6f)
        val after = lab.count("cloud") + lab.count("water")
        println("cloud before $before, cloud and water after ${lab.count("cloud")} + ${lab.count("water")}")
        assertTrue(lab.count("water") > 50, "it rained")
        assertTrue(after <= before, "no more water than there was cloud: $before before, $after after")
    }

    @Test
    fun `a thunderstorm keeps its clouds deep while it lasts, without it they rain thin`() {
        fun depths(storm: Boolean): List<Float> {
            val lab = lab()
            lab.rect("cloud", 4, 7, 91, 20)
            if (storm) lab.world.startStorm(60 * 200)
            lab.world.wakeAll()
            return (1..6).map {
                lab.seconds(30f)
                var n = 0
                for (x in 10..85) n += lab.world.cloudDensity(x)
                n / 76f
            }
        }
        val calm = depths(storm = false)
        val storm = depths(storm = true)
        println("cloud depth every 30 s: without a storm $calm, in a storm $storm")
        assertTrue(storm.all { it >= 6f }, "the storm keeps its cloud deep: $storm")
        assertTrue(calm.last() < storm.last(), "without it the cloud rains thinner: $calm")
    }
}
