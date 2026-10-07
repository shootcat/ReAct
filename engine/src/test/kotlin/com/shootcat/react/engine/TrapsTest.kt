package com.shootcat.react.engine

import com.shootcat.react.engine.analysis.LevelAnalysis
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every trap the guide names for the test levels (Leitfaden V2, 4.), played: after the wrong move the
 * level can no longer be solved. The proof is a complete search from the trapped state ([DEPTH] more
 * moves, well beyond what any solution needs); a lost protected forest ends the search at once.
 */
class TrapsTest {

    private fun play(id: String, vararg moves: String): Run {
        val live = Levels.live(Levels.level(id))
        var run = live.start()
        for (m in moves) {
            val (obj, xy) = m.split("@")
            val (x, y) = xy.split(",").map { it.toInt() }
            run = assertNotNull(live.play(run, obj, Position(x, y)), "$id: $m is not allowed")
        }
        return run
    }

    private fun assertLost(id: String, run: Run, depth: Int = DEPTH) {
        val level = Levels.level(id)
        assertTrue(run.outcome != Outcome.SUCCESS, "$id is not solved yet")
        assertFalse(LevelAnalysis(level, Levels.engine(level)).solvable(run, depth), "$id can still be solved after the trap")
    }

    // ------------------------------------------------------------------ t_01 Kochstelle

    @Test
    fun `t_01 the walkthrough still works, so the traps below are real detours`() {
        val run = play("t_01", "fire_0_4@5,4", "water_3_5@3,4", "fire_0_4@4,4", "charcoal#1@7,6")
        assertEquals(Outcome.SUCCESS, run.outcome)
    }

    @Test
    fun `t_01 fire straight next to the basin sets the forest alight`() {
        val run = play("t_01", "fire_0_4@7,6")
        assertTrue(run.state.objects.any { it.type == "TREE" && it.state == "BURNING" })
        assertLost("t_01", run)
    }

    @Test
    fun `t_01 wood put out too late is ash`() {
        val run = play("t_01", "fire_0_4@5,4", "fire_0_4@6,5")
        assertTrue(run.state.objects.none { it.type == "WOOD" || it.type == "CHARCOAL" })
        assertTrue(run.state.objects.any { it.type == "ASH" })
        assertLost("t_01", run)
    }

    @Test
    fun `t_01 the puddle that puts out the flame instead of the wood costs the only fire`() {
        val run = play("t_01", "fire_0_4@5,4", "water_3_5@5,3")
        assertTrue(run.state.objects.none { it.type == "FIRE" }, "the fire is gone")
        assertLost("t_01", run)
    }

    private companion object {
        const val DEPTH = 6
    }
}
