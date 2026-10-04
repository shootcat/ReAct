package com.shootcat.react.data

import android.content.Context
import com.shootcat.react.engine.LevelLoader
import com.shootcat.react.engine.model.Catalog
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.MergeRule
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.engine.model.WorldData

/** Everything loaded from assets/levels: the element catalog, the worlds and their levels. */
class GameContent(val catalog: Catalog, val worlds: List<WorldData>, levels: List<LevelData>) {

    private val byId: Map<String, LevelData> = levels.associateBy { it.id }

    val types: TypeCatalog get() = catalog.types

    /** Every reaction that exists – the full Discovery matrix. */
    val allRules: List<Rule> = (catalog.rules + levels.flatMap { it.rules }).distinctBy { it.id }

    /** Everything that can be merged by dropping one element onto another. */
    val merges: List<MergeRule> get() = catalog.merges

    fun level(id: String): LevelData? = byId[id]

    fun world(number: Int): WorldData? = worlds.firstOrNull { it.world == number }

    fun worldOf(levelId: String): WorldData? = worlds.firstOrNull { levelId in it.allLevelIds }

    fun isBonus(levelId: String): Boolean = worldOf(levelId)?.bonusLevelId == levelId

    /** "1·07" for a main level, "1·B" for a bonus level. */
    fun label(levelId: String): String {
        val world = worldOf(levelId) ?: return levelId
        if (world.bonusLevelId == levelId) return "${world.world}·B"
        return "${world.world}·%02d".format(world.levelIds.indexOf(levelId) + 1)
    }

    /** The level that follows [levelId] in its world (the bonus comes after the last main level). */
    fun nextInWorld(levelId: String): String? {
        val world = worldOf(levelId) ?: return null
        val index = world.levelIds.indexOf(levelId)
        return when {
            index < 0 -> null
            index + 1 < world.levelIds.size -> world.levelIds[index + 1]
            else -> null
        }
    }
}

object GameRepository {
    private const val LEVEL_DIR = "levels"
    private const val CATALOG_FILE = "elements"

    fun load(context: Context): GameContent {
        val catalog = LevelLoader.parseCatalog(read(context, CATALOG_FILE))
        val worldFiles = context.assets.list(LEVEL_DIR).orEmpty()
            .filter { it.startsWith("world_") && it.endsWith(".json") }
            .sorted()
        val worlds = worldFiles.map { LevelLoader.parseWorld(read(context, it.removeSuffix(".json")), catalog) }
        val levels = worlds.flatMap { world -> world.allLevelIds.map { LevelLoader.parseLevel(read(context, it), world) } }
        return GameContent(catalog, worlds, levels)
    }

    private fun read(context: Context, name: String): String =
        context.assets.open("$LEVEL_DIR/$name.json").bufferedReader().use { it.readText() }
}
