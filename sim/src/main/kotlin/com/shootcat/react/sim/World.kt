package com.shootcat.react.sim

import com.shootcat.react.sim.MaterialTable.Companion.AIR

/** Bits of [World.flags]. */
object Flag {
    const val BURNING = 1
    const val WET = 2

    /** A cell of a structure that is falling right now: it does not catch hold of anchored ground on the way. */
    const val FALLING = 4

    /** Anchored level ground, whatever its material: structures touching it stand (3.1). */
    const val ANCHORED = 8

    /** Falling speed, 0..7, in bits 4 to 6 (of a whole structure for solid cells). */
    const val VEL_SHIFT = 4
    const val VEL_MASK = 0x70

    /**
     * A mark whose meaning depends on the material (2b): the growing tip of a tree or vine, earth loosened
     * by a worm, a fish heading right, a bird that has taken off.
     */
    const val MARK = 0x80
}

/** What happened in the world, counted per step for sounds and tests. */
object Event {
    const val IGNITE = 0
    const val EXTINGUISH = 1
    const val BURNT_OUT = 2
    const val BOIL = 3
    const val CONDENSE = 4
    const val SOLIDIFY = 5
    const val MELT = 6
    const val SOAK = 7
    const val EXPLODE = 8
    const val COUNT = 9
}

/**
 * What can be seen happening to a material, for the discovery book (Leitfaden V3, 4.3). Counted per kind and
 * material in [World.seen]; whoever reads the counts resets them. Counting never changes the world.
 */
object Seen {
    const val IGNITE = 0
    const val BURN_OUT = 1
    const val DOUSE = 2
    const val SUFFOCATE = 3
    const val CHAR = 4
    const val MELT = 5
    const val SALT_MELT = 6
    const val BOIL = 7
    const val RESIDUE = 8
    const val SET = 9
    const val CONDENSE = 10
    const val DISSOLVE = 11
    const val SOAK = 12
    const val ERODE = 13
    const val SEEP = 14
    const val GROW = 15
    const val DIE_HEAT = 16
    const val DIE_SALT = 17
    const val DIE_DRY = 18
    const val FLEE = 19
    const val DIG = 20
    const val FALL = 21
    const val HOLD_HEAT = 22
    const val GLOW = 23
    const val FLOAT = 24
    const val CONDUCT = 25
    const val SHINE = 26
    const val WET_NO_FIRE = 27
    const val CRACK = 28
    const val PERMEATE = 29
    const val COMPACT = 30
    const val AVALANCHE = 31
    const val BURST = 32
    const val EXPLODE = 33
    const val COUNT = 34

    val NAMES = listOf(
        "ignite", "burn_out", "douse", "suffocate", "char", "melt", "salt_melt", "boil", "residue", "set", "condense",
        "dissolve", "soak", "erode", "seep", "grow", "die_heat", "die_salt", "die_dry", "flee", "dig", "fall",
        "hold_heat", "glow", "float", "conduct", "shine", "wet_no_fire", "crack", "permeate", "compact", "avalanche",
        "burst", "explode",
    )

    fun parse(name: String): Int = NAMES.indexOf(name).also { require(it >= 0) { "unknown observation '$name'" } }
}

/**
 * The particle world (Leitfaden V3, 4.1 and 4.2): a cellular automaton with one particle per cell, kept in
 * flat primitive arrays, never in objects per particle.
 *
 * One [step] first lets heat flow between neighbours, then updates every cell from the bottom row to the
 * top, each row in the opposite direction of the one before. A particle that moved in this step is stamped
 * and not moved again. The world is cut into chunks of 32 × 32 cells; only chunks in which something
 * happened in the step before are updated, the rest sleep. Last, structures of solid material that are not
 * anchored fall as a whole (Leitfaden V3, 3.1).
 *
 * Everything that looks random comes from one xorshift generator seeded per world and consumed in a fixed
 * order, and all arithmetic is plain float addition and multiplication (no `exp`, no `sin`): the same
 * actions give exactly the same world on every device, at any frame rate.
 */
class World(val width: Int, val height: Int, val materials: MaterialTable, seed: Long) {

    val size = width * height
    val mat = ByteArray(size)
    val temp = FloatArray(size)
    val life = ShortArray(size)
    val variant = ByteArray(size)
    val flags = ByteArray(size)

    /** The step mark at which the particle in this cell last moved. */
    internal val stamp = IntArray(size)

    var step = 0
        private set
    internal var mark = 0
    private var rng = seedState(seed)

    val chunksX = (width + CHUNK - 1) / CHUNK
    val chunksY = (height + CHUNK - 1) / CHUNK
    private var awake = BooleanArray(chunksX * chunksY)

    /** Chunks woken since the current step began: they are updated in the next one. */
    private var pending = BooleanArray(chunksX * chunksY) { true }

    /**
     * Chunks in which temperatures differ, so heat has to flow: a chunk can be busy (water flowing) without
     * any heat to exchange, and heat is the costliest part of a step.
     */
    private var heatAwake = BooleanArray(chunksX * chunksY)
    private var heatPending = BooleanArray(chunksX * chunksY) { true }

    /** For measuring: update every chunk, asleep or not. */
    var keepAllAwake = false

    /** How many chunks the last step updated. */
    var awakeChunks = 0
        private set

    /** Cells the player has placed, per material. */
    val placed = IntArray(materials.count)

    /** Counts per [Event]; whoever reads them resets them. */
    val events = IntArray(Event.COUNT)

    /** Counts per [Seen] kind and material (kind × materials + material); whoever reads them resets them. */
    val seen = IntArray(Seen.COUNT * materials.count)

    internal fun note(kind: Int, m: Int) {
        seen[kind * materials.count + m]++
    }

    /** The temperature of the open air, which air comes back to (a cold mountain is colder than a meadow). */
    var ambient = materials.ambient
        private set

    /** Sets the temperature of the surroundings and brings every cell to it, before a world is built. */
    fun setAmbient(t: Float) {
        ambient = t
        temp.fill(t)
    }
    private val kindOf = materials.kind
    private val densityOf = materials.density
    private val fireId = materials.idOrNull("fire") ?: AIR
    private val smokeId = materials.idOrNull("smoke") ?: AIR
    private val steamId = materials.idOrNull("steam") ?: AIR
    private val deadPlantId = materials.idOrNull("dead_plant") ?: AIR
    private val waterId = materials.idOrNull("water") ?: AIR

    /** How readily ground takes in fresh water: absorbent soil soaks it up, permeable gravel lets it into its pores. */
    private val takesWater = FloatArray(materials.count) { maxOf(materials.absorbs[it], materials.permeable[it]) }
    private val plantOf = materials.plant
    private val softOf = materials.soft

    /** Light for plants and animals (2b), worked out every [LIGHT_EVERY] steps. */
    internal val light = Light(this)
    private val living = Life(this)

    // structures (3.1): which structure a solid cell belongs to (number + 1), and the loose ones
    private val owner = IntArray(size)
    private var structuresDirty = true
    private val loose = ArrayList<IntArray>()
    private val looseDensity = ArrayList<Float>()
    /** Loose structures held by roots: plants with leaves or vines do not tip over. */
    private val looseRooted = ArrayList<Boolean>()
    /** Loose structures to leave alone until the next search: a falling block has torn part of them off. */
    private var skip = BooleanArray(64)
    /** For each structure number: its place in [loose], or -1 if it stands. */
    private val looseOf = IntArray(size + 2)
    private val fillStack = IntArray(size)
    private var fillCells = IntArray(256)
    private var fillTop = 0
    private var fillAnchored = false

    // pressure (4a.4): which gas region each cell of gas belongs to, and the cells of the closed ones
    private val region = IntArray(size)
    private var gasDirty = true
    private var closedCells = IntArray(256)
    private var closedStart = IntArray(17)
    private var closedCount = 0

    /** The pressure in the closed gas region around each cell found at the last check, for display and tests (1: the air outside). */
    fun pressureAt(x: Int, y: Int): Float {
        val r = region[y * width + x]
        if (r >= 0) return 1f
        val k = -r - 1
        return if (k < closedCount) closedPressure[k] else 1f
    }
    private var closedPressure = FloatArray(17)

    /** Time spent on pressure so far, for the benchmark. */
    var pressureNanos = 0L
        private set

    /** How many structures are not anchored (they fall, or lie on something that holds them). */
    val looseStructures: Int get() = loose.size

    init {
        temp.fill(ambient)
    }

    fun index(x: Int, y: Int) = y * width + x
    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun material(x: Int, y: Int): Int = mat[y * width + x].toInt()
    fun isAwake(cx: Int, cy: Int) = pending[cy * chunksX + cx]

    /** The light at (x, y), 0 dark to 1 daylight, as the plants see it (2b). */
    fun lightAt(x: Int, y: Int): Float = light.at(x, y)

    // ------------------------------------------------------------------ random numbers

    internal fun next(): Int {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return x
    }

    /** True with probability [p] (0..1). */
    internal fun chance(p: Float): Boolean = (next() ushr 8) < (p * 16777216f).toInt()
    internal fun coin(): Boolean = next() < 0
    internal fun below(n: Int): Int = (((next().toLong() and 0xffffffffL) * n) ushr 32).toInt()

    // ------------------------------------------------------------------ placing material

    /**
     * Fills the air inside a disc with [material]; with [fill] below 1 only that share of it, scattered, the
     * way poured sand or water looks. Only air is replaced: nothing goes into rock or anything else.
     */
    fun paint(material: Int, cx: Int, cy: Int, radius: Int, fill: Float = 1f, limit: Int = Int.MAX_VALUE): Int {
        var n = 0
        val r2 = radius * radius + radius
        for (dy in -radius..radius) {
            val y = cy + dy
            if (y < 0 || y >= height) continue
            for (dx in -radius..radius) {
                if (n >= limit) break
                val x = cx + dx
                if (x < 0 || x >= width || dx * dx + dy * dy > r2) continue
                val i = y * width + x
                if (mat[i].toInt() != AIR) continue
                if (fill < 1f && !chance(fill)) continue
                spawn(x, y, i, material, materials.spawnTemp[material])
                n++
            }
        }
        placed[material] += n
        return n
    }

    /**
     * Puts one animal of [material] at or near (x, y): into the nearest cell of where it lives (water for a
     * fish, earth for a worm) within reach, otherwise into the nearest air. Returns false if there is no room.
     */
    fun placeAnimal(material: Int, x: Int, y: Int): Boolean {
        val home = materials.livesIn[material]
        var best = -1
        var bestHome = false
        var bestD = Int.MAX_VALUE
        for (dy in -ANIMAL_REACH..ANIMAL_REACH) {
            val yy = y + dy
            if (yy < 0 || yy >= height) continue
            for (dx in -ANIMAL_REACH..ANIMAL_REACH) {
                val xx = x + dx
                if (xx < 0 || xx >= width) continue
                val i = yy * width + xx
                val q = mat[i].toInt()
                val isHome = home >= 0 && q == home && (flags[i].toInt() and Flag.ANCHORED) == 0
                if (q != AIR && !isHome) continue
                val d = dx * dx + dy * dy
                if ((isHome && !bestHome) || (isHome == bestHome && d < bestD)) {
                    best = i
                    bestHome = isHome
                    bestD = d
                }
            }
        }
        if (best < 0) return false
        spawn(best % width, best / width, best, material, materials.spawnTemp[material])
        placed[material]++
        return true
    }

    /** A straight stroke from (x0, y0) to (x1, y1), [radius] thick: air along it becomes [material]. */
    fun paintLine(material: Int, x0: Int, y0: Int, x1: Int, y1: Int, radius: Int, limit: Int = Int.MAX_VALUE): Int {
        var n = 0
        Shapes.line(x0, y0, x1, y1) { x, y -> n += paint(material, x, y, radius, limit = limit - n) }
        return n
    }

    /** A rectangle between two corners, filled or as a frame [thickness] cells wide: air in it becomes [material]. */
    fun paintRect(material: Int, x0: Int, y0: Int, x1: Int, y1: Int, frame: Boolean, thickness: Int, limit: Int = Int.MAX_VALUE): Int {
        var n = 0
        Shapes.rect(x0, y0, x1, y1, frame, thickness) { x, y ->
            if (inside(x, y) && n < limit) {
                val i = y * width + x
                if (mat[i].toInt() == AIR) {
                    spawn(x, y, i, material, materials.spawnTemp[material])
                    n++
                }
            }
        }
        placed[material] += n
        return n
    }

    fun eraseLine(x0: Int, y0: Int, x1: Int, y1: Int, radius: Int): Int {
        var n = 0
        Shapes.line(x0, y0, x1, y1) { x, y -> n += erase(x, y, radius) }
        return n
    }

    fun eraseRect(x0: Int, y0: Int, x1: Int, y1: Int, frame: Boolean, thickness: Int): Int {
        var n = 0
        Shapes.rect(x0, y0, x1, y1, frame, thickness) { x, y -> if (inside(x, y)) n += erase(x, y, 0) }
        return n
    }

    /** Turns everything inside the disc back into air (only the sandbox offers this). */
    fun erase(cx: Int, cy: Int, radius: Int): Int {
        var n = 0
        val r2 = radius * radius + radius
        for (dy in -radius..radius) {
            val y = cy + dy
            if (y < 0 || y >= height) continue
            for (dx in -radius..radius) {
                val x = cx + dx
                if (x < 0 || x >= width || dx * dx + dy * dy > r2) continue
                val i = y * width + x
                val m = mat[i].toInt()
                if (m == AIR || isGround(i, m)) continue
                clear(x, y, i)
                n++
            }
        }
        return n
    }

    /** Sets one cell directly, for building worlds; [t] is its temperature (the material's own if NaN). */
    fun set(x: Int, y: Int, material: Int, t: Float = Float.NaN) {
        val i = y * width + x
        spawn(x, y, i, material, if (t.isNaN()) materials.spawnTemp[material] else t)
    }

    fun setWet(x: Int, y: Int, wet: Boolean) {
        val i = y * width + x
        flags[i] = (if (wet) flags[i].toInt() or Flag.WET else flags[i].toInt() and Flag.WET.inv()).toByte()
        wake(x, y)
    }

    /** Marks a cell as anchored level ground (or not), whatever it is made of. */
    fun setAnchored(x: Int, y: Int, anchored: Boolean) {
        val i = y * width + x
        flags[i] = (if (anchored) flags[i].toInt() or Flag.ANCHORED else flags[i].toInt() and Flag.ANCHORED.inv()).toByte()
        structuresDirty = true
        wake(x, y)
    }

    /** Anchored level ground the player can never take away: rock, or anything marked anchored by the level. */
    private fun isGround(i: Int, m: Int) = kindOf[m] == Kind.STATIC || (flags[i].toInt() and Flag.ANCHORED) != 0

    fun setTemperature(x: Int, y: Int, t: Float) {
        temp[y * width + x] = t
        wakeHeat(x, y)
    }

    /** Everything but the anchored ground becomes air at the temperature of the surroundings; time and chance go on. */
    fun clearAll() {
        for (i in 0 until size) {
            val m = mat[i].toInt()
            if (m == AIR || isGround(i, m)) continue
            mat[i] = AIR.toByte()
            temp[i] = ambient
            life[i] = 0
            flags[i] = 0
        }
        pending.fill(true)
        heatPending.fill(true)
        structuresDirty = true
    }

    /**
     * The object cell [i] belongs to, for the hand (Leitfaden V3, 4.3): for a solid cell its whole structure
     * (4-connected solid cells), for a loose grain a lump of up to [lump] grains of the same material.
     * [Piece.anchored] says whether it is built into anchored ground: touching it beside or above. Lying on
     * it does not count, a log on the rock floor can be lifted. At most [max] cells are looked at.
     */
    fun pieceAt(i: Int, lump: Int, max: Int): Piece? {
        val m = mat[i].toInt()
        val solid = kindOf[m] == Kind.SOLID
        if (!solid && kindOf[m] != Kind.POWDER) return null
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        val cells = ArrayList<Int>()
        var anchored = false
        stack.addLast(i)
        seen += i
        val limit = if (solid) max else lump
        while (stack.isNotEmpty() && cells.size < limit) {
            val c = stack.removeFirst()
            cells += c
            val x = c % width
            for (n in intArrayOf(if (x > 0) c - 1 else -1, if (x < width - 1) c + 1 else -1, c - width, c + width)) {
                if (n < 0 || n >= size || n in seen) continue
                val q = mat[n].toInt()
                // like structures: a log leaning on a bridge is a piece of its own
                if (solid && kindOf[q] == Kind.SOLID && plantOf[q] == plantOf[m] || !solid && q == m) {
                    seen += n
                    stack.addLast(n)
                } else if (n != c + width && (materials.anchored[q] || (flags[n].toInt() and Flag.ANCHORED) != 0)) {
                    anchored = true
                }
            }
        }
        // a structure larger than [max] counts as too big to hold
        return Piece(cells.toIntArray(), anchored, solid && stack.isNotEmpty())
    }

    /** Cells of an object under the finger; [tooBig] if it goes on beyond what was looked at. */
    class Piece(val cells: IntArray, val anchored: Boolean, val tooBig: Boolean)

    /** A full-grown tree (or bush) on the ground below (x, y), for building worlds (2b). */
    fun plantTree(x: Int, y: Int, tree: Boolean) = living.plantTree(x, y, tree)

    /** Lets the trees and vines with growing tips inside the box grow at once, for building worlds. */
    fun growNow(x0: Int, y0: Int, x1: Int, y1: Int, rounds: Int) = living.growNow(x0, y0, x1, y1, rounds)

    /** Wakes the whole world, e.g. after building it cell by cell. */
    fun wakeAll() {
        pending.fill(true)
        heatPending.fill(true)
    }

    // ------------------------------------------------------------------ the step

    fun step() {
        val t = awake
        awake = pending
        pending = t
        pending.fill(false)
        val h = heatAwake
        heatAwake = heatPending
        heatPending = h
        heatPending.fill(false)
        if (keepAllAwake) {
            awake.fill(true)
            heatAwake.fill(true)
        }
        mark = step + 1
        var n = 0
        for (b in awake) if (b) n++
        awakeChunks = n
        // gas can only have moved where something happens
        if (n > 0) gasDirty = true
        if (step % LIGHT_EVERY == 0) light.compute()
        heat()
        if (n > 0) update()
        living.randomTicks()
        if (structuresDirty) findStructures()
        dropLoose()
        if (step % PRESSURE_EVERY == 0) {
            val t0 = System.nanoTime()
            pressure()
            pressureNanos += System.nanoTime() - t0
        }
        step++
    }

    fun run(steps: Int) = repeat(steps) { step() }

    // ------------------------------------------------------------------ heat (4.2)

    private fun heat() {
        val w = width
        val h = height
        val m = mat
        val tp = temp
        val pa = materials.pairA
        val pb = materials.pairB
        val ra = materials.riseA
        val rb = materials.riseB
        val n = materials.count
        for (cy in 0 until chunksY) {
            for (cx in 0 until chunksX) {
                val c = cy * chunksX + cx
                if (!heatAwake[c]) continue
                val x0 = cx * CHUNK
                val x1 = minOf(w, x0 + CHUNK) - 1
                val y0 = cy * CHUNK
                val y1 = minOf(h, y0 + CHUNK) - 1
                val leftAsleep = cx > 0 && !heatAwake[c - 1]
                val upAsleep = cy > 0 && !heatAwake[c - chunksX]
                var hot = false
                for (y in y0..y1) {
                    var i = y * w + x0
                    for (x in x0..x1) {
                        val ma = m[i].toInt()
                        // right neighbour
                        if (x < w - 1) {
                            val j = i + 1
                            val d = tp[j] - tp[i]
                            if (d > EPS || d < -EPS) {
                                val k = ma * n + m[j].toInt()
                                val da = pa[k] * d
                                tp[i] += da
                                tp[j] -= pb[k] * d
                                if (da > HEAT_WAKE || da < -HEAT_WAKE || d * pb[k] > HEAT_WAKE || d * pb[k] < -HEAT_WAKE) {
                                    hot = true
                                    if (x == x1) wakeChunk(cx + 1, cy)
                                }
                            }
                        }
                        // lower neighbour; out of a hotter gas below, heat rises faster
                        if (y < h - 1) {
                            val j = i + w
                            val d = tp[j] - tp[i]
                            if (d > EPS || d < -EPS) {
                                val mj = m[j].toInt()
                                val k = ma * n + mj
                                val up = d > 0f && kindOf[mj] == Kind.GAS
                                val ka = if (up) ra[k] else pa[k]
                                val kb = if (up) rb[k] else pb[k]
                                val da = ka * d
                                tp[i] += da
                                tp[j] -= kb * d
                                if (da > HEAT_WAKE || da < -HEAT_WAKE || d * kb > HEAT_WAKE || d * kb < -HEAT_WAKE) {
                                    hot = true
                                    if (y == y1) wakeChunk(cx, cy + 1)
                                }
                            }
                        }
                        // neighbours in sleeping chunks to the left and above are not visited from there
                        if (x == x0 && leftAsleep) {
                            val j = i - 1
                            val d = tp[i] - tp[j]
                            if (d > EPS || d < -EPS) {
                                val k = m[j].toInt() * n + ma
                                val dj = pa[k] * d
                                tp[j] += dj
                                tp[i] -= pb[k] * d
                                if (dj > HEAT_WAKE || dj < -HEAT_WAKE) wakeChunk(cx - 1, cy)
                                if (d * pb[k] > HEAT_WAKE || d * pb[k] < -HEAT_WAKE) hot = true
                            }
                        }
                        if (y == y0 && upAsleep) {
                            val j = i - w
                            val d = tp[i] - tp[j]
                            if (d > EPS || d < -EPS) {
                                val k = m[j].toInt() * n + ma
                                val dj = pa[k] * d
                                tp[j] += dj
                                tp[i] -= pb[k] * d
                                if (dj > HEAT_WAKE || dj < -HEAT_WAKE) wakeChunk(cx, cy - 1)
                                if (d * pb[k] > HEAT_WAKE || d * pb[k] < -HEAT_WAKE) hot = true
                            }
                        }
                        // open air carries heat away and comes back to the temperature of the surroundings
                        if (ma == AIR) {
                            val d = ambient - tp[i]
                            if (d > AIR_SNAP || d < -AIR_SNAP) {
                                tp[i] += d * AIR_RELAX
                                hot = true
                            } else {
                                tp[i] = ambient
                            }
                        }
                        i++
                    }
                }
                if (hot) {
                    pending[c] = true
                    heatPending[c] = true
                }
            }
        }
    }

    internal fun wakeChunk(cx: Int, cy: Int) {
        if (cx in 0 until chunksX && cy in 0 until chunksY) {
            pending[cy * chunksX + cx] = true
            heatPending[cy * chunksX + cx] = true
        }
    }

    /** The chunk of (x, y) and, at chunk borders, the neighbouring ones are updated in the next step. */
    internal fun wake(x: Int, y: Int) {
        val cx = x shr SHIFT
        val cy = y shr SHIFT
        val lx = x and (CHUNK - 1)
        val ly = y and (CHUNK - 1)
        val xa = if (lx == 0 && cx > 0) cx - 1 else cx
        val xb = if (lx == CHUNK - 1 && cx < chunksX - 1) cx + 1 else cx
        val ya = if (ly == 0 && cy > 0) cy - 1 else cy
        val yb = if (ly == CHUNK - 1 && cy < chunksY - 1) cy + 1 else cy
        for (yy in ya..yb) for (xx in xa..xb) pending[yy * chunksX + xx] = true
    }

    /** Like [wake], and heat has to flow there too. */
    internal fun wakeHeat(x: Int, y: Int) {
        val cx = x shr SHIFT
        val cy = y shr SHIFT
        val lx = x and (CHUNK - 1)
        val ly = y and (CHUNK - 1)
        val xa = if (lx == 0 && cx > 0) cx - 1 else cx
        val xb = if (lx == CHUNK - 1 && cx < chunksX - 1) cx + 1 else cx
        val ya = if (ly == 0 && cy > 0) cy - 1 else cy
        val yb = if (ly == CHUNK - 1 && cy < chunksY - 1) cy + 1 else cy
        for (yy in ya..yb) for (xx in xa..xb) {
            pending[yy * chunksX + xx] = true
            heatPending[yy * chunksX + xx] = true
        }
    }

    // ------------------------------------------------------------------ cells

    private fun update() {
        val w = width
        for (y in height - 1 downTo 0) {
            val rowChunk = (y shr SHIFT) * chunksX
            val row = y * w
            if (((y + step) and 1) == 0) {
                for (cx in 0 until chunksX) {
                    if (!awake[rowChunk + cx]) continue
                    val x1 = minOf(w, (cx + 1) * CHUNK)
                    var x = cx * CHUNK
                    while (x < x1) {
                        val i = row + x
                        if (mat[i].toInt() != AIR && stamp[i] != mark) updateCell(x, y, i)
                        x++
                    }
                }
            } else {
                for (cx in chunksX - 1 downTo 0) {
                    if (!awake[rowChunk + cx]) continue
                    val x0 = cx * CHUNK
                    var x = minOf(w, x0 + CHUNK) - 1
                    while (x >= x0) {
                        val i = row + x
                        if (mat[i].toInt() != AIR && stamp[i] != mark) updateCell(x, y, i)
                        x--
                    }
                }
            }
        }
    }

    private fun updateCell(x: Int, y: Int, i: Int) {
        val mt = materials
        val m = mat[i].toInt()
        val f = flags[i].toInt()
        if (mt.inert[m] && (f and (Flag.BURNING or Flag.WET)) == 0) return

        // fire (4.2: ignition is a temperature threshold, and burning needs air)
        if (f and Flag.BURNING != 0) {
            if (!burn(x, y, i, m)) return
        } else if (mt.flammable[m] && temp[i] >= mt.igniteTemp[m] && ((f and Flag.WET) == 0 || !mt.wetBlocksBurning[m])) {
            if (oxygen(x, y, i) > 0) {
                flags[i] = (f or Flag.BURNING).toByte()
                life[i] = burnLife(m).toShort()
                events[Event.IGNITE]++
                note(Seen.IGNITE, m)
                wake(x, y)
            } else if (mt.quenchProduct[m] != m && temp[i] >= mt.igniteTemp[m] + CHAR_OVER && chance(CHAR_CHANCE)) {
                // well above burning heat but no air to burn with: it slowly chars, as wood does in a charcoal kiln
                note(Seen.CHAR, m)
                change(x, y, i, mt.quenchProduct[m])
                return
            }
        }

        // salt dissolves in water and turns it salty
        if (mt.dissolvesIn[m] >= 0 && dissolve(x, y, i, m)) return

        // brittle material cracks when water, ice or snow cools it suddenly (4a.2)
        if (mt.shockProduct[m] >= 0 && temp[i] > SHOCK_FROM && shocked(x, y, i, m)) {
            note(Seen.CRACK, m)
            change(x, y, i, mt.shockProduct[m])
            return
        }

        // firedamp goes off at fire, embers or its ignition heat (4a.4)
        if (mt.explosive[m] && ignites(x, y, i, m)) {
            detonate(x, y, i, m)
            return
        }

        // phase changes, all at temperature thresholds; salt next to ice or snow lowers its melting point
        val t = temp[i]
        val byHeat = t >= mt.meltTemp[m]
        if ((byHeat && melted(x, y, i, m)) || (!byHeat && t >= mt.saltMeltTemp[m] && saltyNeighbour(x, y, i))) {
            note(if (byHeat) Seen.MELT else Seen.SALT_MELT, m)
            change(x, y, i, mt.meltProduct[m])
            events[Event.MELT]++
            return
        }
        if (t >= mt.boilTemp[m] && boil(x, y, i, m)) return
        if (t <= mt.freezeTemp[m] && freeze(x, y, i, m)) return

        // wet material dries when it gets hot, and steams
        if (f and Flag.WET != 0 && t >= DRY_TEMP && chance(DRY_CHANCE)) {
            flags[i] = (flags[i].toInt() and Flag.WET.inv()).toByte()
            temp[i] = t - DRY_COOLING
            emit(x, y, i, steamId)
            events[Event.BOIL]++
            wakeHeat(x, y)
        }

        // wet soil loosened by worms lets the water seep on down; water runs through gravel
        if (f and SEEPING == SEEPING && mt.kind[m] == Kind.POWDER && mt.absorbs[m] > 0f) seep(x, y, i)
        if (f and Flag.WET != 0 && mt.permeable[m] > 0f && flags[i].toInt() and Flag.WET != 0) percolate(x, y, i, m)

        // smoke thins out
        if (mt.lifetimeSteps[m] > 0) {
            val l = life[i] - 1
            if (l <= 0) {
                change(x, y, i, mt.decayProduct[m])
                return
            }
            life[i] = l.toShort()
            wake(x, y)
        }

        when (mt.kind[m]) {
            Kind.POWDER -> movePowder(x, y, i, m)
            Kind.LIQUID -> moveLiquid(x, y, i, m)
            Kind.GAS -> moveGas(x, y, i, m)
            Kind.LIFE -> if (mt.animal[m]) living.animal(x, y, i, m)
        }
    }

    /** Wet, loosened soil passes its water on to dry soil below it: worm burrows let water seep in (2b). */
    private fun seep(x: Int, y: Int, i: Int) {
        if (y >= height - 1) return
        val b = i + width
        val q = mat[b].toInt()
        if (kindOf[q] != Kind.POWDER || materials.absorbs[q] <= 0f || flags[b].toInt() and Flag.WET != 0) return
        wake(x, y)
        if (!chance(SEEP)) return
        flags[b] = (flags[b].toInt() or Flag.WET).toByte()
        flags[i] = (flags[i].toInt() and Flag.WET.inv()).toByte()
        note(Seen.SEEP, mat[i].toInt())
        wake(x, y + 1)
    }

    /** Roots of a plant beside it or up to three cells above (grass, moss, vine, trunk) hold loose ground in place (2b). */
    private fun rooted(x: Int, y: Int, i: Int): Boolean {
        val r = materials.roots
        val w = width
        if ((x > 0 && r[mat[i - 1].toInt()]) || (x < w - 1 && r[mat[i + 1].toInt()])) return true
        if (y > 0 && ((x > 0 && r[mat[i - w - 1].toInt()]) || (x < w - 1 && r[mat[i - w + 1].toInt()]))) return true
        var j = i - w
        var k = 0
        while (k < 3 && j >= 0) {
            if (r[mat[j].toInt()]) return true
            j -= w
            k++
        }
        return false
    }

    /** A neighbour (4) that is salty: salt or salt water. */
    internal fun saltyNeighbour(x: Int, y: Int, i: Int): Boolean {
        val sa = materials.salty
        return (y > 0 && sa[mat[i - width].toInt()]) || (y < height - 1 && sa[mat[i + width].toInt()]) ||
            (x > 0 && sa[mat[i - 1].toInt()]) || (x < width - 1 && sa[mat[i + 1].toInt()])
    }

    /** A neighbour (4) that is a liquid that wets (water, salt water): its material, or -1. */
    private fun wetNeighbour(x: Int, y: Int, i: Int): Int {
        val wt = materials.wets
        if (y > 0 && wt[mat[i - width].toInt()]) return mat[i - width].toInt()
        if (y < height - 1 && wt[mat[i + width].toInt()]) return mat[i + width].toInt()
        if (x > 0 && wt[mat[i - 1].toInt()]) return mat[i - 1].toInt()
        if (x < width - 1 && wt[mat[i + 1].toInt()]) return mat[i + 1].toInt()
        return -1
    }

    /**
     * A grain that dissolves (salt) next to its solvent (water): both cells become the solution (salt
     * water). Returns whether it happened.
     */
    private fun dissolve(x: Int, y: Int, i: Int, m: Int): Boolean {
        val solvent = materials.dissolvesIn[m]
        val w = width
        var j = -1
        if (y < height - 1 && mat[i + w].toInt() == solvent) j = i + w
        else if (x > 0 && mat[i - 1].toInt() == solvent) j = i - 1
        else if (x < w - 1 && mat[i + 1].toInt() == solvent) j = i + 1
        else if (y > 0 && mat[i - w].toInt() == solvent) j = i - w
        if (j < 0) return false
        if (!chance(DISSOLVE)) {
            wake(x, y)
            return false
        }
        val product = materials.dissolvedProduct[m]
        note(Seen.DISSOLVE, m)
        change(x, y, i, product)
        change(j % w, j / w, j, product)
        return true
    }

    /** Neighbours (4) that give a fire air: air itself, and flames. */
    private fun oxygen(x: Int, y: Int, i: Int): Int {
        val ox = materials.oxygen
        var n = 0
        if (y > 0 && ox[mat[i - width].toInt()]) n++
        if (y < height - 1 && ox[mat[i + width].toInt()]) n++
        if (x > 0 && ox[mat[i - 1].toInt()]) n++
        if (x < width - 1 && ox[mat[i + 1].toInt()]) n++
        return n
    }

    private fun burnLife(m: Int): Int {
        val s = materials.burnSteps[m]
        return (s * 3 / 4 + below(s / 2 + 1)).coerceIn(1, Short.MAX_VALUE.toInt())
    }

    private fun initLife(m: Int): Int {
        val mt = materials
        if (mt.spawnBurning[m]) return burnLife(m)
        val l = mt.lifetimeSteps[m]
        if (l > 0) return (l * 3 / 5 + below(l * 4 / 5 + 1)).coerceIn(1, Short.MAX_VALUE.toInt())
        return 0
    }

    /**
     * One step of burning: the cell heats itself towards its burning temperature and sends flames and smoke
     * into the air around it. Without air it soon goes out, cooled below its ignition point too; then it
     * becomes its quench product (wood: charcoal). Burnt through, it leaves its residue (wood: some ash).
     * Returns whether the cell still holds the same burning material.
     */
    private fun burn(x: Int, y: Int, i: Int, m: Int): Boolean {
        val mt = materials
        wakeHeat(x, y)
        val t = temp[i]
        if (t < mt.quenchTemp[m]) {
            // cooled below burning: by water next to it, if there is any
            val dousing = wetNeighbour(x, y, i)
            if (dousing >= 0) note(Seen.DOUSE, dousing)
            quench(x, y, i, m)
            return false
        }
        if (oxygen(x, y, i) == 0) {
            if (chance(SUFFOCATE)) {
                note(Seen.SUFFOCATE, m)
                quench(x, y, i, m)
                return false
            }
        } else {
            // burning gives off a steady amount of heat; how hot that makes the cell depends on how much its
            // surroundings take away (water takes a lot)
            val bt = mt.burnTemp[m]
            if (t < bt) temp[i] = minOf(bt, t + mt.burnHeat[m] / mt.heatCapacity[m])
            if (mt.flame[m] > 0f && chance(mt.flame[m])) emit(x, y, i, fireId)
            if (mt.smoke[m] > 0f && chance(mt.smoke[m])) emit(x, y, i, smokeId)
        }
        val l = life[i] - 1
        if (l <= 0) {
            val r = mt.residue[m]
            note(Seen.BURN_OUT, m)
            change(x, y, i, if (r >= 1f || (r > 0f && chance(r))) mt.burnProduct[m] else AIR)
            events[Event.BURNT_OUT]++
            return false
        }
        life[i] = l.toShort()
        return true
    }

    private fun quench(x: Int, y: Int, i: Int, m: Int) {
        val product = materials.quenchProduct[m]
        if (product == m) {
            flags[i] = (flags[i].toInt() and Flag.BURNING.inv()).toByte()
            life[i] = 0
            wake(x, y)
        } else {
            change(x, y, i, product)
        }
        events[Event.EXTINGUISH]++
    }

    /**
     * Melting takes the latent heat on top of the melting point, if the material has one (ice, snow); it is
     * collected in [life]. Salt melts ice without it.
     */
    private fun melted(x: Int, y: Int, i: Int, m: Int): Boolean {
        val lat = materials.latent[m]
        if (lat <= 0 || materials.boilTemp[m] != Float.MAX_VALUE || materials.freezeTemp[m] != -Float.MAX_VALUE) return true
        val mt = materials.meltTemp[m]
        val excess = (temp[i] - mt).toInt()
        if (excess > 0) {
            temp[i] -= excess
            life[i] = minOf(Short.MAX_VALUE.toInt(), life[i] + excess).toShort()
            wakeHeat(x, y)
        } else {
            wake(x, y)
        }
        if (life[i] < lat) return false
        temp[i] = mt
        return true
    }

    /** Boiling takes the latent heat on top of the boiling point; it is collected in [life]. */
    private fun boil(x: Int, y: Int, i: Int, m: Int): Boolean {
        val mt = materials
        val bt = mt.boilTemp[m]
        val lat = mt.latent[m]
        if (lat > 0) {
            // next to something glowing hot (lava) it flashes into steam at once, taking the heat from it
            if (life[i] < lat) flash(x, y, i, m, lat)
            val excess = (temp[i] - bt).toInt()
            if (excess > 0) {
                temp[i] -= excess
                life[i] = minOf(Short.MAX_VALUE.toInt(), life[i] + excess).toShort()
                wakeHeat(x, y)
            }
            if (life[i] < lat) return false
            temp[i] = bt + 2f
        }
        val residue = mt.boilResidue[m]
        note(Seen.BOIL, m)
        if (residue >= 0 && chance(mt.boilResidueShare[m])) {
            // the water goes up as steam, what was dissolved in it stays behind
            note(Seen.RESIDUE, m)
            change(x, y, i, residue)
            emit(x, y, i, mt.boilProduct[m])
        } else {
            change(x, y, i, mt.boilProduct[m])
        }
        events[Event.BOIL]++
        return true
    }

    /** Setting takes the latent heat out on top of cooling to the freezing point; it is collected in [life]. */
    private fun freeze(x: Int, y: Int, i: Int, m: Int): Boolean {
        val mt = materials
        val ft = mt.freezeTemp[m]
        val lat = mt.latent[m]
        if (lat > 0 && mt.boilTemp[m] == Float.MAX_VALUE) {
            val deficit = (ft - temp[i]).toInt()
            if (deficit > 0) {
                temp[i] += deficit
                life[i] = minOf(Short.MAX_VALUE.toInt(), life[i] + deficit).toShort()
                wakeHeat(x, y)
            }
            if (life[i] < lat) return false
            temp[i] = ft - 2f
        }
        note(if (mt.kind[m] == Kind.GAS) Seen.CONDENSE else Seen.SET, m)
        change(x, y, i, mt.freezeProduct[m])
        events[if (mt.kind[m] == Kind.GAS) Event.CONDENSE else Event.SOLIDIFY]++
        return true
    }

    /**
     * Sends a new particle of [product] (flame, smoke, steam) into a neighbouring air cell: mostly the one
     * above, otherwise the first free one beside or below.
     */
    private fun emit(x: Int, y: Int, i: Int, product: Int) {
        if (product == AIR) return
        val w = width
        val r = below(10)
        val first = if (r < 5) 0 else if (r < 7) 1 else if (r < 9) 2 else 3
        for (k in 0 until 4) {
            val dir = (first + k) and 3
            val nx = if (dir == 1) x - 1 else if (dir == 2) x + 1 else x
            val ny = if (dir == 0) y - 1 else if (dir == 3) y + 1 else y
            if (nx < 0 || nx >= w || ny < 0 || ny >= height) continue
            val j = ny * w + nx
            if (mat[j].toInt() != AIR) continue
            // flames and steam come out at their own temperature, smoke carries some of the heat away
            val t = if (materials.hasSpawnTemp[product]) maxOf(materials.spawnTemp[product], temp[j]) else maxOf(temp[i] * 0.6f, temp[j])
            spawn(nx, ny, j, product, t)
            stamp[j] = mark
            return
        }
    }

    internal fun spawn(x: Int, y: Int, i: Int, m: Int, t: Float) {
        if (kindOf[m] == Kind.SOLID || kindOf[mat[i].toInt()] == Kind.SOLID) structuresDirty = true
        if ((kindOf[m] == Kind.GAS) != (kindOf[mat[i].toInt()] == Kind.GAS)) gasDirty = true
        mat[i] = m.toByte()
        temp[i] = t
        life[i] = initLife(m).toShort()
        variant[i] = next().toByte()
        flags[i] = (if (materials.spawnBurning[m]) Flag.BURNING else 0).toByte()
        // a vine is planted as a growing tip
        val g = materials.growSize[m]
        if (g > 0) {
            flags[i] = (flags[i].toInt() or Flag.MARK).toByte()
            life[i] = g.toShort()
        }
        wakeHeat(x, y)
    }

    internal fun clear(x: Int, y: Int, i: Int) {
        if (kindOf[mat[i].toInt()] == Kind.SOLID) structuresDirty = true
        mat[i] = AIR.toByte()
        temp[i] = ambient
        life[i] = 0
        flags[i] = 0
        wakeHeat(x, y)
    }

    /** The cell turns into [product] and keeps its temperature. */
    internal fun change(x: Int, y: Int, i: Int, product: Int) {
        if (kindOf[product] == Kind.SOLID || kindOf[mat[i].toInt()] == Kind.SOLID) structuresDirty = true
        if ((kindOf[product] == Kind.GAS) != (kindOf[mat[i].toInt()] == Kind.GAS)) gasDirty = true
        mat[i] = product.toByte()
        life[i] = initLife(product).toShort()
        variant[i] = next().toByte()
        flags[i] = ((if (materials.spawnBurning[product]) Flag.BURNING else 0) or (flags[i].toInt() and Flag.ANCHORED)).toByte()
        stamp[i] = mark
        wakeHeat(x, y)
    }

    internal fun swap(i: Int, j: Int, x: Int, y: Int, x2: Int, y2: Int) {
        val m = mat[i]; mat[i] = mat[j]; mat[j] = m
        val t = temp[i]; temp[i] = temp[j]; temp[j] = t
        val l = life[i]; life[i] = life[j]; life[j] = l
        val v = variant[i]; variant[i] = variant[j]; variant[j] = v
        val f = flags[i]; flags[i] = flags[j]; flags[j] = f
        stamp[i] = mark
        stamp[j] = mark
        // a hot particle carries its heat into new surroundings
        val dt = temp[i] - temp[j]
        if (dt > SWAP_HEAT || dt < -SWAP_HEAT) {
            wakeHeat(x, y)
            wakeHeat(x2, y2)
        } else {
            wake(x, y)
            wake(x2, y2)
        }
    }

    private fun setVelocity(i: Int, v: Int) {
        flags[i] = ((flags[i].toInt() and Flag.VEL_MASK.inv()) or (v shl Flag.VEL_SHIFT)).toByte()
    }

    /**
     * Can a falling or flowing particle of [m] take the place of [q]? Only gases, lighter liquids and lighter
     * animals give way.
     */
    private fun yields(m: Int, q: Int): Boolean {
        if (q == AIR) return true
        val k = kindOf[q]
        return (k == Kind.GAS || k == Kind.LIQUID || materials.animal[q]) && densityOf[q] < densityOf[m]
    }

    /** Falls up to 1 + speed / 2 cells through air and gases; a liquid below slows it down. Returns whether it moved. */
    internal fun fall(x: Int, y: Int, i: Int, m: Int): Boolean {
        if (y >= height - 1) return false
        val w = width
        val below = i + w
        val q = mat[below].toInt()
        if (!yields(m, q)) return false
        if (kindOf[q] == Kind.LIQUID) {
            // sinking through a liquid is slow
            setVelocity(i, 0)
            if (chance(SINK_CHANCE)) swap(i, below, x, y, x, y + 1)
            return true
        }
        val v = (flags[i].toInt() and Flag.VEL_MASK) shr Flag.VEL_SHIFT
        // a falling liquid stays a stream instead of breaking into drops with holes between them
        val maxFall = if (kindOf[m] == Kind.LIQUID) 1 + (minOf(v, 2) shr 1) else 1 + (v shr 1)
        var j = below
        var ny = y + 1
        var d = 1
        while (d < maxFall && ny < height - 1) {
            val k = j + w
            val qk = mat[k].toInt()
            if (qk != AIR && kindOf[qk] != Kind.GAS) break
            if (!yields(m, qk)) break
            j = k
            ny++
            d++
        }
        swap(i, j, x, y, x, ny)
        setVelocity(j, minOf(7, v + 1))
        return true
    }

    private fun movePowder(x: Int, y: Int, i: Int, m: Int) {
        if (fall(x, y, i, m)) return
        if ((flags[i].toInt() and Flag.VEL_MASK) != 0) setVelocity(i, 0)
        if (y >= height - 1) return
        if (materials.avalanche[m]) {
            // snow that has taken up some melting heat comes loose
            if (flags[i].toInt() and Flag.MARK == 0 && life[i] > AVALANCHE_WARM) loosen(i, x, y)
            if (flags[i].toInt() and Flag.MARK != 0) {
                avalanche(x, y, i, m)
                return
            }
        }
        val fr = if (flags[i].toInt() and Flag.WET != 0) materials.wetFriction[m] else materials.friction[m]
        if (chance(fr)) return
        // roots hold the soil, wet or dry; turf holds itself
        if (materials.roots[m] || (materials.erodes[m] && rooted(x, y, i))) return
        val d = if (coin()) 1 else -1
        if (slide(x, y, i, m, d)) return
        slide(x, y, i, m, -d)
    }

    /** Diagonally down, but not through a gap between two diagonal neighbours. */
    private fun slide(x: Int, y: Int, i: Int, m: Int, d: Int): Boolean {
        val nx = x + d
        if (nx < 0 || nx >= width) return false
        if (!yields(m, mat[i + d].toInt())) return false
        val j = i + d + width
        if (!yields(m, mat[j].toInt())) return false
        swap(i, j, x, y, nx, y + 1)
        return true
    }

    /** A quenching neighbour (water, ice, snow) takes more heat out of cell [i] in one step than it can bear. */
    private fun shocked(x: Int, y: Int, i: Int, m: Int): Boolean {
        val w = width
        val n = materials.count
        val t = temp[i]
        val limit = materials.shockRate[m]
        val q = materials.quenches
        val pa = materials.pairA
        fun quench(j: Int): Boolean {
            val o = mat[j].toInt()
            return q[o] && pa[m * n + o] * (t - temp[j]) > limit
        }
        return (x > 0 && quench(i - 1)) || (x < w - 1 && quench(i + 1)) || (y > 0 && quench(i - w)) || (y < height - 1 && quench(i + w))
    }

    /**
     * Water in the pores of permeable grains (gravel, glass shards; [Flag.WET] holds one cell of water) runs
     * on (4a.3): out into the open below or beside, else on into dry grains below or beside. So water seeps
     * through a bed of gravel into a cave below it, or through a dam to its foot on the other side. Sand,
     * earth and mud are not permeable: they stay on top, so gravel filters.
     */
    private fun percolate(x: Int, y: Int, i: Int, m: Int) {
        val w = width
        val pm = materials.permeable
        val d = if (coin()) 1 else -1
        var to = -1
        var out = false
        val down = if (y < height - 1) i + w else -1
        val a = if (x + d in 0 until w) i + d else -1
        val b = if (x - d in 0 until w) i - d else -1
        for (j in intArrayOf(down, a, b)) if (j >= 0 && open(j)) {
            to = j
            out = true
            break
        }
        if (to < 0) for (j in intArrayOf(down, a, b)) {
            if (j >= 0 && pm[mat[j].toInt()] > 0f && flags[j].toInt() and Flag.WET == 0) {
                to = j
                break
            }
        }
        if (to < 0) return
        wake(x, y)
        if (!chance(pm[m])) return
        flags[i] = (flags[i].toInt() and Flag.WET.inv()).toByte()
        val tx = to % w
        val ty = to / w
        if (out) {
            spawn(tx, ty, to, waterId, temp[i])
            note(Seen.PERMEATE, m)
        } else {
            flags[to] = (flags[to].toInt() or Flag.WET).toByte()
            wake(tx, ty)
        }
    }

    /** Air or another gas: room for water that has come through. */
    private fun open(j: Int): Boolean {
        val q = mat[j].toInt()
        return q == AIR || kindOf[q] == Kind.GAS
    }

    /** Snow at [i] comes loose and starts to slide. */
    private fun loosen(i: Int, x: Int, y: Int) {
        flags[i] = (flags[i].toInt() or Flag.MARK).toByte()
        wake(x, y)
    }

    /**
     * Loose snow on a slope slides down it like a thick liquid and takes the snow around it along (4a.3). On
     * level ground it comes to rest again.
     */
    private fun avalanche(x: Int, y: Int, i: Int, m: Int) {
        wake(x, y)
        val w = width
        if (chance(AVALANCHE_SPREAD)) {
            // the snow above and beside it, uphill too, is torn along
            val j = when (below(5)) {
                0 -> if (y > 0) i - w else -1
                1 -> if (x > 0) i - 1 else -1
                2 -> if (x < w - 1) i + 1 else -1
                3 -> if (y > 0 && x > 0) i - w - 1 else -1
                else -> if (y > 0 && x < w - 1) i - w + 1 else -1
            }
            if (j >= 0 && mat[j].toInt() == m && flags[j].toInt() and Flag.MARK == 0) loosen(j, j % w, j / w)
        }
        val d = if (coin()) 1 else -1
        if (slide(x, y, i, m, d) || slide(x, y, i, m, -d) || flow(x, y, i, m, d) || flow(x, y, i, m, -d)) {
            note(Seen.AVALANCHE, m)
            return
        }
        if (chance(AVALANCHE_SETTLE)) flags[i] = (flags[i].toInt() and Flag.MARK.inv()).toByte()
    }

    /** Shakes the ground around (x, y): snow within [r] cells comes loose (a landing block, an explosion). */
    fun shake(cx: Int, cy: Int, r: Int) {
        val av = materials.avalanche
        for (y in maxOf(0, cy - r)..minOf(height - 1, cy + r)) for (x in maxOf(0, cx - r)..minOf(width - 1, cx + r)) {
            val i = y * width + x
            if (av[mat[i].toInt()] && flags[i].toInt() and Flag.MARK == 0) loosen(i, x, y)
        }
    }

    /** Snow under a heavy load is pressed into ice, now and then (a random tick, 4a.3). */
    internal fun compact(x: Int, y: Int, i: Int, m: Int) {
        val w = width
        var load = 0f
        var j = i - w
        var k = 0
        while (j >= 0 && k < COMPACT_REACH) {
            val q = mat[j].toInt()
            if (q == AIR || kindOf[q] == Kind.GAS) break
            load += densityOf[q] / 1000f
            j -= w
            k++
        }
        if (load < materials.compactLoad[m] || !chance(COMPACT_CHANCE)) return
        note(Seen.COMPACT, m)
        change(x, y, i, materials.compactProduct[m])
    }

    private fun moveLiquid(x: Int, y: Int, i: Int, m: Int) {
        val mt = materials
        if (mt.wets[m] && soak(x, y, i)) return
        if (fall(x, y, i, m)) return
        if ((flags[i].toInt() and Flag.VEL_MASK) != 0) setVelocity(i, 0)
        val d = if (coin()) 1 else -1
        if (y < height - 1) {
            if (slide(x, y, i, m, d)) {
                if (mt.wets[m]) erode(x + d, y + 1, d)
                return
            }
            if (slide(x, y, i, m, -d)) {
                if (mt.wets[m]) erode(x - d, y + 1, -d)
                return
            }
        }
        if (mt.viscosity[m] > 0f && chance(mt.viscosity[m])) return
        if (flow(x, y, i, m, d)) return
        flow(x, y, i, m, -d)
    }

    /**
     * Flows sideways towards the nearest place within [MaterialTable.reach] where it can sink lower, at most
     * [MaterialTable.spread] cells per step. Liquid with no lower place in reach is level and lies still (3.3).
     * At the surface it looks past other cells of the same liquid, so a ripple glides over to the low place.
     */
    private fun flow(x: Int, y: Int, i: Int, m: Int, d: Int): Boolean {
        if (y >= height - 1) return false
        val reach = materials.reach[m]
        val surface = y == 0 || mat[i - width].toInt() != m
        val a = dropDistance(x, i, m, d, reach, surface)
        val b = dropDistance(x, i, m, -d, if (a > 0) a else reach, surface)
        val dir: Int
        val dist: Int
        if (a > 0 && (b == 0 || a <= b)) {
            dir = d
            dist = a
        } else if (b > 0) {
            dir = -d
            dist = b
        } else {
            return false
        }
        var k = minOf(dist, materials.spread[m])
        while (k > 0 && !yields(m, mat[i + dir * k].toInt())) k--
        if (k == 0) {
            // the way is taken by liquid moving the same way: wait for it, awake
            wake(x, y)
            return false
        }
        val tx = x + dir * k
        swap(i, i + dir * k, x, y, tx, y)
        if (materials.wets[m]) erode(tx, y, dir)
        return true
    }

    /** How far sideways in direction [d] the liquid finds a place to sink, or 0 if not within [reach]. */
    private fun dropDistance(x: Int, i: Int, m: Int, d: Int, reach: Int, throughSame: Boolean): Int {
        val w = width
        var k = 1
        while (k <= reach) {
            val nx = x + d * k
            if (nx < 0 || nx >= w) return 0
            val j = i + d * k
            val q = mat[j].toInt()
            if (q == m && throughSame) {
                k++
                continue
            }
            if (!yields(m, q)) return 0
            if (yields(m, mat[j + w].toInt())) return k
            k++
        }
        return 0
    }

    /**
     * Water that flows over loose ground carries grains of it along (4.4: water washes away sand, earth and
     * gravel): the grain under the water moves one cell downstream, if there is room there.
     */
    private fun erode(x: Int, y: Int, d: Int) {
        val by = y + 1
        if (by >= height) return
        val b = by * width + x
        val g = mat[b].toInt()
        if (!materials.erodes[g]) return
        if (!chance(ERODE * (1f - materials.hardness[g]))) return
        if (rooted(x, by, b)) return
        val nx = x + d
        if (nx < 0 || nx >= width) return
        val c = b + d
        val q = mat[c].toInt()
        if (q == AIR || materials.kind[q] == Kind.LIQUID) {
            if (materials.permeable[g] == 0f) flags[b] = (flags[b].toInt() or Flag.WET).toByte()
            note(Seen.ERODE, g)
            swap(b, c, x, by, nx, by)
        }
    }

    /** Absorbent ground next to water soaks some of it up and becomes wet; water runs into the pores of gravel. */
    private fun soak(x: Int, y: Int, i: Int): Boolean {
        val ab = if (mat[i].toInt() == waterId) takesWater else materials.absorbs
        val w = width
        var j = -1
        var jx = x
        var jy = y
        if (y < height - 1 && ab[mat[i + w].toInt()] > 0f) { j = i + w; jy = y + 1 }
        else if (x > 0 && ab[mat[i - 1].toInt()] > 0f) { j = i - 1; jx = x - 1 }
        else if (x < w - 1 && ab[mat[i + 1].toInt()] > 0f) { j = i + 1; jx = x + 1 }
        else if (y > 0 && ab[mat[i - w].toInt()] > 0f) { j = i - w; jy = y - 1 }
        if (j < 0) return false
        val f = flags[j].toInt()
        // burning ground does not soak: the heat boils the water first
        if (f and (Flag.WET or Flag.BURNING) != 0) return false
        if (!chance(ab[mat[j].toInt()])) {
            // still soaking in: stay awake even when the water lies still
            wake(x, y)
            return false
        }
        flags[j] = (f or Flag.WET).toByte()
        note(Seen.SOAK, mat[j].toInt())
        wake(jx, jy)
        clear(x, y, i)
        events[Event.SOAK]++
        return true
    }

    private fun moveGas(x: Int, y: Int, i: Int, m: Int) {
        val mt = materials
        val w = width
        val d = if (coin()) 1 else -1
        if (chance(mt.rise[m])) {
            if (y == 0) {
                // out of the top of the world into the open sky
                clear(x, y, i)
                return
            }
            val up = i - w
            if (rises(m, mat[up].toInt())) {
                swap(i, up, x, y, x, y - 1)
                return
            }
            val nx = x + d
            if (nx in 0 until w && rises(m, mat[up + d].toInt())) {
                swap(i, up + d, x, y, nx, y - 1)
                return
            }
        }
        // drift sideways now and then: plumes stay together instead of scattering
        if (coin()) return
        val nx = x + d
        if (nx in 0 until w && mat[i + d].toInt() == AIR) swap(i, i + d, x, y, nx, y)
    }

    /** A gas rises into a heavier gas (air included). */
    private fun rises(m: Int, q: Int): Boolean =
        q != m && kindOf[q] == Kind.GAS && densityOf[q] > densityOf[m]

    // ------------------------------------------------------------------ pressure and explosions (4a.4)

    /**
     * Every [PRESSURE_EVERY] steps: finds the gas regions (4-connected cells of air, steam, smoke, firedamp)
     * anew if gas has appeared or gone somewhere, and works out the pressure in each closed one, a region
     * that does not reach the open sky at the top. Pressure is the gas it holds (steam counts many times, as
     * water spread out thin) times its absolute temperature, per cell, against the air outside. Where it
     * is higher than the weakest wall around can bear, that wall bursts.
     */
    private fun pressure() {
        if (gasDirty) findGasRegions()
        val air = ambient + KELVIN
        val ex = materials.expansion
        for (k in 0 until closedCount) {
            val a = closedStart[k]
            val b = closedStart[k + 1]
            var gas = 0f
            for (c in a until b) {
                val j = closedCells[c]
                gas += ex[mat[j].toInt()] * (temp[j] + KELVIN) / air
            }
            val n = b - a
            val excess = gas - n
            closedPressure[k] = gas / n
            if (excess < MIN_EXCESS) continue
            if (burst(a, b, gas / n, excess)) {
                // the region is open now; the others are looked at next time
                gasDirty = true
                return
            }
        }
    }

    private fun findGasRegions() {
        gasDirty = false
        val w = width
        region.fill(0)
        closedCount = 0
        var used = 0
        var id = 0
        for (i in 0 until size) {
            if (region[i] != 0 || kindOf[mat[i].toInt()] != Kind.GAS) continue
            id++
            var open = false
            fillTop = 0
            fillStack[fillTop++] = i
            region[i] = id
            var count = 0
            while (fillTop > 0) {
                val c = fillStack[--fillTop]
                if (count == fillCells.size) fillCells = fillCells.copyOf(count * 2)
                fillCells[count++] = c
                if (c < w) open = true
                val x = c % w
                if (x > 0) gasVisit(c - 1, id)
                if (x < w - 1) gasVisit(c + 1, id)
                if (c >= w) gasVisit(c - w, id)
                if (c < size - w) gasVisit(c + w, id)
            }
            if (open) continue
            // a closed region: keep its cells, and mark them with its (negative) number
            if (closedCount + 1 >= closedStart.size) {
                closedStart = closedStart.copyOf(closedStart.size * 2)
                closedPressure = closedPressure.copyOf(closedStart.size)
            }
            if (used + count > closedCells.size) closedCells = closedCells.copyOf(maxOf(closedCells.size * 2, used + count))
            closedStart[closedCount] = used
            for (c in 0 until count) {
                closedCells[used++] = fillCells[c]
                region[fillCells[c]] = -closedCount - 1
            }
            closedPressure[closedCount] = 1f
            closedCount++
            closedStart[closedCount] = used
        }
    }

    private fun gasVisit(n: Int, id: Int) {
        if (region[n] == 0 && kindOf[mat[n].toInt()] == Kind.GAS) {
            region[n] = id
            fillStack[fillTop++] = n
        }
    }

    /**
     * The closed region (cells a until b of [closedCells]) has [pressure]: if that is more than the weakest
     * wall around it can bear, the wall bursts (glass first, then clay, ice, wood, brick, stone, metal; rock
     * never) and the pent-up gas blasts out through the gap.
     */
    private fun burst(a: Int, b: Int, pressure: Float, excess: Float): Boolean {
        val w = width
        val st = materials.strength
        var weakest = -1
        var least = Float.POSITIVE_INFINITY
        for (c in a until b) {
            val j = closedCells[c]
            val x = j % w
            for (n in intArrayOf(if (x > 0) j - 1 else -1, if (x < w - 1) j + 1 else -1, j - w, j + w)) {
                if (n < 0 || n >= size) continue
                val q = mat[n].toInt()
                val k = kindOf[q]
                if (k == Kind.GAS) continue
                // a pool or a heap on the floor is no wall: pressure presses it down, it cannot give way
                if (n == j + w && (k == Kind.LIQUID || k == Kind.POWDER)) continue
                val s = if (flags[n].toInt() and Flag.ANCHORED != 0) Float.POSITIVE_INFINITY else st[q]
                if (s < least) {
                    least = s
                    weakest = n
                }
            }
        }
        if (weakest < 0 || pressure <= least) return false
        val q = mat[weakest].toInt()
        note(Seen.BURST, q)
        for (c in a until b) {
            val g = mat[closedCells[c]].toInt()
            if (g != AIR) note(Seen.BURST, g)
        }
        val wx = weakest % w
        val wy = weakest / w
        if (materials.breakProduct[q] >= 0) change(wx, wy, weakest, materials.breakProduct[q])
        // as strong as the pressure was, as far-reaching as the gas that rushes out
        explode(wx, wy, pressure, (2f + kotlin.math.sqrt(excess) * BURST_REACH).toInt())
        return true
    }

    /**
     * An explosion at (cx, cy) (4a.4): a pressure wave of [force] (in the units of wall strength) at the
     * centre, fading out to [reach] cells. It breaks what is weaker than the force where it is, sets what
     * burns alight, shakes snow loose and throws loose and liquid material away from it.
     */
    fun explode(cx: Int, cy: Int, force: Float, reach: Int) {
        if (force <= 0f || reach <= 0) return
        val w = width
        val r = minOf(MAX_BLAST, reach)
        val r2 = r * r
        events[Event.EXPLODE]++
        val mt = materials
        // the wave: break, kindle, shake
        for (y in maxOf(0, cy - r)..minOf(height - 1, cy + r)) for (x in maxOf(0, cx - r)..minOf(w - 1, cx + r)) {
            val dx = x - cx
            val dy = y - cy
            val d2 = dx * dx + dy * dy
            if (d2 > r2) continue
            val i = y * w + x
            val q = mat[i].toInt()
            val here = force * (1f - kotlin.math.sqrt(d2.toFloat()) / (r + 1))
            if (q != AIR && flags[i].toInt() and Flag.ANCHORED == 0 && mt.breakProduct[q] >= 0 && here > mt.strength[q]) {
                note(Seen.EXPLODE, q)
                change(x, y, i, mt.breakProduct[q])
            }
            val m = mat[i].toInt()
            if (mt.flammable[m] && flags[i].toInt() and Flag.BURNING == 0 && here > KINDLE && temp[i] < mt.igniteTemp[m] + 20f) {
                temp[i] = mt.igniteTemp[m] + 20f
            }
            if (mt.avalanche[m] && flags[i].toInt() and Flag.MARK == 0) loosen(i, x, y)
            wakeHeat(x, y)
        }
        // the throw: loose and liquid material flies outwards, the outermost first
        var n = 0
        for (y in maxOf(0, cy - r)..minOf(height - 1, cy + r)) for (x in maxOf(0, cx - r)..minOf(w - 1, cx + r)) {
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy > r2) continue
            val k = kindOf[mat[y * w + x].toInt()]
            if (k != Kind.POWDER && k != Kind.LIQUID) continue
            if (n == blast.size) blast = blast.copyOf(n * 2)
            blast[n++] = ((r2 - dx * dx - dy * dy) shl 20) or (y * w + x).coerceAtMost((1 shl 20) - 1)
        }
        // nearest last: sort by distance from the centre, far first
        java.util.Arrays.sort(blast, 0, n)
        for (k in 0 until n) {
            val i = blast[k] and ((1 shl 20) - 1)
            if (i >= size) continue
            val x = i % w
            val y = i / w
            val dx = (x - cx).toFloat()
            val dy = (y - cy).toFloat()
            val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.5f)
            val push = ((r - d) * THROW * minOf(1f, force / FULL_THROW)).toInt()
            if (push <= 0) continue
            var tx = x
            var ty = y
            for (s in 1..push) {
                val nx = x + (dx / d * s).toInt()
                val ny = y + (dy / d * s - s * 0.3f).toInt()
                if (nx < 0 || nx >= w || ny < 0 || ny >= height) break
                val j = ny * w + nx
                if (j != i && !open(j)) break
                tx = nx
                ty = ny
            }
            if (tx != x || ty != y) {
                val j = ty * w + tx
                swap(i, j, x, y, tx, ty)
                setVelocity(j, minOf(7, push / 2))
            }
        }
        structuresDirty = true
        gasDirty = true
    }

    private var blast = IntArray(1024)

    /** Firedamp at [i] meets fire, embers, something burning or its ignition heat. */
    private fun ignites(x: Int, y: Int, i: Int, m: Int): Boolean {
        val it = materials.igniteTemp[m]
        if (temp[i] >= it) return true
        val w = width
        fun hot(j: Int): Boolean {
            val q = mat[j].toInt()
            return q == fireId || flags[j].toInt() and Flag.BURNING != 0 || temp[j] >= it
        }
        return (x > 0 && hot(i - 1)) || (x < w - 1 && hot(i + 1)) || (y > 0 && hot(i - w)) || (y < height - 1 && hot(i + w))
    }

    /** The whole cloud of firedamp around [i] goes off at once: a flash of fire and an explosion as strong as the cloud is big. */
    private fun detonate(x: Int, y: Int, i: Int, m: Int) {
        val w = width
        fillTop = 0
        var count = 0
        var sx = 0L
        var sy = 0L
        fillStack[fillTop++] = i
        stamp[i] = mark
        mat[i] = fireId.toByte()
        while (fillTop > 0) {
            val c = fillStack[--fillTop]
            count++
            val cx = c % w
            val cy = c / w
            sx += cx
            sy += cy
            change(cx, cy, c, fireId)
            temp[c] = FLASH_HEAT
            for (dy in -1..1) for (dx in -1..1) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= height) continue
                val j = ny * w + nx
                if (mat[j].toInt() == m) {
                    mat[j] = fireId.toByte()
                    fillStack[fillTop++] = j
                }
            }
        }
        note(Seen.EXPLODE, m)
        val root = kotlin.math.sqrt(count.toFloat())
        explode((sx / count).toInt(), (sy / count).toInt(), CLOUD_FORCE + root, (2f + root * CLOUD_REACH).toInt())
    }

    /** Water next to something glowing hot takes its whole boiling heat from it at once (a steam explosion where it is shut in). */
    private fun flash(x: Int, y: Int, i: Int, m: Int, lat: Int) {
        val w = width
        var j = -1
        if (y < height - 1 && temp[i + w] >= FLASH_TEMP) j = i + w
        else if (x > 0 && temp[i - 1] >= FLASH_TEMP) j = i - 1
        else if (x < w - 1 && temp[i + 1] >= FLASH_TEMP) j = i + 1
        else if (y > 0 && temp[i - w] >= FLASH_TEMP) j = i - w
        if (j < 0) return
        val need = lat - life[i] + maxOf(0f, materials.boilTemp[m] - temp[i])
        val cap = materials.heatCapacity
        temp[j] -= need * cap[m] / cap[mat[j].toInt()]
        temp[i] = materials.boilTemp[m] + 2f
        life[i] = lat.toShort()
        wakeHeat(j % w, j / w)
    }

    /** A crack in the rock gives off a little gas into the air beside it (a random tick). */
    internal fun emitFrom(x: Int, y: Int, i: Int, m: Int) {
        if (!chance(materials.emitRate[m])) return
        val w = width
        val d = if (coin()) 1 else -1
        for (j in intArrayOf(if (y > 0) i - w else -1, if (x + d in 0 until w) i + d else -1, if (x - d in 0 until w) i - d else -1, if (y < height - 1) i + w else -1)) {
            if (j >= 0 && mat[j].toInt() == AIR) {
                spawn(j % w, j / w, j, materials.emits[m], temp[i])
                return
            }
        }
    }

    // ------------------------------------------------------------------ structures (3.1)

    /**
     * Finds every structure: a 4-connected group of solid cells. One that touches anchored ground stands;
     * the others are loose. Plants (trunk, leaves, vine) and built material never form one structure: a
     * block that touches a crown is not carried by the tree. Runs only when solid cells were added,
     * removed, changed or moved.
     */
    private fun findStructures() {
        structuresDirty = false
        owner.fill(0)
        loose.clear()
        looseDensity.clear()
        looseRooted.clear()
        val w = width
        var id = 0
        for (i in 0 until size) {
            val m0 = mat[i].toInt()
            if (owner[i] != 0 || kindOf[m0] != Kind.SOLID) continue
            id++
            looseOf[id] = -1
            val plant = plantOf[m0]
            var count = 0
            var falling = false
            var rooted = false
            var mass = 0f
            fillTop = 0
            fillAnchored = false
            fillStack[fillTop++] = i
            owner[i] = id
            while (fillTop > 0) {
                val c = fillStack[--fillTop]
                if (count == fillCells.size) fillCells = fillCells.copyOf(count * 2)
                fillCells[count++] = c
                val fc = flags[c].toInt()
                if (fc and Flag.ANCHORED != 0) fillAnchored = true
                if (fc and Flag.FALLING != 0) falling = true
                val mc = mat[c].toInt()
                if (softOf[mc]) rooted = true
                mass += densityOf[mc]
                val x = c % w
                if (x > 0) visit(c - 1, id, plant)
                if (x < w - 1) visit(c + 1, id, plant)
                if (c >= w) visit(c - w, id, plant)
                if (c < size - w) visit(c + w, id, plant)
            }
            if (!fillAnchored || falling) {
                val cells = fillCells.copyOf(count)
                // bottom cells first, so each falls into the place the one below has just left
                cells.sort()
                cells.reverse()
                looseOf[id] = loose.size
                loose += cells
                looseDensity += mass / count
                looseRooted += rooted
            }
        }
        if (skip.size < loose.size) skip = BooleanArray(loose.size * 2) else skip.fill(false)
    }

    /** A neighbour in the flood fill: solid ones of the same sort (plant or not) join, anchored ground anchors. */
    private fun visit(n: Int, id: Int, plant: Boolean) {
        val mn = mat[n].toInt()
        if (kindOf[mn] == Kind.SOLID && plantOf[mn] == plant) {
            if (owner[n] == 0) {
                owner[n] = id
                fillStack[fillTop++] = n
            }
        } else if (materials.anchored[mn] || (flags[n].toInt() and Flag.ANCHORED) != 0) {
            fillAnchored = true
        }
    }

    /**
     * Every loose structure falls as a whole, without turning, as long as there is room below it: air, gas,
     * a liquid lighter than the structure (a wooden raft rests on water, a stone sinks), or leaves and vines,
     * which a falling block tears through. One that lies on something but has its weight beside what holds
     * it slides off over the edge ([tip]).
     */
    private fun dropLoose() {
        if (loose.isEmpty()) return
        var moved = false
        for ((s, cells) in loose.withIndex()) {
            if (skip[s]) continue
            val plant = plantOf[mat[cells[0]].toInt()]
            val speed = (flags[cells[0]].toInt() and Flag.VEL_MASK) shr Flag.VEL_SHIFT
            val steps = 1 + speed / 3
            var fell = 0
            while (fell < steps && canMove(cells, width, looseDensity[s], plant)) {
                shift(cells, width)
                fell++
            }
            if (fell > 0) {
                if (speed == 0) note(Seen.FALL, mat[cells[0]].toInt())
                moved = true
                val f = Flag.FALLING or (minOf(7, speed + 1) shl Flag.VEL_SHIFT)
                for (c in cells) flags[c] = ((flags[c].toInt() and (Flag.FALLING or Flag.VEL_MASK).inv()) or f).toByte()
            } else if (speed > 0 || flags[cells[0]].toInt() and Flag.FALLING != 0) {
                // landed: from now on it may catch hold of anchored ground it touches
                for (c in cells) flags[c] = (flags[c].toInt() and (Flag.FALLING or Flag.VEL_MASK).inv()).toByte()
                moved = true
                if (speed >= SHAKE_SPEED) landed(cells)
            } else if (!looseRooted[s] && tip(cells, looseDensity[s], plant)) {
                moved = true
            }
        }
        // what moved may now touch other structures or anchored ground
        if (moved) structuresDirty = true
    }

    /** A block that hit the ground hard shakes it where it landed. */
    private fun landed(cells: IntArray) {
        val w = width
        var k = 0
        for (c in cells) {
            val b = c + w
            if (b < size && owner[b] == owner[c]) continue
            if (k++ % SHAKE_RADIUS == 0) shake(c % w, c / w + 1, SHAKE_RADIUS)
        }
    }

    /**
     * A structure that lies still: if its centre of weight is beside the cells it rests on, it tips over the
     * edge. Without turning (that comes with real body physics), it slides one cell down and to that side,
     * or only to the side, if there is room. Plants with leaves or vines are held by their roots.
     */
    private fun tip(cells: IntArray, density: Float, plant: Boolean): Boolean {
        val w = width
        var weight = 0f
        var moment = 0f
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (c in cells) {
            val x = c % w
            val d = densityOf[mat[c].toInt()]
            weight += d
            moment += d * x
            if (rests(c, density, plant)) {
                if (x < left) left = x
                if (x > right) right = x
            }
        }
        if (left > right) return false
        val centre = moment / weight
        val side = when {
            centre < left - TIP_MARGIN -> -1
            centre > right + TIP_MARGIN -> 1
            else -> return false
        }
        for (o in intArrayOf(w + side, side)) {
            if (canMove(cells, o, density, plant)) {
                shift(cells, o)
                note(Seen.FALL, mat[cells[0]].toInt())
                return true
            }
        }
        return false
    }

    /** The structure (cells [cells]) can move by [o] cells: every cell lands in its own place or in one it may pass. */
    private fun canMove(cells: IntArray, o: Int, density: Float, plant: Boolean): Boolean {
        val w = width
        for (c in cells) {
            val t = c + o
            if (t < 0 || t >= size) return false
            val dx = t % w - c % w
            if (dx > 1 || dx < -1) return false
            if (owner[t] == owner[c] && owner[t] != 0) continue
            if (!passable(t, density, plant)) return false
        }
        return true
    }

    /** Cell [c] of a structure lies on something below it. */
    private fun rests(c: Int, density: Float, plant: Boolean): Boolean {
        val b = c + width
        if (b >= size) return true
        if (owner[b] == owner[c] && owner[b] != 0) return false
        return !passable(b, density, plant)
    }

    /** A structure of [density] may move into cell [t]: air, gas, a lighter liquid or animal, or leaves and vines for built material. */
    private fun passable(t: Int, density: Float, plant: Boolean): Boolean {
        val q = mat[t].toInt()
        if (q == AIR) return true
        val k = kindOf[q]
        if (k == Kind.GAS) return true
        if ((k == Kind.LIQUID || materials.animal[q]) && densityOf[q] < density) return true
        return !plant && softOf[q] && densityOf[q] < density && (flags[t].toInt() and Flag.ANCHORED) == 0
    }

    /**
     * Moves the structure by [o] cells (down, sideways or both); whatever was in the way goes to the place
     * the structure left. Leaves and vines in the way are torn off and fall as dead plant matter.
     */
    private fun shift(cells: IntArray, o: Int) {
        val w = width
        // the cell furthest ahead moves first, into the place it may pass; the rest follow into places just left
        val n = cells.size
        for (k0 in 0 until n) {
            val k = if (o > 0) k0 else n - 1 - k0
            val c = cells[k]
            val b = c + o
            if (owner[b] != owner[c] && softOf[mat[b].toInt()]) tear(b)
            val m = mat[c]; mat[c] = mat[b]; mat[b] = m
            val t = temp[c]; temp[c] = temp[b]; temp[b] = t
            val l = life[c]; life[c] = life[b]; life[b] = l
            val v = variant[c]; variant[c] = variant[b]; variant[b] = v
            val f = flags[c]; flags[c] = flags[b]; flags[b] = f
            val ow = owner[c]; owner[c] = owner[b]; owner[b] = ow
            cells[k] = b
            wakeHeat(c % w, c / w)
            wakeHeat(b % w, b / w)
        }
    }

    /** A falling block tears through a leaf or vine at [i]: it becomes dead plant matter; the plant it belonged to waits for the next search. */
    private fun tear(i: Int) {
        val id = owner[i]
        if (id != 0 && looseOf[id] >= 0) skip[looseOf[id]] = true
        owner[i] = 0
        mat[i] = deadPlantId.toByte()
        life[i] = 0
        flags[i] = 0
        structuresDirty = true
    }

    /**
     * A plant grows a new solid cell [j] (at x, y) out of cell [p] (2b). It belongs to the same structure at
     * once: a growing tree needs no new search for structures, unless it grows into anchored ground or into
     * another structure.
     */
    internal fun growSolid(p: Int, x: Int, y: Int, j: Int, m: Int) {
        mat[j] = m.toByte()
        life[j] = 0
        variant[j] = next().toByte()
        flags[j] = 0
        stamp[j] = mark
        wakeHeat(x, y)
        if (structuresDirty) return
        val id = owner[p]
        if (id == 0 || kindOf[mat[p].toInt()] != Kind.SOLID) {
            structuresDirty = true
            return
        }
        owner[j] = id
        val w = width
        val plant = plantOf[m]
        if ((x > 0 && joins(j - 1, id, plant)) || (x < w - 1 && joins(j + 1, id, plant)) || (y > 0 && joins(j - w, id, plant)) || (y < height - 1 && joins(j + w, id, plant))) {
            structuresDirty = true
            return
        }
        val li = looseOf[id]
        if (li < 0) return
        // the cells of a loose structure stay ordered bottom first, the way it falls
        val cells = loose[li]
        val n = cells.size
        var k = 0
        while (k < n && cells[k] > j) k++
        val grown = IntArray(n + 1)
        cells.copyInto(grown, 0, 0, k)
        grown[k] = j
        cells.copyInto(grown, k + 1, k, n)
        loose[li] = grown
        looseDensity[li] = (looseDensity[li] * n + densityOf[m]) / (n + 1)
        if (softOf[m]) looseRooted[li] = true
    }

    /** Cell [n] is anchored ground or part of another structure of the same sort (plant or not) than [id]. */
    private fun joins(n: Int, id: Int, plant: Boolean): Boolean {
        val q = mat[n].toInt()
        return (kindOf[q] == Kind.SOLID && owner[n] != id && plantOf[q] == plant) || materials.anchored[q] || (flags[n].toInt() and Flag.ANCHORED) != 0
    }

    // ------------------------------------------------------------------ snapshots (undo, 4.7)

    class Snapshot internal constructor(
        val step: Int,
        internal val mat: ByteArray,
        internal val temp: FloatArray,
        internal val life: ShortArray,
        internal val variant: ByteArray,
        internal val flags: ByteArray,
        internal val rng: Int,
        internal val pending: BooleanArray,
        internal val heatPending: BooleanArray,
        internal val placed: IntArray,
        internal val light: FloatArray,
    ) {
        val bytes: Int get() = mat.size * 9 + light.size * 4
    }

    fun snapshot() = Snapshot(
        step, mat.copyOf(), temp.copyOf(), life.copyOf(), variant.copyOf(), flags.copyOf(), rng, pending.copyOf(),
        heatPending.copyOf(), placed.copyOf(), light.level.copyOf(),
    )

    fun restore(s: Snapshot) {
        s.mat.copyInto(mat)
        s.temp.copyInto(temp)
        s.life.copyInto(life)
        s.variant.copyInto(variant)
        s.flags.copyInto(flags)
        s.pending.copyInto(pending)
        s.heatPending.copyInto(heatPending)
        s.placed.copyInto(placed)
        s.light.copyInto(light.level)
        stamp.fill(0)
        rng = s.rng
        step = s.step
        structuresDirty = true
        gasDirty = true
    }

    /** A fingerprint of the complete state, for determinism tests. */
    fun hash(): Long {
        var h = -0x340d631b7bdddcdbL
        fun mix(v: Int) {
            h = (h xor v.toLong()) * 0x100000001b3L
        }
        for (i in 0 until size) {
            mix(mat[i].toInt())
            mix(java.lang.Float.floatToRawIntBits(temp[i]))
            mix(life[i].toInt())
            mix(variant[i].toInt())
            mix(flags[i].toInt())
        }
        mix(rng)
        mix(step)
        return h
    }

    /** How many cells hold [material]. */
    fun count(material: Int): Int {
        var n = 0
        val b = material.toByte()
        for (v in mat) if (v == b) n++
        return n
    }

    companion object {
        const val SHIFT = 5
        const val CHUNK = 1 shl SHIFT

        private const val EPS = 0.001f

        /** A temperature change above this keeps a chunk awake. */
        private const val HEAT_WAKE = 0.02f
        private const val AIR_RELAX = 0.03f

        /** Particles swapping places with more than this temperature difference stir up heat exchange. */
        private const val SWAP_HEAT = 0.5f
        private const val AIR_SNAP = 0.5f

        private const val SUFFOCATE = 0.05f

        /** Charring without air starts this far above the ignition point, and slowly. */
        private const val CHAR_OVER = 80f
        private const val CHAR_CHANCE = 0.006f
        private const val SINK_CHANCE = 0.55f
        private const val DISSOLVE = 0.03f
        private const val ERODE = 0.05f
        private const val DRY_TEMP = 100f
        private const val DRY_CHANCE = 0.04f
        private const val DRY_COOLING = 15f

        /** Light is worked out anew every this many steps. */
        const val LIGHT_EVERY = 8

        /** Brittle material colder than this does not crack (4a.2). */
        private const val SHOCK_FROM = 60f

        /** Melting heat snow takes up before it comes loose on a slope; how often sliding snow takes a neighbour along; how soon it settles. */
        private const val AVALANCHE_WARM = 60
        private const val AVALANCHE_SPREAD = 0.6f
        private const val AVALANCHE_SETTLE = 0.04f

        /** A landing block shakes the ground this far when it falls at least [SHAKE_SPEED]. */
        private const val SHAKE_RADIUS = 3
        private const val SHAKE_SPEED = 2

        /** The load on snow is summed up to this many cells above it; chance per random tick to be pressed into ice. */
        private const val COMPACT_REACH = 24
        private const val COMPACT_CHANCE = 0.08f

        /** Pressure is checked every so many steps; absolute zero for the gas law. */
        const val PRESSURE_EVERY = 10
        private const val KELVIN = 273f

        /** Gas beyond what the region would hold as plain air (in cells of air) before anything can burst: small hot pockets in a heap do nothing. */
        private const val MIN_EXCESS = 40f

        /** Reach of a burst per square root of the pent-up gas; force and reach of firedamp going off, on top of the square root of the cloud's cells. */
        private const val BURST_REACH = 0.6f
        private const val CLOUD_FORCE = 4f
        private const val CLOUD_REACH = 1.2f

        /** The widest blast; the force that kindles; how far loose material flies per cell of reach left, in full from [FULL_THROW] on. */
        private const val MAX_BLAST = 22
        private const val KINDLE = 2f
        private const val THROW = 0.8f
        private const val FULL_THROW = 8f

        /** Water next to anything this hot boils at once; firedamp burns this hot. */
        private const val FLASH_TEMP = 600f
        private const val FLASH_HEAT = 1200f

        /** How far around the finger an animal looks for where it lives (or for air). */
        const val ANIMAL_REACH = 3

        /** A structure tips when its centre of weight is this far (cells) beyond the edge of what it rests on. */
        private const val TIP_MARGIN = 0.5f
        private const val SEEP = 0.08f
        private const val SEEPING = Flag.WET or Flag.MARK

        private fun seedState(seed: Long): Int {
            val s = (seed xor (seed ushr 32)).toInt() xor 0x5DEECE66.toInt()
            return if (s == 0) 0x2545F491 else s
        }
    }
}
