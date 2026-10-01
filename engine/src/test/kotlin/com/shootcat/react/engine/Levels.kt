package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.SolutionKind
import com.shootcat.react.engine.model.WorldData
import java.io.File

/** Access to the real level files shipped in app/src/main/assets/levels. */
object Levels {
    private val dir = File(
        System.getProperty("react.levels.dir") ?: error("system property react.levels.dir is not set"),
    )

    fun read(name: String): String = File(dir, "$name.json").readText()

    val world: WorldData by lazy { LevelLoader.parseWorld(read("world_01")) }

    fun level(id: String): LevelData = LevelLoader.parseLevel(read(id), world)

    fun simulate(level: LevelData, setup: GameState): SimulationResult =
        Simulator(level, RuleEngine(world.types, level.rules)).run(setup)

    class Attempt(val result: SimulationResult, val solutions: Set<SolutionKind>) {
        val solved: Boolean get() = result.outcome == Outcome.SUCCESS
    }

    /** Applies the player's moves to a level and runs the simulation. */
    fun attempt(levelId: String, vararg moves: Pair<String, Position>): Attempt {
        val level = level(levelId)
        var setup = level.initialState()
        for ((id, to) in moves) {
            setup = setup.withObjectMoved(id, to) ?: error("move of $id to $to is not allowed in $levelId")
        }
        val result = simulate(level, setup)
        val kinds = SolutionClassifier.classify(level, setup, result).map { it.kind }.toSet()
        return Attempt(result, kinds)
    }
}
