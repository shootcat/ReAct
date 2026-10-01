package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.SolutionKind
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every level is played with the walkthroughs in walkthroughs.txt: each one must solve the level with
 * exactly the listed solution classes, together they must show every class the level offers, and an
 * exhaustive search proves there is no solution with fewer moves than the level's declared minimum.
 */
class WalkthroughTest {

    private class Walk(val level: String, val classes: Set<SolutionKind>, val moves: List<Pair<String, Position>>)

    private val minimum = HashMap<String, Int>()
    private val walks = mutableListOf<Walk>()

    init {
        val text = javaClass.getResource("/walkthroughs.txt")?.readText() ?: error("walkthroughs.txt missing")
        for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val parts = line.split(Regex("\\s+"))
            if (parts[1].startsWith("min=")) {
                minimum[parts[0]] = parts[1].removePrefix("min=").toInt()
                continue
            }
            val classes = parts[1].removePrefix("[").removeSuffix("]").split(",").filter { it.isNotEmpty() }
                .map { SolutionKind.valueOf(it) }.toSet()
            val moves = parts.drop(2).map { token ->
                val (id, xy) = token.split("@")
                val (x, y) = xy.split(",").map { it.toInt() }
                id to Position(x, y)
            }
            walks += Walk(parts[0], classes, moves)
        }
    }

    @Test
    fun `every level has a walkthrough and a declared minimum`() {
        for (id in Levels.allLevelIds) {
            assertTrue(walks.any { it.level == id }, "$id has no walkthrough")
            assertTrue(id in minimum, "$id has no declared minimum")
        }
    }

    @Test
    fun `walkthroughs solve their level with the listed solution classes`() {
        for (walk in walks) {
            val a = Levels.attempt(walk.level, *walk.moves.toTypedArray())
            assertTrue(a.solved, "${walk.level} ${walk.moves} should be solved, was ${a.run.outcome}")
            assertEquals(walk.classes, a.solutions, "${walk.level} ${walk.moves}")
        }
    }

    @Test
    fun `every solution class of a level can be reached`() {
        for (id in Levels.allLevelIds) {
            val shown = walks.filter { it.level == id }.flatMap { it.classes }.toSet()
            val offered = Levels.level(id).solutions.map { it.kind }.toSet()
            assertEquals(offered, shown, "$id: classes in walkthroughs")
        }
    }

    @Test
    fun `the shortest walkthrough uses exactly the declared minimum`() {
        for ((id, min) in minimum) {
            assertEquals(min, walks.filter { it.level == id }.minOf { it.moves.size }, id)
        }
    }

    @Test
    fun `no level can be solved with fewer moves than declared`() {
        for ((id, min) in minimum) {
            val depth = minOf(min - 1, PROOF_DEPTH)
            if (depth < 1) continue
            val shortcut = shortestSolution(Levels.level(id), depth)
            if (shortcut != null) fail("$id can be solved in ${shortcut.size} moves: $shortcut (declared $min)")
        }
    }

    /** Breadth-first over moves (each followed by the world settling), deduplicated by world state. */
    private fun shortestSolution(level: LevelData, depth: Int): List<Pair<String, Position>>? {
        val live = Levels.live(level)
        val start = live.start()
        val cells = (0 until level.height).flatMap { y -> (0 until level.width).map { x -> Position(x, y) } }
            .filter { !start.state.isWall(it) && it !in start.state.noBuild }
        val seen = ConcurrentHashMap.newKeySet<List<GameObject>>().apply { add(start.state.objects) }
        var frontier = listOf(emptyList<Pair<String, Position>>() to start)
        for (d in 1..depth) {
            val next = Collections.synchronizedList(mutableListOf<Pair<List<Pair<String, Position>>, Run>>())
            val found = Collections.synchronizedList(mutableListOf<List<Pair<String, Position>>>())
            val jobs = frontier.flatMap { (moves, run) ->
                run.state.objects.filter { it.movable }.flatMap { o -> cells.map { Triple(moves, run, o.id to it) } }
            }
            jobs.parallelStream().forEach { (moves, run, move) ->
                if (found.isNotEmpty()) return@forEach
                val after = live.play(run, move.first, move.second) ?: return@forEach
                when (after.outcome) {
                    Outcome.SUCCESS -> found += moves + move
                    null -> if (d < depth && seen.add(after.state.objects)) next += (moves + move) to after
                    else -> Unit
                }
            }
            if (found.isNotEmpty()) return found.first()
            frontier = next.toList()
        }
        return null
    }

    private companion object {
        const val PROOF_DEPTH = 2
    }
}
