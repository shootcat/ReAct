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
    /** Empty cells the player may not drop objects into (e.g. inside closed chambers). */
    val noBuild: Set<Position> = emptySet(),
) {
    private val byPosition: Map<Position, GameObject> by lazy { objects.associateBy { it.position } }
    private val byId: Map<String, GameObject> by lazy { objects.associateBy { it.id } }

    fun inBounds(p: Position): Boolean = p.x in 0 until width && p.y in 0 until height

    fun isWall(p: Position): Boolean = p in walls

    fun objectAt(p: Position): GameObject? = byPosition[p]

    fun objectById(id: String): GameObject? = byId[id]

    fun isFree(p: Position): Boolean = inBounds(p) && !isWall(p) && objectAt(p) == null

    fun isBuildable(p: Position): Boolean = isFree(p) && p !in noBuild

    /** Objects top-to-bottom, left-to-right, then by id. */
    fun objectsInReadingOrder(): List<GameObject> =
        objects.sortedWith(compareBy<GameObject>({ it.position.y }, { it.position.x }, { it.id }))

    /** Everything resting on [p]: the column of falling solids and liquids above it, bottom first. */
    fun loadStack(p: Position): List<GameObject> {
        val stack = mutableListOf<GameObject>()
        var cell = p.up()
        while (true) {
            val o = objectAt(cell) ?: break
            if (!o.falls && !o.isLiquid) break
            stack += o
            cell = cell.up()
        }
        return stack
    }

    /** The connected body of rising gas pushing against [p] from below (a closed chamber's steam). */
    fun liftRegion(p: Position): List<GameObject> {
        val start = objectAt(p.down())?.takeIf { it.rises } ?: return emptyList()
        val seen = linkedSetOf(start.position)
        val queue = ArrayDeque(listOf(start.position))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (n in cell.neighbours()) {
                if (n !in seen && objectAt(n)?.rises == true) {
                    seen += n
                    queue += n
                }
            }
        }
        return seen.map { objectAt(it)!! }
    }

    /** Player action while setting up: move a movable object to a free, buildable cell. */
    fun withObjectMoved(id: String, to: Position): GameState? {
        val obj = objectById(id) ?: return null
        if (!obj.movable) return null
        if (obj.position == to) return this
        if (!isBuildable(to)) return null
        return copy(objects = objects.map { if (it.id == id) it.copy(position = to) else it })
    }
}
