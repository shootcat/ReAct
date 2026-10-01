package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.after
import com.shootcat.react.engine.TestWorld.count
import com.shootcat.react.engine.TestWorld.positionsOf
import com.shootcat.react.engine.TestWorld.stateOf
import com.shootcat.react.engine.TestWorld.totalWater
import com.shootcat.react.engine.TestWorld.typeAt
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerAndLiquidsTest {

    private val engine = TestWorld.engine()

    // Electricity

    @Test
    fun `current flows through cables to the lamp and the lamp opens the door`() {
        val start = TestWorld.state(
            "#######",
            "#+---L#",
            "######D",
        )
        val s = engine.step(start).state
        assertEquals("ON", s.stateOf("lamp_5_1"))
        assertEquals("UNLOCKED", s.stateOf("door_6_2"))
    }

    @Test
    fun `a gap in the cable keeps the lamp dark - water bridges it`() {
        val dry = TestWorld.state(
            "#######",
            "#+-.-L#",
            "###.###",
            "#######",
        )
        assertEquals("OFF", dry.after(3).stateOf("lamp_5_1"))
        val wet = TestWorld.state(
            "#######",
            "#+-W-L#",
            "###W###",
            "#######",
        )
        assertEquals("ON", wet.after(1).stateOf("lamp_5_1"))
    }

    @Test
    fun `metal carries current, ice and stone do not`() {
        fun lampWith(c: Char) = TestWorld.state(
            "#######",
            "#+-$c-L#",
            "#######",
        ).after(1).stateOf("lamp_5_1")
        assertEquals("ON", lampWith('m'))
        assertEquals("OFF", lampWith('I'))
        assertEquals("OFF", lampWith('S'))
    }

    @Test
    fun `a powered coil glows and melts ice`() {
        val start = TestWorld.state(
            "######",
            "#+-ZI#",
            "######",
        )
        val s = start.after(3)
        assertEquals("HOT", s.stateOf("coil_3_1"))
        assertEquals(0, s.count("ICE"))
    }

    @Test
    fun `a relay only conducts while a signal holds it closed`() {
        val open = TestWorld.state(
            "########",
            "#+-R-L.#",
            "########",
        )
        assertEquals("OFF", open.after(2).stateOf("lamp_5_1"))
        val pressed = TestWorld.state(
            "#S######",
            "#P#.....",
            "#+-R-L.#",
            "########",
        ).let { s -> s.copy(objects = s.objects.map { if (it.type == "LAMP") it.copy(properties = mapOf("channel" to "B")) else it }) }
        assertEquals("ON", pressed.after(3).stateOf("lamp_5_2"))
    }

    @Test
    fun `steam from below spins the turbine, which powers the lamp`() {
        val start = TestWorld.state(
            "########",
            "#T---L.#",
            "#V######",
            "########",
        )
        val s = start.after(2)
        assertEquals("SPINNING", s.stateOf("turbine_1_1"))
        assertEquals("ON", s.stateOf("lamp_5_1"))
    }

    // Layered liquids

    @Test
    fun `oil floats on water - poured below, it rises to the top`() {
        val start = TestWorld.state(
            "#W#",
            "#Q#",
            "###",
        )
        val s = start.after(2)
        assertEquals("OIL", s.typeAt(1, 0))
        assertEquals("WATER", s.typeAt(1, 1))
    }

    @Test
    fun `fire sets oil alight and the flames spread across the slick`() {
        val start = TestWorld.state(
            "#....#",
            "#FQQQ#",
            "######",
        ).let { s -> s.copy(objects = s.objects.map { if (it.type == "OIL") it.copy(amount = 8) else it }) }
        val s = start.after(4)
        assertEquals(listOf("BURNING", "BURNING", "BURNING"), (2..4).map { x -> s.objectAt(Position(x, 1))?.state })
    }

    @Test
    fun `water on lava forms a stone crust and steam`() {
        val start = TestWorld.state(
            "#...#",
            "#.W.#",
            "#AAA#",
            "#####",
        )
        val s = start.after(2)
        assertTrue(s.count("STONE") >= 1, "the lava hardened where the water hit it")
        assertTrue(s.count("STEAM") >= 1)
    }

    @Test
    fun `lava sets wood alight`() {
        val start = TestWorld.state(
            "#O#",
            "#A#",
            "###",
        )
        assertEquals("BURNING", engine.step(start).state.stateOf("wood_1_0"))
    }

    // Sand

    @Test
    fun `sand slides off a heap`() {
        val start = TestWorld.state(
            "#,...#",
            "#,...#",
            "#,...#",
            "######",
        )
        val s = start.after(6)
        assertTrue(s.positionsOf("SAND").any { it.x > 1 }, "sand spread out sideways")
        assertTrue(s.positionsOf("SAND").all { it.y >= 1 })
    }

    @Test
    fun `wet sand soaks up water, stops sliding and carries current`() {
        val start = TestWorld.state(
            "#.WW..#",
            "#+,,L.#",
            "#######",
        )
        val s = start.after(2)
        assertEquals(listOf("WET", "WET"), listOf(s.stateOf("sand_2_1"), s.stateOf("sand_3_1")))
        assertTrue(s.totalWater() < 16)
        assertEquals("ON", s.after(2).stateOf("lamp_4_1"))
    }
}
