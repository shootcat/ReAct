package com.shootcat.react.sim

/** A small test world with a frame of rock, for trying out one behaviour at a time. */
class Lab(val width: Int = 64, val height: Int = 64, seed: Long = 7L) {
    val materials = Lab.materials
    val world = World(width, height, materials, seed)

    init {
        rect("rock", 0, height - 2, width - 1, height - 1)
        rect("rock", 0, 0, 1, height - 1)
        rect("rock", width - 2, 0, width - 1, height - 1)
    }

    fun id(name: String) = materials.id(name)

    /** Fills the rectangle (corners included) with [name], at its own or the given temperature. */
    fun rect(name: String, x0: Int, y0: Int, x1: Int, y1: Int, t: Float = Float.NaN) {
        val m = materials.id(name)
        for (y in y0..y1) for (x in x0..x1) world.set(x, y, m, t)
    }

    fun count(name: String) = world.count(materials.id(name))

    fun countIn(name: String, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val m = materials.id(name)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (world.material(x, y) == m) n++
        return n
    }

    fun at(x: Int, y: Int): String = materials.spec(world.material(x, y)).id

    fun temp(x: Int, y: Int) = world.temp[world.index(x, y)]

    fun seconds(s: Float) = world.run((s * MaterialTable.STEPS_PER_SECOND).toInt())

    /** The highest row (smallest y) that holds [name] in column [x], or -1. */
    fun top(name: String, x: Int): Int {
        val m = materials.id(name)
        for (y in 0 until height) if (world.material(x, y) == m) return y
        return -1
    }

    fun picture(name: String) = Pictures.write(world, IntArray(world.size) { 0xFF9CC3E0.toInt() }, name, scale = 5)

    companion object {
        val materials: MaterialTable by lazy { MaterialTable.load() }
    }
}
