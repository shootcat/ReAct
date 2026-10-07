package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.after
import com.shootcat.react.engine.TestWorld.count
import com.shootcat.react.engine.TestWorld.typeAt
import com.shootcat.react.engine.TestWorld.with
import com.shootcat.react.engine.model.Area
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.LevelGoal
import com.shootcat.react.engine.model.MergeRule
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.TargetCleared
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetExtinguished
import com.shootcat.react.engine.model.TargetMaxMoves
import com.shootcat.react.engine.model.TargetPreserved
import com.shootcat.react.engine.model.TargetRainTriggered
import com.shootcat.react.engine.model.TargetState
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.WindZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drag-and-merge, heat radius, the water cycle (steam, clouds, wind, rain) and the goal checks. */
class NatureTest {

    private val merges = listOf(
        MergeRule("flames", "Flammen", "FIRE", "FIRE", "BIG_FIRE"),
        MergeRule("water", "Wasser", "WATER", "WATER", "WATER"),
    )
    private val drops = Drops(TestWorld.types, TestWorld.rules, merges)

    private fun movable(state: GameState) = state.copy(objects = state.objects.map { it.copy(isMovable = true) })

    // ---------------------------------------------------------------- drag and drop

    @Test
    fun `two flames merge into a big fire that keeps the target's id`() {
        val start = TestWorld.state(
            "#.....#",
            "#F...F#",
            "#######",
        )
        val drop = drops.resolve(start, "fire_1_1", Position(5, 1))
        assertIs<Drop.Merged>(drop)
        assertEquals("fire_5_1", drop.targetId)
        val big = drop.state.objectById("fire_5_1")!!
        assertEquals("BIG_FIRE", big.type)
        assertTrue(big.isMovable)
        assertNull(drop.state.objectById("fire_1_1"))
    }

    @Test
    fun `water dropped onto water adds up and the rest overflows upwards`() {
        val start = movable(TestWorld.state(
            "#.....#",
            "#6...5#",
            "#######",
        ))
        val drop = drops.resolve(start, "water_1_1", Position(5, 1))
        assertIs<Drop.Merged>(drop)
        assertEquals(8, drop.state.objectAt(Position(5, 1))!!.amount)
        assertEquals(3, drop.state.objectAt(Position(5, 0))!!.amount)
        assertEquals(11, drop.state.objects.filter { it.isLiquid }.sumOf { it.amount })
    }

    @Test
    fun `a stone dropped into a pond goes under and pushes the water up`() {
        val start = TestWorld.state(
            "#S....#",
            "#.#WW.#",
            "#.#WW##",
            "#######",
        ).let { s -> s.copy(objects = s.objects.map { if (it.type == "WATER") it.copy(isMovable = false) else it }) }
        val drop = drops.resolve(start, "stone_1_0", Position(3, 2))
        assertIs<Drop.Placed>(drop)
        assertEquals("STONE", drop.state.typeAt(3, 2))
        // The water that was there sits on top now: the level rose.
        assertEquals(32, drop.state.objects.filter { it.isLiquid }.sumOf { it.amount })
        assertEquals("WATER", drop.state.typeAt(3, 0))
    }

    @Test
    fun `fire dropped onto ice lands next to it and melts it`() {
        val start = TestWorld.state(
            "#F....#",
            "#...I.#",
            "#######",
        )
        val drop = drops.resolve(start, "fire_1_0", Position(4, 1))
        assertIs<Drop.NextTo>(drop)
        assertEquals(Position(4, 0), drop.landing)
        assertEquals(0, drop.state.after(1).count("ICE"))
    }

    @Test
    fun `things that neither merge nor react bounce off`() {
        val start = TestWorld.state(
            "#S...S#",
            "#######",
        )
        assertNull(drops.resolve(start, "stone_1_0", Position(5, 0)))
        // Not into walls either, and fixed things cannot be dragged at all.
        assertNull(drops.resolve(start, "stone_1_0", Position(0, 0)))
        val fixed = start.copy(objects = start.objects.map { it.copy(isMovable = false) })
        assertNull(drops.resolve(fixed, "stone_1_0", Position(3, 0)))
    }

    // ---------------------------------------------------------------- heat

    @Test
    fun `a big fire melts ice two cells away through the air but not through rock`() {
        val open = TestWorld.state(
            "#X.I#",
            "#####",
        )
        assertEquals(0, open.after(1).count("ICE"))
        val wall = TestWorld.state(
            "#X#I#",
            "#####",
        )
        assertEquals(1, wall.after(1).count("ICE"))
        val small = TestWorld.state(
            "#F.I#",
            "#####",
        )
        assertEquals(1, small.after(1).count("ICE"))
    }

    @Test
    fun `only strong heat boils water and only deep water douses a big fire`() {
        val boil = Rule(
            "boil_hot", "Sieden", Trigger.TOUCH,
            RuleConditions(sourceHot = true, minHeat = 7, target = "WATER"),
            RuleEffect(spawnObject = "STEAM", spawnAmount = 2, targetConsume = 1),
        )
        val douseBig = Rule(
            "douse_big", "Ersticken", Trigger.TOUCH,
            RuleConditions(source = "WATER", sourceMinAmount = 6, target = "BIG_FIRE"),
            RuleEffect(targetState = "OUT", spawnObject = "STEAM", spawnAmount = 6, sourceConsume = 3),
        )
        val engine = TestWorld.engine(listOf(boil, douseBig))
        val small = TestWorld.state("#F3#", "####")
        assertEquals(3, engine.step(small).state.objects.filter { it.isLiquid }.sumOf { it.amount })

        val shallow = TestWorld.state("#X3#", "####")
        val afterShallow = engine.step(shallow).state
        assertEquals(1, afterShallow.count("BIG_FIRE"))
        assertEquals(2, afterShallow.objects.filter { it.isLiquid }.sumOf { it.amount })

        val deep = TestWorld.state("#XW#", "####")
        assertEquals(0, engine.step(deep).state.count("BIG_FIRE"))
    }

    // ---------------------------------------------------------------- clouds, wind, rain

    private fun vapor(x: Int, y: Int, amount: Int) =
        TestWorld.types.create("vapor_${x}_$y", "VAPOR", Position(x, y), amount = amount)

    private fun cloud(x: Int, y: Int, amount: Int) =
        TestWorld.types.create("cloud_${x}_$y", "CLOUD", Position(x, y), amount = amount)

    @Test
    fun `enough vapor in a row under the rock condenses into a cloud in the middle`() {
        val start = TestWorld.state(
            "#######",
            "#.....#",
            "#.....#",
            "#######",
        ).with(vapor(2, 1, 4)).with(vapor(3, 1, 4)).with(vapor(4, 1, 2))
        val after = TestWorld.engine().step(start).state
        assertEquals(1, after.count("CLOUD"))
        val c = after.objects.single { it.type == "CLOUD" }
        assertEquals(Position(3, 1), c.position)
        assertEquals(5, c.amount)
        assertEquals(0, after.count("VAPOR"))
    }

    @Test
    fun `too little vapor stays vapor`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "#####",
        ).with(vapor(2, 1, 6))
        assertEquals(0, start.after(3).count("CLOUD"))
    }

    @Test
    fun `vapor touching a cloud is taken up and a full cloud rains until it is gone`() {
        val start = TestWorld.state(
            "#######",
            "#.....#",
            "#.....#",
            "#.....#",
            "#######",
        ).with(cloud(3, 1, 7)).with(vapor(3, 2, 2))
        val engine = TestWorld.engine()
        val first = engine.step(start)
        val c = first.state.objectAt(Position(3, 1))!!
        assertEquals("RAINING", c.state)
        assertTrue(first.cues.any { it.sound == "rain" })
        // It lets one unit fall per step.
        assertEquals(7, c.amount)
        assertEquals(1, first.state.objects.filter { it.isLiquid }.sumOf { it.amount })
        val later = first.state.after(10, engine)
        assertEquals(0, later.count("CLOUD"))
        assertEquals(8, later.objects.filter { it.isLiquid }.sumOf { it.amount })
    }

    @Test
    fun `wind carries clouds until a wall stops them`() {
        val start = TestWorld.state(
            "######",
            "#....#",
            "#....#",
            "######",
        ).with(cloud(1, 1, 3))
        val engine = RuleEngine(TestWorld.types, TestWorld.rules, wind = listOf(WindZone(Area(0, 1, 5, 1), 1)))
        assertEquals("CLOUD", start.after(1, engine).typeAt(2, 1))
        assertEquals("CLOUD", start.after(5, engine).typeAt(4, 1))
    }

    @Test
    fun `clouds drifting into each other join`() {
        val start = TestWorld.state(
            "######",
            "#....#",
            "######",
        ).with(cloud(1, 1, 3)).with(cloud(2, 1, 2))
        val engine = RuleEngine(TestWorld.types, TestWorld.rules, wind = listOf(WindZone(Area(0, 1, 5, 1), 1)))
        val later = start.after(5, engine)
        assertEquals(1, later.count("CLOUD"))
        assertEquals(5, later.objects.single { it.type == "CLOUD" }.amount)
    }

    // ---------------------------------------------------------------- goals

    private fun level(state: GameState, goals: List<LevelGoal>) = LevelData(
        id = "test", title = "Test", world = 1, intro = "",
        width = state.width, height = state.height, walls = state.walls,
        objects = state.objects, rules = TestWorld.rules, goals = goals, maxSteps = 100,
        merges = merges,
    )

    @Test
    fun `goals are checked against the world`() {
        val s = TestWorld.state(
            "#F..#",
            "#.WW#",
            "#####",
        ).with(cloud(3, 0, 8).copy(state = "RAINING"))
        assertTrue(TargetContainerFilled(Area(2, 1, 3, 1), "WATER", 16, "voll").isMet(s, 0))
        assertFalse(TargetContainerFilled(Area(2, 1, 3, 1), "WATER", 17, "voll").isMet(s, 0))
        assertFalse(TargetExtinguished(null, "aus").isMet(s, 0))
        assertTrue(TargetExtinguished(Area(2, 0, 3, 1), "aus").isMet(s, 0))
        assertTrue(TargetRainTriggered(null, "Regen").isMet(s, 0))
        assertFalse(TargetRainTriggered(Area(1, 0, 1, 1), "Regen").isMet(s, 0))
        assertTrue(TargetPreserved("fire_1_0", "ACTIVE", "bleibt").isMet(s, 0))
        assertFalse(TargetCleared(setOf("WATER"), null, "weg").isMet(s, 0))
        assertTrue(TargetMaxMoves(2, "zwei").isMet(s, 2))
        assertFalse(TargetMaxMoves(2, "zwei").isMet(s, 3))
        assertTrue(TargetState("ice_9_9", "MELTED", vanishes = true, text = "weg").isMet(s, 0))
        assertFalse(TargetState("ice_9_9", "MELTED", vanishes = false, text = "weg").isMet(s, 0))
    }

    @Test
    fun `the level is solved once the main goals hold, optional goals are judged then`() {
        val start = TestWorld.state(
            "#F.......#",
            "#....I...#",
            "#...###..#",
            "#...#.#..#",
            "##########",
        )
        val goals = listOf(
            TargetState("ice_5_1", "MELTED", vanishes = true, text = "Schmilz das Eis"),
            TargetMaxMoves(1, "Ein Zug"),
            TargetPreserved("fire_1_0", "ACTIVE", "Das Feuer bleibt"),
        )
        val sim = LiveSimulation(level(start, goals), TestWorld.engine())
        val run = sim.play(sim.start(), "fire_1_0", Position(5, 0))
        assertNotNull(run)
        assertEquals(Outcome.SUCCESS, run.outcome)
        // Judged once the world has settled: the meltwater has put the fire out by then.
        assertEquals(setOf(1), run.achieved)
        assertEquals("OUT", run.state.objectById("fire_1_0")?.state)
    }

    @Test
    fun `a goal that only holds for a moment does not count, rain counts once it rained`() {
        // The water runs through the hollow and on into the deep pit: the hollow is full only briefly.
        val start = TestWorld.state(
            "#....I.....#",
            "#..........#",
            "#.F.......##",
            "####.#######",
            "#........###",
            "#.........##",
            "##########.#",
            "############",
        )
        val goals = listOf(TargetContainerFilled(Area(4, 3, 4, 3), "WATER", 1, "Kurz nass"))
        val sim = LiveSimulation(level(start, goals), TestWorld.engine())
        val run = sim.play(sim.start(), "fire_2_2", Position(5, 1))
        assertNotNull(run)
        assertEquals(null, run.outcome)
    }

    @Test
    fun `a merge is a move and can be undone by keeping the run before it`() {
        val start = TestWorld.state(
            "#F...F..I#",
            "##########",
        )
        val goals = listOf(TargetState("ice_8_0", "MELTED", vanishes = true, text = "Schmilz das Eis"))
        val sim = LiveSimulation(level(start, goals), TestWorld.engine())
        val before = sim.start()
        val merged = sim.move(before, "fire_1_0", Position(5, 0))
        assertNotNull(merged)
        assertEquals("BIG_FIRE", merged.state.objectById("fire_5_0")!!.type)
        assertEquals(1, merged.moves.size)
        // The big fire reaches the ice two cells away? No: it is three away, nothing happens.
        assertEquals(1, sim.settle(merged).state.count("ICE"))
        assertEquals("FIRE", before.state.objectById("fire_5_0")!!.type)
    }
}
