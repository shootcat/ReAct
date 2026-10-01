package com.shootcat.react.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LevelFilesTest {

    @Test
    fun `world file lists exactly the three beta levels`() {
        assertEquals(listOf("level_00", "level_01", "level_02"), Levels.world.levelIds)
        assertEquals(Levels.world.levelIds.toSet(), Levels.world.map.map { it.levelId }.toSet())
    }

    @Test
    fun `every level loads and has a goal and solution classes`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            assertEquals(id, level.id)
            assertTrue(level.goals.isNotEmpty(), "$id has goals")
            assertTrue(level.solutions.isNotEmpty(), "$id has solution classes")
            assertTrue(level.objects.any { it.movable }, "$id has something to experiment with")
        }
    }

    @Test
    fun `no level solves itself without the player`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            val result = Levels.simulate(level, level.initialState())
            assertNotEquals(Outcome.SUCCESS, result.outcome, "$id must not be solved without moves")
        }
    }

    @Test
    fun `objects cannot be dropped onto walls or other objects`() {
        val level = Levels.level("level_00")
        val start = level.initialState()
        assertEquals(null, start.withObjectMoved("fire_1", com.shootcat.react.engine.model.Position(0, 0)))
        assertEquals(null, start.withObjectMoved("fire_1", com.shootcat.react.engine.model.Position(6, 4)))
        assertEquals(null, start.withObjectMoved("ice_block_1", com.shootcat.react.engine.model.Position(3, 3)))
    }

    private fun withLevel(edit: (String) -> String) = edit(Levels.read("level_00"))

    @Test
    fun `invalid level files are rejected with a clear error`() {
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"type\": \"FIRE\"", "\"type\": \"LAVA\"") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"x\": 2, \"y\": 4", "\"x\": 0, \"y\": 4") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"######.#\",", "\"######.\",") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"object_id\": \"door_1\"", "\"object_id\": \"door_9\"") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"title\"", "\"titel\"") }, Levels.world)
        }
    }

    @Test
    fun `reactions read naturally`() {
        val texts = Levels.world.rules.map { Reactions.describe(it, Levels.world.types).text }
        assertEquals(
            listOf(
                "Feuer + Eis → Wasser",
                "Feuer + Wasser → Dampf",
                "Wasser + Schalter → Schalter aktiviert",
                "Gewicht ≥ 3 + Druckplatte → Druckplatte gedrückt",
                "Schalter aktiviert → Tür offen",
                "Druckplatte gedrückt → Tür offen",
            ),
            texts,
        )
    }
}
