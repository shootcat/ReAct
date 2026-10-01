package com.shootcat.react.engine

import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelFilesTest {

    @Test
    fun `world 1 lists thirteen levels, all on the map`() {
        assertEquals((0..12).map { "level_%02d".format(it) }, Levels.world.levelIds)
        assertEquals(Levels.world.levelIds.toSet(), Levels.world.map.map { it.levelId }.toSet())
    }

    @Test
    fun `every level loads and has goals, solution classes and something to experiment with`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            assertEquals(id, level.id)
            assertTrue(level.goals.isNotEmpty(), "$id has goals")
            assertTrue(level.solutions.isNotEmpty(), "$id has solution classes")
            assertTrue(level.objects.any { it.movable }, "$id has movable objects")
        }
    }

    @Test
    fun `every level is at rest until the player acts`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            val start = level.initialState()
            val step = RuleEngine(Levels.world.types, level.rules).step(start)
            assertEquals(start.objects, step.state.objects, "$id must not change by itself")
        }
    }

    @Test
    fun `no level solves itself`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            assertNotEquals(Outcome.SUCCESS, Levels.simulate(level, level.initialState()).outcome, id)
        }
    }

    @Test
    fun `objects cannot be dropped onto walls, other objects or closed areas`() {
        val l0 = Levels.level("level_00").initialState()
        assertNull(l0.withObjectMoved("fire_1", Position(0, 0)))
        assertNull(l0.withObjectMoved("fire_1", Position(6, 4)))
        assertNull(l0.withObjectMoved("ice_block_1", Position(3, 3)))
        val l2 = Levels.level("level_02").initialState()
        assertNull(l2.withObjectMoved("stone_1", Position(5, 4)), "the shaft interior is closed")
    }

    private fun withLevel(edit: (String) -> String) = edit(Levels.read("level_00"))

    @Test
    fun `invalid level files are rejected with a clear error`() {
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"type\": \"FIRE\"", "\"type\": \"LAVA\"") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"#.F...i#\"", "\"#.F...Z#\"") }, Levels.world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"######B#\"", "\"######B\"") }, Levels.world)
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
                "Hitze + Eis → Wasser",
                "Wasser + Feuer → Dampf",
                "Dampf + Eis → Wasser",
                "Hitze + Holz → Holz brennt",
                "Wasser + Holz → Dampf",
                "Hitze + Metall → Metall heiß",
                "Metall + Wasser → Dampf",
                "Wasser + Schalter → Schalter aktiviert",
                "Gewicht + Druckplatte → Druckplatte gedrückt",
                "Druck + Kolben → Kolben hochgedrückt",
                "Signal + Tür → Tür offen",
                "Signal + Klappe → Klappe offen",
            ),
            texts,
        )
    }
}
