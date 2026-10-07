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

    // ------------------------------------------------------------------ t_02 Süßwasser

    @Test
    fun `t_02 the walkthrough still works`() {
        val run = play("t_02", "wood_0_9@3,5", "fire_0_5@2,5", "seawater_5_6@4,5", "fire_0_5@3,5", "charcoal#1@1,10")
        assertEquals(Outcome.SUCCESS, run.outcome)
    }

    @Test
    fun `t_02 salt water poured into the basin spoils it for good`() {
        val run = play("t_02", "seawater_5_6@5,9")
        assertTrue(run.state.objects.any { it.type == "SEAWATER" && it.position.y == 10 && it.position.x in 6..7 })
        assertLost("t_02", run)
    }

    @Test
    fun `t_02 fire at the sea sets the hut alight`() {
        val run = play("t_02", "fire_0_5@1,10")
        assertEquals("BURNING", run.state.objectById("hut_0_10")?.state)
        assertLost("t_02", run)
    }

    @Test
    fun `t_02 earth and the puddle make mud and use up the only water to put out a fire`() {
        val run = play("t_02", "seawater_5_6@8,5")
        assertTrue(run.state.objects.any { it.type == "MUD" })
        assertTrue(run.state.objects.none { it.isMovable && it.type == "SEAWATER" })
        assertLost("t_02", run)
    }

    // ------------------------------------------------------------------ t_03 Tiefe Schmelze
    //
    // Level 3 is too big for a complete search over six more moves. Each trap is shown lost by its
    // material balance instead – what is used up cannot come back – and a complete search over the next
    // [SHORT] moves finds no way out either.

    private val t03Solution = arrayOf(
        "stone_6_4@2,4", "tree_2_4@4,7", "water_2_8@3,7", "wood_1_7@6,7", "snow_9_4@7,7",
        "fire_5_7@4,7", "ore_1_10@6,7", "charcoal#7@6,10", "charcoal#1@6,10",
    )

    private fun assertNoQuickWayOut(run: Run) {
        val level = Levels.level("t_03")
        assertEquals(null, LevelAnalysis(level, Levels.engine(level)).shortestWithin(SHORT, run), "t_03 has a way out")
    }

    /** Things that can still give off steam-free water for putting out a fire (2 units in one cell). */
    private fun douses(run: Run): Int =
        run.state.objects.count { it.type == "SNOW" } +
            run.state.objects.filter { it.type == "WATER" && it.isMovable }.sumOf { it.amount / 2 }

    @Test
    fun `t_03 the walkthrough still works`() {
        assertEquals(Outcome.SUCCESS, play("t_03", *t03Solution).outcome)
    }

    @Test
    fun `t_03 embers made too early leave the second log unlit for good`() {
        val run = play("t_03", *t03Solution.take(3).toTypedArray(), "fire_5_7@4,7")
        val s = run.state.objects
        assertTrue(s.none { it.type == "FIRE" || it.isFlame }, "no flame left: nothing can catch fire any more")
        assertTrue(s.any { it.type == "WOOD" && it.state == "DRY" } && s.none { it.type == "CHARCOAL" })
        assertTrue(s.none { it.type == "SMELT" || it.type == "METAL" }, "and without charcoal no smelt, so no metal and no glass")
        assertNoQuickWayOut(run)
    }

    @Test
    fun `t_03 lighting the tree at the edge burns the forest`() {
        val run = play("t_03", "fire_5_7@3,4")
        assertTrue(run.state.objects.any { it.type == "TREE" && it.state == "BURNING" && it.position.x <= 1 })
        assertLost("t_03", run, depth = 1)
    }

    @Test
    fun `t_03 salt on the snowball wastes it`() {
        val run = play("t_03", "salt_7_4@8,4")
        assertTrue(run.state.objects.none { it.type == "SNOW" || it.type == "SALT" })
        assertTrue(run.state.objects.filter { it.type == "WATER" && !it.isMovable }.all { it.position.x == 10 }, "the meltwater ran down the shaft")
        assertEquals(1, douses(run), "only the puddle is left, but two logs have to be put out")
        assertNoQuickWayOut(run)
    }

    @Test
    fun `t_03 earth and the puddle make mud and use up water that was needed`() {
        val run = play("t_03", "water_2_8@8,10")
        assertTrue(run.state.objects.any { it.type == "MUD" })
        assertEquals(1, douses(run), "only the snowball is left, but two logs have to be put out")
        assertNoQuickWayOut(run)
    }

    @Test
    fun `t_03 both logs burning at once cannot both be put out in time`() {
        val run = play("t_03", "stone_6_4@2,4", "tree_2_4@4,7", "wood_1_7@6,7")
        assertEquals(1, run.state.objects.count { it.type == "ASH" })
        assertEquals(1, run.state.objects.count { it.type == "WOOD" }, "one log left for two charcoals")
        assertTrue(run.state.objects.none { it.type == "TREE" && it.position.x == 2 }, "and the tree at the edge is already felled")
        assertNoQuickWayOut(run)
    }

    private companion object {
        const val DEPTH = 6
        const val SHORT = 3
    }
}
