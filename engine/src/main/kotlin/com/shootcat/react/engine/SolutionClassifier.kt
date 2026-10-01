package com.shootcat.react.engine

import com.shootcat.react.engine.model.EventRequirement
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.SolutionSpec

/** Decides which of a level's solution classes (Standard / Minimal / System-Override) a run satisfies. */
object SolutionClassifier {

    fun classify(level: LevelData, run: Run): List<SolutionSpec> {
        if (run.outcome != Outcome.SUCCESS) return emptyList()
        val events = run.events.filter { it.positive }
        val moved = run.movedObjects
        return level.solutions.filter { spec ->
            spec.requires.all { req -> events.any { it.satisfies(req) } } &&
                (spec.maxMoved == null || run.moves.size <= spec.maxMoved) &&
                spec.unmoved.none { it in moved } &&
                spec.moved.all { it in moved } &&
                spec.forbids.none { rule -> events.any { it.ruleId == rule } }
        }
    }

    private fun RuleEvent.satisfies(req: EventRequirement): Boolean =
        ruleId == req.rule &&
            (req.source == null || req.source in sourceTypes) &&
            (req.withoutSource == null || req.withoutSource !in sourceTypes) &&
            (req.target == null || req.target == targetId || req.target == targetType)
}
