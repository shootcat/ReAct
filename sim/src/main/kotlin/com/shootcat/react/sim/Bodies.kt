package com.shootcat.react.sim

import com.shootcat.react.sim.MaterialTable.Companion.AIR
import kotlin.math.sqrt

/**
 * One rigid body (Phase 4): a loose structure that falls, tips, turns and rolls as a whole.
 *
 * Its cells keep their place in the body's own frame ([lx], [ly], a grid of whole cells as the structure was
 * when it came loose) and are drawn into the world wherever the body is: cell k sits in world cell [at] k.
 * They stay ordinary cells of the world there: they burn, melt and carry heat like any other.
 */
internal class Body(val id: Int) {
    var n = 0
    var lx = IntArray(0)
    var ly = IntArray(0)
    var at = IntArray(0)

    /** Centre of weight in the body's frame, mass (density / 1000 per cell), moment of inertia, reach of the farthest cell. */
    var comX = 0f
    var comY = 0f
    var mass = 1f
    var inertia = 1f
    var radius = 0f

    /** Where the centre of weight is in the world (cell centres at whole numbers) and how the body is turned. */
    var px = 0f
    var py = 0f
    var cs = 1f
    var sn = 0f

    /** Cells per step, and radians per step (positive turns from +x towards +y, clockwise on the screen). */
    var vx = 0f
    var vy = 0f
    var w = 0f

    var asleep = false

    /** Steps in a row it lay still and slow, and in a row its cells did not move at all. */
    var still = 0
    var stuck = 0

    /** All of it is plant (a fallen tree, a burnt-off crown): its roots may take hold again. */
    var plant = false

    /** The world changed some of its cells since it last looked. */
    var dirty = false

    /** Fingerprint of what is around it, taken when it fell asleep. */
    var support = 0L

    /** The material most of it is made of. */
    var main = AIR

    /** Box of its cells in the world. */
    var x0 = 0
    var y0 = 0
    var x1 = 0
    var y1 = 0

    /** Which cells of its own frame it fills, a box [ow] × [oh] from ([ox], [oy]): to tell a gap inside it from outside. */
    var occ = BooleanArray(0)
    var ox = 0
    var oy = 0
    var ow = 0
    var oh = 0

    /** How far it has turned (radians, either way) since it last lay still: for noticing that it fell over. */
    var turned = 0f

    /** The last step it lay on the ground (or on a body lying on the ground): it carries what rests on it. */
    var ground = -100

    /** Other bodies it pressed against in its last step. */
    var touch = IntArray(4)
    var touches = 0
}

/**
 * The bodies of the world (Phase 4, "Körperphysik, schlanke Fassung"): every loose structure is a rigid body.
 *
 * A body is moved in steps of at most half a cell (sub-steps), each time drawn into the grid anew from its
 * own frame. Where a cell would land on something it may not pass (rock, sand, another body), the body has
 * hit it: the surface around tells the direction it faces, and impulses at up to two points per surface
 * stop, bounce and rub the body there, so it tips over an edge, rolls down a slope and comes to rest on a
 * floor. Then it sleeps until something changes around it. In a liquid it floats by its density.
 *
 * All arithmetic is float addition, multiplication, division and square roots in a fixed order, and the
 * bodies are moved in the order of their slots: the same actions give the same world on every device.
 */
internal class Bodies(private val world: World) {
    private val width = world.width
    private val height = world.height
    private val size = world.size
    private val mt = world.materials
    private val mat = world.mat
    private val temp = world.temp
    private val life = world.life
    private val variant = world.variant
    private val flags = world.flags
    private val kind = mt.kind
    private val dens = FloatArray(mt.count) { maxOf(MIN_DENSITY, mt.density[it] / 1000f) }
    private val fric = mt.friction
    private val plantOf = mt.plant
    private val softOf = mt.soft
    private val animalOf = mt.animal
    private val anchoredOf = mt.anchored
    private val organism = mt.organism
    private val strength = mt.strength
    private val breakProduct = mt.breakProduct
    private val compacts = mt.compactProduct
    private val deadPlant = mt.idOrNull("dead_plant") ?: AIR

    /** Which body each cell belongs to (its id), 0 for none. */
    val bodyAt = IntArray(size)

    private var slots = arrayOfNulls<Body>(16)

    /** How many bodies there are, and how many were awake in the last step. */
    var count = 0
        private set
    var awakeCount = 0
        private set

    fun body(id: Int): Body? = if (id in 1..slots.size) slots[id - 1] else null

    fun each(action: (Body) -> Unit) {
        for (b in slots) if (b != null) action(b)
    }

    // ------------------------------------------------------------------ making and losing bodies

    private fun newBody(): Body {
        var s = 0
        while (s < slots.size && slots[s] != null) s++
        if (s == slots.size) slots = slots.copyOf(slots.size * 2)
        val b = Body(s + 1)
        slots[s] = b
        count++
        return b
    }

    /** Takes [b] out of the bodies; its cells stay in the world as they are. */
    fun forget(b: Body) = remove(b)

    private fun remove(b: Body) {
        for (k in 0 until b.n) if (bodyAt[b.at[k]] == b.id) bodyAt[b.at[k]] = 0
        slots[b.id - 1] = null
        count--
    }

    /**
     * Makes a body of the [n] world cells [cells] (a loose structure, or what the hand put down), at rest, not
     * turned. Growing tips stop growing: a fallen tree does not grow on.
     */
    fun create(cells: IntArray, n: Int, vx: Float = 0f, vy: Float = 0f): Body {
        val b = newBody()
        val w = width
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        for (k in 0 until n) {
            val c = cells[k]
            minX = minOf(minX, c % w)
            minY = minOf(minY, c / w)
        }
        b.n = n
        b.lx = IntArray(n) { cells[it] % w - minX }
        b.ly = IntArray(n) { cells[it] / w - minY }
        b.at = cells.copyOf(n)
        var plant = true
        for (k in 0 until n) {
            val c = cells[k]
            bodyAt[c] = b.id
            val m = mat[c].toInt()
            if (!plantOf[m]) plant = false
            flags[c] = (flags[c].toInt() and (Flag.MARK or Flag.FALLING or Flag.VEL_MASK).inv()).toByte()
        }
        b.plant = plant
        b.px = minX.toFloat()
        b.py = minY.toFloat()
        b.comX = 0f
        b.comY = 0f
        massProps(b)
        bounds(b)
        b.vx = vx
        b.vy = vy
        return b
    }

    /** Merges bodies resting against each other into one, where they are, with their common motion. */
    private fun merge(parts: Array<Body>, k: Int) {
        var n = 0
        var px = 0f
        var py = 0f
        var m = 0f
        for (j in 0 until k) {
            n += parts[j].n
            px += parts[j].vx * parts[j].mass
            py += parts[j].vy * parts[j].mass
            m += parts[j].mass
        }
        val cells = IntArray(n)
        var c = 0
        for (j in 0 until k) {
            val p = parts[j]
            p.at.copyInto(cells, c, 0, p.n)
            c += p.n
            remove(p)
        }
        cells.sort()
        create(cells, n, px / m, py / m)
    }

    /** Mass, centre of weight and inertia from the cells; the body stays where it is. */
    private fun massProps(b: Body) {
        var m = 0f
        var sx = 0f
        var sy = 0f
        for (k in 0 until b.n) {
            val d = dens[mat[b.at[k]].toInt()]
            m += d
            sx += d * b.lx[k]
            sy += d * b.ly[k]
        }
        // the main material: the most frequent among (up to) 32 cells spread over the body
        val stride = maxOf(1, b.n / 32)
        var bestN = 0
        for (k in 0 until b.n step stride) {
            val q = mat[b.at[k]]
            var same = 0
            for (j in 0 until b.n step stride) if (mat[b.at[j]] == q) same++
            if (same > bestN) {
                bestN = same
                b.main = q.toInt()
            }
        }
        val cx = sx / m
        val cy = sy / m
        // keep every cell where it is: the centre moves within the body
        val dx = cx - b.comX
        val dy = cy - b.comY
        b.px += b.cs * dx - b.sn * dy
        b.py += b.sn * dx + b.cs * dy
        b.comX = cx
        b.comY = cy
        var inertia = 0f
        var r2 = 0f
        for (k in 0 until b.n) {
            val d = dens[mat[b.at[k]].toInt()]
            val rx = b.lx[k] - cx
            val ry = b.ly[k] - cy
            val q = rx * rx + ry * ry
            inertia += d * (q + CELL_INERTIA)
            if (q > r2) r2 = q
        }
        b.mass = m
        b.inertia = inertia
        b.radius = sqrt(r2)
        outline(b)
    }

    /** Which cells of its own frame [b] fills. */
    private fun outline(b: Body) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (k in 0 until b.n) {
            if (b.lx[k] < minX) minX = b.lx[k]
            if (b.lx[k] > maxX) maxX = b.lx[k]
            if (b.ly[k] < minY) minY = b.ly[k]
            if (b.ly[k] > maxY) maxY = b.ly[k]
        }
        b.ox = minX
        b.oy = minY
        b.ow = maxX - minX + 1
        b.oh = maxY - minY + 1
        if (b.occ.size < b.ow * b.oh) b.occ = BooleanArray(b.ow * b.oh) else java.util.Arrays.fill(b.occ, 0, b.ow * b.oh, false)
        for (k in 0 until b.n) b.occ[(b.ly[k] - minY) * b.ow + b.lx[k] - minX] = true
    }

    private fun bounds(b: Body) {
        val w = width
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (k in 0 until b.n) {
            val c = b.at[k]
            val x = c % w
            val y = c / w
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        b.x0 = x0
        b.y0 = y0
        b.x1 = x1
        b.y1 = y1
    }

    /** The world changed cell [i] of a body into [product] (burnt, melted, broken, taken away). */
    fun changed(i: Int, product: Int) {
        val id = bodyAt[i]
        if (id == 0) return
        val b = slots[id - 1] ?: return
        b.dirty = true
        // turned into another solid (a dying trunk into dead wood) it stays part of the body
        if (product < 0 || kind[product] != Kind.SOLID) bodyAt[i] = 0
    }

    /**
     * Looks at the cells of [b] the world has changed: those no longer its own are gone. What is left may
     * have fallen apart: each piece goes on as a body of its own. Returns whether [b] is still there.
     */
    private fun sync(b: Body): Boolean {
        b.dirty = false
        var keep = 0
        for (k in 0 until b.n) {
            if (bodyAt[b.at[k]] != b.id) continue
            if (keep != k) {
                b.lx[keep] = b.lx[k]
                b.ly[keep] = b.ly[k]
                b.at[keep] = b.at[k]
            }
            keep++
        }
        if (keep == 0) {
            remove(b)
            return false
        }
        val lost = keep < b.n
        b.n = keep
        if (lost) {
            b.asleep = false
            b.still = 0
            b.stuck = 0
            split(b)
        } else {
            massProps(b)
        }
        bounds(b)
        return true
    }

    private var label = IntArray(256)
    private var queue = IntArray(256)

    /** Splits [b] into its 4-connected pieces (in its own frame); the biggest stays [b]. */
    private fun split(b: Body) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (k in 0 until b.n) {
            minX = minOf(minX, b.lx[k])
            maxX = maxOf(maxX, b.lx[k])
            minY = minOf(minY, b.ly[k])
            maxY = maxOf(maxY, b.ly[k])
        }
        val bw = maxX - minX + 1
        val area = bw * (maxY - minY + 1)
        if (label.size < area) label = IntArray(maxOf(area, label.size * 2))
        if (queue.size < b.n) queue = IntArray(maxOf(b.n, queue.size * 2))
        java.util.Arrays.fill(label, 0, area, -1)
        for (k in 0 until b.n) label[(b.ly[k] - minY) * bw + b.lx[k] - minX] = k
        // piece of each cell
        val piece = IntArray(b.n) { -1 }
        var pieces = 0
        val sizes = IntArray(b.n)
        for (k0 in 0 until b.n) {
            if (piece[k0] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = k0
            piece[k0] = pieces
            while (head < tail) {
                val k = queue[head++]
                sizes[pieces]++
                val x = b.lx[k] - minX
                val y = b.ly[k] - minY
                for (d in 0 until 4) {
                    val nx = x + DX4[d]
                    val ny = y + DY4[d]
                    if (nx < 0 || ny < 0 || nx >= bw || ny > maxY - minY) continue
                    val j = label[ny * bw + nx]
                    if (j >= 0 && piece[j] < 0) {
                        piece[j] = pieces
                        queue[tail++] = j
                    }
                }
            }
            pieces++
        }
        if (pieces == 1) {
            massProps(b)
            return
        }
        var biggest = 0
        for (p in 1 until pieces) if (sizes[p] > sizes[biggest]) biggest = p
        val opx = b.px
        val opy = b.py
        for (p in 0 until pieces) {
            if (p == biggest) continue
            val nb = newBody()
            val n = sizes[p]
            nb.n = n
            nb.lx = IntArray(n)
            nb.ly = IntArray(n)
            nb.at = IntArray(n)
            var j = 0
            for (k in 0 until b.n) if (piece[k] == p) {
                nb.lx[j] = b.lx[k]
                nb.ly[j] = b.ly[k]
                nb.at[j] = b.at[k]
                bodyAt[b.at[k]] = nb.id
                j++
            }
            nb.comX = b.comX
            nb.comY = b.comY
            nb.px = opx
            nb.py = opy
            nb.cs = b.cs
            nb.sn = b.sn
            nb.plant = b.plant
            massProps(nb)
            // a piece flies on with the motion its place in the whole had
            nb.vx = b.vx - b.w * (nb.py - opy)
            nb.vy = b.vy + b.w * (nb.px - opx)
            nb.w = b.w
            bounds(nb)
        }
        var j = 0
        for (k in 0 until b.n) if (piece[k] == biggest) {
            b.lx[j] = b.lx[k]
            b.ly[j] = b.ly[k]
            b.at[j] = b.at[k]
            j++
        }
        b.n = j
        massProps(b)
        val vx = b.vx - b.w * (b.py - opy)
        val vy = b.vy + b.w * (b.px - opx)
        b.vx = vx
        b.vy = vy
    }

    // ------------------------------------------------------------------ the step

    /** Every body in turn: notices what the world did to it, sleeps or moves. */
    fun step() {
        var awake = 0
        var s = 0
        while (s < slots.size) {
            val b = slots[s++] ?: continue
            if (b.dirty && !sync(b)) continue
            if (b.asleep) {
                if (!disturbed(b)) continue
                wake(b)
            }
            awake++
            move(b)
        }
        awakeCount = awake
    }

    fun wake(b: Body) {
        b.asleep = false
        b.still = 0
        b.stuck = 0
    }

    // scratch for drawing a body into the grid: the cells of a trial pose, of the last pose taken, exact places
    private var cand = IntArray(64)
    private var best = IntArray(64)
    private var fxs = FloatArray(64)
    private var fys = FloatArray(64)
    private var bestValid = false

    // contacts of a trial pose
    private var ck = IntArray(64)
    private var cg = IntArray(64)
    private var cnx = FloatArray(64)
    private var cny = FloatArray(64)
    private var cGroup = IntArray(64)

    // impacts of this step (body cell, obstacle cell, speed, reduced mass)
    private val impK = IntArray(MAX_IMPACTS)
    private val impG = IntArray(MAX_IMPACTS)
    private val impV = FloatArray(MAX_IMPACTS)
    private val impM = FloatArray(MAX_IMPACTS)
    private var impacts = 0

    /** Normal of the last collision, averaged over its surfaces: the way out. */
    private var outX = 0f
    private var outY = 0f

    private fun ensure(n: Int) {
        if (cand.size >= n) return
        val s = maxOf(n, cand.size * 2)
        cand = IntArray(s)
        best = IntArray(s)
        fxs = FloatArray(s)
        fys = FloatArray(s)
        ck = IntArray(s)
        cg = IntArray(s)
        cnx = FloatArray(s)
        cny = FloatArray(s)
        cGroup = IntArray(s)
    }

    private fun move(b: Body) {
        ensure(b.n)
        b.touches = 0
        impacts = 0
        b.vy += G
        val wet = buoyancy(b)
        if (b.vx > VMAX) b.vx = VMAX else if (b.vx < -VMAX) b.vx = -VMAX
        if (b.vy > VMAX) b.vy = VMAX else if (b.vy < -VMAX) b.vy = -VMAX
        if (b.w > WMAX) b.w = WMAX else if (b.w < -WMAX) b.w = -WMAX
        b.w *= AIR_SPIN
        val ax = if (b.vx < 0f) -b.vx else b.vx
        val ay = if (b.vy < 0f) -b.vy else b.vy
        val aw = (if (b.w < 0f) -b.w else b.w) * b.radius
        var steps = ((maxOf(ax, ay) + aw) / SUB).toInt() + 1
        if (steps > MAX_SUB) steps = MAX_SUB
        val inv = 1f / steps
        var px = b.px
        var py = b.py
        var cs = b.cs
        var sn = b.sn
        bestValid = false
        var blocked = 0
        var contact = false
        for (s in 0 until steps) {
            var turn = b.w * inv
            var c1 = cs * cosOf(turn) - sn * sinOf(turn)
            var s1 = sn * cosOf(turn) + cs * sinOf(turn)
            var nx = px + b.vx * inv
            var ny = py + b.vy * inv
            if (tryPose(b, nx, ny, c1, s1)) {
                px = nx; py = ny; cs = c1; sn = s1
                blocked = 0
                continue
            }
            contact = true
            collide(b, nx, ny)
            turn = b.w * inv
            c1 = cs * cosOf(turn) - sn * sinOf(turn)
            s1 = sn * cosOf(turn) + cs * sinOf(turn)
            nx = px + b.vx * inv
            ny = py + b.vy * inv
            if (tryPose(b, nx, ny, c1, s1)) {
                px = nx; py = ny; cs = c1; sn = s1
                continue
            }
            // pushed back out along the surfaces it hit: a body turning over an edge lifts a little at the edge
            val ox = outX
            val oy = outY
            if (tryPose(b, nx + ox * PUSH, ny + oy * PUSH, c1, s1)) {
                px = nx + ox * PUSH; py = ny + oy * PUSH; cs = c1; sn = s1
                continue
            }
            if (tryPose(b, nx + ox * PUSH * 2f, ny + oy * PUSH * 2f, c1, s1)) {
                px = nx + ox * PUSH * 2f; py = ny + oy * PUSH * 2f; cs = c1; sn = s1
                continue
            }
            if (++blocked >= 2) break
        }
        // keep the turn a true turn: lengths stay one
        val len = sqrt(cs * cs + sn * sn)
        cs /= len
        sn /= len
        b.px = px
        b.py = py
        b.cs = cs
        b.sn = sn
        var moved = false
        if (bestValid) {
            for (k in 0 until b.n) if (best[k] != b.at[k]) {
                moved = true
                break
            }
            if (moved) commit(b, best)
        }
        if (contact) b.w *= ROLL
        if (moved && !b.asleep && b.vy > FALL_NOTE && b.still == 0 && b.stuck == 0) world.note(Seen.FALL, b.main)
        b.turned += if (b.w < 0f) -b.w else b.w
        // rolling: turning along the ground while it moves along it
        if (contact && moved && b.radius >= ROLL_RADIUS && (if (b.w < 0f) -b.w else b.w) * b.radius > ROLL_NOTE &&
            (if (b.vx < 0f) -b.vx else b.vx) > ROLL_NOTE) world.note(Seen.ROLL, b.main)
        if (impacts > 0) impact(b)
        if (slots[b.id - 1] !== b) return
        // rest
        if (moved) {
            b.still = 0
            b.stuck = 0
        } else {
            b.stuck++
            val slow = maxOf(if (b.vx < 0f) -b.vx else b.vx, if (b.vy < 0f) -b.vy else b.vy) +
                (if (b.w < 0f) -b.w else b.w) * b.radius < SLEEP_SPEED
            if (slow) b.still++ else b.still = 0
        }
        if (wet > 0f && b.still > 0) world.note(Seen.FLOAT, b.main)
        if (b.still >= SLEEP_STEPS || b.stuck >= STUCK_STEPS || (b.touches > 0 && b.still >= MERGE_STEPS)) rest(b)
    }

    /** Draws [b] at the pose into [cand] and takes it if every cell is free; then it is the pose of [best]. */
    private fun tryPose(b: Body, px: Float, py: Float, cs: Float, sn: Float): Boolean {
        if (!place(b, px, py, cs, sn, cand)) return false
        val id = b.id
        for (k in 0 until b.n) {
            val g = cand[k]
            if (bodyAt[g] != id && !passable(b, g)) return false
        }
        val t = best
        best = cand
        cand = t
        bestValid = true
        return true
    }

    private val claim = IntArray(size)
    private var claimMark = 0

    /**
     * The world cells of [b] at the pose: each cell goes to the cell its centre falls in; if another cell of
     * the body is already there, to the nearest free one around. Returns false if a cell would leave the
     * world; [out] then holds -1 there.
     */
    private fun place(b: Body, px: Float, py: Float, cs: Float, sn: Float, out: IntArray): Boolean {
        if (++claimMark == Int.MAX_VALUE) {
            claim.fill(0)
            claimMark = 1
        }
        val w = width
        val lim = w - 0.5f
        val hl = height - 0.5f
        var inside = true
        for (k in 0 until b.n) {
            val rx = b.lx[k] - b.comX
            val ry = b.ly[k] - b.comY
            val fx = px + cs * rx - sn * ry
            val fy = py + sn * rx + cs * ry
            fxs[k] = fx
            fys[k] = fy
            if (fx < -0.5f || fy < -0.5f || fx >= lim || fy >= hl) {
                out[k] = -1
                inside = false
                continue
            }
            val g = (fy + 0.5f).toInt() * w + (fx + 0.5f).toInt()
            if (claim[g] == claimMark) {
                // another cell got there first: placed in a second round, into a gap left over
                if (clashes == clash.size) clash = clash.copyOf(clashes * 2)
                clash[clashes++] = k
                continue
            }
            claim[g] = claimMark
            out[k] = g
        }
        for (j in 0 until clashes) {
            val k = clash[j]
            val fx = fxs[k]
            val fy = fys[k]
            val g = nearestFree(b, fx, fy, (fx + 0.5f).toInt(), (fy + 0.5f).toInt(), px, py, cs, sn)
            if (g < 0) {
                out[k] = -1
                inside = false
                continue
            }
            claim[g] = claimMark
            out[k] = g
        }
        clashes = 0
        return inside
    }

    private var clash = IntArray(64)
    private var clashes = 0

    /**
     * The free cell near (fx, fy) for a cell whose own place another cell of the body took: preferably a gap
     * inside the body's outline there, so that a turned body has no holes.
     */
    private fun nearestFree(b: Body, fx: Float, fy: Float, gx: Int, gy: Int, px: Float, py: Float, cs: Float, sn: Float): Int {
        val w = width
        var best = -1
        var bd = Float.MAX_VALUE
        for (r in 1..2) {
            for (dy in -r..r) for (dx in -r..r) {
                if (dx > -r && dx < r && dy > -r && dy < r) continue
                val x = gx + dx
                val y = gy + dy
                if (x < 0 || y < 0 || x >= w || y >= height) continue
                val c = y * w + x
                if (claim[c] == claimMark) continue
                val ex = x - fx
                val ey = y - fy
                var d = ex * ex + ey * ey
                if (!within(b, x - px, y - py, cs, sn)) d += OUTSIDE
                if (d < bd) {
                    bd = d
                    best = c
                }
            }
            if (best >= 0 && bd < OUTSIDE) return best
        }
        return best
    }

    /** The point (dx, dy) from the body's centre, turned back into its own frame, falls on one of its cells. */
    private fun within(b: Body, dx: Float, dy: Float, cs: Float, sn: Float): Boolean {
        val lx = cs * dx + sn * dy + b.comX
        val ly = -sn * dx + cs * dy + b.comY
        if (lx < -0.5f || ly < -0.5f) return false
        val x = (lx + 0.5f).toInt() - b.ox
        val y = (ly + 0.5f).toInt() - b.oy
        return x >= 0 && y >= 0 && x < b.ow && y < b.oh && b.occ[y * b.ow + x]
    }

    /**
     * A cell of [b] may go into cell [g]: air, gas, any liquid (it floats or sinks by its density), animals,
     * leaves and vines a non-plant body tears through, and light debris a much heavier body pushes aside (ash,
     * charcoal, embers, withered leaves; snow only where it floats or hangs loose).
     */
    private fun passable(b: Body, g: Int): Boolean {
        val q = mat[g].toInt()
        if (q == AIR) return true
        val k = kind[q]
        if (k == Kind.GAS || k == Kind.LIQUID || animalOf[q]) return true
        if (flags[g].toInt() and Flag.ANCHORED != 0) return false
        if (softOf[q]) return !b.plant
        // snow packs under a load (it is pressed into ice); other light debris a heavy body pushes aside
        return k == Kind.POWDER && dens[q] <= LIGHT_POWDER && b.mass > dens[q] * SINK_INTO * b.n && (compacts[q] < 0 || adrift(g))
    }

    /**
     * Light loose stuff at [g] that floats or hangs loose: no firm ground under it, only more of the same down
     * to a liquid or air. A layer of snow on the ground carries a stone (and is pressed into ice by it).
     */
    private fun adrift(g: Int): Boolean {
        val w = width
        var c = g + w
        var n = 0
        while (c < size && n < ADRIFT_DEPTH) {
            val q = mat[c].toInt()
            val k = kind[q]
            if (q == AIR || k == Kind.LIQUID || k == Kind.GAS) return true
            if (k != Kind.POWDER || dens[q] > LIGHT_POWDER) return false
            c += w
            n++
        }
        return false
    }

    /**
     * Ground: rock, what the level anchored, and firm loose ground (earth, sand, gravel, clay); not light
     * debris such as embers, ash or snow heaped against a body.
     */
    private fun ground(c: Int): Boolean {
        val q = mat[c].toInt()
        val k = kind[q]
        return k == Kind.STATIC || flags[c].toInt() and Flag.ANCHORED != 0 || (k == Kind.POWDER && dens[q] >= FIRM_GROUND)
    }

    /** Neither the body's own cell nor one it may pass: something it hits. */
    private fun hard(b: Body, c: Int): Boolean = bodyAt[c] != b.id && !passable(b, c)

    // manifold points of one collision
    private val pK = IntArray(MAX_POINTS)
    private val pG = IntArray(MAX_POINTS)
    private val pX = FloatArray(MAX_POINTS)
    private val pY = FloatArray(MAX_POINTS)
    private val pNx = FloatArray(MAX_POINTS)
    private val pNy = FloatArray(MAX_POINTS)
    private val pOther = arrayOfNulls<Body>(MAX_POINTS)
    private val pMu = FloatArray(MAX_POINTS)
    private val pTarget = FloatArray(MAX_POINTS)
    private val pKn = FloatArray(MAX_POINTS)
    private val pKt = FloatArray(MAX_POINTS)
    private val pJn = FloatArray(MAX_POINTS)
    private val pJt = FloatArray(MAX_POINTS)
    private val pV0 = FloatArray(MAX_POINTS)
    private val mStart = IntArray(MAX_GROUPS)
    private val mCount = IntArray(MAX_GROUPS)
    private var manifolds = 0
    private val gKey = IntArray(MAX_GROUPS)
    private val gSx = FloatArray(MAX_GROUPS)
    private val gSy = FloatArray(MAX_GROUPS)
    private val gN = IntArray(MAX_GROUPS)
    private val gMin = IntArray(MAX_GROUPS)
    private val gMax = IntArray(MAX_GROUPS)
    private val gSmin = FloatArray(MAX_GROUPS)
    private val gSmax = FloatArray(MAX_GROUPS)

    /**
     * [b] would hit something at the trial pose (centre [nx], [ny]) in [cand]: finds where and which way the
     * surfaces face, and changes the motion of [b] (and of a body it hit) with impulses at up to two points per
     * surface, so that it stops or bounces there and rubs along it.
     */
    private fun collide(b: Body, nx: Float, ny: Float) {
        val w = width
        var nc = 0
        for (k in 0 until b.n) {
            val g = cand[k]
            if (g >= 0 && !hard(b, g)) continue
            var sx = 0f
            var sy = 0f
            if (g < 0) {
                val fx = fxs[k]
                val fy = fys[k]
                when {
                    fx < -0.5f -> sx = 1f
                    fx >= width - 0.5f -> sx = -1f
                    fy < -0.5f -> sy = 1f
                    else -> sy = -1f
                }
            } else {
                // the surface faces away from where the obstacle is: its hard cells around, only its own (a body
                // lying next to the foot of another must not tilt the ground under it); of light debris only what
                // is on the far side from where the cell comes from (embers heaped beside a post are not the ground
                // it stands on), of the ground all of it (the steps of a slope)
                val gx = g % w
                val gy = g / w
                val ax = b.at[k] % w - gx
                val ay = b.at[k] / w - gy
                val owner = bodyAt[g]
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val x = gx + dx
                    val y = gy + dy
                    val solid = x < 0 || y < 0 || x >= w || y >= height ||
                        (bodyAt[y * w + x] == owner && hard(b, y * w + x) && (dx * ax + dy * ay <= 0 || ground(y * w + x)))
                    if (solid) {
                        sx -= dx
                        sy -= dy
                    }
                }
                if (sx * sx + sy * sy < 0.25f) {
                    // a lone cell or a thin plate: it faces the body
                    sx = nx - gx
                    sy = ny - gy
                    if (sx * sx + sy * sy < 0.01f) {
                        sx = 0f
                        sy = -1f
                    }
                }
            }
            val l = sqrt(sx * sx + sy * sy)
            ck[nc] = k
            cg[nc] = g
            cnx[nc] = sx / l
            cny[nc] = sy / l
            nc++
        }
        if (nc == 0) {
            outX = 0f
            outY = -1f
            return
        }
        // group the contacts by what was hit and which way it faces
        var groups = 0
        for (c in 0 until nc) {
            val g = cg[c]
            val other = if (g >= 0) bodyAt[g] else 0
            val key = other * 8 + sector(cnx[c], cny[c])
            var gi = 0
            while (gi < groups && gKey[gi] != key) gi++
            if (gi == groups) {
                if (groups == MAX_GROUPS) {
                    cGroup[c] = -1
                    continue
                }
                gKey[gi] = key
                gSx[gi] = 0f
                gSy[gi] = 0f
                gN[gi] = 0
                gSmin[gi] = Float.MAX_VALUE
                gSmax[gi] = -Float.MAX_VALUE
                groups++
            }
            cGroup[c] = gi
            gSx[gi] += cnx[c]
            gSy[gi] += cny[c]
            gN[gi]++
        }
        var ox = 0f
        var oy = 0f
        for (gi in 0 until groups) {
            val l = sqrt(gSx[gi] * gSx[gi] + gSy[gi] * gSy[gi])
            if (l < 1e-4f) {
                gSx[gi] = 0f
                gSy[gi] = -1f
            } else {
                gSx[gi] /= l
                gSy[gi] /= l
            }
            ox += gSx[gi] * gN[gi]
            oy += gSy[gi] * gN[gi]
        }
        val ol = sqrt(ox * ox + oy * oy)
        if (ol < 1e-4f) {
            outX = 0f
            outY = -1f
        } else {
            outX = ox / ol
            outY = oy / ol
        }
        // the two ends of each surface along it
        for (c in 0 until nc) {
            val gi = cGroup[c]
            if (gi < 0) continue
            val k = ck[c]
            val s = -gSy[gi] * fxs[k] + gSx[gi] * fys[k]
            if (s < gSmin[gi]) {
                gSmin[gi] = s
                gMin[gi] = c
            }
            if (s > gSmax[gi]) {
                gSmax[gi] = s
                gMax[gi] = c
            }
        }
        var np = 0
        manifolds = 0
        for (gi in 0 until groups) {
            val a = gMin[gi]
            val z = gMax[gi]
            val first = np
            if (gSmax[gi] - gSmin[gi] < 1f) {
                np = point(b, np, (fxs[ck[a]] + fxs[ck[z]]) * 0.5f, (fys[ck[a]] + fys[ck[z]]) * 0.5f, ck[a], cg[a], gSx[gi], gSy[gi], nx, ny)
            } else {
                np = point(b, np, fxs[ck[a]], fys[ck[a]], ck[a], cg[a], gSx[gi], gSy[gi], nx, ny)
                np = point(b, np, fxs[ck[z]], fys[ck[z]], ck[z], cg[z], gSx[gi], gSy[gi], nx, ny)
            }
            if (np > first) {
                mStart[manifolds] = first
                mCount[manifolds] = np - first
                manifolds++
            }
        }
        solve(b, np, nx, ny)
    }

    /** Which of eight directions a normal points to. */
    private fun sector(x: Float, y: Float): Int {
        val ax = if (x < 0f) -x else x
        val ay = if (y < 0f) -y else y
        return when {
            ax > ay * 2.414f -> if (x > 0f) 0 else 4
            ay > ax * 2.414f -> if (y > 0f) 2 else 6
            x > 0f -> if (y > 0f) 1 else 7
            else -> if (y > 0f) 3 else 5
        }
    }

    /** Adds a contact point at (x, y) facing (nx, ny); [cx], [cy] is the body's centre at the trial pose. */
    private fun point(b: Body, np: Int, x: Float, y: Float, k: Int, g: Int, nx: Float, ny: Float, cx: Float, cy: Float): Int {
        if (np == MAX_POINTS) return np
        val id = if (g >= 0) bodyAt[g] else 0
        var other = if (id != 0) slots[id - 1] else null
        val rax = x - cx
        val ray = y - cy
        var rbx = 0f
        var rby = 0f
        if (other != null) {
            rbx = x - other.px
            rby = y - other.py
        }
        // how fast they come together there
        var vx = b.vx - b.w * ray
        var vy = b.vy + b.w * rax
        if (other != null) {
            vx -= other.vx - other.w * rby
            vy -= other.vy + other.w * rbx
        }
        val vn = vx * nx + vy * ny
        if (other != null) {
            if (b.touches < b.touch.size) b.touch[b.touches++] = other.id
            // a body lying on the ground (asleep, or resting there just now) holds what rests on it as the ground
            // would; only a hard blow moves it (and wakes it)
            if (other.asleep || world.step - other.ground <= GROUNDED) {
                if (vn < -WAKE_SPEED) wake(other) else other = null
            }
        }
        // resting on the ground, or on a body that rests on it
        if (ny < -0.5f && (g < 0 || bodyAt[g] == 0 || other == null)) b.ground = world.step
        val ima = 1f / b.mass
        val iia = 1f / b.inertia
        val imb = if (other != null) 1f / other.mass else 0f
        val iib = if (other != null) 1f / other.inertia else 0f
        val rna = rax * ny - ray * nx
        val rnb = rbx * ny - rby * nx
        val tx = -ny
        val ty = nx
        val rta = rax * ty - ray * tx
        val rtb = rbx * ty - rby * tx
        pK[np] = k
        pG[np] = g
        pX[np] = rax
        pY[np] = ray
        pNx[np] = nx
        pNy[np] = ny
        pOther[np] = other
        pV0[np] = vn
        pKn[np] = ima + iia * rna * rna + imb + iib * rnb * rnb
        pKt[np] = ima + iia * rta * rta + imb + iib * rtb * rtb
        pTarget[np] = if (vn < -BOUNCE_FROM) -RESTITUTION * vn else 0f
        val mb = if (g >= 0) mat[g].toInt() else -1
        val ma = mat[b.at[k]].toInt()
        pMu[np] = if (mb < 0) fric[ma] else minOf(fric[ma], fric[mb])
        pJn[np] = 0f
        pJt[np] = 0f
        return np + 1
    }

    /**
     * Impulses at the contact points, a few rounds: first the rubbing along each surface, at most mu times
     * the push; then the push itself, for a surface touched at two points both at once (a box on a floor
     * gets the same push at both ends and does not start to turn), the sums never pulling.
     */
    private fun solve(b: Body, np: Int, cx: Float, cy: Float) {
        if (np == 0) return
        val ima = 1f / b.mass
        val iia = 1f / b.inertia
        for (round in 0 until ROUNDS) {
            for (mi in 0 until manifolds) {
                val p0 = mStart[mi]
                val pn = mCount[mi]
                for (p in p0 until p0 + pn) {
                    val o = pOther[p]
                    val rax = pX[p]
                    val ray = pY[p]
                    val rbx = if (o != null) rax + cx - o.px else 0f
                    val rby = if (o != null) ray + cy - o.py else 0f
                    val tx = -pNy[p]
                    val ty = pNx[p]
                    val vt = relX(b, o, rax, ray, rbx, rby) * tx + relY(b, o, rax, ray, rbx, rby) * ty
                    val lim = pMu[p] * pJn[p]
                    var dt = -vt / pKt[p]
                    val jt = (pJt[p] + dt).coerceIn(-lim, lim)
                    dt = jt - pJt[p]
                    pJt[p] = jt
                    apply(b, o, ima, iia, rax, ray, rbx, rby, tx * dt, ty * dt)
                }
                if (pn == 2 && pOther[p0] === pOther[p0 + 1] && pair(b, p0, p0 + 1, ima, iia, cx, cy)) continue
                for (p in p0 until p0 + pn) {
                    val o = pOther[p]
                    val rax = pX[p]
                    val ray = pY[p]
                    val rbx = if (o != null) rax + cx - o.px else 0f
                    val rby = if (o != null) ray + cy - o.py else 0f
                    val nx = pNx[p]
                    val ny = pNy[p]
                    val vn = relX(b, o, rax, ray, rbx, rby) * nx + relY(b, o, rax, ray, rbx, rby) * ny
                    var dj = (pTarget[p] - vn) / pKn[p]
                    val jn = maxOf(0f, pJn[p] + dj)
                    dj = jn - pJn[p]
                    pJn[p] = jn
                    apply(b, o, ima, iia, rax, ray, rbx, rby, nx * dj, ny * dj)
                }
            }
        }
        for (p in 0 until np) {
            val v = -pV0[p]
            if (v < BREAK_FROM || impacts == MAX_IMPACTS) continue
            val o = pOther[p]
            impK[impacts] = pK[p]
            impG[impacts] = pG[p]
            impV[impacts] = v
            impM[impacts] = if (o == null) b.mass else b.mass * o.mass / (b.mass + o.mass)
            impacts++
        }
    }

    private fun relX(b: Body, o: Body?, rax: Float, ray: Float, rbx: Float, rby: Float): Float =
        b.vx - b.w * ray - (if (o != null) o.vx - o.w * rby else 0f)

    private fun relY(b: Body, o: Body?, rax: Float, ray: Float, rbx: Float, rby: Float): Float =
        b.vy + b.w * rax - (if (o != null) o.vy + o.w * rbx else 0f)

    /**
     * The push at two points of one surface, solved together (the two-point case of Box2D's block solver):
     * both pushing, only one, or neither, whichever leaves no point sinking in and no push pulling.
     * Returns false if the two points are too close to tell apart (then they are solved one by one).
     */
    private fun pair(b: Body, p1: Int, p2: Int, ima: Float, iia: Float, cx: Float, cy: Float): Boolean {
        val o = pOther[p1]
        val nx = pNx[p1]
        val ny = pNy[p1]
        val imb = if (o != null) 1f / o.mass else 0f
        val iib = if (o != null) 1f / o.inertia else 0f
        val r1x = pX[p1]
        val r1y = pY[p1]
        val r2x = pX[p2]
        val r2y = pY[p2]
        val s1x = if (o != null) r1x + cx - o.px else 0f
        val s1y = if (o != null) r1y + cy - o.py else 0f
        val s2x = if (o != null) r2x + cx - o.px else 0f
        val s2y = if (o != null) r2y + cy - o.py else 0f
        val rn1 = r1x * ny - r1y * nx
        val rn2 = r2x * ny - r2y * nx
        val sn1 = s1x * ny - s1y * nx
        val sn2 = s2x * ny - s2y * nx
        val k11 = ima + iia * rn1 * rn1 + imb + iib * sn1 * sn1
        val k22 = ima + iia * rn2 * rn2 + imb + iib * sn2 * sn2
        val k12 = ima + iia * rn1 * rn2 + imb + iib * sn1 * sn2
        val det = k11 * k22 - k12 * k12
        if (det < PAIR_DET * k11 * k22) return false
        val a1 = pJn[p1]
        val a2 = pJn[p2]
        val vn1 = relX(b, o, r1x, r1y, s1x, s1y) * nx + relY(b, o, r1x, r1y, s1x, s1y) * ny
        val vn2 = relX(b, o, r2x, r2y, s2x, s2y) * nx + relY(b, o, r2x, r2y, s2x, s2y) * ny
        val b1 = vn1 - pTarget[p1] - (k11 * a1 + k12 * a2)
        val b2 = vn2 - pTarget[p2] - (k12 * a1 + k22 * a2)
        var x1 = -(k22 * b1 - k12 * b2) / det
        var x2 = -(k11 * b2 - k12 * b1) / det
        if (x1 < 0f || x2 < 0f) {
            x1 = -b1 / k11
            x2 = 0f
            if (x1 < 0f || k12 * x1 + b2 < 0f) {
                x1 = 0f
                x2 = -b2 / k22
                if (x2 < 0f || k12 * x2 + b1 < 0f) {
                    x1 = 0f
                    x2 = 0f
                }
            }
        }
        val d1 = x1 - a1
        val d2 = x2 - a2
        pJn[p1] = x1
        pJn[p2] = x2
        apply(b, o, ima, iia, r1x, r1y, s1x, s1y, nx * d1, ny * d1)
        apply(b, o, ima, iia, r2x, r2y, s2x, s2y, nx * d2, ny * d2)
        return true
    }

    private fun apply(b: Body, o: Body?, ima: Float, iia: Float, rax: Float, ray: Float, rbx: Float, rby: Float, jx: Float, jy: Float) {
        b.vx += jx * ima
        b.vy += jy * ima
        b.w += (rax * jy - ray * jx) * iia
        if (o != null) {
            val imb = 1f / o.mass
            o.vx -= jx * imb
            o.vy -= jy * imb
            o.w -= (rbx * jy - rby * jx) / o.inertia
        }
    }

    // scratch for moving a body's cells in the grid
    private var bm = ByteArray(64)
    private var bt = FloatArray(64)
    private var bl = ShortArray(64)
    private var bv = ByteArray(64)
    private var bf = ByteArray(64)
    private var entered = IntArray(64)
    private var left = IntArray(64)
    private var dm = ByteArray(64)
    private var dtp = FloatArray(64)
    private var dl = ShortArray(64)
    private var dv = ByteArray(64)
    private var df = ByteArray(64)

    /**
     * Moves the cells of [b] into [next]. What was in the cells it moves into (air, water, …) takes the places
     * it left, in order; leaves and vines in the way are torn off and come down as withered leaves.
     */
    private fun commit(b: Body, next: IntArray) {
        val n = b.n
        if (bm.size < n) {
            val s = maxOf(n, bm.size * 2)
            bm = ByteArray(s); bt = FloatArray(s); bl = ShortArray(s); bv = ByteArray(s); bf = ByteArray(s)
            entered = IntArray(s); left = IntArray(s)
            dm = ByteArray(s); dtp = FloatArray(s); dl = ShortArray(s); dv = ByteArray(s); df = ByteArray(s)
        }
        if (++claimMark == Int.MAX_VALUE) {
            claim.fill(0)
            claimMark = 1
        }
        val id = b.id
        var ne = 0
        var nl = 0
        for (k in 0 until n) {
            val c = b.at[k]
            bm[k] = mat[c]
            bt[k] = temp[c]
            bl[k] = life[c]
            bv[k] = variant[c]
            bf[k] = flags[c]
            claim[next[k]] = claimMark
            if (bodyAt[next[k]] != id) entered[ne++] = next[k]
        }
        for (k in 0 until n) if (claim[b.at[k]] != claimMark) left[nl++] = b.at[k]
        java.util.Arrays.sort(entered, 0, ne)
        java.util.Arrays.sort(left, 0, nl)
        val w = width
        var tore = false
        for (e in 0 until ne) {
            val c = entered[e]
            val q = mat[c].toInt()
            if (softOf[q]) {
                // torn off
                val o = bodyAt[c]
                if (o != 0 && o != id) slots[o - 1]?.dirty = true
                dm[e] = deadPlant.toByte()
                dtp[e] = temp[c]
                dl[e] = 0
                dv[e] = variant[c]
                df[e] = 0
                tore = true
            } else {
                dm[e] = mat[c]
                dtp[e] = temp[c]
                dl[e] = life[c]
                dv[e] = variant[c]
                df[e] = flags[c]
            }
        }
        for (e in 0 until minOf(ne, nl)) {
            val c = left[e]
            mat[c] = dm[e]
            temp[c] = dtp[e]
            life[c] = dl[e]
            variant[c] = dv[e]
            flags[c] = df[e]
            bodyAt[c] = 0
            world.wakeHeat(c % w, c / w)
        }
        for (k in 0 until n) {
            val c = next[k]
            mat[c] = bm[k]
            temp[c] = bt[k]
            life[c] = bl[k]
            variant[c] = bv[k]
            flags[c] = bf[k]
            if (bodyAt[c] != id) world.wakeHeat(c % w, c / w)
            bodyAt[c] = id
            b.at[k] = c
        }
        world.gasMoved()
        if (tore) world.structuresMoved()
        bounds(b)
    }

    // per row of the world: the leftmost and rightmost cell of the body there, the liquid at its ends
    private val rowMin = IntArray(height)
    private val rowMax = IntArray(height)
    private val rowRho = FloatArray(height)

    /**
     * Floating (4): a body in a liquid is pushed up by the liquid it puts aside: each of its cells below the
     * surface by the liquid that lies beside it in that row (a block in lava over a layer of molten metal is
     * pushed up hard only where the metal is), at the middle of those cells; so a raft rights itself. The
     * liquid also slows it down. Returns the share of the body under the surface.
     */
    private fun buoyancy(b: Body): Float {
        val w = width
        val id = b.id
        for (y in b.y0..b.y1) {
            rowMin[y] = Int.MAX_VALUE
            rowMax[y] = Int.MIN_VALUE
        }
        for (k in 0 until b.n) {
            val c = b.at[k]
            val x = c % w
            val y = c / w
            if (x < rowMin[y]) rowMin[y] = x
            if (x > rowMax[y]) rowMax[y] = x
        }
        var surface = Int.MAX_VALUE
        for (y in b.y0..b.y1) {
            var rho = 0f
            if (rowMin[y] <= rowMax[y]) {
                if (rowMin[y] > 0) {
                    val l = y * w + rowMin[y] - 1
                    if (bodyAt[l] != id && kind[mat[l].toInt()] == Kind.LIQUID) rho = dens[mat[l].toInt()]
                }
                if (rowMax[y] < w - 1) {
                    val r = y * w + rowMax[y] + 1
                    if (bodyAt[r] != id && kind[mat[r].toInt()] == Kind.LIQUID && dens[mat[r].toInt()] > rho) rho = dens[mat[r].toInt()]
                }
            }
            rowRho[y] = rho
            if (rho > 0f && y < surface) surface = y
        }
        if (surface == Int.MAX_VALUE) {
            // walled in at the sides, under a liquid: all of it is in it
            var rho = 0f
            for (k in 0 until b.n) {
                val c = b.at[k]
                if (c < w) continue
                val u = c - w
                if (bodyAt[u] != id && kind[mat[u].toInt()] == Kind.LIQUID) {
                    if (u / w < surface) surface = u / w
                    if (dens[mat[u].toInt()] > rho) rho = dens[mat[u].toInt()]
                }
            }
            if (rho == 0f) return 0f
            for (y in b.y0..b.y1) rowRho[y] = rho
            surface -= 1
        }
        // the row above the surface takes the part of its cells that the body, sinking, has already put under
        if (surface > b.y0 && rowRho[surface - 1] == 0f) rowRho[surface - 1] = rowRho[surface]
        // the top edge of the highest liquid cell beside it
        val line = surface - 0.5f
        var f = 0f
        var share = 0f
        var mx = 0f
        for (k in 0 until b.n) {
            val rho = rowRho[b.at[k] / w]
            if (rho == 0f) continue
            val rx = b.lx[k] - b.comX
            val ry = b.ly[k] - b.comY
            val y = b.py + b.sn * rx + b.cs * ry
            var sub = y + 0.5f - line
            if (sub <= 0f) continue
            if (sub > 1f) sub = 1f
            f += rho * sub
            share += sub
            mx += rho * sub * (b.cs * rx - b.sn * ry)
        }
        if (f == 0f) return 0f
        val force = f * G
        b.vy -= force / b.mass
        b.w -= (mx / f) * force / b.inertia
        share /= b.n
        b.vx *= 1f - DRAG * share
        b.vy *= 1f - DRAG * share
        b.w *= 1f - SPIN_DRAG * share
        return share
    }

    /**
     * Breaking on impact (5): what hits hard enough cracks, by the impact's energy against its strength.
     * The body's own cells where it hit by the speed alone (glass, ice and brick are brittle, stone stands
     * much more); what it hits by the energy, so a heavy boulder breaks the ice a pebble does not.
     */
    private fun impact(b: Body) {
        val w = width
        for (j in 0 until impacts) {
            val v = impV[j]
            val e = 0.5f * v * v
            val k = impK[j]
            if (k < b.n) {
                val c = b.at[k]
                val m = mat[c].toInt()
                if (bodyAt[c] == b.id && breakProduct[m] >= 0) {
                    val limit = SELF_BREAK * strength[m] * strength[m]
                    if (e > limit) shatter(c, m, e / limit, b)
                }
            }
            val g = impG[j]
            if (g >= 0) {
                val q = mat[g].toInt()
                if (kind[q] == Kind.SOLID && breakProduct[q] >= 0 && flags[g].toInt() and Flag.ANCHORED == 0) {
                    val limit = HIT_BREAK * strength[q] * strength[q]
                    val energy = e * impM[j]
                    if (energy > limit) shatter(g, q, energy / limit, body(bodyAt[g]))
                }
                if (v >= SHAKE_SPEED) world.shake(g % w, g / w, SHAKE_RADIUS)
            }
            if (slots[b.id - 1] !== b) return
        }
    }

    /**
     * Cell [c] of [m] breaks with [q] times the energy it takes: a core of broken material around it, and from
     * there cracks running through the same material, which split it into pieces (ice floes, shards of glass).
     * A small body breaks altogether.
     */
    /** Thermal shock (4a.2) in a body: the cell [c] of [m] cracks, and cracks run on through the piece. */
    fun crack(c: Int, m: Int) {
        val b = body(bodyAt[c])
        shatter(c, m, SHOCK, b, mt.shockProduct[m], Seen.CRACK)
    }

    private fun shatter(c: Int, m: Int, q: Float, owner: Body?, product: Int = breakProduct[m], seen: Int = Seen.SHATTER) {
        val w = width
        val x0 = c % w
        val y0 = c / w
        val r = minOf(MAX_CORE, CORE + CORE_GROW * q)
        world.note(seen, m)
        if (owner != null && owner.n <= r * r * WHOLE) {
            for (k in 0 until owner.n) {
                val i = owner.at[k]
                if (mat[i].toInt() == m && bodyAt[i] == owner.id) world.change(i % w, i / w, i, product)
            }
            return
        }
        val ri = r.toInt() + 1
        val r2 = r * r
        for (dy in -ri..ri) for (dx in -ri..ri) {
            if (dx * dx + dy * dy > r2) continue
            val x = x0 + dx
            val y = y0 + dy
            if (x < 0 || y < 0 || x >= w || y >= height) continue
            val i = y * w + x
            if (mat[i].toInt() == m && flags[i].toInt() and Flag.ANCHORED == 0) world.change(x, y, i, product)
        }
        val cracks = minOf(MAX_CRACKS, (q - 0.5f).toInt())
        val length = minOf(MAX_CRACK, (CRACK + CRACK_GROW * q).toInt())
        for (n in 0 until cracks) {
            val d = world.below(16)
            val dx = DIR_X[d]
            val dy = DIR_Y[d]
            for (t in 1..length) {
                val x = x0 + (dx * t + if (dx * t < 0f) -0.5f else 0.5f).toInt()
                val y = y0 + (dy * t + if (dy * t < 0f) -0.5f else 0.5f).toInt()
                if (x < 0 || y < 0 || x >= w || y >= height) break
                val i = y * w + x
                val q2 = mat[i].toInt()
                if (q2 == product) continue
                if (q2 != m || flags[i].toInt() and Flag.ANCHORED != 0) break
                world.change(x, y, i, product)
            }
        }
    }

    // ------------------------------------------------------------------ rest and waking

    /**
     * [b] lies still. Resting against another body that lies still too, they become one. Otherwise it lies
     * square if it is nearly so, and goes to sleep; if it is anchored (or a plant has taken root) it becomes
     * a fixed part of the world again.
     */
    private fun rest(b: Body) {
        if (b.touches > 0) {
            val parts = arrayOfNulls<Body>(b.touches + 1)
            var k = 0
            parts[k++] = b
            for (t in 0 until b.touches) {
                val o = slots[b.touch[t] - 1] ?: continue
                if (o === b || parts.any { it === o }) continue
                if (!o.asleep && o.still < MERGE_STEPS / 2 && world.step - o.ground > GROUNDED) {
                    // it rests on something still moving: wait
                    b.still = MERGE_STEPS / 2
                    return
                }
                parts[k++] = o
            }
            if (k > 1) {
                @Suppress("UNCHECKED_CAST")
                merge(parts as Array<Body>, k)
                return
            }
        }
        // it fell over on its way here: turned by about a quarter, not rolled on and on
        if (b.turned in TOPPLE_FROM..TOPPLE_TO) world.note(Seen.TOPPLE, b.main)
        b.turned = 0f
        square(b)
        if (world.anchoredBody(b) || (b.plant && world.rootedBody(b))) {
            dissolve(b)
            return
        }
        b.asleep = true
        b.vx = 0f
        b.vy = 0f
        b.w = 0f
        b.support = fingerprint(b)
    }

    /** Turns a body lying nearly square the last bit, if there is room. */
    private fun square(b: Body) {
        val a = if (b.sn < 0f) -b.sn else b.sn
        val c = if (b.cs < 0f) -b.cs else b.cs
        if (a < 1e-6f || c < 1e-6f) return
        val cs: Float
        val sn: Float
        if (a < SQUARE) {
            cs = if (b.cs < 0f) -1f else 1f
            sn = 0f
        } else if (c < SQUARE) {
            cs = 0f
            sn = if (b.sn < 0f) -1f else 1f
        } else {
            return
        }
        bestValid = false
        if (!tryPose(b, b.px, b.py, cs, sn)) return
        b.cs = cs
        b.sn = sn
        var moved = false
        for (k in 0 until b.n) if (best[k] != b.at[k]) moved = true
        if (moved) commit(b, best)
    }

    /** The body becomes a fixed part of the world again: its cells are plain cells of a standing structure. */
    private fun dissolve(b: Body) {
        remove(b)
        world.structuresMoved()
    }

    /** A sleeping body wakes when something around it changed: what held it may be gone. */
    private fun disturbed(b: Body): Boolean {
        val cx0 = maxOf(0, (b.x0 - 1) shr World.SHIFT)
        val cy0 = maxOf(0, (b.y0 - 1) shr World.SHIFT)
        val cx1 = minOf(world.chunksX - 1, (b.x1 + 1) shr World.SHIFT)
        val cy1 = minOf(world.chunksY - 1, (b.y1 + 1) shr World.SHIFT)
        var near = false
        for (cy in cy0..cy1) for (cx in cx0..cx1) if (world.updated(cx, cy)) near = true
        if (!near) return false
        return fingerprint(b) != b.support
    }

    private fun fingerprint(b: Body): Long {
        val w = width
        var h = -0x340d631b7bdddcdbL
        val id = b.id
        for (k in 0 until b.n) {
            val c = b.at[k]
            val x = c % w
            if (x > 0 && bodyAt[c - 1] != id) h = (h xor mat[c - 1].toLong()) * 0x100000001b3L
            if (x < w - 1 && bodyAt[c + 1] != id) h = (h xor mat[c + 1].toLong()) * 0x100000001b3L
            if (c >= w && bodyAt[c - w] != id) h = (h xor mat[c - w].toLong()) * 0x100000001b3L
            if (c + w < size && bodyAt[c + w] != id) h = (h xor (mat[c + w].toLong() + 1000L)) * 0x100000001b3L
        }
        return h
    }

    // ------------------------------------------------------------------ from outside

    /** A blast (4a.4) throws the bodies within [reach] of (cx, cy) away from it. */
    fun blast(cx: Int, cy: Int, force: Float, reach: Int) {
        for (b in slots) {
            if (b == null) continue
            val dx = b.px - cx
            val dy = b.py - cy
            val d = sqrt(dx * dx + dy * dy)
            if (d > reach + b.radius) continue
            val f = force * BLAST * (1f - minOf(1f, maxOf(0f, d - b.radius) / (reach + 1))) / b.mass
            val l = maxOf(0.5f, d)
            b.vx += dx / l * f
            b.vy += dy / l * f - f * 0.3f
            wake(b)
        }
    }

    /** Forgets every body (the world is emptied); the cells stay where they are. */
    fun clear() {
        for (s in slots.indices) slots[s] = null
        bodyAt.fill(0)
        count = 0
    }

    // ------------------------------------------------------------------ snapshots

    class Saved(val ints: IntArray, val floats: FloatArray, val longs: LongArray)

    fun save(): Saved {
        val ints = ArrayList<Int>()
        val floats = ArrayList<Float>()
        val longs = ArrayList<Long>()
        ints += slots.size
        for (b in slots) {
            if (b == null) continue
            ints += b.id; ints += b.n
            ints += (if (b.asleep) 1 else 0) or (if (b.plant) 2 else 0) or (if (b.dirty) 4 else 0)
            ints += b.still; ints += b.stuck; ints += b.main
            ints += b.touches
            for (t in 0 until b.touches) ints += b.touch[t]
            ints += b.ground
            for (k in 0 until b.n) {
                ints += b.lx[k]; ints += b.ly[k]; ints += b.at[k]
            }
            floats += b.comX; floats += b.comY; floats += b.mass; floats += b.inertia; floats += b.radius
            floats += b.px; floats += b.py; floats += b.cs; floats += b.sn
            floats += b.vx; floats += b.vy; floats += b.w; floats += b.turned
            longs += b.support
        }
        return Saved(ints.toIntArray(), floats.toFloatArray(), longs.toLongArray())
    }

    fun restore(s: Saved) {
        clear()
        val ints = s.ints
        var i = 0
        var f = 0
        var l = 0
        slots = arrayOfNulls(maxOf(16, ints[i++]))
        while (i < ints.size) {
            val b = Body(ints[i++])
            b.n = ints[i++]
            val fl = ints[i++]
            b.asleep = fl and 1 != 0
            b.plant = fl and 2 != 0
            b.dirty = fl and 4 != 0
            b.still = ints[i++]
            b.stuck = ints[i++]
            b.main = ints[i++]
            b.touches = ints[i++]
            for (t in 0 until b.touches) b.touch[t] = ints[i++]
            b.ground = ints[i++]
            b.lx = IntArray(b.n)
            b.ly = IntArray(b.n)
            b.at = IntArray(b.n)
            for (k in 0 until b.n) {
                b.lx[k] = ints[i++]; b.ly[k] = ints[i++]; b.at[k] = ints[i++]
            }
            b.comX = s.floats[f++]; b.comY = s.floats[f++]; b.mass = s.floats[f++]; b.inertia = s.floats[f++]; b.radius = s.floats[f++]
            b.px = s.floats[f++]; b.py = s.floats[f++]; b.cs = s.floats[f++]; b.sn = s.floats[f++]
            b.vx = s.floats[f++]; b.vy = s.floats[f++]; b.w = s.floats[f++]; b.turned = s.floats[f++]
            b.support = s.longs[l++]
            slots[b.id - 1] = b
            count++
            for (k in 0 until b.n) bodyAt[b.at[k]] = b.id
            bounds(b)
            outline(b)
        }
    }

    fun hash(mix: (Int) -> Unit) {
        mix(count)
        for (b in slots) {
            if (b == null) continue
            mix(b.id); mix(b.n); mix(if (b.asleep) 1 else 0); mix(b.still); mix(b.stuck); mix(b.ground)
            for (k in 0 until b.n) mix(b.at[k])
            for (v in floatArrayOf(b.px, b.py, b.cs, b.sn, b.vx, b.vy, b.w)) mix(java.lang.Float.floatToRawIntBits(v))
        }
    }

    companion object {
        /** Gravity, cells per step per step; the fastest a body moves (per axis), the fastest it turns. */
        const val G = 0.2f
        const val VMAX = 6f
        const val WMAX = 0.35f

        /** At most this far (cells) a cell moves in one sub-step, and so many sub-steps a step. */
        private const val SUB = 0.5f
        private const val MAX_SUB = 16

        /** Cells a body is pushed back out of what it hit, per try. */
        private const val PUSH = 0.6f

        /** Inertia of one square cell about its own centre (per unit of mass). */
        private const val CELL_INERTIA = 1f / 6f
        private const val MIN_DENSITY = 0.05f

        /** Bouncing: from this speed into a surface on, with this share of the speed. */
        private const val BOUNCE_FROM = 1.2f
        private const val RESTITUTION = 0.2f
        private const val ROUNDS = 8

        /** A sleeping body hit faster than this wakes and is pushed; slower, it holds like the ground. */
        private const val WAKE_SPEED = 0.6f
        /** A body that lay on the ground within this many steps still counts as lying there. */
        private const val GROUNDED = 10

        /** Lying still this many steps against another body that lies still, they become one. */
        private const val MERGE_STEPS = 8

        /** Spinning slows in the air a little, and rolling on the ground more (rolling resistance). */
        private const val AIR_SPIN = 0.998f
        private const val ROLL = 0.985f

        /** A liquid slows a body down by this share per step, if all of it is in the liquid. */
        private const val DRAG = 0.1f
        private const val SPIN_DRAG = 0.12f

        /** Light loose stuff a body sinks into: at most this dense (snow, ash, withered leaves), the body at least so many times as dense. */
        private const val LIGHT_POWDER = 0.6f
        private const val SINK_INTO = 3f
        /** Loose ground at least this dense is ground for the way a surface faces (earth, sand, gravel, clay). */
        private const val FIRM_GROUND = 1.2f
        private const val ADRIFT_DEPTH = 16

        /** Lying still and slow so many steps, a body sleeps; not moving a single cell so many, it sleeps anyway. */
        const val SLEEP_STEPS = 20
        private const val STUCK_STEPS = 120
        private const val SLEEP_SPEED = 0.08f

        /** A body lying within this (sine of the angle, about 3.5°) of square is laid square when it sleeps. */
        private const val SQUARE = 0.06f

        /** Falling faster than this (cells per step) counts as falling for the discovery book. */
        private const val FALL_NOTE = 0.5f
        /** Rolling for the book: a body at least this big, its rim and itself this fast (cells per step). */
        private const val ROLL_RADIUS = 2f
        private const val ROLL_NOTE = 0.25f
        /** Fallen over for the book: turned by this much (radians) before it came to rest. */
        private const val TOPPLE_FROM = 0.6f
        private const val TOPPLE_TO = 2.6f

        // breaking (5)
        /** Impacts slower than this break nothing. */
        private const val BREAK_FROM = 1f
        /** A body's own cells break when half the square of the speed exceeds this times the square of their strength (glass from about five cells' fall). */
        private const val SELF_BREAK = 0.25f
        /** What it hits breaks when the energy (half mass times speed squared) exceeds this times the square of its strength. */
        private const val HIT_BREAK = 8f
        private const val MAX_IMPACTS = 16
        private const val CORE = 0.7f
        private const val CORE_GROW = 0.5f
        private const val MAX_CORE = 4f
        private const val CRACK = 4f
        private const val CRACK_GROW = 5f
        private const val MAX_CRACK = 40
        private const val MAX_CRACKS = 4
        /** How hard a thermal shock cracks a piece of glass, in the measure of an impact. */
        private const val SHOCK = 2.5f
        /** A body with fewer cells than this times the square of the broken core breaks altogether. */
        private const val WHOLE = 2f

        /** A hard landing shakes the ground (snow comes loose). */
        private const val SHAKE_SPEED = 2f
        private const val SHAKE_RADIUS = 3

        /** Speed per unit of blast force and mass. */
        private const val BLAST = 0.6f

        /** A free cell outside the body's outline counts as this much farther (cells squared) than one inside. */
        private const val OUTSIDE = 1.5f

        /** Two contact points closer than this (relative) count as one for the paired push. */
        private const val PAIR_DET = 1e-4f

        private const val MAX_POINTS = 16
        private const val MAX_GROUPS = 8

        private val DX4 = intArrayOf(1, -1, 0, 0)
        private val DY4 = intArrayOf(0, 0, 1, -1)

        /** Sixteen directions, a sixteenth of a turn apart. */
        private val DIR_X = floatArrayOf(1f, 0.9239f, 0.7071f, 0.3827f, 0f, -0.3827f, -0.7071f, -0.9239f, -1f, -0.9239f, -0.7071f, -0.3827f, 0f, 0.3827f, 0.7071f, 0.9239f)
        private val DIR_Y = floatArrayOf(0f, 0.3827f, 0.7071f, 0.9239f, 1f, 0.9239f, 0.7071f, 0.3827f, 0f, -0.3827f, -0.7071f, -0.9239f, -1f, -0.9239f, -0.7071f, -0.3827f)

        /** Cosine and sine of a small turn (at most [WMAX]): a few terms of their series, nothing a device could round differently. */
        fun cosOf(a: Float): Float {
            val a2 = a * a
            return 1f - a2 * 0.5f + a2 * a2 * (1f / 24f)
        }

        fun sinOf(a: Float): Float {
            val a2 = a * a
            return a * (1f - a2 * (1f / 6f) + a2 * a2 * (1f / 120f))
        }
    }
}
