package com.shootcat.react.engine

import com.shootcat.react.engine.model.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LevelFilesTest {

    @Test
    fun `one world, the test, with its levels in order and all on its map`() {
        val w = Levels.worlds.single()
        assertEquals(1, w.world)
        assertEquals("Test", w.title)
        assertTrue(w.levelIds.size <= 3, "at most three test levels")
        assertEquals(w.levelIds.indices.map { "t_%02d".format(it + 1) }, w.levelIds)
        assertEquals(null, w.bonusLevelId)
        assertTrue(w.reactionsKnown, "the player knows every reaction of the test levels already")
        assertEquals(w.allLevelIds.toSet(), w.map.map { it.levelId }.toSet())
        assertNotNull(w.icon)
    }

    @Test
    fun `every level of the exam puts things down on placement fields only`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            val fields = assertNotNull(level.placement, "$id uses marked placement")
            assertTrue(fields.size in 3..16, "$id has ${fields.size} placement fields")
        }
    }

    @Test
    fun `every level has a clear task, loose things to move and a portrait landscape`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            assertEquals(id, level.id)
            assertTrue(level.mainGoals.isNotEmpty(), "$id has a main goal")
            assertTrue(level.goals.all { it.text.isNotBlank() }, "$id: every goal has a text")
            assertTrue(level.objects.any { it.isMovable }, "$id has movable objects")
            assertTrue(level.height >= level.width, "$id is laid out for portrait (${level.width}x${level.height})")
            assertEquals(level.walls, level.terrain.keys, "$id: every wall is earth or rock")
        }
    }

    @Test
    fun `every level is at rest until the player acts`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            val start = level.initialState()
            val step = Levels.engine(level).step(start)
            assertEquals(start.objects, step.state.objects, "$id must not change by itself")
        }
    }

    @Test
    fun `no level solves itself`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            assertNotEquals(Outcome.SUCCESS, Levels.simulate(level, level.initialState()).outcome, id)
        }
    }

    @Test
    fun `reactions and merges read naturally`() {
        val types = Levels.catalog.types
        val texts = Levels.catalog.rules.associate { it.id to Reactions.describe(it, types).text }
        assertEquals("Hitze + Eis → Wasser", texts["heat_melts_ice"])
        assertEquals("Wasser + Flamme → Dampf", texts["water_douses_fire"])
        assertTrue(Levels.catalog.rules.all { it.world in 1..4 })
        assertTrue(Levels.catalog.types.all.all { it.world in 1..4 })
        val flames = Levels.catalog.merges.single { it.id == "flames_merge" }
        assertEquals("BIG_FIRE", flames.result)
    }

    private val world by lazy { Levels.worlds.first() }

    private fun level(map: String = "\"#....#\", \"#.F..#\", \"######\"", goals: String = DEFAULT_GOALS, legend: String = DEFAULT_LEGEND) = """
        {"id": "x", "title": "X", "world": 1, "map": [$map], "legend": {$legend}, "goals": [$goals]}
    """.trimIndent()

    @Test
    fun `a minimal level loads with its landscape and movable flags`() {
        val l = LevelLoader.parseLevel(level(map = "\"%....#\", \"#.F..#\", \"######\""), world)
        assertEquals(Terrain.ROCK, l.terrain[com.shootcat.react.engine.model.Position(0, 0)])
        assertEquals(Terrain.EARTH, l.terrain[com.shootcat.react.engine.model.Position(5, 0)])
        assertTrue(l.objects.single().isMovable)
        assertEquals(1, l.mainGoals.size)
    }

    @Test
    fun `invalid level files are rejected with a clear error`() {
        assertFailsWith<LevelFormatException> { LevelLoader.parseLevel(level(legend = "\"F\": {\"type\": \"LAVA2\"}"), world) }
        assertFailsWith<LevelFormatException> { LevelLoader.parseLevel(level(map = "\"#..Z.#\", \"#.F..#\", \"######\""), world) }
        assertFailsWith<LevelFormatException> { LevelLoader.parseLevel(level(map = "\"#....\", \"#.F..#\", \"######\""), world) }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(level(goals = "{\"type\": \"state\", \"object\": \"fire_9_9\", \"state\": \"OUT\", \"text\": \"aus\"}"), world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(level(goals = "{\"type\": \"fill\", \"area\": [1, 1, 9, 9], \"min\": 8, \"text\": \"voll\"}"), world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(level(goals = "{\"type\": \"max_moves\", \"moves\": 2, \"text\": \"zwei\", \"optional\": true}"), world)
        }
        assertFailsWith<LevelFormatException> { LevelLoader.parseLevel(level().replace("\"title\"", "\"titel\""), world) }
    }

    private companion object {
        const val DEFAULT_LEGEND = "\"F\": {\"type\": \"FIRE\", \"isMovable\": true}"
        const val DEFAULT_GOALS = "{\"type\": \"extinguish\", \"text\": \"Lösche das Feuer\"}"
    }
}
