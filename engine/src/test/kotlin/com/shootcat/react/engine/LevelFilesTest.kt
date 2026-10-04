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
    fun `four worlds with twenty levels and a bonus level each, all on their map`() {
        assertEquals(listOf(1, 2, 3, 4), Levels.worlds.map { it.world })
        for (w in Levels.worlds) {
            assertEquals((1..20).map { "w${w.world}_%02d".format(it) }, w.levelIds, "world ${w.world}")
            assertEquals("w${w.world}_bonus", w.bonusLevelId)
            assertEquals(w.allLevelIds.toSet(), w.map.map { it.levelId }.toSet(), "map of world ${w.world}")
            assertNotNull(w.icon)
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
        assertEquals("Wasser + Feuer → Dampf", texts["water_douses_fire"])
        assertTrue(Levels.catalog.rules.all { it.world in 1..4 })
        assertTrue(Levels.catalog.types.all.all { it.world in 1..4 })
        val flames = Levels.catalog.merges.single { it.id == "flames_merge" }
        assertEquals("BIG_FIRE", flames.result)
    }

    @Test
    fun `every element appears in a level of its world or later`() {
        val used = Levels.allLevelIds.flatMap { id -> Levels.level(id).objects.map { it.type to Levels.world(id).world } }
        // Things the world itself creates do not need to be placed.
        val created = (Levels.catalog.rules.mapNotNull { it.effect.spawnObject } + Levels.catalog.merges.map { it.result } +
            Levels.catalog.types.all.mapNotNull { it.properties["condense"] }).toSet()
        for (type in Levels.catalog.types.all) {
            if (type.id in created) continue
            val worlds = used.filter { it.first == type.id }.map { it.second }
            assertTrue(worlds.isNotEmpty(), "${type.id} is used somewhere")
            assertTrue(worlds.min() >= type.world, "${type.id} appears before its world ${type.world}")
        }
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
