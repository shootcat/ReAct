package com.shootcat.react.engine

import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.SolutionKind.MINIMAL
import com.shootcat.react.engine.model.SolutionKind.OVERRIDE
import com.shootcat.react.engine.model.SolutionKind.STANDARD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plays the beta levels the way a player would and checks solutions and their classification. */
class LevelSolutionsTest {

    private fun p(x: Int, y: Int) = Position(x, y)

    // Level 0 – FIRE + ICE -> WATER -> door

    @Test
    fun `level 0 fire beside the ice opens the door`() {
        val a = Levels.attempt("level_00", "fire_1" to p(5, 4))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD), a.solutions)
        val discovered = a.result.allEvents.filter { it.positive }.map { it.ruleId }.toSet()
        assertEquals(
            setOf("fire_melts_ice", "fire_evaporates_water", "water_presses_button", "button_opens_door"),
            discovered,
        )
    }

    @Test
    fun `level 0 the fire then boils the meltwater away – the button stays pressed`() {
        val a = Levels.attempt("level_00", "fire_1" to p(5, 4))
        val last = a.result.frames.last().state
        assertEquals("PRESSED", last.objectById("button_1")?.state)
        assertTrue(a.result.allEvents.any { it.ruleId == "fire_evaporates_water" })
        assertEquals(1, last.objects.count { it.type == "STEAM" })
    }

    @Test
    fun `level 0 fire above the ice works too`() {
        assertTrue(Levels.attempt("level_00", "fire_1" to p(6, 3)).solved)
    }

    @Test
    fun `level 0 fire out of reach does nothing`() {
        val a = Levels.attempt("level_00", "fire_1" to p(4, 4))
        assertEquals(Outcome.STABLE, a.result.outcome)
    }

    // Level 1 – WATER -> BUTTON

    @Test
    fun `level 1 melting the ice on the ledge lets the water find the button`() {
        val a = Levels.attempt("level_01", "fire_1" to p(1, 2))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD, MINIMAL), a.solutions)
    }

    @Test
    fun `level 1 fire from above works as well`() {
        assertTrue(Levels.attempt("level_01", "fire_1" to p(2, 1)).solved)
    }

    @Test
    fun `level 1 fire on the wrong side blocks the water`() {
        val a = Levels.attempt("level_01", "fire_1" to p(3, 2))
        assertFalse(a.solved)
        assertTrue(a.result.allEvents.any { it.ruleId == "fire_melts_ice" })
    }

    @Test
    fun `level 1 bringing the ice to the fire is the system override`() {
        val a = Levels.attempt("level_01", "ice_block_1" to p(5, 5))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD, MINIMAL, OVERRIDE), a.solutions)
    }

    @Test
    fun `level 1 ice on the button is not enough`() {
        assertFalse(Levels.attempt("level_01", "ice_block_1" to p(7, 6)).solved)
    }

    @Test
    fun `level 1 melting the ice right on the button is a plain standard solution`() {
        val a = Levels.attempt("level_01", "ice_block_1" to p(7, 6), "fire_1" to p(7, 5))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD), a.solutions)
    }

    // Level 2 – HEAVY STONE -> PRESSURE PLATE, or water

    @Test
    fun `level 2 melting the support drops the stone onto the plate`() {
        for (fire in listOf(p(5, 2), p(7, 2))) {
            val a = Levels.attempt("level_02", "fire_1" to fire)
            assertTrue(a.solved, "fire at $fire")
            assertEquals(setOf(STANDARD), a.solutions, "fire at $fire")
        }
    }

    @Test
    fun `level 2 moving the dam away floods the shaft instead`() {
        val a = Levels.attempt("level_02", "ice_block_2" to p(2, 2))
        assertTrue(a.solved)
        assertEquals(setOf(OVERRIDE), a.solutions)
    }

    @Test
    fun `level 2 melting the dam boils the water that flows under the fire`() {
        val a = Levels.attempt("level_02", "fire_1" to p(8, 2))
        assertFalse(a.solved)
        assertTrue(a.result.allEvents.count { it.ruleId == "fire_evaporates_water" } >= 2)
    }

    @Test
    fun `level 2 both ways at once still count as the stone`() {
        val a = Levels.attempt("level_02", "ice_block_2" to p(2, 2), "fire_1" to p(5, 2))
        assertTrue(a.solved)
    }

    @Test
    fun `level 2 fire inside the channel blocks everything`() {
        assertFalse(Levels.attempt("level_02", "fire_1" to p(6, 3)).solved)
        assertFalse(Levels.attempt("level_02", "fire_1" to p(7, 3)).solved)
    }

    @Test
    fun `level 2 a single unit of water is too light`() {
        val level = Levels.level("level_02")
        val oneWater = level.initialState().let { s ->
            s.copy(objects = s.objects.filter { it.id != "water_2" && it.id != "water_3" })
        }
        val result = Levels.simulate(level, oneWater.withObjectMoved("ice_block_2", p(2, 2))!!)
        assertFalse(result.outcome == Outcome.SUCCESS)
        assertEquals("UP", result.frames.last().state.objectById("plate_1")?.state)
    }
}
