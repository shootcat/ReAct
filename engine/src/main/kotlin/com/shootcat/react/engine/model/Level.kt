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
)

data class SolutionSpec(
    val id: String,
    val kind: SolutionKind,
    val label: String,
    val requires: List<EventRequirement> = emptyList(),
    /** Maximum number of objects the player may have moved. */
    val maxMoved: Int? = null,
    /** Objects that must still be at their starting position. */
    val unmoved: List<String> = emptyList(),
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
) {
    fun initialState(): GameState = GameState(width, height, walls, objects.sortedBy { it.id })
}

data class MapNode(val levelId: String, val x: Float, val y: Float)

data class WorldData(
    val world: Int,
    val title: String,
    val levelIds: List<String>,
    val map: List<MapNode>,
    val types: TypeCatalog,
    val rules: List<Rule>,
)
