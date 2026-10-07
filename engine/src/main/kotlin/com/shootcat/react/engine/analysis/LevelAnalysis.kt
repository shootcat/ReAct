package com.shootcat.react.engine.analysis

import com.shootcat.react.engine.LiveSimulation
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.RuleEngine
import com.shootcat.react.engine.Run
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TargetCleared
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetExtinguished
import com.shootcat.react.engine.model.TargetHeated
import com.shootcat.react.engine.model.TargetPreserved
import com.shootcat.react.engine.model.TargetRainTriggered
import com.shootcat.react.engine.model.TargetState
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Exhaustive search over a level's moves – every move is followed by the world settling – and the numbers
 * that say how hard a level is: the proven minimum, how many different minimal solutions there are, how
 * many first moves lead into a dead end, whether the obvious move works and which things are bait.
 *
 * Moves that lead to the same world count once. With marked placement only the placement fields and the
 * cells of objects (to merge or react with) are tried, so the search is complete and fast. A world in
 * which a main goal can no longer be met (the seed has burnt) is not searched any further.
 */
class LevelAnalysis(private val level: LevelData, engine: RuleEngine) {

    data class Move(val objectId: String, val to: Position) {
        override fun toString() = "$objectId@${to.x},${to.y}"
    }

    /** A world after a number of moves: what lies where and which goals have happened. */
    private data class Key(val objects: List<GameObject>, val latched: Set<Int>)

    private val live = LiveSimulation(level, engine)
    val start: Run = live.start()

    private val successorCache = ConcurrentHashMap<Key, List<Pair<Move, Run>>>()

    /** One run per world: the search treats runs with the same [Key] alike, so it keeps only one of them. */
    private val canonical = ConcurrentHashMap<Key, Run>()
    private val unsolvableWithin = ConcurrentHashMap<Key, Int>()

    private fun key(run: Run) = Key(canonical(run.state.objects), run.latched)

    /**
     * Things the world made itself (melt water, steam, ice) are numbered in the order they appeared, so the
     * same world reached in a different order would look different. Their numbers do not matter: drop them.
     */
    private fun canonical(objects: List<GameObject>): List<GameObject> =
        if (objects.none { SPAWNED in it.id }) objects
        else objects.map { if (SPAWNED in it.id) it.copy(id = SPAWNED) else it }
            .sortedWith(compareBy<GameObject>({ it.id }, { it.position.y }, { it.position.x }))

    private fun solved(run: Run) = run.outcome == Outcome.SUCCESS

    /** Over, or a main goal can no longer be met whatever comes next: nothing more to search here. */
    private fun lost(run: Run) = (run.outcome != null && !solved(run)) || hopeless(run)

    private val stateGoals = level.goals.withIndex().filter { (_, g) -> !g.optional && (g is TargetState || g is TargetPreserved) }
    private val reachable = ConcurrentHashMap<Pair<String, String>, Set<String>>()

    /**
     * A main goal about one thing that can never be met again: the thing is gone, or it is in a state
     * from which no rule leads to the one asked for (a withered seed never sprouts). Merges can change
     * what a thing is, so things that merge are never given up on.
     */
    private fun hopeless(run: Run): Boolean = stateGoals.any { (index, goal) ->
        if (index in run.latched) return@any false
        when (goal) {
            is TargetState -> {
                val o = run.state.objectById(goal.objectId) ?: return@any !goal.vanishes
                o.state != goal.state && level.merges.none { o.type == it.a || o.type == it.b } &&
                    goal.state !in reachable.getOrPut(o.type to o.state) { statesFrom(o) }
            }
            // A protected thing that is gone, turned into something else or can never get back to how it was.
            is TargetPreserved -> goal.things.any { kept ->
                val o = run.state.objectById(kept.id) ?: return@any true
                (kept.type != null && o.type != kept.type) ||
                    o.state != kept.state && kept.state !in reachable.getOrPut(o.type to o.state) { statesFrom(o) }
            }
            else -> false
        }
    }

    /** Every state [o] could still get into: through the rules, or through its own physics (burning down). */
    private fun statesFrom(o: GameObject): Set<String> {
        val physics = o.properties.filterKeys { it.endsWith("_state") }.values
        val seen = mutableSetOf(o.state)
        val queue = ArrayDeque(listOf(o.state))
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            val next = buildList {
                addAll(physics)
                for (rule in level.rules) {
                    val c = rule.conditions
                    if (c.target == o.type && (c.targetState == null || c.targetState == state)) {
                        rule.effect.targetState?.let(::add)
                        rule.elseEffect?.targetState?.let(::add)
                    }
                    if ((c.source == null || c.source == o.type) && (c.sourceState == null || c.sourceState == state)) {
                        rule.effect.sourceState?.let(::add)
                    }
                }
            }
            for (n in next) if (seen.add(n)) queue += n
        }
        return seen
    }

    /** Cells worth dropping something on: placement fields and objects, or every buildable cell. */
    fun targets(state: GameState): List<Position> {
        val fields = state.placement
        if (fields != null) return (fields + state.objects.map { it.position }).distinct()
        return (0 until state.height).flatMap { y -> (0 until state.width).map { x -> Position(x, y) } }
            .filter { !state.isWall(it) && it !in state.noBuild }
    }

    /** Every allowed move from [run] and the settled world after it; moves with the same result count once. */
    fun successors(run: Run): List<Pair<Move, Run>> {
        if (run.outcome != null) return emptyList()
        return successorCache.getOrPut(key(run)) {
            val candidates = run.state.objects.filter { it.canBePickedUp }.flatMap { o -> targets(run.state).map { Move(o.id, it) } }
            val results = candidates.parallelStream()
                .map { m -> live.play(run, m.objectId, m.to)?.let { m to it } }
                .toList()
                .filterNotNull()
            val seen = HashSet<Key>()
            results.filter { seen.add(key(it.second)) }.map { (m, after) -> m to intern(after) }
        }
    }

    private fun intern(run: Run): Run {
        val k = key(run)
        return canonical[k] ?: canonical.putIfAbsent(k, run.copy(events = emptyList())) ?: canonical.getValue(k)
    }

    /** The fewest moves that solve the level, searched up to [limit] moves: one such solution, or null. */
    fun shortest(limit: Int): List<Move>? {
        val seen = HashSet<Key>().apply { add(key(start)) }
        var frontier = listOf(emptyList<Move>() to start)
        for (depth in 1..limit) {
            val next = mutableListOf<Pair<List<Move>, Run>>()
            for ((moves, run) in frontier) {
                for ((m, after) in successors(run)) {
                    if (solved(after)) return moves + m
                    if (!lost(after) && seen.add(key(after))) next += (moves + m) to after
                }
            }
            frontier = next
        }
        return null
    }

    /**
     * The same complete search as [shortest], for levels too big to keep every world in memory: starting at
     * [from], it caches nothing, deduplicates only the layers it still has to expand and merely checks the
     * last one. Returns a solution with at most [limit] moves, or null if there is none.
     */
    fun shortestWithin(limit: Int, from: Run = start): List<Move>? {
        if (solved(from)) return emptyList()
        val seen = HashSet<Key>().apply { add(key(from)) }
        var frontier = listOf(emptyList<Move>() to from)
        for (depth in 1..limit) {
            val expand = depth < limit
            val found = java.util.concurrent.atomic.AtomicReference<List<Move>?>(null)
            val next = java.util.concurrent.ConcurrentLinkedQueue<Pair<List<Move>, Run>>()
            frontier.parallelStream().forEach { (moves, run) ->
                if (found.get() != null) return@forEach
                for (o in run.state.objects.filter { it.canBePickedUp }) {
                    for (to in targets(run.state)) {
                        val after = live.play(run, o.id, to) ?: continue
                        if (solved(after)) found.compareAndSet(null, moves + Move(o.id, to))
                        else if (expand && !lost(after)) next += (moves + Move(o.id, to)) to after.copy(events = emptyList())
                    }
                }
            }
            found.get()?.let { return it }
            frontier = next.filter { seen.add(key(it.second)) }
        }
        return null
    }

    /** Whether the level can still be solved from [run] with at most [moves] more moves. */
    fun solvable(run: Run, moves: Int): Boolean {
        if (solved(run)) return true
        if (moves <= 0 || lost(run)) return false
        val k = key(run)
        if ((unsolvableWithin[k] ?: -1) >= moves) return false
        for ((_, after) in successors(run)) {
            if (solved(after) || solvable(after, moves - 1)) return true
        }
        unsolvableWithin.merge(k, moves, ::maxOf)
        return false
    }

    /**
     * The solutions with exactly [length] moves (for the minimum: all minimal solutions). Different
     * orders count separately, moves with the same result once. Lists at most [keep] of them.
     */
    fun solutions(length: Int, keep: Int = 500): Pair<Long, List<List<Move>>> {
        val counts = HashMap<Pair<Key, Int>, Long>()
        fun count(run: Run, left: Int): Long {
            if (left == 0) return 0
            return counts.getOrPut(key(run) to left) {
                successors(run).sumOf { (_, after) ->
                    when {
                        solved(after) -> if (left == 1) 1L else 0L
                        lost(after) -> 0L
                        else -> count(after, left - 1)
                    }
                }
            }
        }
        val total = count(start, length)
        val listed = mutableListOf<List<Move>>()
        fun collect(run: Run, left: Int, moves: List<Move>) {
            if (listed.size >= keep || left == 0) return
            for ((m, after) in successors(run)) {
                if (listed.size >= keep) return
                when {
                    solved(after) -> if (left == 1) listed += moves + m
                    !lost(after) && count(after, left - 1) > 0 -> collect(after, left - 1, moves + m)
                }
            }
        }
        collect(start, length, emptyList())
        return total to listed
    }

    /** Of all first moves (one per result): how many leave the level unsolvable within [total] moves in all. */
    fun deadEnds(total: Int): Pair<Int, Int> {
        val first = successors(start)
        val dead = first.count { (_, after) -> !solved(after) && !solvable(after, total - 1) }
        return dead to first.size
    }

    /** Where the main goals are: the things they are about, or their areas. */
    fun goalCells(): Set<Position> {
        val state = start.state
        val cells = mutableSetOf<Position>()
        for (goal in level.mainGoals) {
            when (goal) {
                is TargetState -> state.objectById(goal.objectId)?.let { cells += it.position }
                is TargetPreserved -> goal.area?.let { cells += it.cells() }
                    ?: goal.things.forEach { kept -> state.objectById(kept.id)?.let { cells += it.position } }
                is TargetContainerFilled -> cells += goal.area.cells()
                is TargetHeated -> cells += goal.area.cells()
                is TargetExtinguished -> goal.area?.let { cells += it.cells() }
                    ?: state.objects.filter { it.flag("flame") && it.heatOutput > 0 }.forEach { cells += it.position }
                is TargetCleared -> goal.area?.let { cells += it.cells() }
                    ?: state.objects.filter { it.type in goal.types }.forEach { cells += it.position }
                is TargetRainTriggered -> goal.area?.let { cells += it.cells() }
                else -> Unit
            }
        }
        return cells
    }

    /**
     * The obvious moves: any movable thing onto the placement field nearest to a main goal (with free
     * placement: any free cell next to it).
     */
    fun obviousMoves(): List<Move> {
        val goals = goalCells()
        if (goals.isEmpty()) return emptyList()
        val state = start.state
        val candidates = state.placement?.toList()
            ?: goals.flatMap { it.neighbours() }.filter { state.isBuildable(it) }.distinct()
        if (candidates.isEmpty()) return emptyList()
        fun distance(p: Position) = goals.minOf { abs(it.x - p.x) + abs(it.y - p.y) }
        val best = candidates.minOf(::distance)
        val nearest = candidates.filter { distance(it) == best }
        return state.objects.filter { it.isMovable }.flatMap { o -> nearest.map { Move(o.id, it) } }
            .filter { live.play(start, it.objectId, it.to) != null }
    }

    /** Whether [move] as the first move starts a solution with [length] moves in all. */
    fun startsSolution(move: Move, length: Int): Boolean {
        val after = live.play(start, move.objectId, move.to) ?: return false
        return solved(after) || (length > 1 && solvable(after, length - 1))
    }

    private companion object {
        const val SPAWNED = "#"
    }

    private fun com.shootcat.react.engine.model.Area.cells(): List<Position> =
        (y0..y1).flatMap { y -> (x0..x1).map { x -> Position(x, y) } }
}
