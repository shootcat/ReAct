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
}
