package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.after
import com.shootcat.react.engine.TestWorld.amountAt
import com.shootcat.react.engine.TestWorld.positionsOf
import com.shootcat.react.engine.TestWorld.totalWater
import com.shootcat.react.engine.TestWorld.typeAt
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhysicsTest {

    private val physicsOnly = TestWorld.engine(rules = emptyList())

    // Solids

    @Test
    fun `solids fall one cell per step and stacks fall together`() {
        val start = TestWorld.state(
            "#S#",
            "#I#",
            "#.#",
            "#.#",
            "###",
        )
        val s = start.after(1, physicsOnly)
        assertEquals(listOf(Position(1, 1)), s.positionsOf("STONE"))
        assertEquals(listOf(Position(1, 2)), s.positionsOf("ICE"))
        assertEquals(listOf(Position(1, 3)), start.after(5, physicsOnly).positionsOf("ICE"))
    }

    @Test
    fun `a stone sinks through water and pushes it up`() {
        val start = TestWorld.state(
            "#S#",
            "#W#",
            "#W#",
            "###",
        )
        val s = start.after(2, physicsOnly)
        assertEquals(listOf(Position(1, 2)), s.positionsOf("STONE"))
        assertEquals(8, s.amountAt(1, 0))
        assertEquals(8, s.amountAt(1, 1))
    }

    @Test
    fun `ice floats - it rests on water and rises when it is under water`() {
        val onTop = TestWorld.state(
            "#I#",
            "#.#",
            "#W#",
            "###",
        )
        assertEquals(listOf(Position(1, 1)), onTop.after(4, physicsOnly).positionsOf("ICE"))

        val underWater = TestWorld.state(
            "#.#",
            "#W#",
            "#I#",
            "###",
        )
        val s = underWater.after(1, physicsOnly)
        assertEquals(listOf(Position(1, 1)), s.positionsOf("ICE"))
        assertEquals(8, s.amountAt(1, 2))
    }

    @Test
    fun `a fire bowl falls and sinks in water`() {
        val start = TestWorld.state(
            "#F#",
            "#.#",
            "#W#",
            "###",
        )
        assertEquals(listOf(Position(1, 2)), start.after(3, physicsOnly).positionsOf("FIRE"))
    }

    // Liquids

    @Test
    fun `water falls and fills a container from the bottom`() {
        val start = TestWorld.state(
            "#W#",
            "#.#",
            "#4#",
            "###",
        )
        val s = start.after(2, physicsOnly)
        assertEquals(8, s.amountAt(1, 2))
        assertEquals(4, s.amountAt(1, 1))
    }

    @Test
    fun `water on a flat floor spreads into a puddle and comes to rest`() {
        val start = TestWorld.state(
            "#..........#",
            "#....W.....#",
            "############",
        )
        val settled = start.after(30, physicsOnly)
        assertEquals(8, settled.totalWater())
        val wet = settled.objects.count { it.isLiquid }
        assertTrue(wet >= 4, "puddle should cover several cells, was $wet")
        assertEquals(settled, physicsOnly.step(settled).state, "a puddle is at rest")
    }

    @Test
    fun `water runs off towards a nearby edge`() {
        val start = TestWorld.state(
            "#W.....#",
            "######.#",
            "######.#",
            "########",
        )
        val s = start.after(25, physicsOnly)
        assertEquals(8, s.totalWater())
        assertEquals(8, s.amountAt(6, 2), "all water ends up in the pit")
    }

    @Test
    fun `water levels out between connected basins`() {
        val start = TestWorld.state(
            "#W.#",
            "#W.#",
            "####",
        )
        val s = start.after(30, physicsOnly)
        assertEquals(16, s.totalWater())
        assertTrue(kotlin.math.abs(s.amountAt(1, 1) - s.amountAt(2, 1)) <= 1)
    }

    @Test
    fun `water volume is conserved`() {
        var s = TestWorld.state(
            "#W.W..W.#",
            "#.#.##..#",
            "#...#.3.#",
            "##.##...#",
            "#########",
        )
        val total = s.totalWater()
        repeat(40) {
            s = physicsOnly.step(s).state
            assertEquals(total, s.totalWater())
            assertTrue(s.objects.filter { it.isLiquid }.all { it.amount in 1..8 })
        }
    }

    // Gases

    @Test
    fun `steam rises and slides around a ceiling`() {
        val start = TestWorld.state(
            "#######",
            "#.....#",
            "#.###.#",
            "#..V..#",
            "#######",
        )
        // Tie between the two openings goes left: sideways first, then diagonally up.
        assertEquals(listOf(Position(2, 3)), start.after(1, physicsOnly).positionsOf("STEAM"))
        assertEquals(listOf(Position(1, 2)), start.after(2, physicsOnly).positionsOf("STEAM"))
    }

    @Test
    fun `trapped steam gathers in one pocket`() {
        val start = TestWorld.state(
            "###.###",
            "#V....#",
            "#....V#",
            "#######",
        )
        val s = start.after(10, physicsOnly)
        assertEquals(setOf(Position(3, 0), Position(3, 1)), s.positionsOf("STEAM").toSet())
    }

    @Test
    fun `steam bubbles up through water`() {
        val start = TestWorld.state(
            "#.#",
            "#W#",
            "#V#",
            "###",
        )
        val s = start.after(1, physicsOnly)
        assertEquals("STEAM", s.typeAt(1, 1))
        assertEquals(8, s.amountAt(1, 2))
    }
}
