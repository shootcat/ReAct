package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.LIQUID_DENSITY
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props
import com.shootcat.react.engine.model.TypeCatalog
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 2: forces and movement, in a fixed order. Solids and gases move at most one cell per step.
 *
 * 1. Solids ([Props.GRAVITY]) fall. Into a liquid they sink if they are denser, otherwise they rest
 *    on its surface. Gas below them is pushed aside.
 * 2. Solids lighter than the liquid float up through it.
 * 3. Gases ([Props.RISES]) rise, bubble up through liquids, slide diagonally around obstacles and
 *    drift under ceilings towards the nearest opening.
 * 4. Liquids ([Props.LIQUID]) are volumes: each cell holds an amount. They fall and fill the cell below,
 *    run off towards nearby edges and pits, and otherwise spread sideways until neighbouring levels are
 *    (almost) even. Spreading is computed from one snapshot so it is symmetric; ties go left.
 */
internal object Physics {

    private val bottomUp = compareByDescending<GameObject> { it.position.y }.thenBy { it.position.x }.thenBy { it.id }
    private val topDown = compareBy<GameObject> { it.position.y }.thenBy { it.position.x }.thenBy { it.id }
    private val readingOrder = compareBy<Position>({ it.y }, { it.x })

    fun apply(world: MutableWorld, types: TypeCatalog) {
        val moved = HashSet<String>()
        solids(world, moved)
        floaters(world, moved)
        gases(world, moved)
        liquids(world, types)
    }

    private fun solids(world: MutableWorld, moved: MutableSet<String>) {
        for (candidate in world.all().filter { it.falls && !it.isLiquid }.sortedWith(bottomUp)) {
            val o = world.byId(candidate.id) ?: continue
            val below = o.position.down()
            if (world.isFree(below)) {
                world.move(o.id, below)
                moved += o.id
                continue
            }
            val b = world.at(below) ?: continue
            val sinks = b.isLiquid && o.density > b.density
            if (sinks || b.rises) {
                world.swap(o.id, b.id)
                moved += o.id
                moved += b.id
            }
        }
    }

    private fun floaters(world: MutableWorld, moved: MutableSet<String>) {
        val light = world.all().filter { it.falls && !it.isLiquid && it.density < LIQUID_DENSITY }
        for (candidate in light.sortedWith(topDown)) {
            if (candidate.id in moved) continue
            val o = world.byId(candidate.id) ?: continue
            val above = world.at(o.position.up()) ?: continue
            if (above.isLiquid && above.density > o.density && above.amount * 2 >= above.capacity) {
                world.swap(o.id, above.id)
                moved += o.id
            }
        }
    }

    private fun gases(world: MutableWorld, moved: MutableSet<String>) {
        for (candidate in world.all().filter { it.rises }.sortedWith(topDown)) {
            if (candidate.id in moved) continue
            val o = world.byId(candidate.id) ?: continue
            val p = o.position
            val up = p.up()
            if (world.isFree(up)) {
                world.move(o.id, up)
                continue
            }
            val above = world.at(up)
            if (above != null && above.isLiquid) {
                world.swap(o.id, above.id)
                continue
            }
            if (!o.flag(Props.FLOWS)) continue
            val diagonal = DIRECTIONS.firstOrNull { dx ->
                world.isFree(Position(p.x + dx, p.y)) && world.isFree(Position(p.x + dx, p.y - 1))
            }
            if (diagonal != null) {
                world.move(o.id, Position(p.x + diagonal, p.y - 1))
                continue
            }
            val dx = driftDirection(world, p)
            if (dx != 0) world.move(o.id, Position(p.x + dx, p.y))
        }
    }

    /**
     * Gas under a ceiling drifts towards the nearest cell it can rise from – or where gas has already
     * collected above, so trapped steam gathers in one pocket and builds pressure. Ties go left.
     */
    private fun driftDirection(world: MutableWorld, p: Position): Int {
        fun distance(dx: Int): Int? {
            var d = 1
            while (true) {
                val cell = Position(p.x + dx * d, p.y)
                if (!world.isFree(cell)) return null
                val above = cell.up()
                if (world.isFree(above) || world.at(above)?.rises == true) return d
                d++
            }
        }
        val left = distance(-1)
        val right = distance(1)
        return when {
            left == null && right == null -> 0
            right == null -> -1
            left == null -> 1
            left <= right -> -1
            else -> 1
        }
    }

    private fun liquids(world: MutableWorld, types: TypeCatalog) {
        // Falling: move down, or top up the cell below.
        for (candidate in world.all().filter { it.isLiquid }.sortedWith(bottomUp)) {
            val o = world.byId(candidate.id) ?: continue
            val below = o.position.down()
            if (world.isFree(below)) {
                world.move(o.id, below)
                continue
            }
            val b = world.at(below) ?: continue
            if (b.type == o.type && b.amount < b.capacity) {
                val transfer = min(o.amount, b.capacity - b.amount)
                world.setAmount(b.id, b.amount + transfer)
                world.setAmount(o.id, o.amount - transfer)
            }
        }

        // Spreading, computed from one snapshot so left and right are treated alike.
        data class Flow(val from: Position, val to: Position, val amount: Int, val type: String)
        val flows = mutableListOf<Flow>()
        for (o in world.all().filter { it.isLiquid }.sortedWith(topDown)) {
            if (!isSupported(world, o)) continue
            val (left, right) = spreadFlows(world, o)
            if (left > 0) flows += Flow(o.position, o.position.left(), left, o.type)
            if (right > 0) flows += Flow(o.position, o.position.right(), right, o.type)
        }
        // No cell may overflow: each target accepts at most the room it had in the snapshot.
        val room = HashMap<Position, Int>()
        val delta = HashMap<Position, Int>()
        val kind = HashMap<Position, String>()
        for (f in flows) {
            val free = room.getOrPut(f.to) {
                val t = world.at(f.to)
                if (t == null) world.liquidCapacity(f.type, types) else t.capacity - t.amount
            }
            val amount = min(f.amount, free)
            if (amount <= 0) continue
            room[f.to] = free - amount
            delta[f.to] = (delta[f.to] ?: 0) + amount
            delta[f.from] = (delta[f.from] ?: 0) - amount
            kind[f.to] = f.type
        }
        for (p in delta.keys.sortedWith(readingOrder)) {
            val d = delta.getValue(p)
            if (d == 0) continue
            val existing = world.at(p)
            if (existing != null) {
                world.setAmount(existing.id, existing.amount + d)
            } else if (d > 0) {
                world.spawn(kind.getValue(p), p, types, amount = d)
            }
        }
    }

    /**
     * How much a resting liquid cell gives to its left and right neighbour this step.
     * If an edge or pit is close on its row, the water runs off towards it (drainage).
     * Otherwise it levels out with its neighbours, leaving thin puddles on wide flat floors.
     */
    private fun spreadFlows(world: MutableWorld, o: GameObject): Pair<Int, Int> {
        val a = o.amount
        val toLeft = drainDistance(world, o, -1)
        val toRight = drainDistance(world, o, 1)
        if (toLeft != null || toRight != null) {
            val runoff = max(1, a / 2)
            return when {
                toRight == null || (toLeft != null && toLeft < toRight) -> min(runoff, a) to 0
                toLeft == null || toRight < toLeft -> 0 to min(runoff, a)
                else -> {
                    val left = (a + 1) / 2
                    left to (a - left)
                }
            }
        }
        var left = levelFlow(world, o, -1)
        var right = levelFlow(world, o, 1)
        if (left + right > a) {
            left = min(left, (a + 1) / 2)
            right = min(right, a - left)
        }
        return left to right
    }

    /** Distance to the nearest cell on this row the liquid could drop from, if close enough. */
    private fun drainDistance(world: MutableWorld, o: GameObject, dx: Int): Int? {
        for (d in 1..DRAIN_RANGE) {
            val cell = Position(o.position.x + dx * d, o.position.y)
            if (!world.inBounds(cell) || world.isWall(cell)) return null
            val occupant = world.at(cell)
            if (occupant != null && occupant.type != o.type) return null
            val below = cell.down()
            val b = world.at(below)
            if (world.isFree(below) || (b != null && b.type == o.type && b.amount < b.capacity)) return d
        }
        return null
    }

    private fun levelFlow(world: MutableWorld, o: GameObject, dx: Int): Int {
        val n = Position(o.position.x + dx, o.position.y)
        if (!world.inBounds(n) || world.isWall(n)) return 0
        val other = world.at(n) ?: return if (o.amount >= 2) max(1, o.amount / 4) else 0
        if (other.type != o.type) return 0
        val diff = o.amount - other.amount
        return if (diff >= 2) max(1, diff / 4) else 0
    }

    /** A liquid cell spreads only when it cannot fall any further. */
    private fun isSupported(world: MutableWorld, o: GameObject): Boolean {
        val below = o.position.down()
        if (!world.inBounds(below) || world.isWall(below)) return true
        val b = world.at(below) ?: return false
        return if (b.type == o.type) b.amount >= b.capacity else true
    }

    private val DIRECTIONS = intArrayOf(-1, 1)

    /** How far along a row water notices an edge it can run off. */
    private const val DRAIN_RANGE = 8
}
