package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props

/**
 * Phase 2: forces and movement. Every object moves at most one cell per step.
 *
 * - [Props.GRAVITY] objects fall (bottom row first), [Props.RISES] objects rise (top row first).
 * - When the straight way is blocked, [Props.FLOWS] objects slide diagonally onto a free
 *   neighbour cell below (or above, when rising) – left first. Diagonal moves need the side
 *   cell to be free too, so nothing squeezes through corners.
 * - Otherwise they flow one cell towards the nearest opening on their row: a free cell with a
 *   free cell below (above). Ties go left; without an opening they rest.
 */
internal object Physics {

    private val bottomUp = compareByDescending<GameObject> { it.position.y }
        .thenBy { it.position.x }
        .thenBy { it.id }

    private val topDown = compareBy<GameObject> { it.position.y }
        .thenBy { it.position.x }
        .thenBy { it.id }

    fun apply(world: MutableWorld, objects: List<GameObject>) {
        objects.filter { it.flag(Props.GRAVITY) }
            .sortedWith(bottomUp)
            .forEach { move(world, it.id, dy = 1) }
        objects.filter { it.flag(Props.RISES) && !it.flag(Props.GRAVITY) }
            .sortedWith(topDown)
            .forEach { move(world, it.id, dy = -1) }
    }

    private fun move(world: MutableWorld, id: String, dy: Int) {
        val o = world.byId(id) ?: return
        val p = o.position
        val straight = Position(p.x, p.y + dy)
        if (world.isFree(straight)) {
            world.move(id, straight)
            return
        }
        if (!o.flag(Props.FLOWS)) return

        for (dx in DIRECTIONS) {
            val side = Position(p.x + dx, p.y)
            val diagonal = Position(p.x + dx, p.y + dy)
            if (world.isFree(side) && world.isFree(diagonal)) {
                world.move(id, diagonal)
                return
            }
        }
        val dx = flowDirection(world, p, dy)
        if (dx != 0) world.move(id, Position(p.x + dx, p.y))
    }

    private fun flowDirection(world: MutableWorld, p: Position, dy: Int): Int {
        val left = distanceToOpening(world, p, -1, dy)
        val right = distanceToOpening(world, p, 1, dy)
        return when {
            left == null && right == null -> 0
            right == null -> -1
            left == null -> 1
            left <= right -> -1
            else -> 1
        }
    }

    private fun distanceToOpening(world: MutableWorld, p: Position, dx: Int, dy: Int): Int? {
        var d = 1
        while (true) {
            val cell = Position(p.x + dx * d, p.y)
            if (!world.isFree(cell)) return null
            if (world.isFree(Position(cell.x, cell.y + dy))) return d
            d++
        }
    }

    private val DIRECTIONS = intArrayOf(-1, 1)
}
