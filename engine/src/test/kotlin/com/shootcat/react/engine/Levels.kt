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

    fun live(level: LevelData): LiveSimulation = LiveSimulation(level, RuleEngine(world.types, level.rules))

    class Attempt(val run: Run, val steps: Int, val solutions: Set<SolutionKind>) {
        val solved: Boolean get() = run.outcome == Outcome.SUCCESS
        val rules: Set<String> get() = run.events.map { it.ruleId }.toSet()
    }

    /** Plays the moves one after another in live mode; after each one the world reacts until it rests. */
    fun attempt(levelId: String, vararg moves: Pair<String, Position>): Attempt {
        val level = level(levelId)
        val live = live(level)
        var run = live.start()
        var steps = 0
        for ((id, to) in moves) {
            run = live.move(run, id, to) ?: error("move of $id to $to is not allowed in $levelId")
            while (run.active) {
                run = live.step(run).run
                steps++
            }
        }
        val kinds = SolutionClassifier.classify(level, run).map { it.kind }.toSet()
        return Attempt(run, steps, kinds)
    }
}
