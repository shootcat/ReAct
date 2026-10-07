package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.after
import com.shootcat.react.engine.TestWorld.count
import com.shootcat.react.engine.TestWorld.positionsOf
import com.shootcat.react.engine.TestWorld.stateOf
import com.shootcat.react.engine.TestWorld.tempAt
import com.shootcat.react.engine.TestWorld.totalSteam
import com.shootcat.react.engine.TestWorld.totalWater
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeatAndPressureTest {

    private val engine = TestWorld.engine()

    // Heat conduction

    @Test
    fun `heat travels through metal one cell per step and fades with distance`() {
        val start = TestWorld.state(
            "##########",
            "#FMMMMMMM#",
            "##########",
        )
        val s = start.after(8)
        assertEquals((6 downTo 0).toList(), (2..8).map { s.tempAt(it, 1) })
        assertEquals("HOT", s.stateOf("metal_7_1"), "five cells away it is still warm")
        assertEquals("COLD", s.stateOf("metal_8_1"), "the heat does not reach further")
        assertEquals(listOf(0, 0), listOf(start.after(1).tempAt(3, 1), start.after(1).tempAt(4, 1)))
    }

    @Test
    fun `metal cools down once the fire is out`() {
        val hot = TestWorld.state(
            "#######",
            "#FMMM.#",
            "#######",
        ).after(6)
        assertEquals("HOT", hot.stateOf("metal_4_1"))
        val out = hot.copy(objects = hot.objects.map { if (it.type == "FIRE") it.copy(state = "OUT") else it })
        val cold = out.after(8)
        assertEquals(listOf("COLD", "COLD", "COLD"), (2..4).map { cold.stateOf("metal_${it}_1") })
    }

    @Test
    fun `hot metal melts ice behind a wall`() {
        val start = TestWorld.state(
            "#######",
            "#F#...#",
            "#MMI..#",
            "#######",
        )
        val s = start.after(6)
        assertEquals(0, s.count("ICE"))
        assertTrue(s.totalWater() > 0)
    }

    @Test
    fun `hot metal boils water into steam`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "##W##",
            "#FM##",
            "#####",
        )
        val s = start.after(8)
        assertTrue(s.totalSteam() > 0, "steam rises off the water")
        assertTrue(s.totalWater() < 8)
        assertEquals(2 * (8 - s.totalWater()), s.totalSteam(), "each unit of water becomes two of steam")
    }

    // Wood

    @Test
    fun `fire sets wood alight, it burns down and is gone`() {
        val start = TestWorld.state(
            "#####",
            "#FO.#",
            "#####",
        )
        val burning = start.after(1)
        assertEquals("BURNING", burning.stateOf("wood_2_1"))
        assertEquals(0, start.after(12).count("WOOD"))
    }

    @Test
    fun `burning wood sets its neighbour alight`() {
        val start = TestWorld.state(
            "######",
            "#FOO.#",
            "######",
        )
        assertEquals("BURNING", start.after(2).stateOf("wood_3_1"))
    }

    @Test
    fun `water douses burning wood - it turns into charcoal and steams`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "#.O.#",
            "#.W.#",
            "#####",
        ).let { s -> s.copy(objects = s.objects.map { if (it.type == "WOOD") it.copy(state = "BURNING") else it }) }
        val s = engine.step(start).state
        assertEquals(null, s.objectById("wood_2_2"))
        val coal = s.objects.single { it.type == "CHARCOAL" }
        assertEquals(Position(2, 2), coal.position)
        assertEquals(4, s.totalSteam())
    }

    // Pressure

    @Test
    fun `steam pressure in a closed chamber pushes a gate`() {
        val start = TestWorld.state(
            "#######",
            "#cG...#",
            "#######",
        )
        val s = start.after(1)
        assertEquals(listOf(Position(3, 1)), s.positionsOf("GATE"))
    }

    @Test
    fun `the gate stops once the steam has room to expand`() {
        val start = TestWorld.state(
            "##########",
            "#VG......#",
            "##########",
        )
        // 8 units push while there are at least 2 per cell: 8/1, 8/2, 8/3, 8/4 – then 8/5 is too little.
        assertEquals(listOf(Position(6, 1)), start.after(10).positionsOf("GATE"))
    }

    @Test
    fun `steam in an open room has no pressure`() {
        val start = TestWorld.state(
            "#......#",
            "#......#",
            "#.....##",
            "#V.G...#",
            "########",
        )
        assertEquals(listOf(Position(3, 3)), start.after(10).positionsOf("GATE"))
    }
}
