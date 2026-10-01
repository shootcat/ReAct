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

    /** Where the player may drop something: a buildable cell, or one only filled with gas (it gets pushed aside). */
    fun canPlace(p: Position): Boolean {
        if (!inBounds(p) || isWall(p) || p in noBuild) return false
        val o = objectAt(p) ?: return true
        return o.isGas
    }

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

    /**
     * Cells that carry electric current: every active source and everything that carries current
     * connected to one. Current flows instantly through the whole network.
     */
    val powered: Set<Position> by lazy {
        val network = HashSet<Position>()
        val queue = ArrayDeque<Position>()
        for (o in objects) {
            if (o.isPowerSource && network.add(o.position)) queue += o.position
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (n in cell.neighbours()) {
                if (n !in network && objectAt(n)?.carriesPower == true) {
                    network += n
                    queue += n
                }
            }
        }
        network
    }

    /** Whether current reaches the object at [p]: it is part of a live network or touches one. */
    fun isPowered(p: Position): Boolean = p in powered || p.neighbours().any { it in powered }

    /** Player action: move a movable object to a cell where it may be placed. Gas there is pushed aside. */
    fun withObjectMoved(id: String, to: Position): GameState? {
        val obj = objectById(id) ?: return null
        if (!obj.movable) return null
        if (obj.position == to) return this
        if (!canPlace(to)) return null
        val moved = obj.copy(position = to)
        val gas = objectAt(to) ?: return copy(objects = objects.map { if (it.id == id) moved else it })
        val rest = objects.filter { it.id != id && it.id != gas.id }
        val displaced = displaceGas(gas, to, obj.position) ?: return null
        val merged = rest.map { o -> displaced.firstOrNull { it.id == o.id } ?: o }
        val added = displaced.filter { d -> rest.none { it.id == d.id } }
        return copy(objects = (merged + added + moved).sortedBy { it.id })
    }

    /**
     * Pushes the gas from [from] to the nearest place it fits (up first): it tops up a cell of the same gas
     * or fills an empty cell. Returns the changed or new gas objects, or null if it cannot go anywhere.
     */
    private fun displaceGas(gas: GameObject, from: Position, vacated: Position): List<GameObject>? {
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        var left = gas.amount
        val result = mutableListOf<GameObject>()
        var placedOwn = false
        while (queue.isNotEmpty() && left > 0) {
            val cell = queue.removeFirst()
            for (n in cell.neighbours()) {
                if (n in seen || !inBounds(n) || isWall(n)) continue
                seen += n
                val o = if (n == vacated) null else objectAt(n)
                when {
                    o == null -> {
                        if (!placedOwn) {
                            result += gas.copy(position = n, amount = left)
                            placedOwn = true
                            left = 0
                        }
                    }
                    o.type == gas.type -> {
                        val room = o.capacity - o.amount
                        if (room > 0) {
                            val add = minOf(room, left)
                            result += o.copy(amount = o.amount + add)
                            left -= add
                        }
                        queue += n
                    }
                    else -> Unit
                }
                if (left == 0) break
            }
        }
        return if (left == 0) result else null
    }
}
