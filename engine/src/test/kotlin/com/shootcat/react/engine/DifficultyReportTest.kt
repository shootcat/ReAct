package com.shootcat.react.engine

import com.shootcat.react.engine.analysis.LevelAnalysis
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How hard the levels with marked placement are, measured by complete search (see the README, section
 * "Schwierigkeit"): the proven minimum, the number of different minimal solutions, the share of first
 * moves that end in a dead end (searched up to the minimum plus two), whether the obvious move – a
 * movable thing on the placement field next to the main goal – starts a minimal solution, and which things
 * are bait: in no minimal solution at all, or listed in [REPURPOSED] because the solution uses them for
 * something else than their obvious purpose.
 *
 * The thresholds follow the level's number in its world: levels 1–2 take 2–3 moves, 3–4 take 3–4 and need
 * 30 % dead ends, 5–7 take 4–6 with 40 %, 8–10 take 6–8 with 50 %. From level 3 on the obvious move must not
 * start a minimal solution, from level 5 on there must be bait.
 */
class DifficultyReportTest {

    class Report(
        val id: String,
        val number: Int,
        val minimum: Int,
        val solutions: Long,
        val deadEnds: Int,
        val firstMoves: Int,
        val obviousWorks: Boolean,
        val bait: List<String>,
    ) {
        val deadShare: Double get() = if (firstMoves == 0) 0.0 else deadEnds.toDouble() / firstMoves
    }

    private fun band(n: Int): IntRange = when {
        n <= 2 -> 2..3
        n <= 4 -> 3..4
        n <= 7 -> 4..6
        else -> 6..8
    }

    private fun deadShareNeeded(n: Int): Double = when {
        n <= 2 -> 0.0
        n <= 4 -> 0.3
        n <= 7 -> 0.4
        else -> 0.5
    }

    private fun report(id: String): Report {
        val level = Levels.level(id)
        val number = Levels.world(id).levelIds.indexOf(id) + 1
        val analysis = LevelAnalysis(level, Levels.engine(level))
        val shortest = analysis.shortest(10) ?: error("$id cannot be solved within 10 moves")
        val min = shortest.size
        val (count, listed) = analysis.solutions(min)
        val (dead, first) = analysis.deadEnds(min + 2)
        val obvious = analysis.obviousMoves()
        val obviousWorks = obvious.any { analysis.startsSolution(it, min) }
        val movable = level.objects.filter { it.isMovable }.map { it.id }
        val used = listed.flatten().map { it.objectId }.toSet()
        val unused = movable.filter { it !in used }
        val repurposed = REPURPOSED[id].orEmpty().keys.filter { it in movable }
        return Report(id, number, min, count, dead, first, obviousWorks, unused + repurposed)
    }

    private companion object {
        /** Things a level's solution uses for something else than their obvious purpose, with the reason. */
        val REPURPOSED: Map<String, Map<String, String>> = mapOf()
    }

    @Test
    fun `levels with placement fields meet the difficulty curve`() {
        val ids = Levels.allLevelIds.filter { Levels.level(it).placement != null }
        val reports = ids.map(::report)
        val table = buildString {
            appendLine("| Level | Mindestzüge | Lösungen | Sackgassen | Naheliegender Zug | Köder |")
            appendLine("|---|---|---|---|---|---|")
            for (r in reports) {
                val share = "%d %% (%d/%d)".format((r.deadShare * 100).toInt(), r.deadEnds, r.firstMoves)
                val obvious = if (r.obviousWorks) "führt zur Lösung" else "führt nicht zur Lösung"
                appendLine("| ${r.id} | ${r.minimum} | ${r.solutions} | $share | $obvious | ${r.bait.joinToString().ifEmpty { "–" }} |")
            }
        }
        println(table)
        File("build").mkdirs()
        File("build/difficulty.md").writeText(table)
        val problems = reports.flatMap { r ->
            listOfNotNull(
                "${r.id}: minimum ${r.minimum} outside ${band(r.number)}".takeIf { r.minimum !in band(r.number) },
                "${r.id}: only ${(r.deadShare * 100).toInt()} % dead ends, needs ${(deadShareNeeded(r.number) * 100).toInt()} %"
                    .takeIf { r.deadShare < deadShareNeeded(r.number) },
                "${r.id}: the obvious move starts a minimal solution".takeIf { r.number >= 3 && r.obviousWorks },
                "${r.id}: no bait".takeIf { r.number >= 5 && r.bait.isEmpty() },
            )
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
