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
 * A goal that can [fail] (a village that must stay warm through the night) loses the level when it does.
 */
sealed class Goal(val text: String, val area: Area, val holdSteps: Int) {
    abstract fun met(world: World): Boolean

    /** How far along it is, 0 to 1, to show the player. */
    abstract fun progress(world: World): Float

    open val guards get() = false

    /** It can no longer be reached: the level is lost. */
    open fun failed(world: World): Boolean = false

    /** The cells of the area, as a mask over the world (made when first needed). */
    private var mask: BooleanArray? = null

    protected fun inside(world: World, i: Int): Boolean {
        val m = mask ?: BooleanArray(world.size).also { b ->
            for (c in area.cells) b[c] = true
            mask = b
        }
        return i in m.indices && m[i]
    }

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

    /** At least [amount] inhabitants on their feet in the area (Phase 5): not fainted, not being brought back. */
    class Bring(text: String, area: Area, hold: Int, val amount: Int) : Goal(text, area, hold) {
        fun count(world: World): Int {
            val f = world.folk
            var n = 0
            for (k in 0 until f.count) if (f.standing(k) && inside(world, f.y(k) * world.width + f.x(k))) n++
            return n
        }
        override fun met(world: World) = count(world) >= amount
        override fun progress(world: World) = (count(world).toFloat() / amount).coerceAtMost(1f)
    }

    /**
     * At least [amount] inhabitants who were shut in brought out into the village (Phase 5), on their feet. A
     * rescue level brings one that fainted back to where it stood, never into the village; the area is where the
     * goal is shown, the village is where they count.
     */
    class Rescue(text: String, area: Area, hold: Int, val amount: Int) : Goal(text, area, hold) {
        fun count(world: World): Int {
            val f = world.folk
            var n = 0
            for (k in 0 until f.count) if (f.standing(k) && f.home(k)) n++
            return n
        }
        override fun met(world: World) = count(world) >= amount
        override fun progress(world: World) = (count(world).toFloat() / amount).coerceAtMost(1f)
    }

    /**
     * The air in the area (the village's houses) stays at [min] °C or warmer until the night is over, [until] steps
     * (Phase 5). Met when the night has passed; failed as soon as the air there is colder, after the first
     * [GRACE] steps (the level may begin with a house cooling down).
     */
    class KeepWarm(text: String, area: Area, val min: Float, val until: Int, private val gas: BooleanArray) : Goal(text, area, 0) {
        fun average(world: World): Float {
            var sum = 0.0
            var n = 0
            for (c in area.cells) if (gas[world.mat[c].toInt()]) {
                sum += world.temp[c]
                n++
            }
            if (n == 0) for (c in area.cells) {
                sum += world.temp[c]
                n++
            }
            return (sum / n).toFloat()
        }
        override fun met(world: World) = world.step >= until && !failed(world)
        override fun failed(world: World) = world.step >= GRACE && average(world) < min
        override fun progress(world: World) = (world.step.toFloat() / until).coerceIn(0f, 1f)

        companion object {
            const val GRACE = 2 * STEPS_PER_SECOND
        }
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
                "bring" -> Bring(spec.text, area, hold, spec.amount.coerceAtLeast(1))
                "rescue" -> Rescue(spec.text, area, hold, spec.amount.coerceAtLeast(1))
                "keep_warm" -> {
                    require(spec.seconds > 0f) { "a keep_warm goal names the length of the night in seconds" }
                    KeepWarm(spec.text, area, requireNotNull(spec.min) { "a keep_warm goal names its least temperature" }, (spec.seconds * STEPS_PER_SECOND).toInt(), BooleanArray(materials.count) { materials.kind[it] == Kind.GAS })
                }
                else -> error("unknown goal type '${spec.type}'")
            }
        }
    }
}

/** The simulation runs 60 steps per second (Leitfaden V3, 4.1.5). */
const val STEPS_PER_SECOND = 60
