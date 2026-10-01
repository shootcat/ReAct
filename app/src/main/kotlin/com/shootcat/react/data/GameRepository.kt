package com.shootcat.react.data

import android.content.Context
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.WorldData

/** Everything loaded from assets/levels: the world (types + rules) and its levels. */
class GameContent(val world: WorldData, val levels: List<LevelData>) {

    /** Every reaction that exists in this world – the full Discovery matrix. */
    val allRules: List<Rule> = (world.rules + levels.flatMap { it.rules }).distinctBy { it.id }

    fun level(id: String): LevelData? = levels.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = levels.indexOfFirst { it.id == id }
}

object GameRepository {
    private const val LEVEL_DIR = "levels"
    private const val WORLD_FILE = "world_01"

    fun load(context: Context): GameContent {
        val world = LevelLoader.parseWorld(read(context, WORLD_FILE))
        val levels = world.levelIds.map { LevelLoader.parseLevel(read(context, it), world) }
        return GameContent(world, levels)
    }

    private fun read(context: Context, name: String): String =
        context.assets.open("$LEVEL_DIR/$name.json").bufferedReader().use { it.readText() }
}
