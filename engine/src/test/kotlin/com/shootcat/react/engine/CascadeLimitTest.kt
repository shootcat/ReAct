package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Goal
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CascadeLimitTest {

    /** [pairs] columns with a fire on top of an ice block, all melting in the same step. */
    private fun meltingRow(pairs: Int): GameState {
        val objects = mutableListOf<GameObject>()
        for (x in 0 until pairs) {
            objects += TestWorld.types.create("fire_$x", "FIRE", Position(x, 0))
            objects += TestWorld.types.create("ice_$x", "ICE", Position(x, 1))
        }
        val floor = (0 until pairs).map { Position(it, 2) }.toSet()
        return GameState(pairs, 3, floor, objects.sortedBy { it.id })
    }

    @Test
    fun `one hundred reactions in a step are allowed`() {
        val result = TestWorld.engine().step(meltingRow(100))
        assertFalse(result.overloaded)
        assertEquals(100, result.transformations)
    }

    @Test
    fun `the 101st reaction aborts the step`() {
        val result = TestWorld.engine().step(meltingRow(101))
        assertTrue(result.overloaded)
        assertEquals(100, result.transformations)
        assertEquals(100, result.events.size)
    }

    @Test
    fun `the simulation stops with an overload`() {
        val result = Simulator(TestWorld.engine(), listOf(Goal("fire_0", "NEVER")), 50).run(meltingRow(101))
        assertEquals(Outcome.OVERLOAD, result.outcome)
        assertEquals(1, result.lastIndex)
    }

    @Test
    fun `an endless signal loop is cut off deterministically`() {
        // Two lamps that switch on whenever the other one is off: they would flip forever.
        val inverter = Rule(
            "inverter", "Inverter", Trigger.SIGNAL,
            RuleConditions(source = "LAMP", sourceState = "OFF", target = "LAMP"),
            RuleEffect(targetState = "ON"),
            elseEffect = RuleEffect(targetState = "OFF"),
        )
        val lamps = listOf(
            TestWorld.types.create("lamp_a", "LAMP", Position(0, 0), properties = mapOf("channel" to "A")),
            TestWorld.types.create("lamp_b", "LAMP", Position(1, 0), properties = mapOf("channel" to "A")),
        )
        val state = GameState(2, 1, emptySet(), lamps)
        val engine = TestWorld.engine(listOf(inverter))

        val first = engine.step(state)
        val second = engine.step(state)
        assertTrue(first.overloaded)
        assertEquals(100, first.transformations)
        assertEquals(first, second)
    }
}
