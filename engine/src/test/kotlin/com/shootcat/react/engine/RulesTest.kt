package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.after
import com.shootcat.react.engine.TestWorld.amountAt
import com.shootcat.react.engine.TestWorld.count
import com.shootcat.react.engine.TestWorld.stateOf
import com.shootcat.react.engine.TestWorld.totalSteam
import com.shootcat.react.engine.TestWorld.typeAt
import com.shootcat.react.engine.model.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {

    private val engine = TestWorld.engine()

    @Test
    fun `fire melts ice into a full cell of water`() {
        val start = TestWorld.state(
            "######",
            "#.FI.#",
            "######",
        )
        val result = engine.step(start)
        assertEquals(0, result.state.count("ICE"))
        assertEquals("melt", result.events.first().ruleId)
        assertEquals(Phase.STATE, result.events.first().phase)
        // The melt water starts spreading in the same step (phase 2).
        assertEquals(8, result.state.objects.filter { it.isLiquid }.sumOf { it.amount })
    }

    @Test
    fun `water douses fire - the bowl stays, steam rises and some water evaporates`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "#.F.#",
            "#.W.#",
            "#####",
        )
        val result = engine.step(start)
        assertEquals("OUT", result.state.stateOf("fire_2_2"))
        assertEquals(6, result.state.totalSteam(), "three units of water expand into six of steam")
        assertEquals(5, result.state.objects.filter { it.isLiquid }.sumOf { it.amount })
    }

    @Test
    fun `an extinguished fire no longer melts ice`() {
        val start = TestWorld.state(
            "#####",
            "#FI.#",
            "#####",
        ).let { s -> s.copy(objects = s.objects.map { if (it.type == "FIRE") it.copy(state = "OUT") else it }) }
        assertEquals(1, engine.step(start).state.count("ICE"))
    }

    @Test
    fun `steam melts ice and partly condenses`() {
        val start = TestWorld.state(
            "#####",
            "#.I.#",
            "#.V.#",
            "#####",
        )
        val result = engine.step(start)
        assertEquals(0, result.state.count("ICE"))
        assertEquals(4, result.state.totalSteam())
        assertEquals("thaw", result.events.single().ruleId)
    }

    @Test
    fun `water presses the button and the signal opens the door in the same step`() {
        val start = TestWorld.state(
            "#####",
            "#W.D#",
            "#B###",
            "#####",
        )
        val result = engine.step(start)
        assertEquals("PRESSED", result.state.stateOf("button_1_2"))
        assertEquals("UNLOCKED", result.state.stateOf("door_3_1"))
        assertEquals(listOf(Phase.STATE, Phase.SIGNAL), result.events.map { it.phase })
    }

    @Test
    fun `ice does not press the button`() {
        val start = TestWorld.state(
            "#####",
            "#I.D#",
            "#B###",
            "#####",
        )
        assertEquals("UP", engine.step(start).state.stateOf("button_1_2"))
    }

    @Test
    fun `a plate needs real weight`() {
        fun plateAfter(vararg column: String): String? {
            val rows = column.map { "#$it#" } + listOf("#P#", "###")
            return engine.step(TestWorld.state(*rows.toTypedArray())).state.stateOf("plate_1_${column.size}")
        }
        assertEquals("UP", plateAfter("W"), "one cell of water weighs 16")
        assertEquals("PRESSED", plateAfter("W", "W"), "two cells weigh 32")
        assertEquals("PRESSED", plateAfter("S"), "a stone weighs 40")
        assertEquals("UP", plateAfter("I", "I"), "two ice blocks weigh only 28")
    }

    @Test
    fun `a plate springs back when the weight is gone`() {
        val pressed = TestWorld.state("#.#", "#P#", "###").let { s ->
            s.copy(objects = s.objects.map { it.copy(state = "PRESSED") })
        }
        val result = engine.step(pressed)
        assertEquals("UP", result.state.stateOf("plate_1_1"))
        assertFalse(result.events.single().positive)
    }

    @Test
    fun `steam pressure needs enough gas under the piston`() {
        val one = TestWorld.state("#K#", "#V#", "###")
        assertEquals("IDLE", engine.step(one).state.stateOf("piston_1_0"), "one cell of steam pushes with 8")
        val two = TestWorld.state("##K##", "#.V.#", "#.V.#", "#####")
        assertEquals("PUSHED", engine.step(two).state.stateOf("piston_2_0"))
    }

    @Test
    fun `a signal opens a hatch for good and the stone on it falls`() {
        val start = TestWorld.state(
            "#####",
            "#.S.#",
            "#.H.#",
            "#K..#",
            "#V..#",
            "#V..#",
            "#####",
        )
        val result = engine.step(start)
        assertEquals(0, result.state.count("HATCH"))
        assertEquals("STONE", start.after(6).typeAt(2, 5))
    }

    @Test
    fun `doors only listen to their own channel`() {
        val start = TestWorld.state(
            "#S..#",
            "#P.D#",
            "#####",
        ).let { s ->
            s.copy(objects = s.objects.map { if (it.type == "DOOR") it.copy(properties = mapOf("channel" to "B")) else it })
        }
        val result = engine.step(start)
        assertEquals("PRESSED", result.state.stateOf("plate_1_1"))
        assertEquals("LOCKED", result.state.stateOf("door_3_1"))
    }

    @Test
    fun `any signal beats another rule's else effect`() {
        val start = TestWorld.state(
            "#W..#",
            "#B.D#",
            "##P##",
            "#####",
        )
        val result = engine.step(start)
        assertEquals("UP", result.state.stateOf("plate_2_2"))
        assertEquals("UNLOCKED", result.state.stateOf("door_3_1"))
    }

    @Test
    fun `state changes happen before movement`() {
        val start = TestWorld.state(
            "#####",
            "#FI.#",
            "#...#",
            "#####",
        )
        val result = engine.step(start)
        // Melted in phase 1 – fire and melt water both fell in phase 2.
        assertTrue(result.state.amountAt(2, 2) > 0)
        assertEquals("FIRE", result.state.typeAt(1, 2))
    }
}
