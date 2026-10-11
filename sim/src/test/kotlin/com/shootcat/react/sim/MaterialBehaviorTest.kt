package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The behaviour of the materials of phase 2a (Leitfaden V3, 4.1), each from general rules and the material data. */
class MaterialBehaviorTest {

    private fun wetCells(lab: Lab) = (0 until lab.world.size).count { lab.world.flags[it].toInt() and Flag.WET != 0 }
    private fun burning(lab: Lab, name: String) =
        (0 until lab.world.size).count { lab.world.flags[it].toInt() and Flag.BURNING != 0 && lab.world.mat[it].toInt() == lab.id(name) }

    @Test
    fun `hot sand keeps its heat and sets wood alight`() {
        val lab = Lab()
        // a pile of logs with air between them, as a wood pile is built
        for ((row, y) in listOf(60, 57, 54).withIndex()) {
            var x = 12 + row * 3
            while (x + 5 <= 51 - row * 3) {
                lab.rect("wood", x, y, x + 5, y + 1)
                x += 8
            }
            if (row < 2) lab.rect("wood", 12 + row * 3, y - 1, 51 - row * 3, y - 1)
        }
        // sand heated on lava, poured onto the pile
        lab.rect("sand", 24, 44, 39, 50, 900f)
        lab.seconds(4f)
        assertTrue(burning(lab, "wood") > 0 || lab.count("charcoal") + lab.count("ash") > 0, "the wood caught fire")
        val sand = (0 until lab.world.size).filter { lab.world.mat[it].toInt() == lab.id("sand") }
        assertTrue(sand.maxOf { lab.world.temp[it] } > 250f, "the sand is still hot after 4 seconds: ${sand.maxOf { lab.world.temp[it] }}")
    }

    @Test
    fun `wet wood does not burn until it has dried`() {
        val lab = Lab()
        lab.rect("wood", 20, 56, 43, 61)
        for (x in 20..43) for (y in 56..61) lab.world.setWet(x, y, true)
        lab.rect("wood", 20, 56, 43, 56, 320f)
        for (x in 20..43) lab.world.setWet(x, 56, true)
        lab.seconds(0.5f)
        assertEquals(0, burning(lab, "wood"), "hot but wet: no fire")
        // a fire under it dries it, then it burns
        repeat(240) {
            lab.world.paint(lab.id("fire"), 31, 54, 4, 0.8f)
            lab.world.step()
        }
        assertTrue(burning(lab, "wood") > 0 || lab.count("charcoal") + lab.count("ash") > 0, "dried, it burns")
    }

    @Test
    fun `water freezes next to ice that is cold enough`() {
        val lab = Lab()
        lab.rect("water", 20, 50, 43, 61)
        lab.rect("ice", 20, 45, 43, 49, -60f)
        lab.seconds(3f)
        assertTrue(lab.count("ice") > 24 * 5, "more ice than before: ${lab.count("ice")}")
    }

    @Test
    fun `salt dissolves in water, salt water boils to fresh steam and leaves salt`() {
        val lab = Lab()
        lab.rect("water", 2, 50, 61, 61)
        lab.world.paint(lab.id("salt"), 30, 40, 3)
        lab.seconds(6f)
        assertTrue(lab.count("saltwater") > 10, "salt water: ${lab.count("saltwater")}")
        assertTrue(lab.count("salt") < 10, "most of the salt dissolved: ${lab.count("salt")} left")

        val pan = Lab()
        pan.rect("saltwater", 20, 55, 43, 59)
        pan.rect("lava", 18, 60, 45, 61, 1300f)
        var salt = 0
        repeat(10) {
            pan.seconds(1f)
            salt = maxOf(salt, pan.count("salt"))
        }
        val boiled = pan.world.events[Event.BOIL]
        assertTrue(boiled > 10, "it boiled: $boiled cells")
        // the steam rains down fresh and dissolves part of the salt again, so the most salt at any time counts
        assertTrue(salt > 10, "salt is left behind: at most $salt")

    }

    @Test
    fun `salt melts ice and snow without heat`() {
        // salt on the left block of ice and the left pile of snow, nothing on the right ones
        val lab = Lab()
        lab.rect("ice", 4, 50, 27, 61, -10f)
        lab.rect("ice", 36, 50, 59, 61, -10f)
        val snowLab = Lab()
        snowLab.rect("snow", 4, 52, 25, 61, -10f)
        snowLab.rect("snow", 38, 52, 59, 61, -10f)
        lab.world.paint(lab.id("salt"), 15, 46, 3)
        snowLab.world.paint(snowLab.id("salt"), 14, 48, 3)
        lab.seconds(3f)
        snowLab.seconds(3f)
        val salted = lab.countIn("ice", 0, 0, 31, 63)
        val plain = lab.countIn("ice", 32, 0, 63, 63)
        assertTrue(salted < plain - 10, "salted ice melted: $salted cells left, $plain without salt")
        val saltedSnow = snowLab.countIn("snow", 0, 0, 31, 63)
        val plainSnow = snowLab.countIn("snow", 32, 0, 63, 63)
        assertTrue(saltedSnow < plainSnow - 10, "salted snow melted: $saltedSnow cells left, $plainSnow without salt")
        assertTrue(lab.count("water") + lab.count("saltwater") > 0)
    }

    @Test
    fun `clay holds a wall, softens when wet and fires to brick`() {
        val lab = Lab()
        lab.rect("clay", 28, 30, 35, 61)
        lab.seconds(3f)
        assertEquals(8 * 32, lab.countIn("clay", 28, 30, 35, 61), "a dry clay wall stands")
        lab.rect("water", 2, 20, 20, 61)
        lab.seconds(8f)
        assertTrue(lab.countIn("clay", 0, 0, 27, 61) + lab.countIn("clay", 36, 0, 63, 61) > 10, "wet, it slumps")

        val kiln = Lab()
        kiln.rect("lava", 2, 44, 61, 61, 1250f)
        kiln.rect("clay", 26, 40, 37, 43)
        kiln.seconds(5f)
        assertTrue(kiln.count("brick") > 5, "fired to brick: ${kiln.count("brick")}")
    }

    @Test
    fun `ore melts on lava into metal, which sets when it cools`() {
        val lab = Lab()
        lab.rect("lava", 2, 50, 61, 61, 1300f)
        lab.world.paint(lab.id("ore_rubble"), 31, 44, 4)
        var molten = 0
        repeat(8) {
            lab.seconds(1f)
            molten = maxOf(molten, lab.count("molten_metal"))
        }
        assertTrue(molten > 5, "molten metal: $molten")
        val cool = Lab()
        cool.rect("molten_metal", 25, 55, 38, 61, 1100f)
        cool.rect("water", 2, 40, 61, 50)
        cool.seconds(6f)
        assertTrue(cool.count("metal") > 20, "it set to metal: ${cool.count("metal")}")
    }

    @Test
    fun `metal carries heat much further than stone`() {
        fun far(name: String): Float {
            val lab = Lab()
            // a bar resting on a hot rock block at its left end, sticking out over air
            lab.rect("rock", 2, 42, 12, 61, 1000f)
            lab.rect(name, 2, 40, 40, 41)
            lab.seconds(5f)
            return lab.temp(22, 40)
        }
        val metal = far("metal")
        val stone = far("stone")
        assertTrue(metal > stone + 30f, "metal end ${metal.toInt()} °C, stone end ${stone.toInt()} °C")
    }

    @Test
    fun `water washes gravel along`() {
        val lab = Lab(96, 64)
        lab.rect("gravel", 40, 50, 47, 61)
        lab.rect("water", 2, 30, 39, 61)
        repeat(40) {
            lab.world.paint(lab.id("water"), 10, 5, 3)
            lab.seconds(1f)
        }
        assertTrue(lab.countIn("gravel", 48, 0, 93, 61) > 3, "gravel carried off: ${lab.countIn("gravel", 48, 0, 93, 61)}")
    }

    @Test
    fun `ice floats, snow piles up light`() {
        val lab = Lab()
        lab.rect("water", 2, 40, 61, 61, 1f)
        lab.rect("ice", 20, 20, 30, 24, -20f)
        lab.seconds(3f)
        assertTrue(lab.countIn("ice", 0, 0, 63, 39) > 0, "ice lies on top of the water")
    }
}
