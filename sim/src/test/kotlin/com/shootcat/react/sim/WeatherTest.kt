package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3c: atmosphere and weather. Steam out in the open cools as it rises and turns into cloud at the
 * condensation height; in a closed cave it condenses on cold surfaces as before. Dense clouds rain, in frost
 * they snow, and rain from boiling salt water is fresh. The wind carries gases, ash, snow and, in a gale,
 * dry sand, and warm air with them, so fire runs downwind. A wide, deep bank of cloud is a thunderstorm:
 * lightning strikes the highest or the most conductive point.
 */
class WeatherTest {

    private fun meanX(lab: Lab, name: String): Float {
        val m = lab.id(name)
        var sum = 0L
        var n = 0
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == m) {
            sum += x
            n++
        }
        return if (n == 0) Float.NaN else sum.toFloat() / n
    }

    @Test
    fun `steam in the open rises to the condensation height and turns into cloud, not into water`() {
        val clouds = Lab()
        clouds.world.setWeather(cloudBase = 12)
        clouds.rect("steam", 20, 44, 43, 55)
        val legacy = Lab()
        legacy.rect("steam", 20, 44, 43, 55)
        clouds.seconds(15f)
        legacy.seconds(15f)
        clouds.picture("weather_steam_to_cloud")
        val cloud = clouds.count("cloud")
        println("open steam after 15 s: with a condensation height ${clouds.count("cloud")} cloud, ${clouds.count("water")} water, ${clouds.count("steam")} steam; without ${legacy.count("water")} water, ${legacy.count("steam")} steam")
        assertTrue(cloud > 150, "cloud: $cloud")
        // a layer round the condensation height: what rises later stacks up under what is there
        assertTrue(clouds.countIn("cloud", 0, 20, 63, 63) < cloud / 5, "the clouds hang at the condensation height: ${clouds.countIn("cloud", 0, 20, 63, 63)} of $cloud lower down")
        assertTrue(clouds.countIn("water", 0, 0, 63, 43) < 10, "no water condensed in the air on the way up")
        assertEquals(0, legacy.count("cloud"), "without a condensation height there are no clouds")
        assertTrue(legacy.count("water") > 100, "and the steam condenses as before: ${legacy.count("water")}")
    }

    @Test
    fun `in a closed cave steam condenses on the cold walls as before, even with a condensation height`() {
        val lab = Lab()
        lab.world.setWeather(cloudBase = 12)
        lab.rect("rock", 10, 30, 53, 33)
        lab.rect("rock", 10, 34, 13, 55)
        lab.rect("rock", 50, 34, 53, 55)
        lab.rect("rock", 10, 56, 53, 61)
        lab.rect("steam", 20, 44, 43, 50)
        lab.world.wakeAll()
        lab.seconds(20f)
        assertEquals(0, lab.count("cloud"), "no cloud in the cave")
        assertTrue(lab.count("water") > 100, "the steam has condensed: ${lab.count("water")} water")
    }

    @Test
    fun `a dense cloud rains, in frost it snows`() {
        val rain = Lab()
        rain.world.setWeather(cloudBase = 22)
        rain.rect("cloud", 6, 10, 57, 22, 15f)
        val snow = Lab()
        snow.world.setAmbient(-8f)
        snow.rect("rock", 0, snow.height - 2, snow.width - 1, snow.height - 1)
        snow.world.setWeather(cloudBase = 22)
        snow.rect("cloud", 6, 10, 57, 22, -8f)
        rain.seconds(30f)
        snow.seconds(30f)
        rain.picture("weather_rain")
        snow.picture("weather_snow")
        println("a cloud of 676 cells after 30 s: ${rain.count("cloud")} cloud left, ${rain.count("water")} water; in frost ${snow.count("snow")} snow")
        assertTrue(rain.countIn("water", 0, 40, 63, 63) > 50, "rain on the ground: ${rain.countIn("water", 0, 40, 63, 63)}")
        assertTrue(snow.countIn("snow", 0, 40, 63, 63) > 50, "snow on the ground: ${snow.countIn("snow", 0, 40, 63, 63)}")
        // the bank is a thunderstorm as well: where lightning strikes, a little snow melts
        assertTrue(snow.count("water") < snow.count("snow") / 10, "it snows, it does not rain: ${snow.count("water")} water")
    }

    @Test
    fun `steam from boiling salt water rains down fresh`() {
        val lab = Lab(128, 80)
        // a light wind takes the cloud off the basin, so its rain falls on dry stone beside it
        lab.world.setWeather(cloudBase = 10, wind = 0.15f)
        // salt water kept boiling over magma, in a stone basin on the left
        lab.rect("magma", 12, 74, 31, 77)
        lab.rect("stone", 10, 70, 33, 73)
        lab.rect("stone", 10, 60, 11, 69)
        lab.rect("stone", 32, 60, 33, 69)
        lab.rect("saltwater", 12, 62, 31, 69, 99f)
        lab.world.wakeAll()
        lab.seconds(180f)
        lab.picture("weather_salt_rain")
        val fresh = lab.countIn("water", 36, 0, 125, 77)
        val salty = lab.countIn("saltwater", 2, 0, 9, 77) + lab.countIn("saltwater", 34, 0, 125, 77)
        println("boiling salt water after 180 s: ${lab.count("cloud")} cloud, $fresh fresh water beside the basin, $salty salt water outside, ${lab.count("salt")} salt")
        assertTrue(fresh > 20, "the cloud rained fresh water beside the basin: $fresh")
        assertEquals(0, salty, "nothing salty fell outside the basin")
    }

    @Test
    fun `the wind moves a cloud as a whole, it is not torn into a streak`() {
        val lab = Lab(160, 64)
        lab.world.setWeather(cloudBase = 20, wind = 0.5f)
        lab.rect("cloud", 20, 12, 49, 20)
        lab.world.wakeAll()
        val cells = lab.count("cloud")
        val before = meanX(lab, "cloud")
        lab.seconds(10f)
        val moved = meanX(lab, "cloud") - before
        var x0 = Int.MAX_VALUE
        var x1 = -1
        val id = lab.id("cloud")
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == id) {
            x0 = minOf(x0, x)
            x1 = maxOf(x1, x)
        }
        println("cloud of $cells cells in wind 0.5 for 10 s: moved ${"%.1f".format(moved)} columns, now ${x1 - x0 + 1} wide (was 30), ${lab.count("cloud")} cloud and ${lab.count("water")} rain")
        assertTrue(moved in 20f..40f, "about 3 columns a second: $moved")
        assertTrue(x1 - x0 + 1 <= 45, "the bank keeps together: ${x1 - x0 + 1} wide")
    }

    @Test
    fun `the wind carries smoke and clouds, snow and ash, dry sand only in a gale`() {
        fun drift(name: String, wind: Float, wet: Boolean = false): Float {
            val lab = Lab()
            lab.world.setWeather(wind = wind)
            if (lab.materials.kind[lab.id(name)] == Kind.GAS) lab.rect(name, 26, 30, 37, 40) else {
                lab.rect(name, 20, 56, 43, 61)
                if (wet) for (y in 56..61) for (x in 20..43) lab.world.setWet(x, y, true)
            }
            val before = meanX(lab, name)
            // smoke rises out of the open lab within a few seconds: one second for gases
            lab.seconds(if (lab.materials.kind[lab.id(name)] == Kind.GAS) 1f else 3f)
            return meanX(lab, name) - before
        }
        val smoke = drift("smoke", 0.6f)
        val calm = drift("smoke", 0f)
        val snow = drift("snow", 0.7f)
        val ash = drift("ash", 0.4f)
        val sandWind = drift("sand", 0.7f)
        val sandGale = drift("sand", 1f)
        val wetSand = drift("sand", 1f, wet = true)
        println("drift (cells; gases in 1 s, the rest in 3 s): smoke ${"%.1f".format(smoke)} (calm ${"%.1f".format(calm)}), snow ${"%.1f".format(snow)}, ash ${"%.1f".format(ash)}, sand at 0.7 ${"%.2f".format(sandWind)}, in a gale ${"%.2f".format(sandGale)}, wet ${"%.2f".format(wetSand)}")
        assertTrue(smoke > calm + 5f, "smoke goes with the wind")
        assertTrue(snow > 0.5f, "snow drifts")
        assertTrue(ash > 0.5f, "ash blows away")
        assertTrue(sandWind < 0.05f, "a fresh wind leaves sand lying")
        assertTrue(sandGale > 0.2f, "a gale carries it")
        assertTrue(wetSand < 0.05f, "wet sand stays")
    }

    @Test
    fun `fire runs downwind`() {
        fun burnt(wind: Float): Pair<Int, Int> {
            val lab = Lab(96, 40)
            lab.world.setWeather(wind = wind)
            lab.rect("grass", 4, 37, 91, 37)
            // dead leaves: they lift only in a wind above 0.4, so 0.35 leaves them lying and only the fire moves
            lab.rect("dead_plant", 4, 36, 91, 36)
            lab.rect("dead_plant", 46, 36, 49, 36, 400f)
            lab.world.wakeAll()
            lab.seconds(6f)
            fun gone(x0: Int, x1: Int): Int {
                var n = 0
                for (x in x0..x1) if (lab.world.material(x, 36) != lab.id("dead_plant")) n++
                return n
            }
            return gone(4, 45) to gone(50, 91)
        }
        val (calmLeft, calmRight) = burnt(0f)
        val (left, right) = burnt(0.35f)
        println("dead leaves burnt in 6 s, calm: $calmLeft left, $calmRight right; wind to the right: $left left, $right right")
        assertTrue(right > left + 5, "downwind it burns further: $right against $left")
    }

    private fun stormLab(): Lab {
        val lab = Lab(96, 64)
        lab.world.setWeather(cloudBase = 16)
        lab.rect("cloud", 4, 4, 91, 16)
        return lab
    }

    private fun untilStrike(lab: Lab, seconds: Int = 40): Boolean {
        repeat(seconds * 60) {
            lab.world.step()
            if (lab.world.events[Event.LIGHTNING] > 0) return true
        }
        return false
    }

    @Test
    fun `lightning strikes the metal rather than the higher sand around`() {
        val lab = stormLab()
        lab.rect("sand", 2, 44, 93, 61)
        lab.rect("metal", 60, 40, 61, 43)
        lab.world.wakeAll()
        assertTrue(untilStrike(lab), "a bank of cloud 88 wide and 13 deep is a thunderstorm")
        val x = lab.world.boltX[lab.world.boltLength - 1]
        val hot = maxOf(lab.temp(60, 40), lab.temp(61, 40))
        println("struck at column $x, metal ${"%.0f".format(hot)} °C")
        assertTrue(x in 59..62, "it struck the metal: column $x")
        assertTrue(hot > 600f, "and heated it: $hot")
    }

    @Test
    fun `lightning runs down a metal rod and goes to ground at its foot`() {
        /** Burning wood half a second after the first stroke, with a rod standing on the woodpile or in the earth beside it. */
        fun fire(onPile: Boolean): Int {
            val lab = stormLab()
            lab.rect("earth", 2, 50, 93, 61)
            lab.rect("wood", 40, 44, 55, 49)
            if (onPile) lab.rect("metal", 47, 30, 47, 43) else lab.rect("metal", 60, 30, 60, 49)
            lab.world.wakeAll()
            assertTrue(untilStrike(lab), "a thunderstorm")
            val x = lab.world.boltX[lab.world.boltLength - 1]
            assertTrue(x == if (onPile) 47 else 60, "it struck the rod: column $x")
            lab.seconds(0.5f)
            lab.picture(if (onPile) "weather_rod_on_pile" else "weather_rod_grounded")
            var burning = 0
            val wood = lab.id("wood")
            for (i in 0 until lab.world.size) if (lab.world.mat[i].toInt() == wood && lab.world.flags[i].toInt() and Flag.BURNING != 0) burning++
            return burning
        }
        val onPile = fire(onPile = true)
        val grounded = fire(onPile = false)
        println("rod struck: standing on the woodpile $onPile cells of wood burning, standing in the earth beside it $grounded")
        assertTrue(onPile > 0, "the current went into the wood under the rod and kindled it")
        assertEquals(0, grounded, "a rod in the earth takes the lightning away")
    }

    @Test
    fun `lightning in sand makes a tube of glass, in wood it kindles fire`() {
        val sand = stormLab()
        sand.rect("sand", 2, 40, 93, 61)
        sand.world.wakeAll()
        assertTrue(untilStrike(sand))
        sand.seconds(0.2f)
        sand.picture("weather_fulgurite")
        val glass = sand.count("glass")
        println("fulgurite: $glass cells of glass")
        assertTrue(glass >= 3, "a fulgurite: $glass")

        val wood = stormLab()
        wood.rect("rock", 2, 50, 93, 61)
        wood.rect("wood", 40, 36, 43, 49)
        wood.world.wakeAll()
        assertTrue(untilStrike(wood))
        val x = wood.world.boltX[wood.world.boltLength - 1]
        wood.seconds(1f)
        var burning = 0
        for (y in 0 until wood.height) for (xx in 0 until wood.width) if (wood.world.flags[wood.world.index(xx, y)].toInt() and Flag.BURNING != 0 && wood.at(xx, y) == "wood") burning++
        println("struck the post at column $x: $burning cells of wood burning")
        assertTrue(x in 40..43, "the post is the highest point: $x")
        assertTrue(burning > 0, "and it burns")
    }

    @Test
    fun `without a condensation height clouds dissolve`() {
        val lab = Lab()
        lab.rect("cloud", 10, 10, 50, 20)
        lab.seconds(20f)
        assertTrue(lab.count("cloud") < 20, "${lab.count("cloud")} cloud left")
    }

    @Test
    fun `gusts are a fixed rhythm, and the weather goes back with an undo`() {
        val w = World(64, 64, Lab.materials, 3L)
        w.setWeather(wind = 0.3f, gust = 0.2f, gustSteps = 100)
        val winds = FloatArray(200) { w.step(); w.windNow }
        val again = World(64, 64, Lab.materials, 99L)
        again.setWeather(wind = 0.3f, gust = 0.2f, gustSteps = 100)
        for (k in 0 until 200) {
            again.step()
            assertEquals(winds[k], again.windNow, "step $k")
        }
        assertTrue(winds.max() > 0.45f && winds.min() < 0.15f, "the gusts come and go: ${winds.min()} .. ${winds.max()}")
        val snap = w.snapshot()
        w.setWeather(cloudBase = 5, wind = -0.8f, gust = 0f)
        w.changeAmbient(-10f)
        w.restore(snap)
        assertEquals(-1, w.cloudBase)
        assertEquals(0.3f, w.wind)
        assertEquals(Lab.materials.ambient, w.ambient)
    }
}

/** Weather on the meadow (3c), for the report: a thunderstorm in a fresh wind, caught with a bolt, and the rain after it. */
class WeatherPicturesTest {
    @Test
    fun `a thunderstorm over the meadow`() {
        val materials = MaterialTable.load()
        val size = WorldSize.MEDIUM
        val s = Session(materials, size.width, size.height, Terrain.SEED) { Template.MEADOW.build(it) }
        val backdrop = Backdrop.of(s.world)
        repeat(120) { s.tick() }
        s.setWeather(cloudBase = s.world.cloudBase, wind = 0.4f, gust = 0.2f, random = false, ambient = s.world.ambient)
        s.storm(size.width / 2)
        val area = intArrayOf(size.width / 2 - 160, 0, size.width / 2 + 160, size.height - 1)
        var caught = false
        var steps = 0
        while (!caught && steps < 60 * 60) {
            s.tick()
            steps++
            if (s.world.boltAge == 8) {
                Pictures.write(s.world, backdrop, "weather_storm_bolt", frame = steps, area = area)
                caught = true
            }
        }
        assertTrue(caught, "lightning within a minute of the storm")
        repeat(600) { s.tick() }
        Pictures.write(s.world, backdrop, "weather_storm_rain", frame = steps + 600, area = area)
    }
}
