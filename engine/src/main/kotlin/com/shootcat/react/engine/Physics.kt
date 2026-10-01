package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props

/**
 * Phase 2: forces and movement. Every object moves at most one cell per step.
 *
 * - Objects with [Props.GRAVITY] fall when the cell below is free.
 * - Objects that are also [Props.LIQUID] and cannot fall flow one cell towards the nearest
 *   drop on their row (a free cell with a free cell below). Ties go left; without a drop they rest.
 */
internal object Physics {

    private val bottomUp = compareByDescending<GameObject> { it.position.y }
        .thenBy { it.position.x }
        .thenBy { it.id }

    fun apply(world: MutableWorld, objects: List<GameObject>): Boolean {
        var moved = false
        for (candidate in objects.filter { it.flag(Props.GRAVITY) }.sortedWith(bottomUp)) {
            val o = world.byId(candidate.id) ?: continue
            val below = o.position.down()
            if (world.isFree(below)) {
                world.move(o.id, below)
                moved = true
                continue
            }
            if (o.flag(Props.LIQUID)) {
                val dx = flowDirection(world, o.position)
                if (dx != 0) {
                    world.move(o.id, Position(o.position.x + dx, o.position.y))
                    moved = true
                }
            }
        }
        return moved
    }

    private fun flowDirection(world: MutableWorld, p: Position): Int {
        val left = distanceToDrop(world, p, -1)
        val right = distanceToDrop(world, p, 1)
        return when {
            left == null && right == null -> 0
            right == null -> -1
            left == null -> 1
            left <= right -> -1
            else -> 1
        }
    }

    private fun distanceToDrop(world: MutableWorld, p: Position, dx: Int): Int? {
        var d = 1
        while (true) {
            val cell = Position(p.x + dx * d, p.y)
            if (!world.isFree(cell)) return null
            if (world.isFree(cell.down())) return d
            d++
        }
    }
}
