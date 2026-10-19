package com.shootcat.react.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The discovery book (Leitfaden V3, 4.3): properties of materials, plants and animals, each found the first
 * time the player sees it happen. An entry names a property ("Sand behält Wärme"), never a recipe.
 *
 * The entries and their little scenes are in `discoveries.json`. What the world reports in [World.seen],
 * plus a few things only a look at the cells shows (holding heat, glowing, floating, carrying heat, shining
 * in the dark, wet wood that does not burn), decides which are found.
 */
@Serializable
data class DiscoverySpec(
    val id: String,
    /** The material the property belongs to. */
    val material: String,
    /** The observation that shows it ([Seen.NAMES]). */
    val seen: String,
    /** Materials the observation is made on, if not [material]; "*" for any. */
    val of: List<String>? = null,
    /** The property, to follow the material's name: "behält Wärme". */
    val text: String,
    /** A little world that shows it: 48 × 32 cells with rock walls and floor, filled as given. */
    val scene: List<SceneFill> = emptyList(),
    /** A roof of rock over the scene: dark inside. */
    val roof: Boolean = false,
    /** The weather of the scene (3c); none: no clouds, no wind. */
    val weather: SceneWeather? = null,
    /** Whose property it is when it is no material's: "Bewohner" (Phase 5); the book groups such entries under it. */
    val subject: String? = null,
    /** Inhabitants in the scene (Phase 5), and their village (x, y). */
    val folk: List<SceneFigure> = emptyList(),
    val village: List<Int>? = null,
)

/** An inhabitant in a scene: where it is put down, which way it looks, how cold and thirsty it is already. */
@Serializable
data class SceneFigure(val x: Int, val y: Int, val dir: Int = 1, val cold: Int = 0, val thirst: Int = 0)

/** The weather of a scene: where clouds form, the wind, the temperature of the air, a thunderstorm. */
@Serializable
data class SceneWeather(
    @SerialName("cloud_base") val cloudBase: Int = -1,
    val wind: Float = 0f,
    val ambient: Float? = null,
    val storm: Boolean = false,
)

/** A rectangle of a material in a scene, at its own or the given temperature, wet if asked. */
@Serializable
data class SceneFill(val m: String, val x: List<Int>, val t: Float? = null, val wet: Boolean = false)

@Serializable
private data class DiscoveryFile(val note: String = "", val entries: List<DiscoverySpec>)

class Discoveries(val materials: MaterialTable, val entries: List<DiscoverySpec>) {

    /** An entry with its material resolved. */
    class Entry(val spec: DiscoverySpec, val material: Int, val kind: Int, val name: String) {
        val id get() = spec.id

        /** The sentence as the book and the notice show it: "Sand behält Wärme", "Bewohner trinken kein Salzwasser". */
        val sentence get() = "$name ${spec.text}"
    }

    val all: List<Entry> = entries.map { s ->
        val m = materials.id(s.material)
        Entry(s, m, Seen.parse(s.seen), s.subject ?: materials.spec(m).name)
    }

    private val byTrigger = HashMap<Int, MutableList<Entry>>()
    private val anyOf = Array(Seen.COUNT) { mutableListOf<Entry>() }

    init {
        require(all.map { it.id }.toSet().size == all.size) { "discovery ids must be unique" }
        for (e in all) {
            val of = e.spec.of ?: listOf(e.spec.material)
            if (of == listOf("*")) {
                anyOf[e.kind] += e
            } else {
                for (name in of) byTrigger.getOrPut(e.kind * materials.count + materials.id(name)) { mutableListOf() } += e
            }
            for (f in e.spec.scene) materials.id(f.m)
        }
    }

    fun byId(id: String): Entry? = all.firstOrNull { it.id == id }

    /** Entries the observation [kind] on material [m] shows. */
    fun triggeredBy(kind: Int, m: Int): List<Entry> =
        (byTrigger[kind * materials.count + m] ?: emptyList<Entry>()) + anyOf[kind]

    /** Builds the scene of [entry] into a new world of [SCENE_W] × [SCENE_H] cells. */
    fun scene(entry: Entry, seed: Long = 11L): World {
        val w = World(SCENE_W, SCENE_H, materials, seed)
        val weather = entry.spec.weather
        weather?.ambient?.let { w.setAmbient(it) }
        val rock = materials.id("rock")
        for (y in 0 until SCENE_H) for (x in 0 until SCENE_W) {
            if (y >= SCENE_H - 2 || x < 2 || x >= SCENE_W - 2 || (entry.spec.roof && y < 2)) w.set(x, y, rock)
        }
        for (f in entry.spec.scene) {
            val m = materials.id(f.m)
            for (y in f.x[1]..f.x[3]) for (x in f.x[0]..f.x[2]) {
                if (!w.inside(x, y)) continue
                w.set(x, y, m, f.t ?: Float.NaN)
                if (f.wet) w.setWet(x, y, true)
            }
        }
        if (weather != null) {
            w.setWeather(cloudBase = weather.cloudBase, wind = weather.wind)
            if (weather.storm) w.callStorm(SCENE_W / 2)
        }
        entry.spec.village?.let { w.folk.setHome(it[0], it[1]) }
        for (f in entry.spec.folk) {
            val id = w.folk.add(f.x, f.y)
            w.folk.face(id, f.dir)
            w.folk.setNeeds(id, cold = f.cold, thirst = f.thirst)
        }
        w.wakeAll()
        return w
    }

    companion object {
        const val SCENE_W = 48
        const val SCENE_H = 32

        /** A scene runs this many steps (20 seconds), then starts again. */
        const val SCENE_STEPS = 1200

        fun load(materials: MaterialTable): Discoveries {
            val stream = Discoveries::class.java.getResourceAsStream("/discoveries.json") ?: error("discoveries.json is missing")
            val text = stream.bufferedReader().use { it.readText() }
            return Discoveries(materials, Json.decodeFromString(DiscoveryFile.serializer(), text).entries)
        }
    }
}

/**
 * Watches a world for discoveries: call [check] now and then (every half second is plenty). It looks at
 * what the world reported since the last call and at a quarter of the cells, and returns the entries seen
 * for the first time; [known] grows with them. Watching never changes the world.
 */
class DiscoveryWatch(private val book: Discoveries, known: Set<String>) {

    val known = HashSet(known)
    private var stripe = 0

    fun check(world: World): List<Discoveries.Entry> {
        observe(world)
        val n = book.materials.count
        val seen = world.seen
        val found = ArrayList<Discoveries.Entry>()
        for (k in 0 until Seen.COUNT) for (m in 0 until n) {
            if (seen[k * n + m] == 0) continue
            for (e in book.triggeredBy(k, m)) if (known.add(e.id)) found += e
        }
        seen.fill(0)
        return found
    }

    /** What only a look at the cells shows; every call looks at another quarter of the rows. */
    private fun observe(world: World) {
        val mt = world.materials
        val w = world.width
        val h = world.height
        val mat = world.mat
        val temp = world.temp
        val flags = world.flags
        stripe = (stripe + 1) and 3
        var y = stripe
        while (y < h) {
            for (x in 0 until w) {
                val i = y * w + x
                val m = mat[i].toInt()
                if (m == MaterialTable.AIR) continue
                val k = mt.kind[m]
                val f = flags[i].toInt()
                val t = temp[i]
                val burning = f and Flag.BURNING != 0
                if (k != Kind.GAS && k != Kind.LIQUID && !burning && mt.glow[m] == 0f) {
                    if (t > GLOWING) world.note(Seen.GLOW, m)
                    if (t > HOT && t >= hottestNeighbour(world, x, y, i)) world.note(Seen.HOLD_HEAT, m)
                }
                if ((k == Kind.POWDER || k == Kind.SOLID) && y < h - 1 && (f and Flag.ANCHORED) == 0) {
                    val b = mat[i + w].toInt()
                    if (mt.kind[b] == Kind.LIQUID && mt.density[b] > mt.density[m]) world.note(Seen.FLOAT, m)
                }
                // a light liquid lying on a heavier one: oil on water (Phase 4b)
                if (k == Kind.LIQUID && y < h - 1) {
                    val b = mat[i + w].toInt()
                    if (b != m && mt.kind[b] == Kind.LIQUID && mt.density[b] > mt.density[m]) world.note(Seen.FLOAT, m)
                }
                if (k == Kind.SOLID && mt.conductivity[m] >= GOOD_CONDUCTOR && t > CARRIED && carried(world, x, y, i, m)) world.note(Seen.CONDUCT, m)
                if (mt.animal[m] && mt.emitsLight[m] > 0f && world.lightAt(x, y) < IN_THE_DARK) world.note(Seen.SHINE, m)
                if (mt.flammable[m] && mt.wetBlocksBurning[m] && f and Flag.WET != 0 && !burning && t >= mt.igniteTemp[m]) world.note(Seen.WET_NO_FIRE, m)
            }
            y += 4
        }
    }

    private fun hottestNeighbour(world: World, x: Int, y: Int, i: Int): Float {
        val tp = world.temp
        val w = world.width
        var t = -Float.MAX_VALUE
        if (x > 0) t = maxOf(t, tp[i - 1])
        if (x < w - 1) t = maxOf(t, tp[i + 1])
        if (y > 0) t = maxOf(t, tp[i - w])
        if (y < world.height - 1) t = maxOf(t, tp[i + w])
        return t
    }

    /** Hot, with every neighbour of another material clearly cooler: the heat came along this material. */
    private fun carried(world: World, x: Int, y: Int, i: Int, m: Int): Boolean {
        val w = world.width
        val t = world.temp[i] - COOLER
        var same = 0
        for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, i - w, i + w)) {
            if (j < 0 || j >= world.size) continue
            if (world.mat[j].toInt() == m) same++ else if (world.temp[j] > t) return false
        }
        return same > 0
    }

    private companion object {
        const val GLOWING = 550f
        const val HOT = 150f
        const val GOOD_CONDUCTOR = 0.9f
        const val CARRIED = 120f
        const val COOLER = 20f
        const val IN_THE_DARK = 0.4f
    }
}
