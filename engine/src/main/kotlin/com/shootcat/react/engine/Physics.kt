package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props
import com.shootcat.react.engine.model.TypeCatalog
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 2: forces, movement and heat, in a fixed order. Solids move at most one cell per step.
 *
 * 1. Solids ([Props.GRAVITY]) fall. Into a liquid they sink if they are denser, otherwise they rest
 *    on its surface. Gas below them is pushed aside. Loose material ([Props.GRANULAR]) slides off heaps.
 * 2. Solids lighter than the liquid float up through it (buoyancy).
 * 3. Gases ([Props.GAS]) are volumes that behave like an upside-down liquid: they rise, bubble up
 *    through liquids, run along ceilings towards openings and fill closed chambers from the top.
 * 4. Liquids ([Props.LIQUID]) fall, run off towards nearby edges and pits and otherwise level out.
 *    Different liquids layer by density (oil floats on water, water sinks below it).
 * 5. Gas pressure pushes barriers ([Props.PUSHABLE]): every connected body of air has a pressure
 *    (gas per cell), and a barrier between two bodies moves away from the higher one.
 * 6. Heat flows through conductors ([Props.CONDUCTS]), losing one degree per cell.
 * 7. Burning things use up their fuel ([Props.FUEL]).
 *
 * Flows are computed from one snapshot so left and right are treated alike; ties go left.
 */
internal object Physics {

    private val bottomUp = compareByDescending<GameObject> { it.position.y }.thenBy { it.position.x }.thenBy { it.id }
    private val topDown = compareBy<GameObject> { it.position.y }.thenBy { it.position.x }.thenBy { it.id }
    private val readingOrder = compareBy<Position>({ it.y }, { it.x })

    fun apply(world: MutableWorld, types: TypeCatalog) {
        val moved = HashSet<String>()
        solids(world, moved)
        floaters(world, moved)
        fluids(world, types, gas = true)
        fluids(world, types, gas = false)
        pressure(world)
        heat(world)
        combustion(world, types)
    }

    private fun solids(world: MutableWorld, moved: MutableSet<String>) {
        for (candidate in world.all().filter { it.falls && !it.isFluid }.sortedWith(bottomUp)) {
            val o = world.byId(candidate.id) ?: continue
            val below = o.position.down()
            if (world.isFree(below)) {
                world.move(o.id, below)
                moved += o.id
                continue
            }
            val b = world.at(below)
            if (b != null && ((b.isLiquid && o.density > b.density) || b.isGas)) {
                world.swap(o.id, b.id)
                moved += o.id
                moved += b.id
                continue
            }
            // Sand slides off a heap: diagonally down if both the side and the cell below it are free.
            if (o.isGranular) {
                val dx = SIDES.firstOrNull { dx ->
                    world.isFree(Position(o.position.x + dx, o.position.y)) &&
                        world.isFree(Position(o.position.x + dx, o.position.y + 1))
                }
                if (dx != null) {
                    world.move(o.id, Position(o.position.x + dx, o.position.y + 1))
                    moved += o.id
                }
            }
        }
    }

    /** Buoyancy: a solid lighter than the liquid above it rises through it. */
    private fun floaters(world: MutableWorld, moved: MutableSet<String>) {
        val solids = world.all().filter { it.falls && !it.isFluid }
        for (candidate in solids.sortedWith(topDown)) {
            if (candidate.id in moved) continue
            val o = world.byId(candidate.id) ?: continue
            val above = world.at(o.position.up()) ?: continue
            if (above.isLiquid && above.density > o.density && above.amount * 2 >= above.capacity) {
                world.swap(o.id, above.id)
                moved += o.id
            }
        }
    }

    // ---------------------------------------------------------------- liquids and gases

    /**
     * Moves all liquids ([gas] = false, they fall) or all gases ([gas] = true, they rise).
     * Both are volumes: each cell holds an amount up to its capacity and the total never changes.
     */
    private fun fluids(world: MutableWorld, types: TypeCatalog, gas: Boolean) {
        val dy = if (gas) -1 else 1
        fun isKind(o: GameObject) = if (gas) o.isGas else o.isLiquid

        // Falling (rising): move on, or top up the next cell.
        for (candidate in world.all().filter(::isKind).sortedWith(if (gas) topDown else bottomUp)) {
            val o = world.byId(candidate.id) ?: continue
            val next = Position(o.position.x, o.position.y + dy)
            if (world.isFree(next)) {
                world.move(o.id, next)
                continue
            }
            val n = world.at(next) ?: continue
            if (n.type == o.type && n.amount < n.capacity) {
                val transfer = min(o.amount, n.capacity - n.amount)
                world.setAmount(n.id, n.amount + transfer)
                world.setAmount(o.id, o.amount - transfer)
            } else if (gas && n.isLiquid) {
                // Bubbles rise through water.
                world.swap(o.id, n.id)
            } else if (!gas && n.isLiquid && o.density > n.density) {
                // Layering: the denser liquid sinks below the lighter one (water under oil).
                world.swap(o.id, n.id)
            }
        }

        // Liquid next to a gas-filled drop pours into it and pushes the gas aside (it rises next step).
        if (!gas) {
            for (candidate in world.all().filter { it.isLiquid }.sortedWith(topDown)) {
                val o = world.byId(candidate.id) ?: continue
                if (!isSupported(world, o, dy)) continue
                for (dx in SIDES) {
                    val side = world.at(Position(o.position.x + dx, o.position.y))
                    if (side == null || !side.isGas) continue
                    val below = Position(side.position.x, side.position.y + 1)
                    val b = world.at(below)
                    if (world.isFree(below) || b?.isGas == true || (b != null && b.type == o.type && b.amount < b.capacity)) {
                        world.swap(o.id, side.id)
                        break
                    }
                }
            }
        }

        // Spreading, computed from one snapshot so left and right are treated alike.
        data class Flow(val from: Position, val to: Position, val amount: Int, val type: String)
        val flows = mutableListOf<Flow>()
        for (o in world.all().filter(::isKind).sortedWith(topDown)) {
            if (!isSupported(world, o, dy)) continue
            val (left, right) = spreadFlows(world, o, dy)
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
                if (t == null) world.fluidCapacity(f.type, types) else t.capacity - t.amount
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
     * How much a resting cell gives to its left and right neighbour this step.
     * If an edge (for gas: an opening above) is close on its row, the fluid runs off towards it.
     * Otherwise it levels out with its neighbours, leaving thin films on wide flat floors and ceilings.
     */
    private fun spreadFlows(world: MutableWorld, o: GameObject, dy: Int): Pair<Int, Int> {
        val a = o.amount
        val toLeft = drainDistance(world, o, -1, dy)
        val toRight = drainDistance(world, o, 1, dy)
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

    /** Distance to the nearest cell on this row the fluid could fall (rise) from, if close enough. */
    private fun drainDistance(world: MutableWorld, o: GameObject, dx: Int, dy: Int): Int? {
        for (d in 1..DRAIN_RANGE) {
            val cell = Position(o.position.x + dx * d, o.position.y)
            if (!world.inBounds(cell) || world.isWall(cell)) return null
            val occupant = world.at(cell)
            if (occupant != null && occupant.type != o.type) return null
            val beyond = Position(cell.x, cell.y + dy)
            val b = world.at(beyond)
            if (world.isFree(beyond) || (b != null && b.type == o.type && b.amount < b.capacity)) return d
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

    /** A cell spreads sideways only when it cannot fall (rise) any further. */
    private fun isSupported(world: MutableWorld, o: GameObject, dy: Int): Boolean {
        val next = Position(o.position.x, o.position.y + dy)
        if (!world.inBounds(next) || world.isWall(next)) return true
        val n = world.at(next) ?: return false
        return when {
            n.type == o.type -> n.amount >= n.capacity
            o.isGas && n.isLiquid -> false
            else -> true
        }
    }

    // ---------------------------------------------------------------- pressure

    /**
     * Every connected body of air (empty cells and gas) has a pressure: its gas divided by its cells.
     * A small closed chamber full of steam has a high pressure, the open room around it almost none.
     * A pushable barrier moves one cell away from the side with the higher pressure if that side
     * exceeds the other by at least its [Props.RESIST] and the cell in front of it is free (or gas).
     */
    private fun pressure(world: MutableWorld) {
        val barriers = world.all().filter { it.flag(Props.PUSHABLE) }.sortedWith(topDown)
        if (barriers.isEmpty()) return
        val air = AirRegions(world)
        for (barrier in barriers) {
            val p = barrier.position
            val resist = barrier.int(Props.RESIST, 1).toLong()
            var best: Position? = null
            var bestForce: Pressure? = null
            for ((dx, dy) in PUSH_DIRECTIONS) {
                val front = Position(p.x + dx, p.y + dy)
                if (!world.isFree(front) && world.at(front)?.isGas != true) continue
                val behind = Position(p.x - dx, p.y - dy)
                val net = air.pressure(behind) - air.pressure(front)
                if (net.atLeast(resist) && (bestForce == null || net > bestForce)) {
                    best = front
                    bestForce = net
                }
            }
            if (best == null) continue
            val gasInFront = world.at(best)
            if (gasInFront != null) world.swap(barrier.id, gasInFront.id) else world.move(barrier.id, best)
        }
    }

    /** Exact pressure as a fraction gas / cells. */
    private data class Pressure(val gas: Long, val cells: Long) : Comparable<Pressure> {
        operator fun minus(o: Pressure) = Pressure(gas * o.cells - o.gas * cells, cells * o.cells)
        fun atLeast(value: Long) = gas >= value * cells
        override fun compareTo(other: Pressure) = (gas * other.cells).compareTo(other.gas * cells)
    }

    private class AirRegions(private val world: MutableWorld) {
        private val regionOf = HashMap<Position, Int>()
        private val pressures = mutableListOf<Pressure>()

        private fun isAir(p: Position): Boolean {
            if (!world.inBounds(p) || world.isWall(p)) return false
            val o = world.at(p)
            return o == null || o.isGas
        }

        fun pressure(p: Position): Pressure {
            if (!isAir(p)) return NONE
            val known = regionOf[p]
            if (known != null) return pressures[known]
            val index = pressures.size
            var gas = 0L
            var cells = 0L
            val queue = ArrayDeque(listOf(p))
            regionOf[p] = index
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                cells++
                gas += world.at(cell)?.amount ?: 0
                for (n in cell.neighbours()) {
                    if (n !in regionOf && isAir(n)) {
                        regionOf[n] = index
                        queue += n
                    }
                }
            }
            pressures += Pressure(gas, cells)
            return pressures[index]
        }

        companion object {
            val NONE = Pressure(0, 1)
        }
    }

    // ---------------------------------------------------------------- heat

    /**
     * Conductors take the heat of hot neighbours (fire, burning wood) and pass it on to touching
     * conductors, one degree less per cell and one cell per step. A conductor never gets hotter than
     * the steady state its current sources allow; without a source it cools by one degree per step.
     */
    private fun heat(world: MutableWorld) {
        val conductors = world.all().filter { it.conducts }
        if (conductors.isEmpty()) return
        fun sourceHeat(c: GameObject) = c.position.neighbours().maxOfOrNull { n ->
            world.at(n)?.takeIf { !it.conducts }?.heatOutput ?: 0
        } ?: 0

        // Steady state: how warm each conductor would get from the sources there are right now.
        val limit = HashMap<String, Int>()
        for (c in conductors) limit[c.id] = sourceHeat(c)
        var changed = true
        while (changed) {
            changed = false
            for (c in conductors) {
                val fromNeighbours = c.position.neighbours().maxOfOrNull { n ->
                    world.at(n)?.takeIf { it.conducts }?.let { limit.getValue(it.id) - 1 } ?: 0
                } ?: 0
                if (fromNeighbours > limit.getValue(c.id)) {
                    limit[c.id] = fromNeighbours
                    changed = true
                }
            }
        }

        // Heat spreads one cell per step towards that state; cooling is gradual.
        val temps = conductors.associate { c ->
            val spread = c.position.neighbours().maxOfOrNull { n ->
                val o = world.at(n)
                when {
                    o == null -> 0
                    o.conducts -> o.temp - 1
                    else -> o.heatOutput
                }
            } ?: 0
            c.id to maxOf(minOf(limit.getValue(c.id), spread), c.temp - 1, 0)
        }
        for ((id, temp) in temps) {
            if (world.byId(id)?.temp != temp) world.setTemp(id, temp)
        }
    }

    /** Burning things with limited [Props.FUEL] burn down and end up in their [Props.BURNT_STATE]. */
    private fun combustion(world: MutableWorld, types: TypeCatalog) {
        for (o in world.all().filter { it.int(Props.FUEL) > 0 && it.heatOutput > 0 }) {
            val burnt = o.burnt + 1
            val end = o.string(Props.BURNT_STATE)
            if (burnt >= o.int(Props.FUEL) && end != null) {
                world.setState(o.id, end)
                if (end in types.require(o.type).vanishStates) world.remove(o.id)
            } else {
                world.setBurnt(o.id, burnt)
            }
        }
    }

    private val PUSH_DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to -1, 0 to 1)

    /** Sliding order for loose material: left first, then right. */
    private val SIDES = intArrayOf(-1, 1)

    /** How far along a row a fluid notices an edge (an opening) it can run off to. */
    private const val DRAIN_RANGE = 8
}
