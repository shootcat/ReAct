package com.shootcat.react.engine

import com.shootcat.react.engine.model.Goal
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterminismTest {

    @Test
    fun `same setup gives exactly the same timeline`() {
        val start = TestWorld.state(
            "#S......#",
            "#I.F..W.#",
            "#..WW...#",
            "##.###.##",
            "##P###B##",
            "#########",
        )
        val goals = listOf(Goal("door_x", "UNLOCKED"))
        val a = Simulator(TestWorld.engine(), goals, 100).run(start)
        val b = Simulator(TestWorld.engine(), goals, 100).run(start)
        assertEquals(a, b)
    }

    @Test
    fun `real levels replay identically`() {
        for (id in Levels.world.levelIds) {
            val level = Levels.level(id)
            val setups = listOf(level.initialState()) + level.initialState().objects
                .filter { it.movable }
                .flatMap { o ->
                    (0 until level.width).flatMap { x -> (0 until level.height).map { y -> o.id to com.shootcat.react.engine.model.Position(x, y) } }
                }
                .mapNotNull { (id, p) -> level.initialState().withObjectMoved(id, p) }
            for (setup in setups) {
                val a = Levels.simulate(level, setup)
                val b = Levels.simulate(level, setup)
                assertEquals(a, b)
            }
        }
    }
}
