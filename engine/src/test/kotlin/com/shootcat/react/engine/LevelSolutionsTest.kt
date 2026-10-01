package com.shootcat.react.engine

import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.SolutionKind
import com.shootcat.react.engine.model.SolutionKind.MINIMAL
import com.shootcat.react.engine.model.SolutionKind.OVERRIDE
import com.shootcat.react.engine.model.SolutionKind.STANDARD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plays every level the way a player would (live: move, watch, move) and checks solutions and failures. */
class LevelSolutionsTest {

    private fun p(x: Int, y: Int) = Position(x, y)

    private fun solved(level: String, vararg moves: Pair<String, Position>, expect: Set<SolutionKind>): Levels.Attempt {
        val a = Levels.attempt(level, *moves)
        assertTrue(a.solved, "$level ${moves.toList()} should be solved but was ${a.run.outcome}")
        assertEquals(expect, a.solutions, "$level ${moves.toList()}")
        return a
    }

    private fun fails(level: String, vararg moves: Pair<String, Position>) {
        val a = Levels.attempt(level, *moves)
        assertFalse(a.solved, "$level ${moves.toList()} should fail")
    }

    @Test
    fun `level 0 - melt water presses the button, then douses the fire`() {
        val a = solved("level_00", "fire_1" to p(5, 4), expect = setOf(STANDARD))
        assertTrue(a.rules.containsAll(setOf("heat_melts_ice", "water_douses_fire", "water_triggers_button")))
        assertEquals("OUT", a.run.state.objectById("fire_1")?.state)
    }

    @Test
    fun `level 0 - a fire on top sinks into its own melt water and covers the button`() {
        fails("level_00", "fire_1" to p(6, 3))
        fails("level_00", "fire_1" to p(4, 4))
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
    fun `level 4 - one portion of steam is not enough for the piston`() {
        fails("level_04", "fire_1" to p(6, 4))
        solved("level_04", "fire_1" to p(6, 4), "fire_2" to p(6, 4), expect = setOf(STANDARD))
        solved("level_04", "fire_2" to p(5, 4), expect = setOf(MINIMAL, OVERRIDE))
    }

    @Test
    fun `level 5 - steam, piston, hatch, stone, plate, door`() {
        solved("level_05", "fire_2" to p(5, 4), expect = setOf(STANDARD, MINIMAL))
        solved("level_05", "ice_block_2" to p(10, 1), "fire_2" to p(5, 4), expect = setOf(STANDARD))
    }

    @Test
    fun `level 6 - water or pressure`() {
        solved("level_06", "fire_1" to p(2, 1), expect = setOf(STANDARD, MINIMAL))
        solved("level_06", "fire_1" to p(10, 4), "fire_2" to p(10, 4), expect = setOf(OVERRIDE))
        fails("level_06", "fire_1" to p(3, 2))
    }

    @Test
    fun `level 7 - melt, boil, push, drop, displace`() {
        solved("level_07", "ice_block_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
        solved("level_07", "fire_1" to p(1, 2), "ice_block_1" to p(3, 2), expect = setOf(STANDARD))
        fails("level_07", "ice_block_1" to p(2, 3))
    }

    @Test
    fun `level 8 - water lifts the wooden plug off the button`() {
        val a = solved("level_08", "fire_1" to p(6, 2), expect = setOf(STANDARD, MINIMAL))
        assertTrue(a.run.state.objectById("wood_plug")!!.position.y < 5, "the plug floated up")
        solved("level_08", "ice_block_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
    }

    @Test
    fun `level 9 - both doors need their own chain`() {
        solved("level_09", "fire_1" to p(1, 3), "fire_2" to p(9, 3), expect = setOf(STANDARD))
        // Left where it is, the second bowl is doused by the melt water and blocks the way to the button.
        fails("level_09", "fire_1" to p(1, 3))
    }

    @Test
    fun `level 10 - metal carries the heat to the ice`() {
        solved("level_10", "fire_1" to p(4, 2), expect = setOf(STANDARD, MINIMAL))
        solved("level_10", "metal_1" to p(3, 2), "metal_2" to p(4, 2), expect = setOf(STANDARD, OVERRIDE))
        fails("level_10", "metal_1" to p(4, 2))
    }

    @Test
    fun `level 11 - the beam burns away and the stone drops`() {
        val a = solved("level_11", "fire_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
        assertEquals(null, a.run.state.objectById("wood_beam"), "the beam burnt down")
        // Next to the ice, the fire melts it and its own melt water puts it out before the beam catches fire.
        fails("level_11", "ice_block_1" to p(4, 2), "fire_1" to p(3, 2))
    }

    @Test
    fun `level 12 - boiling water pushes the gate away`() {
        solved("level_12", "fire_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL))
        solved("level_12", "metal_1" to p(3, 2), expect = setOf(STANDARD, MINIMAL, OVERRIDE))
        fails("level_12", "metal_1" to p(4, 2))
    }

    @Test
    fun `solutions finish in reasonable time`() {
        val a = Levels.attempt("level_09", "fire_1" to p(1, 3), "fire_2" to p(9, 3))
        assertTrue(a.steps <= 40, "took ${a.steps} steps")
        assertTrue(Levels.attempt("level_11", "fire_1" to p(3, 2)).steps <= 30)
    }
}
