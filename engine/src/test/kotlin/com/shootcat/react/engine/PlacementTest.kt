package com.shootcat.react.engine

import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Placement fields ('+'): with "placement": "marked" things may only be put down there. */
class PlacementTest {

    private val world by lazy { Levels.worlds.first() }

    private fun level(map: List<String>, placement: String? = "marked"): LevelData {
        val rows = map.joinToString(", ") { "\"$it\"" }
        val mode = placement?.let { ", \"placement\": \"$it\"" } ?: ""
        val json = """
            {"id": "p", "title": "P", "world": ${world.world}, "map": [$rows]$mode,
             "legend": {
               "S": {"type": "STONE", "isMovable": true}, "F": {"type": "FIRE", "isMovable": true},
               "i": {"type": "ICE"}, "f": {"type": "FIRE"}
             },
             "goals": [{"type": "extinguish", "text": "Lösche das Feuer"}]}
        """.trimIndent()
        return LevelLoader.parseLevel(json, world)
    }

    private val drops = Drops(Levels.catalog.types, Levels.catalog.rules, Levels.catalog.merges)

    @Test
    fun `placement fields are read from the map`() {
        val l = level(listOf("#+..S..#", "########"))
        assertEquals(setOf(Position(1, 0)), l.placement)
        assertEquals(setOf(Position(1, 0)), l.initialState().placement)
        assertNull(level(listOf("#...S..#", "########"), placement = null).placement)
    }

    @Test
    fun `fields and the marked mode belong together`() {
        assertFailsWith<LevelFormatException> { level(listOf("#+..S..#", "########"), placement = null) }
        assertFailsWith<LevelFormatException> { level(listOf("#...S..#", "########")) }
        assertFailsWith<LevelFormatException> { level(listOf("#+..S..#", "########"), placement = "free") }
    }

    @Test
    fun `things are put down only on a placement field`() {
        val start = level(listOf("#+..S..#", "########")).initialState()
        assertIs<Drop.Placed>(drops.resolve(start, "stone_4_0", Position(1, 0)))
        assertNull(drops.resolve(start, "stone_4_0", Position(2, 0)))
        assertNull(drops.resolve(start, "stone_4_0", Position(6, 0)))
    }

    @Test
    fun `without marked placement every free cell is fine`() {
        val start = level(listOf("#...S..#", "########"), placement = null).initialState()
        assertIs<Drop.Placed>(drops.resolve(start, "stone_4_0", Position(2, 0)))
    }

    @Test
    fun `merging needs the target on or next to a placement field`() {
        val near = level(listOf("#F...F+#", "########")).initialState()
        val merged = drops.resolve(near, "fire_1_0", Position(5, 0))
        assertIs<Drop.Merged>(merged)
        assertEquals("BIG_FIRE", merged.state.objectById("fire_5_0")?.type)
        val far = level(listOf("#F+..F.#", "########")).initialState()
        assertNull(drops.resolve(far, "fire_1_0", Position(5, 0)))
    }

    @Test
    fun `reacting lands only on a placement field next to the partner`() {
        val withField = level(listOf("#F...+i#", "########")).initialState()
        val drop = drops.resolve(withField, "fire_1_0", Position(6, 0))
        assertIs<Drop.NextTo>(drop)
        assertEquals(Position(5, 0), drop.landing)
        val withoutField = level(listOf("#F+...i#", "########")).initialState()
        assertNull(drops.resolve(withoutField, "fire_1_0", Position(6, 0)))
    }

    @Test
    fun `a stone put on a high field falls with the physics`() {
        val l = level(listOf("#S.+..#", "##%.%%#", "##%.%%#", "#######"))
        val live = Levels.live(l)
        val run = live.play(live.start(), "stone_1_0", Position(3, 0))
        assertNotNull(run)
        assertEquals(Position(3, 2), run.state.objectById("stone_1_0")?.position)
        assertTrue(run.state.placement == l.placement)
    }
}
