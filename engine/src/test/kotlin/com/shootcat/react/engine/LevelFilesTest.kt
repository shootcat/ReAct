package com.shootcat.react.engine

import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `every level loads and has goals, solution classes and something to experiment with`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            assertEquals(id, level.id)
            assertTrue(level.goals.isNotEmpty(), "$id has goals")
            assertTrue(level.solutions.isNotEmpty(), "$id has solution classes")
            assertTrue(level.objects.any { it.movable }, "$id has movable objects")
        }
    }

    @Test
    fun `every level is at rest until the player acts`() {
        for (id in Levels.allLevelIds) {
            val level = Levels.level(id)
            val start = level.initialState()
            val step = RuleEngine(Levels.catalog.types, level.rules).step(start)
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
    fun `objects cannot be dropped onto walls, other objects or closed areas`() {
        val l1 = Levels.level("w1_01").initialState()
        assertNull(l1.withObjectMoved("fire_2_3", Position(0, 0)))
        assertNull(l1.withObjectMoved("fire_2_3", Position(6, 3)), "the ice is there")
        assertNull(l1.withObjectMoved("ice_6_3", Position(3, 3)), "fixed ice cannot be moved")
        val l2 = Levels.level("w1_02").initialState()
        assertNull(l2.withObjectMoved("fire_6_3", Position(4, 5)), "the shaft interior is closed")
    }

    private fun withLevel(edit: (String) -> String) = edit(Levels.read("w1_01"))

    @Test
    fun `invalid level files are rejected with a clear error`() {
        val world = Levels.world("w1_01")
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"type\": \"FIRE\"", "\"type\": \"LAVA2\"") }, world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"#.F...i#\"", "\"#.F...Z#\"") }, world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"######B#\"", "\"######B\"") }, world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"object_id\": \"door_1\"", "\"object_id\": \"door_9\"") }, world)
        }
        assertFailsWith<LevelFormatException> {
            LevelLoader.parseLevel(withLevel { it.replace("\"title\"", "\"titel\"") }, world)
        }
    }

    @Test
    fun `every reaction reads naturally and belongs to a world`() {
        val texts = Levels.catalog.rules.associate { it.id to Reactions.describe(it, Levels.catalog.types).text }
        assertEquals("Hitze + Eis → Wasser", texts["heat_melts_ice"])
        assertEquals("Wasser + Feuer → Dampf", texts["water_douses_fire"])
        assertEquals("Strom + Lampe → Lampe an", texts["power_lights_lamp"])
        assertEquals("Wasser + Lava → Stein", texts["water_cools_lava"])
        assertTrue(Levels.catalog.rules.all { it.world in 1..4 })
        assertTrue(Levels.catalog.types.all.all { it.world in 1..4 })
    }

    @Test
    fun `every element appears in a level of its world or later`() {
        val used = Levels.allLevelIds.flatMap { id -> Levels.level(id).objects.map { it.type to Levels.world(id).world } }
        for (type in Levels.catalog.types.all) {
            val worlds = used.filter { it.first == type.id }.map { it.second }
            if (type.id == "STEAM") continue
            assertTrue(worlds.isNotEmpty(), "${type.id} is used somewhere")
            assertTrue(worlds.min() >= type.world, "${type.id} appears before its world ${type.world}")
        }
    }
}
