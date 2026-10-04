package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TypeCatalog

/** Working copy of a [GameState] used while one simulation step is being computed. */
internal class MutableWorld(private val base: GameState) {
    private val width = base.width
    private val height = base.height
    private val walls = base.walls
    private val objects = LinkedHashMap<String, GameObject>()
    private val grid = HashMap<Position, String>()
    private var spawnCounter = base.spawnCounter

    /** Sounds of this step that physics produced (clouds forming, rain starting …). */
    val cues = mutableListOf<Cue>()

    init {
        base.objects.forEach { put(it) }
    }

    private fun put(o: GameObject) {
        objects[o.id] = o
        grid[o.position] = o.id
    }

    fun byId(id: String): GameObject? = objects[id]

    fun at(p: Position): GameObject? = grid[p]?.let { objects[it] }

    fun all(): List<GameObject> = objects.values.toList()

    fun inBounds(p: Position): Boolean = p.x in 0 until width && p.y in 0 until height

    fun isWall(p: Position): Boolean = p in walls

    fun isFree(p: Position): Boolean = inBounds(p) && p !in walls && p !in grid

    fun setState(id: String, state: String) {
        val o = objects.getValue(id)
        objects[id] = o.copy(state = state)
    }

    fun setTemp(id: String, temp: Int) {
        val o = objects.getValue(id)
        objects[id] = o.copy(temp = temp)
    }

    fun setBurnt(id: String, burnt: Int) {
        val o = objects.getValue(id)
        objects[id] = o.copy(burnt = burnt)
    }

    /** Sets the amount of a liquid, gas or cloud; an empty cell disappears. */
    fun setAmount(id: String, amount: Int) {
        val o = objects.getValue(id)
        if (amount <= 0) remove(id) else objects[id] = o.copy(amount = amount)
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

    /** Exchanges the cells of two objects (a stone sinking through water, ice floating up). */
    fun swap(a: String, b: String) {
        val oa = objects.getValue(a)
        val ob = objects.getValue(b)
        put(oa.copy(position = ob.position))
        put(ob.copy(position = oa.position))
    }

    fun fluidCapacity(type: String, types: TypeCatalog): Int =
        types.require(type).properties["capacity"]?.toIntOrNull() ?: 8

    fun cue(sound: String, at: Position) {
        if (cues.none { it.sound == sound }) cues += Cue(sound, at)
    }

    fun spawn(type: String, at: Position, types: TypeCatalog, amount: Int? = null): GameObject {
        spawnCounter++
        val o = types.create(id = "${type.lowercase()}#$spawnCounter", type = type, position = at, amount = amount)
        put(o)
        return o
    }

    fun snapshot(): GameState =
        GameState(width, height, walls, objects.values.sortedBy { it.id }, spawnCounter, base.noBuild)
}
