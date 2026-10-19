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
