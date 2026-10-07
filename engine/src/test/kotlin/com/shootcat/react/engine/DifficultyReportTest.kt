package com.shootcat.react.engine

import com.shootcat.react.engine.analysis.LevelAnalysis
import com.shootcat.react.engine.model.Position
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How hard the test levels are (Leitfaden V2, 5.2, measured as in C.2 of the first guide): the minimum,
 * the number of minimal solutions, the share of first moves that end in a dead end (searched up to the
 * minimum plus two), whether the obvious move – anything onto the placement field next to the main goal –
 * starts a minimal solution, which things are bait, and how many placement fields there are per move.
 *
 * Levels 1 and 2 are searched completely. For level 3 a complete search is only affordable up to
 * [DEEP_LIMIT] moves: that proves there is no short cut. On top, [PLAYOUTS] random games of the minimum
 * plus three moves show how often aimless play solves it; the report says which numbers are proven and
 * which are samples.
 */
class DifficultyReportTest {

    /** What the guide asks of a test level. */
    private class Expectation(val minimum: IntRange, val fieldsPerMove: Double, val complete: Boolean)

    class Report(
        val id: String,
        val minimum: Int,
        val proof: String,
        val solutions: Long?,
        val deadEnds: Int?,
        val firstMoves: Int,
        val obviousWorks: Boolean?,
        val bait: List<String>,
        val fields: Int,
        val playouts: Pair<Int, Int>?,
    )

    private fun declaredMinimum(id: String): Int {
        val line = javaClass.getResource("/walkthroughs.txt")!!.readText().lines()
            .firstOrNull { it.startsWith("$id min=") } ?: error("$id has no declared minimum")
        return line.substringAfter("min=").trim().toInt()
    }

    private fun report(id: String, expectation: Expectation): Report {
        val level = Levels.level(id)
        val analysis = LevelAnalysis(level, Levels.engine(level))
        val fields = level.placement?.size ?: 0
        val movable = level.objects.filter { it.isMovable }.map { it.id }
        val obvious = analysis.obviousMoves()
        if (expectation.complete) {
            val shortest = analysis.shortest(expectation.minimum.last + 1) ?: error("$id cannot be solved within ${expectation.minimum.last + 1} moves")
            val min = shortest.size
            val (count, listed) = analysis.solutions(min)
            val (dead, first) = analysis.deadEnds(min + 2)
            val used = listed.flatten().map { it.objectId }.toSet()
            return Report(
                id, min, "vollständige Suche", count, dead, first,
                obvious.any { analysis.startsSolution(it, min) }, movable.filter { it !in used }, fields, null,
            )
        }
        val min = declaredMinimum(id)
        val short = analysis.shortest(DEEP_LIMIT)
        assertTrue(short == null, "$id can be solved in ${short?.size} moves: $short")
        val first = analysis.successors(analysis.start)
        return Report(
            id, min, "keine Lösung bis $DEEP_LIMIT Züge (vollständig), Walkthrough mit $min", null, null, first.size,
            null, emptyList(), fields, playouts(id, min + 3),
        )
    }

    /** [PLAYOUTS] games of [length] random allowed moves each: how many of them solve the level. */
    private fun playouts(id: String, length: Int): Pair<Int, Int> {
        val level = Levels.level(id)
        val live = Levels.live(level)
        val random = Random(SEED)
        val cells = (0 until level.height).flatMap { y -> (0 until level.width).map { x -> Position(x, y) } }
        var solved = 0
        repeat(PLAYOUTS) {
            var run = live.start()
            var moves = 0
            while (moves < length && run.outcome == null) {
                val targets = cells.filter { run.state.isBuildable(it) || run.state.objectAt(it) != null }
                val movable = run.state.objects.filter { it.isMovable }
                var next: Run? = null
                var tries = 0
                while (next == null && tries < 200) {
                    next = live.play(run, movable.random(random).id, targets.random(random))
                    tries++
                }
                run = next ?: break
                moves++
            }
            if (run.outcome == Outcome.SUCCESS) solved++
        }
        return solved to PLAYOUTS
    }

    @Test
    fun `the test levels are as hard as the guide asks`() {
        val reports = EXPECTED.filterKeys { it in Levels.allLevelIds }.map { (id, e) -> report(id, e) to e }
        val table = buildString {
            appendLine("| Level | Mindestzüge | Beweis | Lösungen | Sackgassen | Naheliegender Zug | Köder | Felder | Zufallsspiele gelöst |")
            appendLine("|---|---|---|---|---|---|---|---|---|")
            for ((r, _) in reports) {
                val dead = r.deadEnds?.let { "%d %% (%d/%d)".format(it * 100 / r.firstMoves.coerceAtLeast(1), it, r.firstMoves) } ?: "–"
                val obvious = when (r.obviousWorks) {
                    true -> "führt zur Lösung"
                    false -> "führt nicht zur Lösung"
                    null -> "–"
                }
                val playouts = r.playouts?.let { (s, n) -> "%d von %d (%.1f %%)".format(s, n, s * 100.0 / n) } ?: "–"
                appendLine(
                    "| ${r.id} | ${r.minimum} | ${r.proof} | ${r.solutions ?: "–"} | $dead | $obvious | " +
                        "${r.bait.joinToString().ifEmpty { "–" }} | ${r.fields} (${"%.1f".format(r.fields.toDouble() / r.minimum)} pro Zug) | $playouts |",
                )
            }
        }
        println(table)
        File("build").mkdirs()
        File("build/difficulty.md").writeText(table)
        val problems = reports.flatMap { (r, e) ->
            listOfNotNull(
                "${r.id}: minimum ${r.minimum} outside ${e.minimum}".takeIf { r.minimum !in e.minimum },
                "${r.id}: ${r.fields} placement fields, needs ${e.fieldsPerMove} per move".takeIf { r.fields < e.fieldsPerMove * r.minimum },
                "${r.id}: the obvious move starts a minimal solution".takeIf { r.obviousWorks == true },
                r.playouts?.let { (s, n) -> "${r.id}: ${s * 100 / n} % of random games solve it".takeIf { s * 100 > n * MAX_RANDOM_PERCENT } },
            )
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private companion object {
        val EXPECTED = mapOf(
            "t_01" to Expectation(4..4, 2.0, complete = true),
            "t_02" to Expectation(4..6, 2.0, complete = true),
            "t_03" to Expectation(9..10, 1.5, complete = false),
        )
        const val DEEP_LIMIT = 6
        const val PLAYOUTS = 3000
        const val SEED = 20261007L
        const val MAX_RANDOM_PERCENT = 5
    }
}
