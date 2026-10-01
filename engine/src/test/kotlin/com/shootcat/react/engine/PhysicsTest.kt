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
        // One step sideways, then it slides diagonally into the hole.
        assertEquals(listOf(Position(3, 1)), start.after(1).positionsOf("WATER"))
        assertEquals(listOf(Position(4, 2)), start.after(2).positionsOf("WATER"))
    }

    @Test
    fun `blocked water slides diagonally down, left first`() {
        val start = TestWorld.state(
            "#####",
            "#.W.#",
            "#.#.#",
            "#####",
        )
        assertEquals(listOf(Position(1, 2)), start.after(1).positionsOf("WATER"))
    }

    @Test
    fun `water does not squeeze through corners`() {
        val start = TestWorld.state(
            "#####",
            "#FW.#",
            "#.#.#",
            "#####",
        )
        // The fire blocks the left side, so the only way is down-right.
        val s = TestWorld.engine(listOf(TestWorld.melt)).step(start).state
        assertEquals(listOf(Position(3, 2)), s.positionsOf("WATER"))
    }

    @Test
    fun `steam rises and slides around a ceiling`() {
        val start = TestWorld.state(
            "#######",
            "#.....#",
            "###.###",
            "#..V..#",
            "#######",
        )
        // Straight up through the gap, then it rests under the ceiling.
        assertEquals(listOf(Position(3, 2)), start.after(1).positionsOf("STEAM"))
        assertEquals(listOf(Position(3, 1)), start.after(2).positionsOf("STEAM"))
        assertEquals(listOf(Position(3, 1)), start.after(5).positionsOf("STEAM"))

        val underLedge = TestWorld.state(
            "#######",
            "#.....#",
            "#.###.#",
            "#..V..#",
            "#######",
        )
        // Tie between the two openings goes left: sideways first, then diagonally up.
        assertEquals(listOf(Position(2, 3)), underLedge.after(1).positionsOf("STEAM"))
        assertEquals(listOf(Position(1, 2)), underLedge.after(2).positionsOf("STEAM"))
    }

    @Test
    fun `steam weighs nothing`() {
        val start = TestWorld.state(
            "#V#",
            "#P#",
            "###",
        )
        assertEquals("UP", engine.step(start).state.objectById("plate_1_1")?.state)
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
        // Ice is frozen in place and does not react with water.
        val start = TestWorld.state(
            "#######",
            "#.WI..#",
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
