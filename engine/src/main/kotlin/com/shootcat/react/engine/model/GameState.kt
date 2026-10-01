package com.shootcat.react.engine.model

/**
 * Immutable snapshot of the world. Objects are kept sorted by id so that two states
 * describing the same world are always equal.
 */
data class GameState(
    val width: Int,
    val height: Int,
    val walls: Set<Position>,
    val objects: List<GameObject>,
    val spawnCounter: Int = 0,
) {
    private val byPosition: Map<Position, GameObject> by lazy { objects.associateBy { it.position } }
    private val byId: Map<String, GameObject> by lazy { objects.associateBy { it.id } }

    fun inBounds(p: Position): Boolean = p.x in 0 until width && p.y in 0 until height

    fun isWall(p: Position): Boolean = p in walls

    fun objectAt(p: Position): GameObject? = byPosition[p]

    fun objectById(id: String): GameObject? = byId[id]

    fun isFree(p: Position): Boolean = inBounds(p) && !isWall(p) && objectAt(p) == null

    /** Objects top-to-bottom, left-to-right, then by id. */
    fun objectsInReadingOrder(): List<GameObject> =
        objects.sortedWith(compareBy<GameObject>({ it.position.y }, { it.position.x }, { it.id }))

    /** Movable objects resting directly on [p], bottom first, up to the first gap or fixed object. */
    fun loadStack(p: Position): List<GameObject> {
        val stack = mutableListOf<GameObject>()
        var cell = p.up()
        while (true) {
            val o = objectAt(cell) ?: break
            if (!o.flag(Props.GRAVITY)) break
            stack += o
            cell = cell.up()
        }
        return stack
    }

    /** Player action while setting up: move a movable object to a free cell. Returns null if not allowed. */
    fun withObjectMoved(id: String, to: Position): GameState? {
        val obj = objectById(id) ?: return null
        if (!obj.movable) return null
        if (obj.position == to) return this
        if (!isFree(to)) return null
        return copy(objects = objects.map { if (it.id == id) it.copy(position = to) else it })
    }
}
