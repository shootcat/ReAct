package com.shootcat.react.engine.model

/** A rectangle of cells; both corners are inclusive. */
data class Area(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    operator fun contains(p: Position): Boolean = p.x in x0..x1 && p.y in y0..y1
    val cells: List<Position> get() = (y0..y1).flatMap { y -> (x0..x1).map { x -> Position(x, y) } }
}

/** The fixed landscape a level is cut into. It never moves and the player cannot drag it. */
enum class Terrain {
    /** Soil: grass grows on it where it meets the open air. */
    EARTH,
    /** Bedrock and boulders. */
    ROCK,
}

/** A steady wind: clouds inside [area] drift [dx] cells per step (negative: to the left). */
data class WindZone(val area: Area, val dx: Int)

/**
 * Dropping one element onto another of a matching kind merges them into a stronger one, e.g. two
 * flames into a big fire. When [result] is the liquid both are made of, their amounts simply add up.
 *
 * With [keep] ("a" or "b") it is a tool instead: the thing of that type stays where it is, untouched, and
 * only the other one turns into [result] in its place – a stone dropped on a tree fells it into wood.
 */
data class MergeRule(
    val id: String,
    val name: String,
    val a: String,
    val b: String,
    val result: String,
    val sound: String? = null,
    val world: Int = 1,
    val keep: String? = null,
) {
    fun matches(x: String, y: String): Boolean = (x == a && y == b) || (x == b && y == a)

    /** The type that survives a tool merge unchanged, or null for an ordinary merge. */
    val keptType: String? get() = when (keep) {
        "a" -> a
        "b" -> b
        else -> null
    }
}

/**
 * What the player has to achieve. The engine checks every goal after each simulation step; the level
 * is solved once all goals that are not [optional] hold and keep holding (see [com.shootcat.react.engine.LiveSimulation]).
 * Optional goals are extra challenges that are judged at that moment.
 */
sealed interface LevelGoal {
    /** The task as the player reads it, e.g. "Fülle den Teich". */
    val text: String
    val optional: Boolean

    /** An event that counts once it has happened (it rained), rather than a state that has to last. */
    val latches: Boolean get() = false

    /** [moves] is the number of moves the player has made so far. */
    fun isMet(state: GameState, moves: Int): Boolean
}

/**
 * At least [min] units of [liquid] lie inside [area] (a pond, a hollow, a basin), and no other liquid:
 * a basin of fresh water holds no salt water. A [full] goal asks for every open cell of the area to be
 * full; [min] is then worked out from the area when the level is loaded.
 */
data class TargetContainerFilled(
    val area: Area,
    val liquid: String,
    val min: Int,
    override val text: String,
    override val optional: Boolean = false,
    val full: Boolean = false,
) : LevelGoal {
    fun amount(state: GameState): Int = state.objects.filter { it.type == liquid && it.position in area }.sumOf { it.amount }

    /** Some other liquid lies in the area. */
    fun spoilt(state: GameState): Boolean = state.objects.any { it.isLiquid && it.type != liquid && it.position in area }

    override fun isMet(state: GameState, moves: Int): Boolean = amount(state) >= min && !spoilt(state)
}

/** At least [min] things of [type] exist anywhere (glass made from sand). */
data class TargetProduced(
    val type: String,
    val min: Int,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    fun count(state: GameState): Int = state.objects.count { it.type == type }
    override fun isMet(state: GameState, moves: Int): Boolean = count(state) >= min
}

/**
 * A liquid inside [area] boils: heat turns some of it into steam. That is something that happens, not a
 * state of the world, so the simulation reports it ([boiledIn]); once it has boiled, the goal stays met.
 */
data class TargetHeated(
    val area: Area,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    override val latches: Boolean get() = true

    /** True if one of the liquid cells that boiled in a step lies in the area. */
    fun boiledIn(boiling: Collection<Position>): Boolean = boiling.any { it in area }

    override fun isMet(state: GameState, moves: Int): Boolean = false
}

/** No open flame burns any more: inside [area], or anywhere if it is null. */
data class TargetExtinguished(
    val area: Area?,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    override fun isMet(state: GameState, moves: Int): Boolean =
        state.objects.none { it.isFlame && (area == null || it.position in area) }
}

/** A cloud rains: inside [area], or anywhere if it is null. Once it has rained, the goal stays met. */
data class TargetRainTriggered(
    val area: Area?,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    override val latches: Boolean get() = true

    override fun isMet(state: GameState, moves: Int): Boolean =
        state.objects.any { it.isRaining && (area == null || it.position in area) }
}

/**
 * [objectId] has reached [state], e.g. a seed that sprouted. If the object can vanish in that state
 * ([vanishes]), the object being gone counts as well.
 */
data class TargetState(
    val objectId: String,
    val state: String,
    val vanishes: Boolean,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    override fun isMet(state: GameState, moves: Int): Boolean {
        val o = state.objectById(objectId) ?: return vanishes
        return o.state == this.state
    }
}

/** One thing a [TargetPreserved] goal protects, as it was at the start ([type] null: any type). */
data class Kept(val id: String, val state: String, val type: String? = null)

/**
 * Everything in [things] is still there and unchanged, e.g. a hut, or every tree of a forest ([area])
 * that must not burn.
 */
data class TargetPreserved(
    val things: List<Kept>,
    override val text: String,
    override val optional: Boolean = true,
    /** The protected area, if the goal is about a whole area rather than one thing. */
    val area: Area? = null,
) : LevelGoal {
    constructor(objectId: String, state: String, text: String, optional: Boolean = true) :
        this(listOf(Kept(objectId, state)), text, optional)

    fun intact(state: GameState, kept: Kept): Boolean =
        state.objectById(kept.id)?.let { (kept.type == null || it.type == kept.type) && it.state == kept.state } == true

    override fun isMet(state: GameState, moves: Int): Boolean = things.all { intact(state, it) }
}

/** Nothing of [types] is left: inside [area], or anywhere if it is null (e.g. all ice melted). */
data class TargetCleared(
    val types: Set<String>,
    val area: Area?,
    override val text: String,
    override val optional: Boolean = false,
) : LevelGoal {
    override fun isMet(state: GameState, moves: Int): Boolean =
        state.objects.none { it.type in types && (area == null || it.position in area) }
}

/** Solved with at most [moves] moves. */
data class TargetMaxMoves(
    val moves: Int,
    override val text: String,
    override val optional: Boolean = true,
) : LevelGoal {
    override fun isMet(state: GameState, moves: Int): Boolean = moves <= this.moves
}

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
    val goals: List<LevelGoal>,
    val maxSteps: Int,
    val noBuild: Set<Position> = emptySet(),
    /** What every wall cell is made of (for drawing the landscape). */
    val terrain: Map<Position, Terrain> = emptyMap(),
    val wind: List<WindZone> = emptyList(),
    val merges: List<MergeRule> = emptyList(),
    /** With marked placement: the placement fields ('+'), the only cells things may be put down on. */
    val placement: Set<Position>? = null,
    /** Which landscape the level is drawn in (1 forest, 2 coast, 3 volcano, 4 frost); its world by default. */
    val look: Int = world,
) {
    val mainGoals: List<LevelGoal> get() = goals.filter { !it.optional }
    val optionalGoals: List<LevelGoal> get() = goals.filter { it.optional }

    fun initialState(): GameState = GameState(width, height, walls, objects.sortedBy { it.id }, noBuild = noBuild, placement = placement)
}

/** A level's spot on the world map; [icon] is an object type that represents the level. */
data class MapNode(val levelId: String, val x: Float, val y: Float, val icon: String? = null)

/** All element types, reactions and merges; every world uses the same physics and chemistry. */
data class Catalog(val types: TypeCatalog, val rules: List<Rule>, val merges: List<MergeRule> = emptyList())

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
    /** Element type that stands for the world (world map). */
    val icon: String? = null,
    val merges: List<MergeRule> = emptyList(),
) {
    val allLevelIds: List<String> get() = levelIds + listOfNotNull(bonusLevelId)
}
