package com.shootcat.react.level

import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.World
import kotlinx.serialization.json.Json

/**
 * A level ready to play: its file, the painted ground and the mask of anchored ground (Leitfaden V3, 5.1).
 * Every colour of the ground is the colour of one material as materials.json gives it; black is air,
 * anything see-through too.
 */
class Level(val spec: LevelSpec, val terrain: Picture, val anchor: Picture?) {
    val width get() = terrain.width
    val height get() = terrain.height

    init {
        require(anchor == null || (anchor.width == width && anchor.height == height)) { "the anchor mask of ${spec.id} is not the size of its ground" }
    }

    /** The material for each painted colour; fails on a colour that is no material. */
    fun materialsOf(materials: MaterialTable): IntArray {
        val byColour = HashMap<Int, Int>()
        for (m in 0 until materials.count) byColour[materials.color[m] and 0xFFFFFF] = m
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (terrain[x, y] ushr 24 < 128) MaterialTable.AIR
            else byColour[terrain.rgb(x, y)] ?: error("${spec.id}: colour #%06x at ($x, $y) is no material".format(terrain.rgb(x, y)))
        }
    }

    /** Builds the level into an empty [world] of its size. */
    fun build(world: World) {
        val mt = world.materials
        require(world.width == width && world.height == height) { "${spec.id} is $width × $height, the world ${world.width} × ${world.height}" }
        spec.ambient?.let { world.setAmbient(it) }
        val cells = materialsOf(mt)
        val animals = ArrayList<Int>()
        for (i in cells.indices) {
            val m = cells[i]
            if (m == MaterialTable.AIR) continue
            if (mt.animal[m]) {
                animals += i
                continue
            }
            world.set(i % width, i / width, m, if (mt.hasSpawnTemp[m]) Float.NaN else world.ambient)
        }
        anchor?.let { a ->
            for (i in cells.indices) {
                val p = a.argb[i]
                val bright = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
                if (bright >= 128 && cells[i] != MaterialTable.AIR && !mt.animal[cells[i]]) world.setAnchored(i % width, i / width, true)
            }
        }
        for (h in spec.heat) {
            val only = h.material?.let { mt.id(it) }
            for (i in Area.of(h.area, width, height).cells) if (only == null || world.mat[i].toInt() == only) world.setTemperature(i % width, i / width, h.temp)
        }
        spec.weather?.let { w ->
            world.setWeather(
                cloudBase = w.cloudBase ?: -1,
                wind = w.wind,
                gust = w.gust,
                gustSteps = (w.gustSeconds * com.shootcat.react.sim.MaterialTable.STEPS_PER_SECOND).toInt(),
                random = false,
            )
            if (w.stormSeconds > 0f) world.startStorm((w.stormSeconds * com.shootcat.react.sim.MaterialTable.STEPS_PER_SECOND).toInt())
        }
        for (t in spec.trees) world.plantTree(t.x, t.y, t.tree)
        for (i in animals) world.placeAnimal(cells[i], i % width, i / width)
        spec.folk?.let { f ->
            f.village?.let { world.folk.setHome(it[0], it[1]) }
            world.folk.backToStart = f.faintReturn == "start"
            for (p in f.figures) {
                val id = world.folk.add(p[0], p[1])
                require(id != 0) { "${spec.id}: no room for an inhabitant at (${p[0]}, ${p[1]})" }
                if (p.size > 2) world.folk.face(id, p[2])
            }
        }
        world.wakeAll()
    }

    /** The goals for a world of this level. */
    fun goals(materials: MaterialTable): List<Goal> = spec.goals.map { Goal.of(it, materials, width, height) }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** Reads a level from its folder: level.json, the ground and the mask. */
        fun read(spec: String, file: (String) -> ByteArray?): Level {
            val s = json.decodeFromString(LevelSpec.serializer(), spec)
            val terrain = Png.read(requireNotNull(file(s.terrain)) { "${s.id}: ${s.terrain} is missing" })
            val anchor = file(s.anchor)?.let { Png.read(it) }
            return Level(s, terrain, anchor)
        }
    }
}

/** The levels that come with the game, in order, read from the resources under /levels. */
object Levels {
    private val json = Json

    /** The ids of all levels, in the order they are played. */
    fun ids(): List<String> = json.decodeFromString<List<String>>(text("/levels/index.json"))

    fun load(id: String): Level = Level.read(text("/levels/$id/level.json")) { name -> bytes("/levels/$id/$name") }

    fun all(): List<Level> = ids().map { load(it) }

    private fun bytes(path: String): ByteArray? = Levels::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    private fun text(path: String): String = String(requireNotNull(bytes(path)) { "$path is missing" }, Charsets.UTF_8)
}
