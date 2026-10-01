package com.shootcat.react.engine

import com.shootcat.react.engine.TestWorld.count
import com.shootcat.react.engine.TestWorld.stateOf
import com.shootcat.react.engine.TestWorld.typeAt
import com.shootcat.react.engine.model.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEngineTest {

    private val engine = TestWorld.engine()

    @Test
    fun `fire touching ice melts it into water`() {
        val start = TestWorld.state(
            "#####",
            "#FI.#",
            "#####",
        )
        val result = engine.step(start)

        assertEquals(0, result.state.count("ICE"))
        assertEquals("WATER", result.state.typeAt(2, 1))
        val event = result.events.single()
        assertEquals("melt", event.ruleId)
        assertEquals(Phase.STATE, event.phase)
        assertEquals(listOf("FIRE"), event.sourceTypes)
        assertTrue(event.positive)
    }

    @Test
    fun `fire touching water turns it into steam that rises`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "#FW.#",
            "#####",
        )
        val result = engine.step(start)
        assertEquals(0, result.state.count("WATER"))
        // Created in phase 1 at the water's cell, risen one cell in phase 2.
        assertEquals("STEAM", result.state.typeAt(2, 1))
        val event = result.events.single()
        assertEquals("evaporate", event.ruleId)
        assertEquals("LIQUID", event.previousState)
        assertEquals("EVAPORATED", event.newState)
    }

    @Test
    fun `melting and evaporating happen one step after another`() {
        val start = TestWorld.state(
            "#####",
            "#...#",
            "#FI.#",
            "#####",
        )
        val first = engine.step(start)
        assertEquals("WATER", first.state.typeAt(2, 2))
        val second = engine.step(first.state)
        assertEquals(0, second.state.count("WATER"))
        assertEquals(1, second.state.count("STEAM"))
    }

    @Test
    fun `ice without fire stays frozen in place`() {
        val start = TestWorld.state(
            "#####",
            "#.I.#",
            "#...#",
            "#####",
        )
        val result = engine.step(start)
        assertEquals(start.objects, result.state.objects)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `touch needs orthogonal contact`() {
        val start = TestWorld.state(
            "#####",
            "#F..#",
            "#.I.#",
            "#####",
        )
        assertEquals(start.objects, engine.step(start).state.objects)
    }

    @Test
    fun `state changes happen before movement in the same step`() {
        val start = TestWorld.state(
            "#####",
            "#FI.#",
            "#...#",
            "#####",
        )
        val result = engine.step(start)
        // Melted in phase 1, fell one cell in phase 2.
        assertEquals("WATER", result.state.typeAt(2, 2))
        assertEquals(null, result.state.typeAt(2, 1))
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
        assertEquals(listOf("water_button", "button_door"), result.events.map { it.ruleId })
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
        val result = engine.step(start)
        assertEquals("UP", result.state.stateOf("button_1_2"))
        assertEquals("LOCKED", result.state.stateOf("door_3_1"))
    }

    @Test
    fun `a plate needs enough weight`() {
        val light = TestWorld.state(
            "#.#",
            "#W#",
            "#W#",
            "#P#",
            "###",
        )
        assertEquals("UP", engine.step(light).state.stateOf("plate_1_3"))

        val heavy = TestWorld.state(
            "#W#",
            "#W#",
            "#W#",
            "#P#",
            "###",
        )
        val result = engine.step(heavy)
        assertEquals("PRESSED", result.state.stateOf("plate_1_3"))
        assertEquals(listOf("WATER", "WATER", "WATER"), result.events.single().sourceTypes)
    }

    @Test
    fun `a stone alone is heavy enough`() {
        val start = TestWorld.state(
            "#S#",
            "#P#",
            "###",
        )
        assertEquals("PRESSED", engine.step(start).state.stateOf("plate_1_1"))
    }

    @Test
    fun `a plate springs back when the weight is gone`() {
        val pressed = TestWorld.state(
            "#.#",
            "#P#",
            "###",
        ).let { s -> s.copy(objects = s.objects.map { it.copy(state = "PRESSED") }) }
        val result = engine.step(pressed)
        assertEquals("UP", result.state.stateOf("plate_1_1"))
        assertFalse(result.events.single().positive)
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
    fun `a matching rule wins over another rule's else effect`() {
        // The door listens to a button (pressed) and a plate (up). The button must win.
        val start = TestWorld.state(
            "#W..#",
            "#B.D#",
            "##P##",
            "#####",
        )
        val result = TestWorld.engine().step(start)
        assertEquals("UP", result.state.stateOf("plate_2_2"))
        assertEquals("UNLOCKED", result.state.stateOf("door_3_1"))
    }
}
