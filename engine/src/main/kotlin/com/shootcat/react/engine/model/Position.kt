package com.shootcat.react.engine.model

data class Position(val x: Int, val y: Int) {
    fun up() = Position(x, y - 1)
    fun down() = Position(x, y + 1)
    fun left() = Position(x - 1, y)
    fun right() = Position(x + 1, y)

    /** Orthogonal neighbours in a fixed order (up, left, right, down) so evaluation stays deterministic. */
    fun neighbours(): List<Position> = listOf(up(), left(), right(), down())

    override fun toString() = "($x,$y)"
}
