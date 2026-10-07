package com.shootcat.react.engine

import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetPreserved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The goals of the sandbox levels: make something, bring a liquid to the boil, fill a basin with fresh water, keep a forest. */
class GoalTypesTest {

    private val world by lazy { Levels.worlds.first() }

    private fun level(map: List<String>, legend: String, goals: String): LevelData {
        val rows = map.joinToString(", ") { "\"$it\"" }
        val json = """
            {"id": "g", "title": "G", "world": ${world.world}, "map": [$rows], "placement": "marked",
             "legend": {$legend}, "goals": [$goals]}
        """.trimIndent()
        return LevelLoader.parseLevel(json, world)
    }

    @Test
    fun `produce holds once there are enough things of the type`() {
        val level = level(
            listOf("~..+B.", "%%%%%%"),
            "\"~\": {\"type\": \"WATER\", \"amount\": 3, \"isMovable\": true}, \"B\": {\"type\": \"WOOD\", \"state\": \"BURNING\"}",
            "{\"type\": \"produce\", \"element\": \"CHARCOAL\", \"text\": \"Mach Holzkohle\"}",
        )
        val goal = level.goals.single()
        assertFalse(goal.isMet(level.initialState(), 0))
        val live = Levels.live(level)
        val run = live.play(live.start(), "water_0_0", Position(3, 0))!!
        assertEquals(Outcome.SUCCESS, run.outcome)
        assertTrue(goal.isMet(run.state, 1))
    }

    @Test
    fun `produce needs a known type`() {
        assertFailsWith<LevelFormatException> {
            level(listOf("+."), "", "{\"type\": \"produce\", \"element\": \"UNOBTAINIUM\", \"text\": \"?\"}")
        }
    }

    private val basin = listOf("G..F....", ".....+..", "%%%%%ww%", "%%%%%%%%")
    private val basinLegend = "\"G\": {\"type\": \"EMBER_ROCK\", \"isMovable\": true}, \"F\": {\"type\": \"FIRE\", \"isMovable\": true}, " +
        "\"w\": {\"type\": \"WATER\", \"amount\": 8}"
    private val heatGoal = "{\"type\": \"heat\", \"area\": [5, 2, 6, 2], \"text\": \"Bring das Wasser zum Kochen\"}"

    @Test
    fun `heat is met once a liquid in the area boils and stays met`() {
        val level = level(basin, basinLegend, heatGoal)
        val live = Levels.live(level)
        val moved = live.move(live.start(), "ember_rock_0_0", Position(5, 1))!!
        var run = moved
        var latchedAt = -1
        var step = 0
        while (run.active) {
            run = live.step(run).run
            step++
            if (latchedAt < 0 && 0 in run.latched) latchedAt = step
        }
        assertEquals(1, latchedAt, "the first boil counts")
        assertEquals(Outcome.SUCCESS, run.outcome)
    }

    @Test
    fun `steam from putting out a fire is no boiling`() {
        val level = level(basin, basinLegend, heatGoal)
        val live = Levels.live(level)
        val run = live.play(live.start(), "fire_3_0", Position(5, 1))!!
        assertTrue(run.events.any { it.ruleId == "water_douses_fire" }, "the water put the fire out and steamed")
        assertTrue(run.state.objects.none { it.type == "FIRE" })
        assertTrue(0 !in run.latched)
        assertNull(run.outcome)
    }

    @Test
    fun `a full basin needs every open cell full`() {
        val full = "{\"type\": \"fill\", \"liquid\": \"WATER\", \"full\": true, \"area\": [1, 1, 3, 1], \"text\": \"Fülle das Becken\"}"
        fun met(row: String): Boolean {
            val level = level(listOf("+....", row, "%%%%%"), fillLegend, full)
            val goal = level.goals.single() as TargetContainerFilled
            assertEquals(16, goal.min, "two open cells: the fixed stone takes no water")
            return goal.isMet(level.initialState(), 0)
        }
        assertTrue(met("%wwT%"))
        assertFalse(met("%wvT%"), "not quite full")
    }

    @Test
    fun `salt water in the basin spoils fresh water`() {
        val some = "{\"type\": \"fill\", \"liquid\": \"WATER\", \"min\": 8, \"area\": [1, 1, 3, 1], \"text\": \"Süßwasser ins Becken\"}"
        fun met(row: String) = level(listOf("+....", row, "%%%%%"), fillLegend, some).let { it.goals.single().isMet(it.initialState(), 0) }
        assertTrue(met("%w.T%"))
        assertFalse(met("%wsT%"))
    }

    private val fillLegend = "\"w\": {\"type\": \"WATER\", \"amount\": 8}, \"v\": {\"type\": \"WATER\", \"amount\": 7}, " +
        "\"s\": {\"type\": \"SEAWATER\", \"amount\": 8}, \"T\": {\"type\": \"STONE\"}"

    @Test
    fun `preserving a forest keeps every tree of the area, and only those`() {
        val legend = "\"F\": {\"type\": \"FIRE\", \"isMovable\": true}, \"t\": {\"type\": \"TREE\"}, \"S\": {\"type\": \"STONE\", \"isMovable\": true}"
        val goals = "{\"type\": \"preserve\", \"types\": [\"TREE\"], \"area\": [5, 0, 7, 0], \"text\": \"Der Wald bleibt\"}, " +
            "{\"type\": \"extinguish\", \"text\": \"Nichts brennt\", \"optional\": true}"
        val level = level(listOf("F.St+ttt+.", "%%%%%%%%%%"), legend, goals)
        val forest = level.goals.first() as TargetPreserved
        assertEquals(listOf("tree_5_0", "tree_6_0", "tree_7_0"), forest.things.map { it.id }.sorted(), "the lone tree at x=3 is no part of it")
        val live = Levels.live(level)
        // Felling the lone tree outside the forest does not touch it.
        val felled = live.play(live.start(), "stone_2_0", Position(3, 0))!!
        assertEquals("WOOD", felled.state.objectAt(Position(3, 0))?.type)
        assertTrue(forest.isMet(felled.state, 1))
        // Fire next to the forest sets it alight: the goal is lost.
        val burnt = live.play(live.start(), "fire_0_0", Position(8, 0))!!
        assertFalse(forest.isMet(burnt.state, 1))
    }

    @Test
    fun `a preserve goal needs an object or an area with types`() {
        assertFailsWith<LevelFormatException> {
            level(listOf("+t"), "\"t\": {\"type\": \"TREE\"}", "{\"type\": \"preserve\", \"area\": [1, 0, 1, 0], \"text\": \"?\"}")
        }
        assertFailsWith<LevelFormatException> {
            level(listOf("+t"), "\"t\": {\"type\": \"TREE\"}", "{\"type\": \"preserve\", \"types\": [\"TREE\"], \"area\": [0, 0, 0, 0], \"text\": \"?\"}")
        }
    }
}
