package com.shootcat.react.level

import com.shootcat.react.sim.Flag
import com.shootcat.react.sim.Kind
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Organism
import com.shootcat.react.sim.World

/** The cells of a polygon (corners in cells): every cell whose middle lies inside. */
class Area(val cells: IntArray) {
    val size get() = cells.size

    companion object {
        fun of(points: List<List<Int>>, width: Int, height: Int): Area {
            require(points.size >= 3 && points.all { it.size == 2 }) { "an area needs at least three corners [x, y]" }
            val xs = points.map { it[0].toFloat() }
            val ys = points.map { it[1].toFloat() }
            val out = ArrayList<Int>()
            val y0 = ys.min().toInt().coerceAtLeast(0)
            val y1 = ys.max().toInt().coerceAtMost(height - 1)
            val x0 = xs.min().toInt().coerceAtLeast(0)
            val x1 = xs.max().toInt().coerceAtMost(width - 1)
            for (y in y0..y1) for (x in x0..x1) {
                // even-odd rule at the middle of the cell; corners on whole numbers take in the cells they frame
                val px = x + 0.5f
                val py = y + 0.5f
                var inside = false
                var j = points.size - 1
                for (k in points.indices) {
                    if ((ys[k] > py) != (ys[j] > py) && px < (xs[j] - xs[k]) * (py - ys[k]) / (ys[j] - ys[k]) + xs[k]) inside = !inside
                    j = k
                }
                if (inside) out += y * width + x
            }
            require(out.isNotEmpty()) { "the area $points holds no cell" }
            return Area(out.toIntArray())
        }
    }
}

/**
 * A goal of a level (Leitfaden V3, 5.2). It says what, never how. [met] looks at the world as it is; the level
 * counts a goal once it has been met without a break for [holdSteps] steps. A goal that [guards] something
 * (animals, a building) is the other way round: it holds from the start and the level is lost when it breaks.
 */
sealed class Goal(val text: String, val area: Area, val holdSteps: Int) {
    abstract fun met(world: World): Boolean

    /** How far along it is, 0 to 1, to show the player. */
    abstract fun progress(world: World): Float

    open val guards get() = false

    /** At least [amount] cells of one of [materials] in the area. */
    class Material(text: String, area: Area, hold: Int, val materials: IntArray, val amount: Int) : Goal(text, area, hold) {
        fun count(world: World): Int = area.cells.count { world.mat[it].toInt() in materials }
        override fun met(world: World) = count(world) >= amount
        override fun progress(world: World) = (count(world).toFloat() / amount).coerceAtMost(1f)
    }

    /** The area filled with [material] to at least [share] of its cells. */
    class Fill(text: String, area: Area, hold: Int, val material: Int, val share: Float) : Goal(text, area, hold) {
        fun filled(world: World): Float = area.cells.count { world.mat[it].toInt() == material }.toFloat() / area.size
        override fun met(world: World) = filled(world) >= share
        override fun progress(world: World) = (filled(world) / share).coerceAtMost(1f)
    }

    /** The average temperature of the area between [min] and [max]. */
    class Temperature(text: String, area: Area, hold: Int, val min: Float, val max: Float) : Goal(text, area, hold) {
        fun average(world: World): Float = area.cells.sumOf { world.temp[it].toDouble() }.toFloat() / area.size
        override fun met(world: World) = average(world) in min..max
        override fun progress(world: World): Float {
            val t = average(world)
            return when {
                t < min -> ((t - world.ambient) / (min - world.ambient)).coerceIn(0f, 1f)
                t > max -> ((world.ambient - t) / (world.ambient - max)).coerceIn(0f, 1f)
                else -> 1f
            }
        }
    }

    /** At least [amount] cells burning in the area. */
    class Ignite(text: String, area: Area, hold: Int, val amount: Int, private val fire: Int) : Goal(text, area, hold) {
        fun burning(world: World): Int = area.cells.count { world.flags[it].toInt() and Flag.BURNING != 0 || world.mat[it].toInt() == fire }
        override fun met(world: World) = burning(world) >= amount
        override fun progress(world: World) = (burning(world).toFloat() / amount).coerceAtMost(1f)
    }

    /** Nothing burning in the area any more. */
    class Extinguish(text: String, area: Area, hold: Int, private val fire: Int) : Goal(text, area, hold) {
        private var most = 1
        fun burning(world: World): Int = area.cells.count { world.flags[it].toInt() and Flag.BURNING != 0 || world.mat[it].toInt() == fire }
        override fun met(world: World) = burning(world) == 0
        override fun progress(world: World): Float {
            val b = burning(world)
            most = maxOf(most, b)
            return 1f - b.toFloat() / most
        }
    }

    /** The average light in the area at least [level] (0 dark, 1 daylight). */
    class Light(text: String, area: Area, hold: Int, val level: Float) : Goal(text, area, hold) {
        fun average(world: World): Float = area.cells.sumOf { world.lightAt(it % world.width, it / world.width).toDouble() }.toFloat() / area.size
        override fun met(world: World) = average(world) >= level
        override fun progress(world: World) = (average(world) / level).coerceAtMost(1f)
    }

    /** At least [amount] cells of living plants in the area. */
    class Grow(text: String, area: Area, hold: Int, val amount: Int, private val plant: BooleanArray) : Goal(text, area, hold) {
        fun count(world: World): Int = area.cells.count { plant[world.mat[it].toInt()] }
        override fun met(world: World) = count(world) >= amount
        override fun progress(world: World) = (count(world).toFloat() / amount).coerceAtMost(1f)
    }

    /** At least [amount] cells of [materials] stay in the area all the time: animals or a building to keep safe. */
    class Protect(text: String, area: Area, val materials: IntArray, val amount: Int) : Goal(text, area, 0) {
        fun count(world: World): Int = area.cells.count { world.mat[it].toInt() in materials }
        override val guards get() = true
        override fun met(world: World) = count(world) >= amount
        override fun progress(world: World) = (count(world).toFloat() / amount).coerceAtMost(1f)
    }

    companion object {
        /** Turns the goals of a level file into goals for a world of [width] × [height]. */
        fun of(spec: GoalSpec, materials: MaterialTable, width: Int, height: Int): Goal {
            val area = Area.of(spec.area, width, height)
            val hold = (spec.hold * STEPS_PER_SECOND).toInt()
            val names = spec.materials + listOfNotNull(spec.material)
            val ids = names.map { materials.id(it) }.toIntArray()
            val fire = materials.id("fire")
            return when (spec.type) {
                "material" -> Material(spec.text, area, hold, ids.also { require(it.isNotEmpty()) }, spec.amount.coerceAtLeast(1))
                "fill" -> {
                    val m = materials.id(requireNotNull(spec.material) { "a fill goal names its liquid" })
                    require(materials.kind[m] == Kind.LIQUID) { "a fill goal needs a liquid, not ${spec.material}" }
                    Fill(spec.text, area, hold, m, spec.share.takeIf { it > 0f } ?: 0.8f)
                }
                "temperature" -> Temperature(spec.text, area, hold, spec.min ?: -1e9f, spec.max ?: 1e9f)
                "ignite" -> Ignite(spec.text, area, hold, spec.amount.coerceAtLeast(1), fire)
                "extinguish" -> Extinguish(spec.text, area, hold, fire)
                "light" -> Light(spec.text, area, hold, spec.light)
                "grow" -> Grow(spec.text, area, hold, spec.amount.coerceAtLeast(1), BooleanArray(materials.count) { materials.organism[it] != Organism.NONE && !materials.animal[it] })
                "protect" -> Protect(spec.text, area, ids.also { require(it.isNotEmpty()) }, spec.amount.coerceAtLeast(1))
                else -> error("unknown goal type '${spec.type}'")
            }
        }
    }
}

/** The simulation runs 60 steps per second (Leitfaden V3, 4.1.5). */
const val STEPS_PER_SECOND = 60
