package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Crude oil (Phase 4b, point 10): how it flows, floats, burns, seeps, poisons, gives off gas and leaves tar and soot. */
class OilTest {

    private val materials = Lab.materials

    private fun Lab.burning(name: String): Int {
        val m = id(name)
        var n = 0
        for (i in 0 until world.size) if (world.mat[i].toInt() == m && world.flags[i].toInt() and Flag.BURNING != 0) n++
        return n
    }

    /** Columns between [x0] and [x1] that hold [name] somewhere. */
    private fun Lab.columns(name: String, x0: Int, x1: Int): Int = (x0..x1).count { x -> (0 until height).any { y -> at(x, y) == name } }

    private fun Lab.heat(x0: Int, y0: Int, x1: Int, y1: Int, t: Float = 500f) {
        for (y in y0..y1) for (x in x0..x1) world.setTemperature(x, y, t)
    }

    @Test
    fun `oil flows more slowly than water, and the warmer, the more freely`() {
        fun width(name: String, t: Float, seconds: Float): Int {
            val lab = Lab(120, 40)
            lab.rect(name, 55, 20, 64, 37, t)
            lab.seconds(seconds)
            return (0 until 120).count { lab.at(it, 37) == name }
        }
        val water = width("water", Float.NaN, 1f)
        val cold = width("oil", Float.NaN, 1f)
        val warm = width("oil", 140f, 1f)
        assertTrue(water > 100, "water runs out at once: $water")
        assertTrue(cold < water / 2, "cold oil runs slowly: $cold")
        assertTrue(warm > cold * 3 / 2, "warm oil runs more freely: $warm against $cold")
        // it keeps spreading, slowly
        assertTrue(width("oil", Float.NaN, 4f) > cold * 3 / 2, "cold oil spreads on")
    }

    @Test
    fun `oil floats on water without mixing and spreads into a thin film`() {
        val lab = Lab(120, 40)
        lab.rect("water", 2, 25, 117, 37)
        lab.rect("oil", 55, 15, 64, 22)
        lab.seconds(12f)
        assertEquals(0, lab.countIn("oil", 2, 25, 117, 37), "no oil in the water")
        val thickest = (2..117).maxOf { x -> (0 until 40).count { y -> lab.at(x, y) == "oil" } }
        assertEquals(1, thickest, "a film one cell thin")
        assertTrue(lab.columns("oil", 2, 117) >= 70, "spread out: ${lab.columns("oil", 2, 117)} columns")
        Pictures.write(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }, "oil_film", scale = 5, frame = 40)
    }

    @Test
    fun `a spark does not light oil, flames held to it, embers, a burning plank and lava do`() {
        fun lit(what: String): Boolean {
            val lab = Lab(60, 40)
            lab.rect("stone", 2, 30, 57, 37)
            lab.rect("oil", 10, 26, 49, 29)
            when (what) {
                "spark" -> lab.rect("fire", 30, 25, 30, 25)
                // a single flame shut in over the oil, as long as it lives
                "boxed spark" -> {
                    lab.rect("stone", 29, 23, 31, 23); lab.rect("stone", 29, 24, 29, 25); lab.rect("stone", 31, 24, 31, 25)
                    lab.rect("fire", 30, 25, 30, 25)
                }
                "ember" -> lab.rect("ember", 30, 25, 30, 25)
                "plank" -> lab.rect("wood", 26, 24, 34, 25, 450f)
                "lava" -> lab.rect("lava", 52, 26, 55, 29)
            }
            repeat(15 * 60) {
                if (what == "flame" && it < 60) lab.rect("fire", 29, 25, 31, 25)
                lab.world.step()
                if (lab.burning("oil") > 0) return true
            }
            return false
        }
        assertTrue(!lit("spark"), "a spark is not enough")
        assertTrue(!lit("boxed spark"), "not even a spark kept over it")
        for (w in listOf("flame", "ember", "plank", "lava")) assertTrue(lit(w), "$w lights it")
    }

    @Test
    fun `burning oil gets very hot, burns long with black smoke and leaves soot`() {
        val lab = Lab(60, 50)
        lab.rect("stone", 2, 40, 57, 47)
        lab.rect("oil", 10, 36, 49, 39)
        val oil = lab.count("oil")
        lab.rect("ember", 30, 35, 30, 35)
        var hottest = 0f
        var black = 0
        repeat(20 * 60) {
            lab.world.step()
            for (x in 10..49) for (y in 30..39) if (lab.at(x, y) == "oil") hottest = maxOf(hottest, lab.temp(x, y))
            black = maxOf(black, lab.count("black_smoke"))
        }
        assertTrue(hottest >= 1100f, "very hot: $hottest")
        assertTrue(black > 50, "much black smoke: $black")
        assertTrue(lab.count("oil") > oil * 3 / 4, "after 20 seconds most of it is still there: ${lab.count("oil")} of $oil")
        lab.seconds(70f)
        assertTrue(lab.count("oil") < oil * 3 / 4, "it burns down: ${lab.count("oil")} of $oil")
        assertTrue(lab.count("soot") > 0, "soot is left")
    }

    @Test
    fun `fire runs over the whole film of oil on water`() {
        val lab = Lab(120, 40)
        lab.rect("water", 2, 25, 117, 37)
        lab.rect("oil", 10, 24, 109, 24)
        lab.rect("ember", 12, 23, 12, 23)
        var widest = 0
        repeat(25) {
            lab.seconds(1f)
            val m = lab.id("oil")
            var x0 = 999
            var x1 = -1
            for (i in 0 until lab.world.size) if (lab.world.mat[i].toInt() == m && lab.world.flags[i].toInt() and Flag.BURNING != 0) {
                x0 = minOf(x0, i % 120)
                x1 = maxOf(x1, i % 120)
            }
            widest = maxOf(widest, x1 - x0)
            if (it == 14) Pictures.write(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }, "oil_fire_on_water", scale = 5)
        }
        assertTrue(widest >= 95, "the fire spread over the film: $widest of 100 cells")
    }

    @Test
    fun `water does not put burning oil out, it spreads the burning oil and splashes it up`() {
        val lab = Lab(80, 50)
        lab.rect("stone", 2, 40, 77, 47)
        lab.rect("oil", 20, 36, 59, 39)
        lab.heat(20, 36, 59, 36)
        lab.seconds(3f)
        val before = lab.burning("oil")
        var highest = 99
        repeat(120) {
            lab.rect("water", 35, 5, 44, 6)
            lab.world.step()
            for (y in 0 until 36) if ((20..59).any { x -> lab.at(x, y) == "oil" }) highest = minOf(highest, y)
        }
        lab.seconds(2f)
        assertTrue(lab.burning("oil") >= before / 2, "still burning: ${lab.burning("oil")} (before $before)")
        assertTrue(highest < 30, "burning oil splashed up: row $highest")
        assertTrue(lab.columns("oil", 2, 77) > 45, "spread out: ${lab.columns("oil", 2, 77)} columns, it was 40")
        assertTrue(lab.world.seen[Seen.SPLASH * materials.count + lab.id("oil")] > 0)
    }

    @Test
    fun `sand or earth smothers burning oil where it covers it`() {
        for (cover in listOf("sand", "earth")) {
            val lab = Lab(60, 50)
            lab.rect("stone", 2, 40, 57, 47)
            lab.rect("oil", 15, 37, 44, 39)
            lab.heat(15, 37, 44, 37)
            lab.seconds(1f)
            assertTrue(lab.burning("oil") > 10)
            repeat(30) {
                lab.rect(cover, 2, 20, 57, 21)
                lab.world.step()
            }
            lab.seconds(10f)
            assertEquals(0, lab.burning("oil"), "$cover put the fire out")
            if (cover == "sand") Pictures.write(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }, "oil_smothered", scale = 5)
        }
    }

    @Test
    fun `oil seeps slowly into sand and earth and makes them flammable`() {
        val lab = Lab(60, 40)
        lab.rect("sand", 2, 25, 29, 37)
        lab.rect("earth", 30, 25, 57, 37)
        lab.rect("oil", 10, 22, 49, 24)
        val oil = lab.count("oil")
        lab.seconds(5f)
        assertTrue(lab.count("oil") > oil / 2, "slowly: ${lab.count("oil")} of $oil left after 5 s")
        lab.seconds(55f)
        assertTrue(lab.count("oil") < oil / 4, "seeped in after a minute: ${lab.count("oil")} of $oil left")
        assertTrue(lab.count("oily_sand") > 20 && lab.count("oily_earth") > 20)
        val deepest = (25..37).last { y -> (2..57).any { x -> lab.at(x, y).startsWith("oily") } }
        assertTrue(deepest >= 30, "and down into the ground: row $deepest")
        Pictures.write(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }, "oil_seeped", scale = 5)
        // sand and earth with oil in them burn, where air comes to them; clean sand and earth do not
        val fire = Lab(60, 40)
        fire.rect("oily_sand", 2, 30, 15, 37)
        fire.rect("oily_earth", 16, 30, 29, 37)
        fire.rect("sand", 30, 30, 43, 37)
        fire.rect("earth", 44, 30, 57, 37)
        fire.heat(2, 30, 57, 30)
        fire.seconds(2f)
        assertTrue(fire.burning("oily_sand") > 5 && fire.burning("oily_earth") > 5, "oily ground burns")
        assertEquals(0, fire.burning("sand") + fire.burning("earth"), "clean ground does not")
    }

    @Test
    fun `plants die where oil gets into the earth, fish under a film of oil`() {
        val lab = Lab(80, 40)
        lab.rect("earth", 2, 30, 39, 37)
        lab.rect("grass", 2, 29, 39, 29)
        // the oil lies in a little hollow of rock on the grass
        lab.rect("rock", 9, 26, 9, 28)
        lab.rect("rock", 15, 26, 15, 28)
        lab.rect("oil", 10, 27, 14, 28)
        lab.seconds(20f)
        assertTrue((10..14).none { lab.at(it, 29) == "grass" }, "no grass left under the oil")
        assertTrue((32..39).count { lab.at(it, 29) == "grass" } >= 6, "grass away from it lives")
        // two ponds, a film of oil on one of them
        val pond = Lab(80, 40)
        pond.rect("rock", 39, 10, 40, 37)
        pond.rect("water", 2, 14, 38, 37)
        pond.rect("water", 41, 14, 77, 37)
        for (k in 0 until 4) {
            pond.world.placeAnimal(pond.id("fish"), 8 + k * 7, 30)
            pond.world.placeAnimal(pond.id("fish"), 47 + k * 7, 30)
        }
        pond.rect("oil", 41, 12, 77, 13)
        pond.seconds(25f)
        assertTrue(pond.countIn("fish", 41, 0, 77, 39) == 0, "no fish lives under the film: ${pond.countIn("fish", 41, 0, 77, 39)}")
        assertEquals(4, pond.countIn("fish", 2, 0, 38, 39), "the fish in the clean pond live")
    }

    @Test
    fun `heated oil gives off explosive gas in a closed room, not in the open`() {
        for (closed in listOf(true, false)) {
            val lab = Lab(60, 40)
            lab.rect("rock", 10, 10, 49, 11)
            lab.rect("rock", 10, 10, 11, 30)
            lab.rect("rock", 48, 10, 49, 30)
            lab.rect("rock", 10, 29, 49, 30, 300f)
            if (!closed) lab.rect("air", 12, 10, 47, 11)
            lab.rect("oil", 12, 26, 47, 28, 200f)
            repeat(4) {
                lab.heat(12, 26, 47, 28, 200f)
                lab.seconds(5f)
            }
            val gas = lab.count("oil_gas")
            if (closed) assertTrue(gas > 50, "gas in the closed room: $gas") else assertEquals(0, gas, "none in the open")
            if (closed) {
                val before = lab.world.events[Event.EXPLODE]
                lab.rect("fire", 30, 14, 30, 14)
                lab.seconds(1f)
                assertTrue(lab.world.events[Event.EXPLODE] > before, "the gas goes off")
            }
        }
    }

    @Test
    fun `evaporating oil leaves tar, which clings and burns well`() {
        val lab = Lab(60, 40)
        lab.rect("stone", 2, 30, 57, 37, 250f)
        lab.rect("oil", 10, 28, 49, 29, 250f)
        val oil = lab.count("oil")
        repeat(6) {
            lab.heat(2, 30, 57, 37, 250f)
            lab.seconds(10f)
        }
        assertTrue(lab.count("oil") < oil, "warm oil evaporates: ${lab.count("oil")} of $oil")
        assertTrue(lab.count("tar") > 3, "and leaves tar: ${lab.count("tar")}")
        val cold = Lab(60, 40)
        cold.rect("stone", 2, 30, 57, 37)
        cold.rect("oil", 10, 28, 49, 29)
        cold.seconds(60f)
        assertTrue(cold.count("oil") >= lab.count("oil") + 5, "cold oil evaporates more slowly: ${cold.count("oil")}")
        // tar under a ledge of rock and on a wall clings to them; a grain lying on the edge of tar does not slide off
        val tar = Lab(60, 40)
        tar.rect("rock", 20, 10, 39, 11)
        tar.rect("tar", 24, 12, 35, 12)
        tar.rect("tar", 2, 14, 2, 24)
        tar.rect("tar", 10, 37, 30, 37)
        tar.rect("sand", 30, 36, 30, 36)
        tar.rect("sand", 50, 36, 50, 36)
        tar.rect("stone", 40, 37, 50, 37)
        tar.seconds(5f)
        assertEquals(12, tar.countIn("tar", 24, 12, 35, 12), "the tar hangs under the ledge")
        assertEquals(11, tar.countIn("tar", 2, 14, 2, 24), "and on the wall")
        assertEquals("sand", tar.at(30, 36), "the grain stays on the edge of the tar")
        assertTrue(tar.at(50, 36) != "sand", "on the edge of stone it slides off")
        tar.rect("ember", 20, 36, 20, 36)
        tar.seconds(3f)
        assertTrue(tar.burning("tar") > 0, "tar burns")
    }

    @Test
    fun `oil seeps out of a spring in the rock, a pocket under rock stays shut until it is opened`() {
        val lab = Lab(60, 40)
        lab.rect("rock", 2, 20, 57, 37)
        // a spring in the wall of a hollow
        lab.rect("air", 10, 30, 25, 37)
        lab.rect("oil_spring", 26, 34, 27, 37)
        // a pocket of oil shut in by rock
        lab.rect("oil", 40, 28, 50, 32)
        lab.seconds(20f)
        assertTrue(lab.countIn("oil", 10, 30, 25, 37) > 5, "oil came out of the spring: ${lab.countIn("oil", 10, 30, 25, 37)}")
        assertEquals(55, lab.countIn("oil", 40, 28, 50, 32), "the pocket is still full")
        // opened from above, the oil can be scooped or burnt, it does not run off upwards
        lab.rect("air", 44, 20, 46, 27)
        lab.seconds(5f)
        assertEquals(55, lab.count("oil") - lab.countIn("oil", 10, 30, 25, 37), "nothing ran away")
    }

    @Test
    fun `the bucket takes oil, but not burning oil`() {
        fun session(burning: Boolean): Session = Session(materials, 64, 64, 9L) { w ->
            val rock = materials.id("rock")
            for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
            for (y in 0..61) for (x in listOf(0, 1, 62, 63)) w.set(x, y, rock)
            val oil = materials.id("oil")
            // burning, only the surface of oil burns: a film one cell thin, all of it alight
            val y0 = if (burning) 61 else 56
            for (y in y0..61) for (x in 2..61) w.set(x, y, oil, if (burning) 600f else Float.NaN)
        }
        val s = session(false)
        repeat(30) { s.tick() }
        s.press(30, 57, Session.SCOOP, 4)
        s.release()
        assertEquals(materials.id("oil"), s.bucketMaterial)
        assertTrue(s.bucketAmount > 10, "oil in the bucket: ${s.bucketAmount}")
        val b = session(true)
        repeat(30) { b.tick() }
        b.press(30, 61, Session.SCOOP, 4)
        b.release()
        assertEquals(0, b.bucketAmount, "no burning oil in the bucket")
    }
}
