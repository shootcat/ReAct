package com.shootcat.react.engine

import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.SolutionKind.MINIMAL
import com.shootcat.react.engine.model.SolutionKind.OVERRIDE
import com.shootcat.react.engine.model.SolutionKind.STANDARD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plays every level the way a player would and checks solutions, failures and classification. */
class LevelSolutionsTest {

    private fun p(x: Int, y: Int) = Position(x, y)

    private fun solved(level: String, vararg moves: Pair<String, Position>, expect: Set<com.shootcat.react.engine.model.SolutionKind>) {
        val a = Levels.attempt(level, *moves)
        assertTrue(a.solved, "$level ${moves.toList()} should be solved but was ${a.result.outcome}")
        assertEquals(expect, a.solutions, "$level ${moves.toList()}")
    }

    private fun fails(level: String, vararg moves: Pair<String, Position>) {
        val a = Levels.attempt(level, *moves)
        assertFalse(a.solved, "$level ${moves.toList()} should fail")
    }

    @Test
    fun `level 0 - melt water presses the button, then douses the fire`() {
        val a = Levels.attempt("level_00", "fire_1" to p(5, 4))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD), a.solutions)
        val rules = a.result.allEvents.filter { it.positive }.map { it.ruleId }.toSet()
        assertTrue(rules.containsAll(setOf("fire_melts_ice", "water_douses_fire", "water_triggers_button")))
        assertEquals("OUT", a.result.frames.last().state.objectById("fire_1")?.state)
    }

    @Test
    fun `level 0 - a fire on top sinks into its own melt water and covers the button`() {
        fails("level_00", "fire_1" to p(6, 3))
        assertEquals(Outcome.STABLE, Levels.attempt("level_00", "fire_1" to p(4, 4)).result.outcome)
    }

    @Test
    fun `level 1 - the melt water runs off the ledge and finds the button`() {
        solved("level_01", "fire_1" to p(1, 2), expect = setOf(STANDARD, MINIMAL))
        solved("level_01", "ice_block_1" to p(4, 3), expect = setOf(STANDARD, MINIMAL, OVERRIDE))
        fails("level_01", "fire_1" to p(3, 2))
    }

    @Test
    fun `level 2 - stone or water make the weight`() {
        solved("level_02", "stone_1" to p(5, 2), expect = setOf(STANDARD))
        solved("level_02", "fire_1" to p(5, 2), expect = setOf(OVERRIDE))
        fails("level_02", "fire_1" to p(6, 1))
    }

    @Test
    fun `level 3 - sinking objects raise the water, floating ice does not`() {
        solved("level_03", "stone_1" to p(4, 2), expect = setOf(STANDARD, OVERRIDE))
        solved("level_03", "fire_1" to p(4, 2), expect = setOf(STANDARD))
        fails("level_03", "ice_block_1" to p(4, 2))
    }

    @Test
    fun `level 4 - two portions of steam lift the piston`() {
        solved("level_04", "fire_1" to p(6, 4), "fire_2" to p(6, 5), expect = setOf(STANDARD))
        solved("level_04", "fire_2" to p(5, 4), expect = setOf(MINIMAL, OVERRIDE))
        fails("level_04", "fire_1" to p(6, 5))
    }

    @Test
    fun `level 5 - steam, piston, hatch, stone, plate, door`() {
        solved("level_05", "fire_2" to p(5, 4), expect = setOf(STANDARD, MINIMAL))
        solved("level_05", "fire_1" to p(5, 4), "fire_2" to p(1, 4), expect = setOf(STANDARD))
    }

    @Test
    fun `level 6 - water or pressure`() {
        solved("level_06", "fire_1" to p(2, 1), expect = setOf(STANDARD, MINIMAL))
        solved("level_06", "fire_1" to p(10, 4), "fire_2" to p(10, 5), expect = setOf(STANDARD, OVERRIDE))
        fails("level_06", "fire_1" to p(3, 2))
    }

    @Test
    fun `level 7 - melt, boil, push, drop, displace`() {
        solved("level_07", "ice_block_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
        solved("level_07", "fire_1" to p(1, 2), "ice_block_1" to p(3, 2), expect = setOf(STANDARD))
        fails("level_07", "ice_block_1" to p(2, 3))
    }

    @Test
    fun `level 8 - water lifts the ice off the button`() {
        val a = Levels.attempt("level_08", "fire_1" to p(6, 2))
        assertTrue(a.solved)
        assertEquals(setOf(STANDARD, MINIMAL), a.solutions)
        val plug = a.result.frames.last().state.objectById("ice_plug")!!
        assertTrue(plug.position.y < 5, "the plug floated up")
        solved("level_08", "ice_block_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
    }

    @Test
    fun `level 9 - both doors need their own chain`() {
        solved("level_09", "fire_1" to p(1, 3), "fire_2" to p(9, 3), expect = setOf(STANDARD))
        // Left where it is, the second bowl is doused by the melt water and blocks the way to the button.
        fails("level_09", "fire_1" to p(1, 3))
    }

    @Test
    fun `every solution finishes in reasonable time`() {
        val a = Levels.attempt("level_09", "fire_1" to p(1, 3), "fire_2" to p(9, 3))
        assertTrue(a.result.lastIndex <= 40, "took ${a.result.lastIndex} steps")
    }
}
