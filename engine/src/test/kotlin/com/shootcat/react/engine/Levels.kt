package com.shootcat.react.engine

import com.shootcat.react.engine.model.Catalog
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.WorldData
import java.io.File

/** Access to the real game data shipped in app/src/main/assets/levels. */
object Levels {
    private val dir = File(
        System.getProperty("react.levels.dir") ?: error("system property react.levels.dir is not set"),
    )

    fun read(name: String): String = File(dir, "$name.json").readText()

    val catalog: Catalog by lazy { LevelLoader.parseCatalog(read("elements")) }

    val worlds: List<WorldData> by lazy {
        dir.listFiles()!!.map { it.name }.filter { it.startsWith("world_") }.sorted()
            .map { LevelLoader.parseWorld(read(it.removeSuffix(".json")), catalog) }
    }

    val allLevelIds: List<String> by lazy { worlds.flatMap { it.allLevelIds } }

    fun world(levelId: String): WorldData = worlds.first { levelId in it.allLevelIds }

    fun level(id: String): LevelData = LevelLoader.parseLevel(read(id), world(id))

    fun engine(level: LevelData): RuleEngine = RuleEngine(catalog.types, level.rules, wind = level.wind)

    fun simulate(level: LevelData, setup: GameState): SimulationResult = Simulator(level, engine(level)).run(setup)

    fun live(level: LevelData): LiveSimulation = LiveSimulation(level, engine(level))

    /** [achieved]: indices of the optional goals met when the level was solved. */
    class Attempt(val run: Run, val steps: Int, val achieved: Set<Int>) {
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
        return Attempt(run, steps, run.achieved)
    }
}
