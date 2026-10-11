package com.shootcat.react.sim

import com.shootcat.react.sim.MaterialTable.Companion.AIR

/** Bits of [World.flags]. */
object Flag {
    const val BURNING = 1
    const val WET = 2

    /** Falling speed, 0..7, in bits 4 to 6. */
    const val VEL_SHIFT = 4
    const val VEL_MASK = 0x70
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
    const val COUNT = 8
}

/**
 * The particle world (Leitfaden V3, 4.1 and 4.2): a cellular automaton with one particle per cell, kept in
 * flat primitive arrays, never in objects per particle.
 *
 * One [step] first lets heat flow between neighbours, then updates every cell from the bottom row to the
 * top, each row in the opposite direction of the one before. A particle that moved in this step is stamped
 * and not moved again. The world is cut into chunks of 32 × 32 cells; only chunks in which something
 * happened in the step before are updated, the rest sleep.
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
    private val stamp = IntArray(size)

    var step = 0
        private set
    private var mark = 0
    private var rng = seedState(seed)

    val chunksX = (width + CHUNK - 1) / CHUNK
    val chunksY = (height + CHUNK - 1) / CHUNK
    private var awake = BooleanArray(chunksX * chunksY)

    /** Chunks woken since the current step began: they are updated in the next one. */
    private var pending = BooleanArray(chunksX * chunksY) { true }

    /** For measuring: update every chunk, asleep or not. */
    var keepAllAwake = false

    /** How many chunks the last step updated. */
    var awakeChunks = 0
        private set

    /** Cells the player has placed, per material. */
    val placed = IntArray(materials.count)

    /** Counts per [Event]; whoever reads them resets them. */
    val events = IntArray(Event.COUNT)

    private val ambient = materials.ambient
    private val kindOf = materials.kind
    private val densityOf = materials.density
    private val fireId = materials.idOrNull("fire") ?: AIR
    private val smokeId = materials.idOrNull("smoke") ?: AIR
    private val steamId = materials.idOrNull("steam") ?: AIR

    init {
        temp.fill(ambient)
    }

    fun index(x: Int, y: Int) = y * width + x
    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun material(x: Int, y: Int): Int = mat[y * width + x].toInt()
    fun isAwake(cx: Int, cy: Int) = pending[cy * chunksX + cx]

    // ------------------------------------------------------------------ random numbers

    private fun next(): Int {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return x
    }

    /** True with probability [p] (0..1). */
    private fun chance(p: Float): Boolean = (next() ushr 8) < (p * 16777216f).toInt()
    private fun coin(): Boolean = next() < 0
    private fun below(n: Int): Int = (((next().toLong() and 0xffffffffL) * n) ushr 32).toInt()

    // ------------------------------------------------------------------ placing material

    /**
     * Fills the air inside a disc with [material]; with [fill] below 1 only that share of it, scattered, the
     * way poured sand or water looks. Only air is replaced: nothing goes into rock or anything else.
     */
    fun paint(material: Int, cx: Int, cy: Int, radius: Int, fill: Float = 1f): Int {
        var n = 0
        val r2 = radius * radius + radius
        for (dy in -radius..radius) {
            val y = cy + dy
            if (y < 0 || y >= height) continue
            for (dx in -radius..radius) {
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
                if (mat[i].toInt() == AIR) continue
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

    fun setTemperature(x: Int, y: Int, t: Float) {
        temp[y * width + x] = t
        wake(x, y)
    }

    /** Everything becomes air at the temperature of the surroundings; time and chance go on. */
    fun clearAll() {
        mat.fill(AIR.toByte())
        temp.fill(ambient)
        life.fill(0)
        flags.fill(0)
        pending.fill(true)
    }

    /** Wakes the whole world, e.g. after building it cell by cell. */
    fun wakeAll() = pending.fill(true)

    // ------------------------------------------------------------------ the step

    fun step() {
        val t = awake
        awake = pending
        pending = t
        pending.fill(false)
        if (keepAllAwake) awake.fill(true)
        mark = step + 1
        var n = 0
        for (b in awake) if (b) n++
        awakeChunks = n
        if (n > 0) {
            heat()
            update()
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
        val n = materials.count
        for (cy in 0 until chunksY) {
            for (cx in 0 until chunksX) {
                val c = cy * chunksX + cx
                if (!awake[c]) continue
                val x0 = cx * CHUNK
                val x1 = minOf(w, x0 + CHUNK) - 1
                val y0 = cy * CHUNK
                val y1 = minOf(h, y0 + CHUNK) - 1
                val leftAsleep = cx > 0 && !awake[c - 1]
                val upAsleep = cy > 0 && !awake[c - chunksX]
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
                        // lower neighbour
                        if (y < h - 1) {
                            val j = i + w
                            val d = tp[j] - tp[i]
                            if (d > EPS || d < -EPS) {
                                val k = ma * n + m[j].toInt()
                                val da = pa[k] * d
                                tp[i] += da
                                tp[j] -= pb[k] * d
                                if (da > HEAT_WAKE || da < -HEAT_WAKE || d * pb[k] > HEAT_WAKE || d * pb[k] < -HEAT_WAKE) {
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
                if (hot) pending[c] = true
            }
        }
    }

    private fun wakeChunk(cx: Int, cy: Int) {
        if (cx in 0 until chunksX && cy in 0 until chunksY) pending[cy * chunksX + cx] = true
    }

    /** The chunk of (x, y) and, at chunk borders, the neighbouring ones are updated in the next step. */
    private fun wake(x: Int, y: Int) {
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
        if (mt.inert[m] && f == 0) return

        // fire (4.2: ignition is a temperature threshold, and burning needs air)
        if (f and Flag.BURNING != 0) {
            if (!burn(x, y, i, m)) return
        } else if (mt.flammable[m] && temp[i] >= mt.igniteTemp[m] && (f and Flag.WET) == 0) {
            if (oxygen(x, y, i) > 0) {
                flags[i] = (f or Flag.BURNING).toByte()
                life[i] = burnLife(m).toShort()
                events[Event.IGNITE]++
                wake(x, y)
            } else if (mt.quenchProduct[m] != m && chance(SUFFOCATE)) {
                // hot enough to burn but no air to burn with: it chars, as wood does in a charcoal kiln
                change(x, y, i, mt.quenchProduct[m])
                return
            }
        }

        // phase changes, all at temperature thresholds
        val t = temp[i]
        if (t >= mt.meltTemp[m]) {
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
            wake(x, y)
        }

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
        }
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
        wake(x, y)
        val t = temp[i]
        if (t < mt.quenchTemp[m]) {
            quench(x, y, i, m)
            return false
        }
        if (oxygen(x, y, i) == 0) {
            if (chance(SUFFOCATE)) {
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

    /** Boiling takes the latent heat on top of the boiling point; it is collected in [life]. */
    private fun boil(x: Int, y: Int, i: Int, m: Int): Boolean {
        val mt = materials
        val bt = mt.boilTemp[m]
        val lat = mt.latent[m]
        if (lat > 0) {
            val excess = (temp[i] - bt).toInt()
            if (excess > 0) {
                temp[i] -= excess
                life[i] = minOf(Short.MAX_VALUE.toInt(), life[i] + excess).toShort()
                wake(x, y)
            }
            if (life[i] < lat) return false
            temp[i] = bt + 2f
        }
        change(x, y, i, mt.boilProduct[m])
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
                wake(x, y)
            }
            if (life[i] < lat) return false
            temp[i] = ft - 2f
        }
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
            val t = if (materials.spawnBurning[product]) materials.spawnTemp[product] else maxOf(temp[i] * 0.6f, temp[j])
            spawn(nx, ny, j, product, t)
            stamp[j] = mark
            return
        }
    }

    private fun spawn(x: Int, y: Int, i: Int, m: Int, t: Float) {
        mat[i] = m.toByte()
        temp[i] = t
        life[i] = initLife(m).toShort()
        variant[i] = next().toByte()
        flags[i] = (if (materials.spawnBurning[m]) Flag.BURNING else 0).toByte()
        wake(x, y)
    }

    private fun clear(x: Int, y: Int, i: Int) {
        mat[i] = AIR.toByte()
        temp[i] = ambient
        life[i] = 0
        flags[i] = 0
        wake(x, y)
    }

    /** The cell turns into [product] and keeps its temperature. */
    private fun change(x: Int, y: Int, i: Int, product: Int) {
        mat[i] = product.toByte()
        life[i] = initLife(product).toShort()
        variant[i] = next().toByte()
        flags[i] = (if (materials.spawnBurning[product]) Flag.BURNING else 0).toByte()
        stamp[i] = mark
        wake(x, y)
    }

    private fun swap(i: Int, j: Int, x: Int, y: Int, x2: Int, y2: Int) {
        val m = mat[i]; mat[i] = mat[j]; mat[j] = m
        val t = temp[i]; temp[i] = temp[j]; temp[j] = t
        val l = life[i]; life[i] = life[j]; life[j] = l
        val v = variant[i]; variant[i] = variant[j]; variant[j] = v
        val f = flags[i]; flags[i] = flags[j]; flags[j] = f
        stamp[i] = mark
        stamp[j] = mark
        wake(x, y)
        wake(x2, y2)
    }

    private fun setVelocity(i: Int, v: Int) {
        flags[i] = ((flags[i].toInt() and Flag.VEL_MASK.inv()) or (v shl Flag.VEL_SHIFT)).toByte()
    }

    /** Can a falling or flowing particle of [m] take the place of [q]? Only gases and lighter liquids give way. */
    private fun yields(m: Int, q: Int): Boolean {
        if (q == AIR) return true
        val k = kindOf[q]
        return (k == Kind.GAS || k == Kind.LIQUID) && densityOf[q] < densityOf[m]
    }

    /** Falls up to 1 + speed / 2 cells through air and gases; a liquid below slows it down. Returns whether it moved. */
    private fun fall(x: Int, y: Int, i: Int, m: Int): Boolean {
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
        val maxFall = 1 + (v shr 1)
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
        val fr = if (flags[i].toInt() and Flag.WET != 0) materials.wetFriction[m] else materials.friction[m]
        if (chance(fr)) return
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

    /** Flows sideways as far as [MaterialTable.spread] allows, stopping early above a drop. */
    private fun flow(x: Int, y: Int, i: Int, m: Int, d: Int): Boolean {
        val spread = materials.spread[m]
        val w = width
        var target = -1
        var tx = x
        var k = 1
        while (k <= spread) {
            val nx = x + d * k
            if (nx < 0 || nx >= w) break
            val j = i + d * k
            if (!yields(m, mat[j].toInt())) break
            target = j
            tx = nx
            if (y < height - 1 && yields(m, mat[j + w].toInt())) break
            k++
        }
        if (target < 0) return false
        swap(i, target, x, y, tx, y)
        if (materials.wets[m]) erode(tx, y, d)
        return true
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
        val nx = x + d
        if (nx < 0 || nx >= width) return
        val c = b + d
        val q = mat[c].toInt()
        if (q == AIR || materials.kind[q] == Kind.LIQUID) {
            flags[b] = (flags[b].toInt() or Flag.WET).toByte()
            swap(b, c, x, by, nx, by)
        }
    }

    /** Absorbent ground next to water soaks some of it up and becomes wet. */
    private fun soak(x: Int, y: Int, i: Int): Boolean {
        val ab = materials.absorbs
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
        if (!chance(ab[mat[j].toInt()])) return false
        flags[j] = (f or Flag.WET).toByte()
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
        internal val placed: IntArray,
    ) {
        val bytes: Int get() = mat.size * 9
    }

    fun snapshot() = Snapshot(step, mat.copyOf(), temp.copyOf(), life.copyOf(), variant.copyOf(), flags.copyOf(), rng, pending.copyOf(), placed.copyOf())

    fun restore(s: Snapshot) {
        s.mat.copyInto(mat)
        s.temp.copyInto(temp)
        s.life.copyInto(life)
        s.variant.copyInto(variant)
        s.flags.copyInto(flags)
        s.pending.copyInto(pending)
        s.placed.copyInto(placed)
        stamp.fill(0)
        rng = s.rng
        step = s.step
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
        private const val AIR_SNAP = 0.5f

        private const val SUFFOCATE = 0.05f
        private const val SINK_CHANCE = 0.55f
        private const val ERODE = 0.05f
        private const val DRY_TEMP = 100f
        private const val DRY_CHANCE = 0.04f
        private const val DRY_COOLING = 15f

        private fun seedState(seed: Long): Int {
            val s = (seed xor (seed ushr 32)).toInt() xor 0x5DEECE66.toInt()
            return if (s == 0) 0x2545F491 else s
        }
    }
}
