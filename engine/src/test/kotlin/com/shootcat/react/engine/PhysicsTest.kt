package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.positionsOf
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class PhysicsTest {

    private val engine = TestWorld.engine()

    private fun GameState.after(steps: Int): GameState {
        var s = this
        repeat(steps) { s = engine.step(s).state }
        return s
    }

    @Test
    fun `a stone falls one cell per step until it rests`() {
        val start = TestWorld.state(
            "#S#",
            "#.#",
            "#.#",
            "###",
        )
        assertEquals(listOf(Position(1, 1)), start.after(1).positionsOf("STONE"))
        assertEquals(listOf(Position(1, 2)), start.after(2).positionsOf("STONE"))
        assertEquals(listOf(Position(1, 2)), start.after(5).positionsOf("STONE"))
    }

    @Test
    fun `a stack falls together`() {
        val start = TestWorld.state(
            "#S#",
            "#W#",
            "#.#",
            "#.#",
            "###",
        )
        val s = start.after(1)
        assertEquals(listOf(Position(1, 1)), s.positionsOf("STONE"))
        assertEquals(listOf(Position(1, 2)), s.positionsOf("WATER"))
    }

    @Test
    fun `water flows towards the nearest drop and falls`() {
        val start = TestWorld.state(
            "#######",
            "#.W...#",
            "####.##",
            "#######",
        )
        assertEquals(listOf(Position(3, 1)), start.after(1).positionsOf("WATER"))
        assertEquals(listOf(Position(4, 1)), start.after(2).positionsOf("WATER"))
        assertEquals(listOf(Position(4, 2)), start.after(3).positionsOf("WATER"))
    }

    @Test
    fun `equal distance flows left`() {
        val start = TestWorld.state(
            "#######",
            "#..W..#",
            "#.###.#",
            "#######",
        )
        assertEquals(listOf(Position(2, 1)), start.after(1).positionsOf("WATER"))
    }

    @Test
    fun `water without a drop rests`() {
        val start = TestWorld.state(
            "######",
            "#.W..#",
            "######",
        )
        assertEquals(start.objects, engine.step(start).state.objects)
    }

    @Test
    fun `fixed objects block the flow`() {
        val start = TestWorld.state(
            "#######",
            "#.WF..#",
            "####.##",
            "#######",
        )
        assertEquals(listOf(Position(2, 1)), start.after(3).positionsOf("WATER"))
    }

    @Test
    fun `water fills a shaft from the bottom`() {
        val start = TestWorld.state(
            "#....#",
            "#.WWW#",
            "##.###",
            "##.###",
            "##.###",
            "######",
        )
        val settled = start.after(20)
        assertEquals(listOf(Position(2, 2), Position(2, 3), Position(2, 4)), settled.positionsOf("WATER"))
    }
}
