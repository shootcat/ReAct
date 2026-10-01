package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position

/** One thing the player did: dragged [objectId] from [from] to [to]. */
data class PlayerMove(val objectId: String, val from: Position, val to: Position)

/**
 * The world as the player experiences it in live mode. It is immutable, so every value can be kept
 * on an undo stack and restored later. [events] are all positive rule effects of this history.
 */
data class Run(
    val state: GameState,
    val moves: List<PlayerMove> = emptyList(),
    val events: List<RuleEvent> = emptyList(),
    /** The world is still reacting to the last move. */
    val active: Boolean = false,
    val stepsSinceMove: Int = 0,
    /** SUCCESS or OVERLOAD end the run; null while it goes on. */
    val outcome: Outcome? = null,
) {
    val movedObjects: Set<String> get() = moves.map { it.objectId }.toSet()
}

/** The result of one step: the new run and everything that happened in it (including else-effects). */
data class Tick(val run: Run, val events: List<RuleEvent>)

/**
 * Live mode: there is no separate setup phase. Every move immediately sets the world in motion and
 * it keeps reacting step by step until it is at rest again, the goals are reached or the cascade
 * protection stops it. The player may move things while the world is still reacting.
 */
class LiveSimulation(private val level: LevelData, private val engine: RuleEngine) {

    fun start(): Run = Run(level.initialState())

    /** Applies a move, or returns null if it is not allowed (not movable, target taken, run over). */
    fun move(run: Run, objectId: String, to: Position): Run? {
        if (run.outcome != null) return null
        val from = run.state.objectById(objectId)?.position ?: return null
        if (from == to) return null
        val next = run.state.withObjectMoved(objectId, to) ?: return null
        return run.copy(
            state = next,
            moves = run.moves + PlayerMove(objectId, from, to),
            active = true,
            stepsSinceMove = 0,
        )
    }

    fun step(run: Run): Tick {
        if (!run.active || run.outcome != null) return Tick(run.copy(active = false), emptyList())
        val step = engine.step(run.state)
        val events = run.events + step.events.filter { it.positive }
        if (step.overloaded) {
            return Tick(run.copy(state = step.state, events = events, active = false, outcome = Outcome.OVERLOAD), step.events)
        }
        // Nothing moves or changes any more: the world is at rest until the next move.
        if (step.state.objects == run.state.objects) return Tick(run.copy(active = false), emptyList())
        val steps = run.stepsSinceMove + 1
        val success = goalsReached(step.state)
        return Tick(
            run.copy(
                state = step.state,
                events = events,
                active = !success && steps < level.maxSteps,
                stepsSinceMove = steps,
                outcome = if (success) Outcome.SUCCESS else null,
            ),
            step.events,
        )
    }

    /** Runs until the world is at rest (or the run ended). */
    fun settle(run: Run): Run {
        var current = run
        while (current.active) current = step(current).run
        return current
    }

    /** Applies a move and lets the world settle; null if the move is not allowed. */
    fun play(run: Run, objectId: String, to: Position): Run? = move(run, objectId, to)?.let(::settle)

    fun goalsReached(state: GameState): Boolean =
        level.goals.isNotEmpty() && level.goals.all { state.objectById(it.objectId)?.state == it.requiredState }
}
