package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 5a, the inhabitants' ways (Leitfaden V3 7a): they walk, climb low steps, fall, faint from high up and are
 * brought back to the village; they turn back from fire, lava, deep water and drops they see; they run from bodies
 * coming at them and faint when hit; they swim a little way; they walk over bridges and stand on rafts.
 */
class FolkTest {

    private val mt = Lab.materials

    /** A lab 160 × 80 whose ground is solid rock from row [ground] down (the figures' feet in the row above). */
    private fun lab(ground: Int = 70): Lab {
        val lab = Lab(160, 80)
        lab.rect("rock", 2, ground, 157, 77)
        return lab
    }

    private val Lab.folk get() = world.folk

    private fun Lab.only(): Folk.Figure = folk.figures().single()

    /** Runs [steps] steps and keeps what the one figure did: its least and greatest column, its states. */
    private class Watch(var minX: Int = Int.MAX_VALUE, var maxX: Int = Int.MIN_VALUE, val states: MutableSet<Int> = HashSet(), var turns: Int = 0)

    private fun Lab.watch(steps: Int): Watch {
        val w = Watch()
        var dir = only().dir
        repeat(steps) {
            world.step()
            val f = only()
            w.minX = minOf(w.minX, f.x)
            w.maxX = maxOf(w.maxX, f.x)
            w.states += f.state
            if (f.dir != dir) w.turns++
            dir = f.dir
        }
        return w
    }

    @Test
    fun `put down in the ground it stands on top, in the air it falls`() {
        val lab = lab()
        lab.folk.add(30, 74)
        assertEquals(69, lab.folk.figures()[0].y, "lifted out of the rock onto it")
        lab.folk.add(60, 60)
        lab.world.run(200)
        assertEquals(69, lab.folk.figures()[1].y, "fallen onto the ground")
        assertEquals(0, lab.folk.figures()[1].faints, "a fall of nine cells does not hurt")
    }

    @Test
    fun `it walks along and turns back at a wall`() {
        val lab = lab()
        lab.rect("rock", 100, 50, 103, 69)
        lab.folk.add(20, 69)
        val w = lab.watch(60 * 30)
        println("walking: from 20 to ${w.minX}..${w.maxX}, ${w.turns} turns")
        assertTrue(w.maxX in 95..99, "it walks up to the wall: ${w.maxX}")
        assertTrue(w.turns >= 2, "and back and forth between the walls: ${w.turns} turns")
        assertEquals(setOf(Folk.WALK), w.states)
    }

    @Test
    fun `it climbs steps of one and two cells, not of three`() {
        val lab = lab()
        lab.rect("rock", 40, 69, 157, 69)
        lab.rect("rock", 50, 67, 157, 68)
        lab.rect("rock", 60, 64, 157, 66)
        lab.folk.add(20, 69)
        val w = lab.watch(60 * 20)
        println("steps: reached column ${w.maxX}, ${lab.folk.did[Folk.Did.CLIMB]} climbs")
        assertEquals(59, w.maxX, "up the one and the two, not the three")
        assertTrue(lab.folk.did[Folk.Did.CLIMB] >= 2)
    }

    @Test
    fun `a high fall makes it faint, then it is brought back to the village`() {
        val lab = lab()
        lab.folk.setHome(130, 60)
        assertEquals(69, lab.folk.homeY)
        lab.folk.add(40, 20)
        lab.world.run(120)
        val down = lab.only()
        println("after a fall of 49 cells: state ${down.state}, cause ${down.cause}")
        assertEquals(Folk.FAINT, down.state)
        assertEquals(Folk.Cause.FALL, down.cause)
        lab.world.run(Folk.FAINT_STEPS + Folk.BACK_STEPS + 10)
        val back = lab.only()
        assertTrue(back.home && back.state == Folk.WALK, "back in the village, walking: ${back.x}, ${back.y}, state ${back.state}")
        // a small drop does not hurt
        val low = lab()
        low.folk.add(40, 62)
        low.world.run(120)
        assertEquals(0, low.only().faints, "seven cells down is nothing")
    }

    @Test
    fun `it stays in the village once it got there`() {
        val lab = lab()
        lab.folk.setHome(80, 60)
        lab.folk.add(40, 69)
        val w = lab.watch(60 * 30)
        assertTrue(lab.only().home, "it reached the village")
        assertTrue(w.maxX <= 80 + Folk.VILLAGE_HALF, "and stays in it: ${w.maxX}")
    }

    @Test
    fun `it turns back from lava and from a fire it sees`() {
        val lab = lab()
        // a pit of lava in the ground ahead
        lab.rect("air", 90, 70, 98, 73)
        lab.rect("lava", 90, 70, 98, 73)
        lab.folk.add(40, 69)
        val w = lab.watch(60 * 20)
        println("lava: up to column ${w.maxX}, ${lab.folk.did[Folk.Did.SEE]} times seen")
        assertTrue(w.maxX < 90 - 2 && w.turns >= 1 && lab.only().faints == 0, "turned back before the lava: ${w.maxX}")

        val fire = lab()
        // a stack of wood on fire ahead
        fire.rect("wood", 90, 60, 94, 69, 700f)
        fire.folk.add(40, 69)
        val f = fire.watch(60 * 10)
        assertTrue(f.maxX < 88 && fire.only().faints == 0, "turned back before the fire: ${f.maxX}")
    }

    @Test
    fun `it wades through shallow water and turns back from deep water`() {
        val lab = lab()
        lab.rect("air", 60, 70, 70, 71)
        lab.rect("water", 60, 70, 70, 71)
        lab.rect("air", 100, 70, 115, 76)
        lab.rect("water", 100, 70, 115, 76)
        lab.folk.add(40, 69)
        val w = lab.watch(60 * 25)
        println("water: from 40 to ${w.minX}..${w.maxX}, states ${w.states}")
        assertTrue(w.maxX in 72..99, "through the shallow pool, back from the deep one: ${w.maxX}")
        assertTrue(Folk.SWIM !in w.states && lab.only().faints == 0)
    }

    @Test
    fun `it turns back at a deep drop and walks down a low one`() {
        val lab = lab(ground = 60)
        // a dip two deep, then a chasm down to the floor
        lab.rect("air", 50, 60, 54, 61)
        lab.rect("air", 90, 60, 100, 77)
        lab.folk.add(30, 59)
        val w = lab.watch(60 * 25)
        println("drops: from 30 to ${w.minX}..${w.maxX}, ${lab.folk.did[Folk.Did.ABYSS]} times at the edge")
        assertTrue(w.maxX in 60..89, "through the dip, back from the chasm: ${w.maxX}")
        assertEquals(0, lab.only().faints)
    }

    @Test
    fun `fallen into a lake it swims to the shore, too far out it tires, goes under and faints`() {
        // a lake 20 wide: it swims across
        val near = lab(ground = 60)
        near.rect("air", 60, 60, 79, 72)
        near.rect("water", 60, 60, 79, 72)
        near.folk.add(62, 30)
        val w = near.watch(60 * 20)
        println("short swim: states ${w.states}, now at ${near.only().x}, ${near.only().y}")
        assertTrue(Folk.SWIM in w.states, "it swam")
        assertTrue(near.only().state == Folk.WALK && (near.only().x < 60 || near.only().x > 79) && near.only().faints == 0, "and got out")

        // a lake 100 wide: too far
        val far = lab(ground = 60)
        far.rect("air", 20, 60, 140, 72)
        far.rect("water", 20, 60, 140, 72)
        far.folk.setHome(150, 50)
        far.folk.add(80, 40)
        var fainted = false
        var cause = 0
        repeat(60 * 30) {
            far.world.step()
            val f = far.only()
            if (f.state == Folk.FAINT && !fainted) {
                fainted = true
                cause = f.cause
            }
        }
        assertTrue(fainted && cause == Folk.Cause.DROWN, "it tired, went under and fainted: cause $cause")
        assertTrue(far.only().home, "and was brought back to the village")
    }

    @Test
    fun `it runs from a block falling at it, and faints when it cannot get away`() {
        val lab = lab()
        lab.folk.add(62, 69)
        lab.rect("stone", 60, 20, 65, 25)
        lab.world.run(60 * 4)
        println("free to run: at ${lab.only().x}, ${lab.folk.did[Folk.Did.DODGE]} dodges, faints ${lab.only().faints}")
        assertEquals(0, lab.only().faints, "it ran out from under the block")

        // walls on either side, as wide apart as the block: nowhere to go
        val boxed = lab()
        boxed.rect("rock", 59, 60, 59, 69)
        boxed.rect("rock", 66, 60, 66, 69)
        boxed.folk.add(62, 69)
        boxed.rect("stone", 60, 20, 65, 25)
        boxed.world.run(60 * 3)
        val f = boxed.only()
        assertTrue(f.faints == 1 && f.cause == Folk.Cause.HIT, "boxed in, the block hit it: faints ${f.faints}, cause ${f.cause}")
    }

    @Test
    fun `it walks over a bridge across a chasm`() {
        val lab = lab(ground = 60)
        lab.rect("air", 80, 60, 99, 77)
        // a plank from one side to the other, lying on the rock at both ends
        lab.rect("wood", 76, 59, 103, 59)
        lab.folk.add(40, 58)
        val w = lab.watch(60 * 20)
        println("bridge: from 40 to ${w.maxX}")
        assertTrue(w.maxX > 103, "across: ${w.maxX}")
        assertEquals(0, lab.only().faints)
    }

    @Test
    fun `standing on a raft they weigh it down and stay on it, a raft too small tips them off`() {
        // a raft of 16 × 3 cells of wood (as heavy as 24 cells of water) with two figures (8) on it: a third deeper
        fun raft(figures: List<Int>, x0: Int = 70, x1: Int = 85, y0: Int = 47): Pair<Float, Lab> {
            val lab = lab(ground = 50)
            lab.rect("air", 20, 50, 140, 70)
            lab.rect("water", 20, 50, 140, 70)
            lab.rect("wood", x0, y0, x1, 49)
            lab.world.run(240)
            for (x in figures) lab.folk.add(x, 40)
            lab.world.run(300)
            var sum = 0f
            repeat(600) {
                lab.world.step()
                sum += lab.world.bodyStates().first { it.material == mt.id("wood") }.y
            }
            return sum / 600 to lab
        }
        val (bare, _) = raft(emptyList())
        val (loaded, lab) = raft(listOf(74, 81))
        println("raft: floats at row $bare, with two figures at row $loaded; they at " + lab.folk.figures().joinToString { "${it.x},${it.y} state ${it.state}" })
        assertTrue(loaded > bare + 0.2f, "their weight presses it deeper: $bare, $loaded")
        assertTrue(lab.folk.figures().all { it.state == Folk.WALK && it.y < 50 && it.x in 70..85 }, "they are still on it")
        // a raft of 8 × 2 cells tips over when one goes to its edge, and it ends up in the water
        val (_, small) = raft(listOf(77), x0 = 74, x1 = 81, y0 = 48)
        val f = small.only()
        println("small raft: the figure at ${f.x},${f.y} state ${f.state}")
        assertTrue(f.state == Folk.SWIM || f.x !in 74..81 || f.faints > 0, "tipped off the small raft")
    }

    @Test
    fun `the same world gives the same figures, and an undo takes them back`() {
        fun run(): Long {
            val lab = lab()
            lab.rect("rock", 100, 50, 103, 69)
            lab.folk.setHome(30, 60)
            for (x in listOf(20, 40, 60, 80)) lab.folk.add(x, 40)
            lab.rect("stone", 58, 10, 63, 15)
            lab.world.run(60 * 10)
            return lab.world.hash()
        }
        assertEquals(run(), run())
        val lab = lab()
        val before = lab.world.snapshot()
        lab.folk.add(50, 69)
        lab.world.run(60)
        lab.world.restore(before)
        assertEquals(0, lab.folk.count)
    }
}
