package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Goal
import com.shootcat.react.engine.model.LevelData

enum class Outcome {
    /** All goals were reached. */
    SUCCESS,
    /** Nothing changes any more and the goals were not reached. */
    STABLE,
    /** Cascade protection stopped a runaway chain reaction. */
    OVERLOAD,
    /** The step limit was reached while the world was still changing. */
    TIMEOUT,
}

/** Frame 0 is the player's setup; frame n is the world after step n. */
data class Frame(
    val index: Int,
    val state: GameState,
    val events: List<RuleEvent>,
    val transformations: Int,
)

data class SimulationResult(val frames: List<Frame>, val outcome: Outcome) {
    val lastIndex: Int get() = frames.lastIndex
    val allEvents: List<RuleEvent> get() = frames.flatMap { it.events }
}

/**
 * Runs the full simulation up front. Because the engine is deterministic, the timeline can then be
 * scrubbed back and forth freely without ever re-simulating.
 */
class Simulator(
    private val engine: RuleEngine,
    private val goals: List<Goal>,
    private val maxSteps: Int,
) {
    constructor(level: LevelData, engine: RuleEngine) : this(engine, level.goals, level.maxSteps)

    fun run(initial: GameState): SimulationResult {
        val frames = mutableListOf(Frame(0, initial, emptyList(), 0))
        if (goalsReached(initial)) return SimulationResult(frames, Outcome.SUCCESS)

        var current = initial
        for (index in 1..maxSteps) {
            val step = engine.step(current)
            if (step.overloaded) {
                frames += Frame(index, step.state, step.events, step.transformations)
                return SimulationResult(frames, Outcome.OVERLOAD)
            }
            if (step.state.objects == current.objects) return SimulationResult(frames, Outcome.STABLE)
            frames += Frame(index, step.state, step.events, step.transformations)
            if (goalsReached(step.state)) return SimulationResult(frames, Outcome.SUCCESS)
            current = step.state
        }
        return SimulationResult(frames, Outcome.TIMEOUT)
    }

    fun goalsReached(state: GameState): Boolean =
        goals.isNotEmpty() && goals.all { state.objectById(it.objectId)?.state == it.requiredState }
}
