package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 5b, what the inhabitants need (Leitfaden V3 7a): warmth, water and air. In the cold they freeze, walk slower
 * and at last faint; freezing, they go up to a fire and warm themselves, or kindle firewood at a flame that is
 * already there (never from nothing); thirsty, they drink fresh water they come to, never salt water; in smoke or
 * firedamp they run for clean air and faint if there is none. All by rules, without chance.
 */
class FolkNeedsTest {

    /** A lab 160 × 80 whose ground is solid rock from row [ground] down, all of it at [ambient] °C. */
    private fun lab(ground: Int = 70, ambient: Float = 20f): Lab {
        val lab = Lab(160, 80)
        lab.world.setAmbient(ambient)
        lab.rect("rock", 2, ground, 157, 77, t = ambient)
        return lab
    }

    private val Lab.folk get() = world.folk

    private fun Lab.only(): Folk.Figure = folk.figures().single()

    /** Runs until [what] holds for the one figure, at most [max] steps: the steps it took, or -1. */
    private fun Lab.until(max: Int, what: (Folk.Figure) -> Boolean): Int {
        for (s in 0 until max) {
            if (what(only())) return s
            world.step()
        }
        return if (what(only())) max else -1
    }

    /** Cells the one figure walks in [steps] steps (back and forth counted). */
    private fun Lab.walked(steps: Int): Int {
        var n = 0
        var x = only().x
        repeat(steps) {
            world.step()
            n += Math.abs(only().x - x)
            x = only().x
        }
        return n
    }

    private fun Lab.burningCells(name: String, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val m = id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) {
            val i = world.index(x, y)
            if (world.mat[i].toInt() == m && world.flags[i].toInt() and Flag.BURNING != 0) n++
        }
        return n
    }

    // ------------------------------------------------------------------ warmth

    @Test
    fun `in hard frost it freezes, walks slower and faints after 20 seconds`() {
        val lab = lab(ambient = -20f)
        lab.folk.add(20, 69)
        val fresh = lab.walked(240)
        val show = 240 + lab.until(3000) { it.cold >= Folk.COLD_SHOW }
        assertEquals(Folk.Need.COLD, lab.folk.needs(0) and Folk.Need.COLD, "it shows that it freezes")
        val slow = show + lab.until(3000) { it.cold >= Folk.COLD_SLOW }
        val frozen = lab.walked(240)
        val faint = slow + 240 + lab.until(3000) { it.state == Folk.FAINT }
        println("frost -20 °C: freezes after ${show / 60f} s, slower after ${slow / 60f} s, faints after ${faint / 60f} s; walked $fresh cells in 4 s fresh, $frozen frozen")
        assertEquals(300, show, "at -20 °C two counts a step: freezing after 5 s")
        assertEquals(600, slow)
        assertEquals(1200, faint, "and fainted after 20 s")
        assertEquals(Folk.Cause.COLD, lab.only().cause)
        assertTrue(frozen * 2 in fresh - 2..fresh + 2, "half pace: $fresh then $frozen")
        // brought back warm
        lab.world.run(Folk.FAINT_STEPS + Folk.BACK_STEPS + 2)
        assertEquals(0, lab.only().cold.coerceAtMost(10) / 10, "warm again when brought back: ${lab.only().cold}")
    }

    @Test
    fun `at 0 degrees it freezes half as fast, at 20 not at all`() {
        val cold = lab(ambient = 0f)
        cold.folk.add(20, 69)
        val show = cold.until(5000) { it.cold >= Folk.COLD_SHOW }
        val faint = show + cold.until(5000) { it.state == Folk.FAINT }
        val mild = lab(ambient = 20f)
        mild.folk.add(20, 69)
        mild.world.run(60 * 60)
        println("frost 0 °C: freezes after ${show / 60f} s, faints after ${faint / 60f} s; at 20 °C after 60 s: cold ${mild.only().cold}")
        assertEquals(600, show)
        assertEquals(2400, faint)
        assertEquals(0, mild.only().cold)
    }

    @Test
    fun `freezing it goes up to a fire, warms itself and goes again`() {
        val lab = lab(ambient = -20f)
        // a campfire: a small heap of burning wood
        lab.rect("wood", 100, 66, 102, 69, t = 400f)
        lab.folk.add(70, 69)
        lab.folk.setNeeds(1, cold = 700)
        val reach = lab.until(900) { it.state == Folk.WARM }
        val stood = lab.only().x
        val warm = lab.until(900) { it.cold == 0 }
        lab.world.run(240)
        val after = lab.only()
        println("fire: stops at column $stood (the pile from 100) after ${reach / 60f} s, warm after ${warm / 60f} s more; then at ${after.x}, state ${after.state}, dir ${after.dir}")
        assertTrue(reach in 0..900, "it went up to the fire")
        assertTrue(stood in 80..96, "up to the hot air round the fire, a little way off: column $stood")
        assertTrue(warm in 1..900, "warm again: $warm steps")
        assertTrue(after.x < stood && after.state == Folk.WALK, "and then it went away from the fire again")
        assertTrue(lab.folk.did[Folk.Did.WARMED] >= 1)
        assertEquals(0, after.faints)
    }

    @Test
    fun `not freezing it turns back from the same fire`() {
        val lab = lab(ambient = 20f)
        lab.rect("wood", 100, 66, 102, 69, t = 400f)
        lab.folk.add(70, 69)
        var nearest = 0
        repeat(900) {
            lab.world.step()
            nearest = maxOf(nearest, lab.only().x)
        }
        println("fire, not cold: came up to column $nearest")
        assertTrue(100 - nearest > Folk.WARM_REACH, "it keeps its distance: ${100 - nearest}")
        assertEquals(0, lab.folk.did[Folk.Did.WARMED])
    }

    @Test
    fun `freezing it kindles firewood at a flame that is there, never from nothing`() {
        fun scene(embers: Boolean, ambient: Float): Lab {
            val lab = lab(ambient = ambient)
            lab.rect("wood", 80, 68, 82, 69)
            if (embers) lab.rect("wood", 96, 62, 97, 69, t = 400f)
            lab.folk.add(60, 69)
            if (ambient < 0f) lab.folk.setNeeds(1, cold = 700)
            return lab
        }
        val lab = scene(embers = true, ambient = -20f)
        val kindled = lab.until(1800) { it.state == Folk.KINDLE }
        val at = lab.only().x
        lab.world.run(90)
        val burning = lab.burningCells("wood", 80, 60, 82, 69)
        val warmed = lab.until(1800) { it.state == Folk.WARM || it.cold == 0 }
        println("kindling: knelt at column $at after ${kindled / 60f} s, 1.5 s later $burning cells of the log burn; warming ${warmed / 60f} s after that, cold ${lab.only().cold}")
        assertTrue(kindled in 0..1800, "it kindled")
        assertEquals(1, lab.folk.did[Folk.Did.KINDLE], "once")
        assertTrue(burning > 0, "the log burns")
        assertTrue(warmed in 0..1800, "and it warms itself at it")

        // no flame anywhere: the wood stays as it is
        val none = scene(embers = false, ambient = -20f)
        none.world.run(60 * 15)
        println("no flame: kindled ${none.folk.did[Folk.Did.KINDLE]} times, ${none.burningCells("wood", 80, 60, 82, 69)} cells burn, cold ${none.only().cold}")
        assertEquals(0, none.folk.did[Folk.Did.KINDLE])
        assertEquals(0, none.burningCells("wood", 80, 60, 82, 69))

        // not cold: it leaves the wood alone
        val mild = scene(embers = true, ambient = 20f)
        mild.world.run(60 * 30)
        assertEquals(0, mild.folk.did[Folk.Did.KINDLE], "only when it freezes")
    }

    // ------------------------------------------------------------------ water

    /** A pond two cells deep in the ground (columns 80 to 95), of [liquid]. */
    private fun pond(liquid: String): Lab {
        val lab = lab()
        lab.rect("air", 80, 70, 95, 71)
        lab.rect(liquid, 80, 70, 95, 71)
        lab.folk.add(60, 69)
        return lab
    }

    @Test
    fun `thirsty it drinks fresh water it comes to, one cell`() {
        val lab = pond("water")
        lab.folk.setNeeds(1, thirst = Folk.THIRSTY + 100)
        val before = lab.count("water")
        val drank = lab.until(900) { it.thirst < Folk.THIRSTY }
        val after = lab.count("water")
        println("drinking: after ${drank / 60f} s at column ${lab.only().x}, water $before -> $after cells")
        assertTrue(drank in 1..900, "it drank")
        assertEquals(0, lab.only().thirst.coerceAtMost(1))
        assertEquals(before - 1, after, "one cell of water less")

        val notThirsty = pond("water")
        notThirsty.world.run(60 * 20)
        assertEquals(0, notThirsty.folk.did[Folk.Did.DRINK], "it drinks only when thirsty")
    }

    @Test
    fun `it never drinks salt water`() {
        val lab = pond("saltwater")
        lab.folk.setNeeds(1, thirst = Folk.THIRSTY + 100)
        val before = lab.count("saltwater")
        lab.world.run(60 * 20)
        println("salt water: drank ${lab.folk.did[Folk.Did.DRINK]}, would not ${lab.folk.did[Folk.Did.NO_SALT]} times, thirst ${lab.only().thirst}")
        assertEquals(0, lab.folk.did[Folk.Did.DRINK])
        assertTrue(lab.folk.did[Folk.Did.NO_SALT] > 0)
        assertTrue(lab.only().thirst >= Folk.THIRSTY)
        assertEquals(before, lab.count("saltwater"))
    }

    @Test
    fun `without water it faints of thirst after five minutes, in the village the well helps`() {
        val lab = lab()
        lab.folk.add(60, 69)
        lab.folk.setNeeds(1, thirst = Folk.THIRST_MAX - 60)
        val faint = lab.until(200) { it.state == Folk.FAINT }
        assertEquals(60, faint)
        assertEquals(Folk.Cause.THIRST, lab.only().cause)

        val home = lab()
        home.folk.setHome(60, 69)
        home.folk.add(60, 69)
        home.folk.setNeeds(1, thirst = Folk.THIRSTY + 3000)
        val quenched = home.until(1000) { it.thirst == 0 }
        println("thirst: faints after ${Folk.THIRST_MAX / 60} s without water; in the village thirst gone after ${quenched / 60f} s")
        assertTrue(quenched in 1..400)
    }

    // ------------------------------------------------------------------ air

    /** A gallery six cells high (rows 64 to 69) under rock, closed at column 120, open to the left. */
    private fun gallery(closedLeft: Boolean): Lab {
        val lab = lab()
        lab.rect("rock", 2, 40, 157, 63, t = 20f)
        lab.rect("rock", 120, 64, 157, 69, t = 20f)
        if (closedLeft) lab.rect("rock", 2, 64, 69, 69, t = 20f)
        return lab
    }

    @Test
    fun `in firedamp it runs for clean air`() {
        val lab = gallery(closedLeft = false)
        lab.rect("firedamp", 70, 64, 119, 67)
        lab.folk.add(100, 69)
        var least = Folk.BREATH_MAX
        var shown = false
        repeat(60 * 10) {
            lab.world.step()
            least = minOf(least, lab.only().breath)
            if (lab.folk.needs(0) and Folk.Need.AIR != 0) shown = true
        }
        val f = lab.only()
        println("firedamp, way out: at least $least of ${Folk.BREATH_MAX} breath, now at column ${f.x} with ${f.breath}, ran ${lab.folk.did[Folk.Did.FLEE]} times, fainted ${f.faints}")
        assertTrue(shown, "it showed it was short of air")
        assertTrue(lab.folk.did[Folk.Did.FLEE] >= 1, "it turned to run")
        assertEquals(0, f.faints, "it got out")
        assertTrue(least > Folk.BREATH_MAX / 2, "in time: $least")
    }

    @Test
    fun `shut in with firedamp it faints after 8 seconds`() {
        val lab = gallery(closedLeft = true)
        lab.rect("firedamp", 70, 64, 119, 69)
        lab.folk.add(100, 69)
        val faint = lab.until(1200) { it.state == Folk.FAINT }
        println("firedamp, no way out: fainted after ${faint / 60f} s")
        assertEquals(Folk.BREATH_MAX, faint)
        assertEquals(Folk.Cause.AIR, lab.only().cause)
    }

    @Test
    fun `in smoke in the open it runs out of it`() {
        val lab = lab()
        lab.rect("smoke", 60, 50, 80, 69)
        lab.folk.add(70, 69)
        repeat(60 * 6) { lab.world.step() }
        val f = lab.only()
        println("smoke: at column ${f.x} with breath ${f.breath}, ran ${lab.folk.did[Folk.Did.FLEE]} times, fainted ${f.faints}")
        assertEquals(0, f.faints)
        assertEquals(Folk.BREATH_MAX, f.breath, "breathing freely again")
    }

    // ------------------------------------------------------------------ rules, not chance

    @Test
    fun `needs are part of the world, the same every time and taken back with undo`() {
        fun run(): Lab {
            val lab = lab(ambient = -20f)
            lab.rect("wood", 80, 68, 82, 69)
            lab.rect("wood", 96, 62, 97, 69, t = 400f)
            lab.folk.add(60, 69)
            lab.folk.add(40, 69)
            lab.folk.setNeeds(1, cold = 700, thirst = Folk.THIRSTY)
            lab.world.run(900)
            return lab
        }
        val a = run()
        val b = run()
        assertEquals(a.world.hash(), b.world.hash(), "same world, same needs")
        val saved = a.world.snapshot()
        val before = a.folk.figures().map { Triple(it.cold, it.thirst, it.breath) }
        a.world.run(300)
        a.world.restore(saved)
        assertEquals(before, a.folk.figures().map { Triple(it.cold, it.thirst, it.breath) })
        a.world.run(300)
        b.world.run(300)
        assertEquals(a.world.hash(), b.world.hash(), "and on alike after undo")
    }
}
