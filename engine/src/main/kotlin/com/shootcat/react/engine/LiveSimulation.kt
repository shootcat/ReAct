package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position

/** One thing the player did: dragged [objectId] from [from] and dropped it on [to]. */
data class PlayerMove(val objectId: String, val from: Position, val to: Position)

/**
 * The world as the player experiences it in live mode. It is immutable, so every value can be kept
 * on an undo stack and restored later exactly. [events] are all positive rule effects of this history.
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
    /** Indices (into the level's goals) of the optional goals that were met when the level was solved. */
    val achieved: Set<Int> = emptySet(),
    /** Goals that count once they happened (it rained) and have happened. */
    val latched: Set<Int> = emptySet(),
    /** For how many steps in a row all main goals have held. */
    val metFor: Int = 0,
) {
    val movedObjects: Set<String> get() = moves.map { it.objectId }.toSet()
}

/** The result of one step: the new run, everything that happened in it and what it sounded like. */
data class Tick(val run: Run, val events: List<RuleEvent>, val cues: List<Cue> = emptyList())

/**
 * Live mode: there is no separate setup phase. Every move immediately sets the world in motion and
 * it keeps reacting step by step until it is at rest again, the goals are met or the cascade
 * protection stops it. The player may move things while the world is still reacting.
 *
 * After every step all goals are checked. The level is solved once all main goals hold and either the
 * world has come to rest or they have held for [STABLE_STEPS] steps in a row – so a pond that only
 * fills for a moment, or a tree the fire is still creeping towards, does not count yet.
 */
class LiveSimulation(private val level: LevelData, private val engine: RuleEngine) {

    private val drops = Drops(engine.types, engine.rules, level.merges)

    fun start(): Run = Run(level.initialState())

    /** What dropping [objectId] on [to] would do, or null if it bounces off (or the run is over). */
    fun drop(run: Run, objectId: String, to: Position): Drop? {
        if (run.outcome != null) return null
        return drops.resolve(run.state, objectId, to)
    }

    /** Applies a drop found by [drop]. */
    fun apply(run: Run, objectId: String, to: Position, drop: Drop): Run {
        val from = run.state.objectById(objectId)?.position ?: to
        return run.copy(
            state = drop.state,
            moves = run.moves + PlayerMove(objectId, from, to),
            active = true,
            stepsSinceMove = 0,
        )
    }

    /** Applies a move, or returns null if it is not allowed (not movable, bounces off, run over). */
    fun move(run: Run, objectId: String, to: Position): Run? = drop(run, objectId, to)?.let { apply(run, objectId, to, it) }

    fun step(run: Run): Tick {
        if (!run.active || run.outcome != null) return Tick(run.copy(active = false), emptyList())
        val step = engine.step(run.state)
        val events = run.events + step.events.filter { it.positive }
        if (step.overloaded) {
            return Tick(run.copy(state = step.state, events = events, active = false, outcome = Outcome.OVERLOAD), step.events, step.cues)
        }
        // Nothing moves or changes any more: the world is at rest until the next move.
        if (step.state.objects == run.state.objects) {
            val solved = goalsReached(run.state, run.moves.size, run.latched)
            return Tick(finish(run.copy(active = false), solved), emptyList())
        }
        val steps = run.stepsSinceMove + 1
        val latched = run.latched + level.goals.indices.filter { level.goals[it].latches && level.goals[it].isMet(step.state, run.moves.size) }
        val met = goalsReached(step.state, run.moves.size, latched)
        val metFor = if (met) run.metFor + 1 else 0
        val next = run.copy(
            state = step.state,
            events = events,
            active = steps < level.maxSteps,
            stepsSinceMove = steps,
            latched = latched,
            metFor = metFor,
        )
        val solved = met && (metFor >= STABLE_STEPS || !next.active)
        return Tick(finish(next, solved), step.events, step.cues)
    }

    private fun finish(run: Run, solved: Boolean): Run =
        if (!solved) run else run.copy(active = false, outcome = Outcome.SUCCESS, achieved = achievedOptional(run.state, run.moves.size))

    /** Runs until the world is at rest (or the run ended). */
    fun settle(run: Run): Run {
        var current = run
        while (current.active) current = step(current).run
        return current
    }

    /** Applies a move and lets the world settle; null if the move is not allowed. */
    fun play(run: Run, objectId: String, to: Position): Run? = move(run, objectId, to)?.let(::settle)

    /** All main goals hold right now ([latched] goals count once they have happened). */
    fun goalsReached(state: GameState, moves: Int, latched: Set<Int> = emptySet()): Boolean =
        level.mainGoals.isNotEmpty() &&
            level.goals.indices.filter { !level.goals[it].optional }.all { it in latched || level.goals[it].isMet(state, moves) }

    private fun achievedOptional(state: GameState, moves: Int): Set<Int> =
        level.goals.indices.filter { level.goals[it].optional && level.goals[it].isMet(state, moves) }.toSet()

    companion object {
        /** Steps the main goals have to hold in a row while the world is still moving. */
        const val STABLE_STEPS = 12
    }
}
