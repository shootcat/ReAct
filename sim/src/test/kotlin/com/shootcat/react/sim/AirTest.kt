package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 3b.9: air carries heat. Warm air rises, open air gives its warmth back to the atmosphere, and air shut
 * in keeps it: a gallery closed with stone or wood and a source of heat inside warms up, an open one hardly.
 */
class AirTest {

    /**
     * Rock from row 20 down, a gallery x 8..54, rows 40..45 through it, a shaft from the sky down to its left
     * end, and at its right end magma in the floor (x 46..52): heat from the deep. With [door] the shaft is
     * stopped at rows 30..32.
     */
    private fun gallery(door: String?): Lab {
        val lab = Lab()
        lab.rect("rock", 2, 20, 61, 61)
        lab.rect("air", 8, 40, 54, 45)
        lab.rect("air", 8, 20, 11, 39)
        lab.rect("magma", 46, 46, 52, 46)
        if (door != null) lab.rect(door, 8, 30, 11, 32)
        lab.world.wakeAll()
        return lab
    }

    private fun meanAir(lab: Lab, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        var sum = 0f
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (lab.at(x, y) == "air") {
            sum += lab.temp(x, y)
            n++
        }
        return sum / n
    }

    @Test
    fun `a gallery shut with stone warms up, an open one hardly`() {
        val open = gallery(null)
        val shut = gallery("stone")
        open.seconds(30f)
        shut.seconds(30f)
        open.picture("air_gallery_open")
        shut.picture("air_gallery_shut")
        val ambient = Lab.materials.ambient
        val o = meanAir(open, 8, 40, 44, 45)
        val s = meanAir(shut, 8, 40, 44, 45)
        println("gallery after 30 s: open %.1f °C, shut %.1f °C (ambient %.1f)".format(o, s, ambient))
        assertTrue(shut.world.shutIn(20, 42), "the air in the shut gallery counts as shut in")
        assertTrue(!open.world.shutIn(20, 42), "the open gallery is open air")
        assertTrue(s > ambient + 40f, "the shut gallery warms up: $s")
        assertTrue(s > o + 30f, "much warmer than the open one: shut $s, open $o")
    }

    @Test
    fun `behind the door the warmth gets all the way along the gallery`() {
        val open = gallery(null)
        val shut = gallery("stone")
        open.seconds(30f)
        shut.seconds(30f)
        // the far end, 30 cells and more from the heat
        val o = meanAir(open, 8, 40, 16, 45)
        val s = meanAir(shut, 8, 40, 16, 45)
        println("far end of the gallery after 30 s: open %.1f °C, shut %.1f °C".format(o, s))
        assertTrue(s > o + 20f, "shut $s, open $o")
    }

    @Test
    fun `a wooden door holds the warmth in as well`() {
        val shut = gallery("wood")
        shut.seconds(30f)
        val s = meanAir(shut, 8, 40, 44, 45)
        println("gallery shut with wood after 30 s: %.1f °C".format(s))
        assertTrue(s > Lab.materials.ambient + 40f, "the gallery behind the wooden door warms up: $s")
    }

    @Test
    fun `warm air rises in the open and cools there`() {
        val lab = Lab()
        lab.rect("rock", 2, 50, 61, 61)
        lab.rect("magma", 28, 50, 34, 50)
        lab.world.wakeAll()
        lab.seconds(10f)
        val above = lab.temp(31, 40)
        val beside = lab.temp(10, 40)
        val high = lab.temp(31, 10)
        println("open air over magma after 10 s: 10 cells above %.1f, as high but 21 cells aside %.1f, 40 cells above %.1f".format(above, beside, high))
        assertTrue(above > beside + 5f, "the warmth goes up: above $above, aside $beside")
        assertTrue(high < above, "and cools on its way: $high")
        val far = lab.temp(8, 10)
        assertTrue(far < Lab.materials.ambient + 10f, "far away and high up the air stays nearly as it was: $far")
    }
}
