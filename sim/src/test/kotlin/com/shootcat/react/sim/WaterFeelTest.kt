package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4b, water: still water has a level surface (3), in one basin and in vessels joined below; what floats
 * lies in it by its density, rocks a little and settles, and the water closes round it (7).
 */
class WaterFeelTest {

    private fun surface(lab: Lab, x: Int): Int = (0 until lab.height).first { lab.at(x, it) == "water" }

    @Test
    fun `a large basin filled from one place has a level surface, within one cell`() {
        val lab = Lab(200, 80)
        repeat(1800) {
            if (it < 1200) lab.world.paint(lab.id("water"), 30, 5, 3, 0.5f)
            lab.world.step()
        }
        val rows = (2..197).map { surface(lab, it) }
        lab.picture("water_basin")
        println("basin: surface rows ${rows.min()}..${rows.max()}")
        assertTrue(rows.max() - rows.min() <= 1, "level to within one cell: rows ${rows.min()}..${rows.max()}")
    }

    @Test
    fun `two vessels joined below come to the same level`() {
        val lab = Lab(120, 80)
        lab.rect("rock", 2, 40, 117, 77)
        lab.rect("air", 10, 20, 40, 70)
        lab.rect("air", 80, 20, 110, 70)
        lab.rect("air", 40, 66, 80, 70)
        // the left vessel full to row 30, the pipe full, the right one empty
        lab.rect("water", 10, 30, 40, 70)
        lab.rect("water", 40, 66, 80, 70)
        lab.seconds(60f)
        val left = (10..40).map { surface(lab, it) }
        val right = (80..110).map { surface(lab, it) }
        lab.picture("water_vessels")
        println("vessels: left ${left.min()}..${left.max()}, right ${right.min()}..${right.max()}")
        assertTrue(maxOf(left.max(), right.max()) - minOf(left.min(), right.min()) <= 1, "the same level: left ${left.min()}..${left.max()}, right ${right.min()}..${right.max()}")
    }

    /** Air cells below the water line beside a body: gaps the water did not close. */
    private fun gaps(lab: Lab, line: Int): Int {
        val w = lab.world
        var n = 0
        for (y in line + 1 until lab.height - 2) for (x in 2 until lab.width - 2) {
            val i = w.index(x, y)
            if (w.mat[i].toInt() != 0) continue
            if (w.bodyOf(i - 1) != 0 || w.bodyOf(i + 1) != 0 || w.bodyOf(i + lab.width) != 0 || w.bodyOf(i - lab.width) != 0) n++
        }
        return n
    }

    @Test
    fun `a wooden raft lies about half in the water, level, rocking a little at first, and the water closes round it`() {
        val lab = Lab(96, 64)
        lab.rect("water", 2, 30, 93, 61)
        lab.rect("wood", 30, 20, 59, 23)
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        repeat(480) {
            lab.world.step()
            if (it in 30..120) {
                val y = lab.world.bodyStates().single().y
                low = minOf(low, y)
                high = maxOf(high, y)
            }
        }
        val raft = lab.world.bodyStates().single()
        val line = surface(lab, 80)
        // how deep it lies: its lower edge (four rows, two below its centre) under the top edge of the water, which is
        // the mean surface of the pool on both sides
        val level = ((2..25) + (64..93)).map { surface(lab, it) }.average() - 0.5
        val under = (raft.y + 2.0 - level) / 4.0
        lab.picture("water_raft")
        println("raft: rocked ${"%.2f".format(high - low)} rows, %.0f %% under the surface, angle ${raft.angle}".format(under * 100))
        assertTrue(high - low > 0.5f, "it rocks a little after it fell in: ${high - low}")
        assertTrue(under in 0.4..0.65, "about half in the water: ${under * 100} %")
        assertTrue(abs(raft.angle) < 1f, "level: ${raft.angle}°")
        assertEquals(0, gaps(lab, line), "no gaps in the water beside it")
    }

    @Test
    fun `a raft with a load near one end leans towards it and settles`() {
        val lab = Lab(96, 64)
        lab.rect("water", 2, 30, 93, 61)
        lab.rect("wood", 30, 25, 59, 28)
        lab.seconds(3f)
        // a stone laid on near the right end, a cell over the deck (dropped from higher onto the very end it may
        // tip off, as the end dips under it: measured from one to six cells high at 50, 52 and 54, it stayed on in
        // ten of twelve, the two at 54, three cells from the end)
        val deck = (0 until lab.height).first { lab.world.bodyOf(lab.world.index(53, it)) != 0 }
        lab.rect("stone", 52, deck - 4, 54, deck - 2)
        lab.seconds(6f)
        val angles = ArrayList<Float>()
        repeat(60) {
            lab.world.step()
            angles += lab.world.bodyStates().first { b -> b.material == lab.id("wood") }.angle
        }
        lab.picture("water_raft_load")
        val stone = lab.world.bodyStates().first { it.material == lab.id("stone") }
        println("raft with load: angle ${angles.min()}..${angles.max()}, stone at ${stone.x},${stone.y}")
        assertTrue(angles.min() > 1f, "it leans towards the load (the right end down): ${angles.min()}°")
        assertTrue(angles.max() - angles.min() < 3f, "and has settled: ${angles.min()}..${angles.max()}°")
        assertTrue(stone.y < surface(lab, 80), "the stone rides on it, above the water")
    }
}
