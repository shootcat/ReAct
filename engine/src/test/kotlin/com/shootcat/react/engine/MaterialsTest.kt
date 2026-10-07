package com.shootcat.react.engine

import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The material chains of the sandbox (Leitfaden V2): tools that change things without being used up,
 * wood quenched into charcoal, heat with and without a flame, metal that carries heat, burning counted
 * in moves.
 */
class MaterialsTest {

    private val world by lazy { Levels.worlds.first() }
    private val drops = Drops(Levels.catalog.types, Levels.catalog.rules, Levels.catalog.merges)

    private fun level(map: List<String>, legend: String = ""): LevelData {
        val rows = map.joinToString(", ") { "\"$it\"" }
        val json = """
            {"id": "m", "title": "M", "world": ${world.world}, "map": [$rows], "placement": "marked",
             "legend": {"S": {"type": "STONE", "isMovable": true}, "t": {"type": "TREE"}${if (legend.isEmpty()) "" else ", $legend"}},
             "goals": [{"type": "extinguish", "text": "Lösche das Feuer"}]}
        """.trimIndent()
        return LevelLoader.parseLevel(json, world)
    }

    @Test
    fun `a stone dropped on a tree fells it into wood and stays where it was`() {
        val start = level(listOf("S..+t...", "%%%%%%%%")).initialState()
        val drop = drops.resolve(start, "stone_0_0", Position(4, 0))
        assertIs<Drop.Merged>(drop)
        assertEquals(Position(0, 0), drop.state.objectById("stone_0_0")?.position, "the stone stays on its own cell")
        val wood = drop.state.objectAt(Position(4, 0))!!
        assertEquals("WOOD", wood.type)
        assertTrue(wood.isMovable, "the felled wood can be carried away")
        assertEquals(2, drop.state.objects.size)
    }

    @Test
    fun `felling counts as one move in live mode`() {
        val level = level(listOf("S..+t...", "%%%%%%%%"))
        val live = Levels.live(level)
        val run = live.play(live.start(), "stone_0_0", Position(4, 0))!!
        assertEquals(1, run.moves.size)
        assertEquals("WOOD", run.state.objectAt(Position(4, 0))?.type)
        assertEquals("STONE", run.state.objectAt(Position(0, 0))?.type)
    }

    @Test
    fun `a tool needs its target on or next to a placement field`() {
        val far = level(listOf("S+..t...", "%%%%%%%%")).initialState()
        assertNull(drops.resolve(far, "stone_0_0", Position(4, 0)))
    }

    @Test
    fun `burning wood quenched in time becomes loose charcoal`() {
        val level = level(listOf("%%%%", "+...", "..W.", "..~.", "%%%%"), "\"W\": {\"type\": \"WOOD\", \"state\": \"BURNING\", \"isMovable\": true}, \"~\": {\"type\": \"WATER\", \"amount\": 3}")
        val after = Levels.engine(level).step(level.initialState()).state
        val coal = after.objects.single { it.type == "CHARCOAL" }
        assertTrue(coal.isMovable)
        assertTrue(after.objects.none { it.type == "WOOD" })
        assertTrue(after.objects.any { it.type == "STEAM" })
    }

    @Test
    fun `a burning tree quenched in time becomes charcoal too`() {
        val level = level(listOf("+...", ".~u.", "%%%%"), "\"~\": {\"type\": \"WATER\", \"amount\": 3}, \"u\": {\"type\": \"TREE\", \"state\": \"BURNING\"}")
        val after = Levels.engine(level).step(level.initialState()).state
        assertEquals("CHARCOAL", after.objectAt(Position(2, 1))?.type)
    }

    @Test
    fun `only an open flame sets wood alight, glowing heat does not`() {
        val legend = "\"W\": {\"type\": \"WOOD\", \"isMovable\": true}, \"F\": {\"type\": \"FIRE\", \"isMovable\": true}, " +
            "\"G\": {\"type\": \"EMBER_ROCK\"}"
        val flame = level(listOf("+...", ".FW.", "%%%%"), legend)
        val lit = Levels.engine(flame).step(flame.initialState()).state
        assertEquals("BURNING", lit.objectAt(Position(2, 1))?.state)
        val glow = level(listOf("+...", ".GW.", "%%%%"), legend)
        val warm = Levels.engine(glow).step(glow.initialState()).state
        assertEquals("DRY", warm.objectAt(Position(2, 1))?.state, "embers are hot but have no flame")
    }

    @Test
    fun `dropping wood next to glowing heat is no reaction, next to a flame it is`() {
        val legend = "\"W\": {\"type\": \"WOOD\", \"isMovable\": true}, \"f\": {\"type\": \"FIRE\"}, \"G\": {\"type\": \"EMBER_ROCK\"}"
        val start = level(listOf("W.+f+G..", "%%%%%%%%"), legend).initialState()
        assertIs<Drop.NextTo>(drops.resolve(start, "wood_0_0", Position(3, 0)))
        assertNull(drops.resolve(start, "wood_0_0", Position(5, 0)), "wood dropped on embers bounces off")
    }
}
