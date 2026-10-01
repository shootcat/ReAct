package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TypeCatalog

/** Working copy of a [GameState] used while one simulation step is being computed. */
internal class MutableWorld(base: GameState) {
    private val width = base.width
    private val height = base.height
    private val walls = base.walls
    private val objects = LinkedHashMap<String, GameObject>()
    private val grid = HashMap<Position, String>()
    private var spawnCounter = base.spawnCounter

    init {
        base.objects.forEach { put(it) }
    }

    private fun put(o: GameObject) {
        objects[o.id] = o
        grid[o.position] = o.id
    }

    fun byId(id: String): GameObject? = objects[id]

    fun isFree(p: Position): Boolean =
        p.x in 0 until width && p.y in 0 until height && p !in walls && p !in grid

    fun setState(id: String, state: String) {
        val o = objects.getValue(id)
        objects[id] = o.copy(state = state)
    }

    fun remove(id: String) {
        val o = objects.remove(id) ?: return
        grid.remove(o.position)
    }

    fun move(id: String, to: Position) {
        val o = objects.getValue(id)
        grid.remove(o.position)
        put(o.copy(position = to))
    }

    fun spawn(type: String, at: Position, types: TypeCatalog): GameObject {
        spawnCounter++
        val o = types.create(id = "${type.lowercase()}#$spawnCounter", type = type, position = at)
        put(o)
        return o
    }

    fun snapshot(): GameState =
        GameState(width, height, walls, objects.values.sortedBy { it.id }, spawnCounter)
}
