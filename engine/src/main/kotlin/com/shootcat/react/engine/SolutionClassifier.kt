package com.shootcat.react.engine

import com.shootcat.react.engine.model.EventRequirement
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.SolutionSpec

/** Decides which of a level's solution classes (Standard / Minimal / System-Override) a run satisfies. */
object SolutionClassifier {

    /** Ids of objects the player moved away from their starting position. */
    fun movedObjects(level: LevelData, setup: GameState): Set<String> {
        val start = level.initialState()
        return setup.objects
            .filter { start.objectById(it.id)?.position != it.position }
            .map { it.id }
            .toSet()
    }

    fun classify(level: LevelData, setup: GameState, result: SimulationResult): List<SolutionSpec> {
        if (result.outcome != Outcome.SUCCESS) return emptyList()
        val events = result.allEvents.filter { it.positive }
        val moved = movedObjects(level, setup)
        return level.solutions.filter { spec ->
            spec.requires.all { req -> events.any { it.satisfies(req) } } &&
                (spec.maxMoved == null || moved.size <= spec.maxMoved) &&
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
