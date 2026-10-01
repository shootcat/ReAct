package com.shootcat.react.engine.model

data class Goal(val objectId: String, val requiredState: String)

enum class SolutionKind(val label: String) {
    /** The obvious solution the puzzle was designed around. */
    STANDARD("Standard-Weg"),
    /** The solution that needs the fewest objects. */
    MINIMAL("Minimal-Weg"),
    /** An unexpected combination of systems. */
    OVERRIDE("System-Override"),
}

/** A rule that must have fired during a run, optionally with/without a given source type involved. */
data class EventRequirement(
    val rule: String,
    val source: String? = null,
    val withoutSource: String? = null,
    /** Object id or type the rule must have acted on. */
    val target: String? = null,
)

data class SolutionSpec(
    val id: String,
    val kind: SolutionKind,
    val label: String,
    val requires: List<EventRequirement> = emptyList(),
    /** Maximum number of moves the player may have made. */
    val maxMoved: Int? = null,
    /** Objects that must still be at their starting position. */
    val unmoved: List<String> = emptyList(),
    /** Objects the player must have moved. */
    val moved: List<String> = emptyList(),
    /** Rules that must not have fired (e.g. "without melting anything"). */
    val forbids: List<String> = emptyList(),
)

data class LevelData(
    val id: String,
    val title: String,
    val world: Int,
    val intro: String,
    val width: Int,
    val height: Int,
    val walls: Set<Position>,
    val objects: List<GameObject>,
    val rules: List<Rule>,
    val goals: List<Goal>,
    val solutions: List<SolutionSpec>,
    val maxSteps: Int,
    val noBuild: Set<Position> = emptySet(),
) {
    fun initialState(): GameState = GameState(width, height, walls, objects.sortedBy { it.id }, noBuild = noBuild)
}

/** A level's spot on the world map; [icon] is an object type that represents the level. */
data class MapNode(val levelId: String, val x: Float, val y: Float, val icon: String? = null)

/** All element types and reactions; every world uses the same physics and chemistry. */
data class Catalog(val types: TypeCatalog, val rules: List<Rule>)

data class WorldData(
    val world: Int,
    val title: String,
    /** The main levels, in order. */
    val levelIds: List<String>,
    val map: List<MapNode>,
    val types: TypeCatalog,
    val rules: List<Rule>,
    /** Extra level that opens once every main level of the world is solved. */
    val bonusLevelId: String? = null,
    /** Element type that stands for the world (world selection). */
    val icon: String? = null,
) {
    val allLevelIds: List<String> get() = levelIds + listOfNotNull(bonusLevelId)
}
