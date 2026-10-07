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
    /**
     * The placement fields of a level with marked placement: the only cells the player may put something
     * down on. Null means anywhere that is buildable.
     */
    val placement: Set<Position>? = null,
) {
    private val byPosition: Map<Position, GameObject> by lazy { objects.associateBy { it.position } }
    private val byId: Map<String, GameObject> by lazy { objects.associateBy { it.id } }

    fun inBounds(p: Position): Boolean = p.x in 0 until width && p.y in 0 until height

    fun isWall(p: Position): Boolean = p in walls

    fun objectAt(p: Position): GameObject? = byPosition[p]

    fun objectById(id: String): GameObject? = byId[id]

    fun isFree(p: Position): Boolean = inBounds(p) && !isWall(p) && objectAt(p) == null

    fun isBuildable(p: Position): Boolean = isFree(p) && p !in noBuild && isPlacementField(p)

    /** Whether the player may put things down on [p] at all (always true without marked placement). */
    fun isPlacementField(p: Position): Boolean = placement == null || p in placement

    /** Where the player may put something down: a buildable cell, or one only filled with gas (it gets pushed aside). */
    fun canPlace(p: Position): Boolean {
        if (!inBounds(p) || isWall(p) || p in noBuild || !isPlacementField(p)) return false
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
        if (!obj.canBePickedUp) return null
        if (obj.position == to) return this
        if (!canPlace(to)) return null
        val gas = objectAt(to) ?: return copy(objects = objects.map { if (it.id == id) obj.copy(position = to) else it })
        return replacing(obj, to, gas)
    }

    /**
     * Player action: drop a solid into a liquid. It takes the cell and pushes the liquid out of the way
     * (up first), so the level rises – a stone thrown into a pond.
     */
    fun withLiquidDisplaced(id: String, to: Position): GameState? {
        val obj = objectById(id) ?: return null
        val liquid = objectAt(to)?.takeIf { it.isLiquid } ?: return null
        if (!obj.canBePickedUp || obj.hasAmount || isWall(to) || to in noBuild) return null
        return replacing(obj, to, liquid)
    }

    /** [into] takes the place of the object with [replacedId] (a tree felled into wood); nothing else moves. */
    fun withReplaced(replacedId: String, into: GameObject): GameState? {
        if (objectById(replacedId) == null) return null
        return copy(objects = (objects.filter { it.id != replacedId } + into).sortedBy { it.id })
    }

    /**
     * Two objects merge: [sourceId] disappears and the object [into] takes the target's place. When
     * [into] holds more than one cell can, the rest overflows into the cells around it (up first).
     */
    fun withMerged(sourceId: String, into: GameObject): GameState? {
        val source = objectById(sourceId) ?: return null
        val target = objectAt(into.position) ?: return null
        val rest = objects.filter { it.id != source.id && it.id != target.id }
        if (!into.hasAmount || into.amount <= into.capacity) {
            return copy(objects = (rest + into).sortedBy { it.id })
        }
        val full = into.copy(amount = into.capacity)
        val overflow = source.copy(type = into.type, state = into.state, properties = into.properties, isMovable = false,
            amount = into.amount - into.capacity)
        val spilled = displace(overflow, into.position, vacated = source.position, others = rest + full) ?: return null
        return copy(objects = (rest.map { o -> spilled.firstOrNull { it.id == o.id } ?: o } +
            spilled.filter { s -> rest.none { it.id == s.id } } + full).sortedBy { it.id })
    }

    /** [obj] takes the cell of the fluid [fluid], which is pushed aside. */
    private fun replacing(obj: GameObject, to: Position, fluid: GameObject): GameState? {
        val rest = objects.filter { it.id != obj.id && it.id != fluid.id }
        val displaced = displace(fluid, to, obj.position, rest) ?: return null
        val merged = rest.map { o -> displaced.firstOrNull { it.id == o.id } ?: o }
        val added = displaced.filter { d -> rest.none { it.id == d.id } }
        return copy(objects = (merged + added + obj.copy(position = to)).sortedBy { it.id })
    }

    /**
     * Pushes the fluid [fluid] from [from] to the nearest place it fits (up first): it tops up cells of
     * the same kind or fills one empty cell. [vacated] counts as empty. Returns the changed or new
     * objects, or null if the fluid cannot go anywhere.
     */
    private fun displace(fluid: GameObject, from: Position, vacated: Position, others: List<GameObject>): List<GameObject>? {
        val at = others.associateBy { it.position }
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        var left = fluid.amount
        val result = mutableListOf<GameObject>()
        var placedOwn = false
        while (queue.isNotEmpty() && left > 0) {
            val cell = queue.removeFirst()
            for (n in cell.neighbours()) {
                if (n in seen || !inBounds(n) || isWall(n)) continue
                seen += n
                val o = if (n == vacated) null else at[n]
                when {
                    o == null -> {
                        if (!placedOwn) {
                            result += fluid.copy(position = n, amount = left)
                            placedOwn = true
                            left = 0
                        }
                    }
                    o.type == fluid.type -> {
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
