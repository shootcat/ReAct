package com.shootcat.react.sim

/**
 * The inhabitants (Phase 5, Leitfaden V3 7a): little figures in the manner of Lemmings. They are goals, not players:
 * the player solves the puzzle, they act in ways one can foresee.
 *
 * A figure stands in one column, [HEIGHT] cells tall, its feet in row y. It is no part of the grid: sand, water and
 * bodies move round and through it, and it looks at the grid to know where it is. It walks on whatever bears it
 * (rock, earth, a plank, a raft), climbs steps of up to [CLIMB] cells, falls where nothing bears it, and faints from
 * a fall of [FAINT_FALL] cells or more. It turns back from what it sees ahead: fire, embers, lava, smoke, anything
 * scorching, water too deep to wade, a drop it would hurt itself on. It runs from a body coming at it and faints if
 * one hits it. In deep water it swims a little while, then goes under and faints.
 *
 * No one dies (7a): a fainted figure lies still a moment and is then brought back to the village (or, without one, to
 * where it was put down), stylised, never shown hurt.
 *
 * Everything follows rules, in the order of the figures, without chance: the same world and the same actions give the
 * same figures on every device.
 */
class Folk internal constructor(private val world: World) {

    private val w = world.width
    private val h = world.height
    private val mt = world.materials
    private val kind = mt.kind

    /** A body of a figure may be in a cell of this material (air, any gas, grass, leaves, a small animal). */
    private val pass = BooleanArray(mt.count) { m ->
        val o = mt.organism[m]
        kind[m] == Kind.GAS || kind[m] == Kind.LIFE || mt.animal[m] ||
            o == Organism.GRASS || o == Organism.MOSS || o == Organism.SEED || o == Organism.LEAF || o == Organism.VINE
    }

    /** It bears a figure (rock, earth, sand, snow, wood, a body): neither to walk through nor a liquid. */
    private val bears = BooleanArray(mt.count) { !pass[it] && kind[it] != Kind.LIQUID }

    /** Seen, it is turned away from: fire and embers, lava and molten metal, smoke. */
    private val fearsome = BooleanArray(mt.count) { m ->
        val id = mt.spec(m).id
        mt.spawnBurning[m] || id == "lava" || id == "molten_metal" || id == "magma" || id == "smoke" || id == "black_smoke" || id == "steam"
    }

    /** A liquid one can swim in (water, salt water, oil); in anything thicker one only sinks. */
    private val swimmable = BooleanArray(mt.count) { kind[it] == Kind.LIQUID && !fearsome[it] && mt.viscosity[it] < World.LEVEL_VISCOSITY }

    // ------------------------------------------------------------------ the figures, in slots

    private var cap = 16
    private var ids = IntArray(cap)
    private var xs = IntArray(cap)
    private var ys = IntArray(cap)
    private var dirs = IntArray(cap)
    private var states = IntArray(cap)
    private var timers = IntArray(cap)
    private var falls = IntArray(cap)
    private var speeds = FloatArray(cap)
    private var drops = FloatArray(cap)
    private var staminas = IntArray(cap)
    private var runs = IntArray(cap)
    private var startXs = IntArray(cap)
    private var startYs = IntArray(cap)
    private var rides = IntArray(cap)
    private var rideXs = FloatArray(cap)
    private var rideYs = FloatArray(cap)
    private var faintCounts = IntArray(cap)
    private var causes = IntArray(cap)
    private var stood = BooleanArray(cap)

    /** How many figures there are. */
    var count = 0
        private set
    private var nextId = 1

    /** The village (7a): where a figure that fainted is brought back to, and the goal; -1 while there is none. */
    var homeX = -1
        private set
    var homeY = -1
        private set

    /** How often each rule acted since this was last read (for the discovery book and tests): see [Did]. */
    val did = IntArray(Did.COUNT)

    /** One figure as seen from outside. */
    class Figure(
        val id: Int,
        val x: Int,
        val y: Int,
        val dir: Int,
        val state: Int,
        val faints: Int,
        /** Why it last fainted: a [Cause]. */
        val cause: Int,
        /** It is in the village. */
        val home: Boolean,
        /** Swimming: steps of breath it has left; on land the full [SWIM_STAMINA]. */
        val stamina: Int,
    )

    fun figures(): List<Figure> = (0 until count).map { k ->
        Figure(ids[k], xs[k], ys[k], dirs[k], states[k], faintCounts[k], causes[k], atHome(k), staminas[k])
    }

    /** For drawing: position (feet), direction, state and timer of figure [k] in slot order. */
    fun x(k: Int) = xs[k]
    fun y(k: Int) = ys[k]
    fun dir(k: Int) = dirs[k]
    fun state(k: Int) = states[k]
    fun timer(k: Int) = timers[k]
    fun id(k: Int) = ids[k]

    /** How many figures are in the village. */
    fun atHomeCount(): Int {
        var n = 0
        for (k in 0 until count) if (atHome(k) && states[k] != FAINT) n++
        return n
    }

    // ------------------------------------------------------------------ putting figures and the village down

    /**
     * A figure put down at ([x], [y]) (one tap, one figure): if that is inside the ground, it is put on top; in the air
     * it falls. Returns its id, or 0 if there is no room for it there.
     */
    fun add(x: Int, y: Int): Int {
        if (count >= MAX || x < 1 || x > w - 2) return 0
        var fy = y.coerceIn(HEIGHT - 1, h - 2)
        var lift = 0
        while (!free(x, fy) && lift < PLACE_LIFT) {
            fy--
            lift++
            if (fy < HEIGHT - 1) return 0
        }
        if (!free(x, fy)) return 0
        if (count == cap) grow()
        val k = count++
        ids[k] = nextId++
        xs[k] = x
        ys[k] = fy
        dirs[k] = 1
        states[k] = WALK
        timers[k] = 0
        falls[k] = 0
        speeds[k] = 0f
        drops[k] = 0f
        staminas[k] = SWIM_STAMINA
        runs[k] = 0
        // where it is brought back to without a village: where it first stood (put down in the air, where it lands)
        startXs[k] = x
        startYs[k] = fy
        stood[k] = bearsAt(x, fy + 1)
        rides[k] = 0
        faintCounts[k] = 0
        causes[k] = Cause.NONE
        return ids[k]
    }

    /** The village, at the ground under ([x], [y]). */
    fun setHome(x: Int, y: Int) {
        var gy = y.coerceIn(HEIGHT - 1, h - 2)
        while (gy < h - 2 && !bearsAt(x, gy + 1)) gy++
        while (gy > HEIGHT - 1 && !free(x, gy)) gy--
        homeX = x.coerceIn(0, w - 1)
        homeY = gy
    }

    fun clear() {
        count = 0
        homeX = -1
        homeY = -1
    }

    private fun grow() {
        cap *= 2
        ids = ids.copyOf(cap)
        xs = xs.copyOf(cap)
        ys = ys.copyOf(cap)
        dirs = dirs.copyOf(cap)
        states = states.copyOf(cap)
        timers = timers.copyOf(cap)
        falls = falls.copyOf(cap)
        speeds = speeds.copyOf(cap)
        drops = drops.copyOf(cap)
        staminas = staminas.copyOf(cap)
        runs = runs.copyOf(cap)
        startXs = startXs.copyOf(cap)
        startYs = startYs.copyOf(cap)
        rides = rides.copyOf(cap)
        rideXs = rideXs.copyOf(cap)
        rideYs = rideYs.copyOf(cap)
        faintCounts = faintCounts.copyOf(cap)
        causes = causes.copyOf(cap)
        stood = stood.copyOf(cap)
    }

    // ------------------------------------------------------------------ what the grid is to a figure

    private fun at(x: Int, y: Int) = y * w + x

    /** Cell (x, y) bears a figure: ground, a plank, a body; the bottom of the world too. */
    private fun bearsAt(x: Int, y: Int): Boolean {
        if (y >= h) return true
        if (x < 0 || x >= w || y < 0) return false
        val i = at(x, y)
        return bears[world.mat[i].toInt()] || world.bodyOf(i) != 0
    }

    /** A figure's body fits into column [x] with its feet in row [y]: nothing that bears is in the way. */
    private fun free(x: Int, y: Int): Boolean {
        if (x < 0 || x >= w || y >= h || y - HEIGHT + 1 < 0) return false
        for (r in 0 until HEIGHT) if (bearsAt(x, y - r)) return false
        return true
    }

    /** Cell (x, y) is fearsome to look at: fire, lava, smoke, steam, or anything scorching hot. */
    private fun fearsomeAt(x: Int, y: Int): Boolean {
        if (x < 0 || x >= w || y < 0 || y >= h) return false
        val i = at(x, y)
        return fearsome[world.mat[i].toInt()] || world.temp[i] >= SCORCHING
    }

    private fun liquidAt(x: Int, y: Int): Boolean {
        if (x < 0 || x >= w || y < 0 || y >= h) return false
        return kind[world.mat[at(x, y)].toInt()] == Kind.LIQUID
    }

    /** Rows of liquid in column [x] from row [y] down. */
    private fun liquidDepth(x: Int, y: Int): Int {
        var d = 0
        var yy = maxOf(0, y)
        while (yy < h && liquidAt(x, yy)) {
            d++
            yy++
        }
        return d
    }

    /** In the village: within [VILLAGE_HALF] columns of it and about its height. */
    private fun atHome(k: Int): Boolean =
        homeX >= 0 && xs[k] in homeX - VILLAGE_HALF..homeX + VILLAGE_HALF && ys[k] in homeY - VILLAGE_RISE..homeY + VILLAGE_RISE

    // ------------------------------------------------------------------ one step

    /** Bodies that carried figures in the last step: their load is taken off again before it is laid on anew. */
    private var loaded = IntArray(8)
    private var loadedCount = 0

    internal fun step() {
        val step = world.step
        for (k in 0 until count) {
            when (states[k]) {
                FAINT -> {
                    if (--timers[k] <= 0) bringBack(k)
                }
                BACK -> {
                    if (--timers[k] <= 0) states[k] = WALK
                }
                else -> live(k, step)
            }
        }
        weigh()
    }

    private fun live(k: Int, step: Int) {
        val rode = rides[k]
        ride(k)
        if (hurt(k)) return
        val x = xs[k]
        val y = ys[k]
        // up to the chest in a liquid: swimming (or sinking, in tar)
        val wet = liquidDepth(x, y - HEIGHT + 2)
        if (states[k] != SWIM && states[k] != SINK && wet >= SWIM_DEPTH && !bearsAt(x, y + 1)) {
            if (states[k] == FALL && falls[k] >= FAINT_FALL_WATER) return faint(k, Cause.FALL)
            states[k] = if (swimmable[world.mat[at(x, y)].toInt()]) SWIM else SINK
            timers[k] = 0
            falls[k] = 0
            speeds[k] = 0f
            if (states[k] == SWIM) did[Did.SWIM]++
        }
        when (states[k]) {
            WALK -> walk(k, step, rode)
            FALL -> fall(k)
            SWIM -> swim(k, step)
            SINK -> sink(k)
        }
        if (runs[k] > 0) runs[k]--
    }

    /** Hurt where it stands: hit by a body on the move, in fire or lava, buried. Returns whether it fainted. */
    private fun hurt(k: Int): Boolean {
        val x = xs[k]
        val y = ys[k]
        var blocked = 0
        for (r in 0 until HEIGHT) {
            val yy = y - r
            if (yy < 0) continue
            val i = at(x, yy)
            val b = world.bodyOf(i)
            if (b != 0) {
                // a body came into it: one on the move, or one that came down over its head (and may have just come
                // to a stop on it), hits it; one that only rises under its feet (a raft bobbing) it stands up on
                val body = world.bodies.body(b)
                if (body != null && !body.asleep &&
                    (body.vx * body.vx + body.vy * body.vy >= HIT_SPEED * HIT_SPEED || body.y0 <= y - HEIGHT + 1)
                ) {
                    did[Did.HIT]++
                    faint(k, Cause.HIT)
                    return true
                }
            }
            if (fearsomeAt(x, yy) && !(kind[world.mat[i].toInt()] == Kind.GAS && !mt.spawnBurning[world.mat[i].toInt()] && world.temp[i] < SCORCHING)) {
                faint(k, Cause.HEAT)
                return true
            }
            if (bearsAt(x, yy)) blocked++
        }
        if (blocked > 0) {
            // something came down on it or pushed in: it stands up out of it if it can, else it is buried
            for (up in 1..PLACE_LIFT) {
                if (free(x, y - up)) {
                    ys[k] = y - up
                    return false
                }
            }
            faint(k, Cause.BURIED)
            return true
        }
        return false
    }

    private fun walk(k: Int, step: Int, rode: Int) {
        val x = xs[k]
        var y = ys[k]
        // on a raft that bobbed down a little: it stays on it
        if (rode != 0 && !bearsAt(x, y + 1) && y + 2 < h && world.bodyOf(at(x, y + 2)) == rode && free(x, y + 1)) {
            y++
            ys[k] = y
        }
        if (!bearsAt(x, y + 1)) {
            if (liquidAt(x, y + 1)) {
                // wading into deeper water: it goes on as long as it can stand
                ys[k] = y + 1
                return
            }
            states[k] = FALL
            falls[k] = 0
            speeds[k] = 0f
            drops[k] = 0f
            return
        }
        // something coming at it: it runs the other way
        if ((step + ids[k]) % DODGE_EVERY == 0) {
            val from = threat(k)
            if (from != 0) {
                if (dirs[k] == from) did[Did.DODGE]++
                dirs[k] = -from
                runs[k] = RUN_STEPS
            }
        }
        // what it sees ahead
        if ((step + ids[k]) % LOOK_EVERY == 0 && sees(k)) {
            dirs[k] = -dirs[k]
            did[Did.TURN]++
        }
        val every = if (runs[k] > 0) RUN_EVERY else WALK_EVERY
        if ((step + ids[k]) % every != 0) return
        if (!stride(k)) dirs[k] = -dirs[k]
    }

    /** One step in its direction: on, up a step, or down off an edge it need not fear. Returns whether it went. */
    private fun stride(k: Int): Boolean {
        val x = xs[k]
        val y = ys[k]
        val nx = x + dirs[k]
        if (nx < 1 || nx > w - 2) return false
        // it stays in the village once it got there
        if (atHome(k) && (nx < homeX - VILLAGE_HALF || nx > homeX + VILLAGE_HALF)) return false
        for (up in 0..CLIMB) {
            val ny = y - up
            if (!free(nx, ny)) continue
            if (up > 0) {
                // room above its head to climb
                var room = true
                for (r in 1..up) if (bearsAt(x, y - HEIGHT + 1 - r)) room = false
                if (!room || !bearsAt(nx, ny + 1)) return false
                xs[k] = nx
                ys[k] = ny
                did[Did.CLIMB]++
                return true
            }
            if (bearsAt(nx, ny + 1)) {
                // level ground, perhaps under shallow water; water too deep to wade it turns back from
                if (liquidDepth(nx, ny - HEIGHT + 2) >= SWIM_DEPTH) return deepWater(k)
                xs[k] = nx
                return true
            }
            // an edge: how far down, and onto what
            var d = 0
            var yy = ny + 1
            while (yy < h && !bearsAt(nx, yy) && !liquidAt(nx, yy)) {
                d++
                yy++
            }
            if (yy >= h || d >= ABYSS) {
                did[Did.ABYSS]++
                return false
            }
            if (liquidAt(nx, yy) && liquidDepth(nx, yy) >= SWIM_DEPTH) return deepWater(k)
            xs[k] = nx
            return true
        }
        return false
    }

    private fun deepWater(k: Int): Boolean {
        did[Did.DEEP]++
        return false
    }

    /** Fire, lava, smoke or anything scorching within [SIGHT] cells ahead, as far as nothing blocks the view. */
    private fun sees(k: Int): Boolean {
        val x = xs[k]
        val y = ys[k]
        val d = dirs[k]
        for (r in -1 until HEIGHT + 1) {
            val yy = y - r
            for (s in 1..SIGHT) {
                val xx = x + d * s
                if (xx < 0 || xx >= w || yy < 0 || yy >= h) break
                if (fearsomeAt(xx, yy)) {
                    did[Did.SEE]++
                    return true
                }
                if (bearsAt(xx, yy)) break
            }
        }
        return false
    }

    /**
     * A body on the move that would hit it: falling from above over its column, or rolling or sliding at it along
     * the ground. Returns the side it comes from (-1 left, 1 right), 0 for none.
     */
    private fun threat(k: Int): Int {
        val x = xs[k]
        val y = ys[k]
        var from = 0
        world.bodies.each { b ->
            if (from != 0 || b.asleep) return@each
            val v2 = b.vx * b.vx + b.vy * b.vy
            if (v2 < DODGE_SPEED * DODGE_SPEED) return@each
            val side = if (b.px > x) 1 else -1
            // from above: over its head and coming down towards it
            if (b.vy > 0f && b.y1 < y - HEIGHT + 1 && y - b.y1 < DODGE_ABOVE && x >= b.x0 - DODGE_MARGIN && x <= b.x1 + DODGE_MARGIN) {
                from = side
                return@each
            }
            // along the ground: at its height and coming its way
            if (b.y1 >= y - HEIGHT + 1 && b.y0 <= y + 1) {
                val gap = if (side > 0) b.x0 - x else x - b.x1
                if (gap in 0..DODGE_AHEAD && b.vx * side < -DODGE_SPEED / 2f) from = side
            }
        }
        return from
    }

    private fun fall(k: Int) {
        speeds[k] = minOf(MAX_FALL, speeds[k] + GRAVITY)
        drops[k] += speeds[k]
        while (drops[k] >= 1f) {
            drops[k] -= 1f
            val x = xs[k]
            val y = ys[k]
            if (bearsAt(x, y + 1)) {
                land(k)
                return
            }
            if (liquidAt(x, y + 1) && liquidDepth(x, y + 1) >= SWIM_DEPTH) {
                // into deep water: it goes under a little and comes up swimming
                ys[k] = y + 1
                falls[k]++
                return
            }
            ys[k] = y + 1
            falls[k]++
            if (ys[k] >= h - 1) {
                land(k)
                return
            }
        }
        if (bearsAt(xs[k], ys[k] + 1)) land(k)
    }

    private fun land(k: Int) {
        val fell = falls[k]
        falls[k] = 0
        speeds[k] = 0f
        drops[k] = 0f
        if (fell >= FAINT_FALL) {
            did[Did.FALL_HURT]++
            faint(k, Cause.FALL)
            return
        }
        states[k] = WALK
        if (!stood[k]) {
            stood[k] = true
            startXs[k] = xs[k]
            startYs[k] = ys[k]
        }
    }

    private fun swim(k: Int, step: Int) {
        val x = xs[k]
        val y = ys[k]
        // the head above the surface: the top of the liquid in its column
        var top = y
        while (top > 0 && liquidAt(x, top - 1)) top--
        if (!liquidAt(x, top)) {
            // the water is gone round it
            states[k] = if (bearsAt(x, y + 1)) WALK else FALL
            return
        }
        val want = top + HEIGHT - 2
        if (y > want && free(x, y - 1)) ys[k] = y - 1
        else if (y < want && !bearsAt(x, y + 1)) ys[k] = y + 1
        if (--staminas[k] <= 0) {
            states[k] = SINK
            timers[k] = 0
            did[Did.TIRED]++
            return
        }
        if ((step + ids[k]) % SWIM_EVERY != 0) return
        val nx = x + dirs[k]
        if (nx < 1 || nx > w - 2) {
            dirs[k] = -dirs[k]
            return
        }
        val yy = ys[k]
        // out onto the shore: ground at about its height
        for (up in 0..CLIMB + 1) {
            val ny = yy - up
            if (free(nx, ny) && bearsAt(nx, ny + 1) && liquidDepth(nx, ny - HEIGHT + 2) < SWIM_DEPTH) {
                xs[k] = nx
                ys[k] = ny
                states[k] = WALK
                staminas[k] = SWIM_STAMINA
                return
            }
        }
        if (free(nx, yy)) xs[k] = nx else dirs[k] = -dirs[k]
    }

    private fun sink(k: Int) {
        timers[k]++
        val x = xs[k]
        val y = ys[k]
        if (timers[k] % SINK_EVERY == 0 && !bearsAt(x, y + 1) && liquidAt(x, y + 1)) ys[k] = y + 1
        if (timers[k] >= SINK_STEPS) faint(k, Cause.DROWN)
        else if (!liquidAt(x, y - HEIGHT + 2) && bearsAt(x, y + 1)) {
            // shallow enough to stand after all
            states[k] = WALK
            staminas[k] = SWIM_STAMINA
        }
    }

    /** It faints where it is, lies still a moment and is then brought back (7a: no one dies). */
    private fun faint(k: Int, cause: Int) {
        states[k] = FAINT
        timers[k] = FAINT_STEPS
        causes[k] = cause
        faintCounts[k]++
        falls[k] = 0
        speeds[k] = 0f
        drops[k] = 0f
        runs[k] = 0
        rides[k] = 0
        did[Did.FAINT]++
    }

    /** Back to the village, or to where it was put down: on the ground there, its breath back, looking on. */
    private fun bringBack(k: Int) {
        val tx = if (homeX >= 0) homeX else startXs[k]
        val ty = if (homeX >= 0) homeY else startYs[k]
        var x = tx.coerceIn(1, w - 2)
        var y = ty.coerceIn(HEIGHT - 1, h - 2)
        // a little to the side where the place itself is taken
        var found = false
        for (off in 0..BACK_SEARCH) {
            for (sx in if (off == 0) intArrayOf(0) else intArrayOf(off, -off)) {
                val cx = (tx + sx).coerceIn(1, w - 2)
                var cy = y
                var up = 0
                while (!free(cx, cy) && up < PLACE_LIFT) {
                    cy--
                    up++
                }
                if (free(cx, cy)) {
                    x = cx
                    y = cy
                    found = true
                    break
                }
            }
            if (found) break
        }
        xs[k] = x
        ys[k] = y
        states[k] = BACK
        timers[k] = BACK_STEPS
        staminas[k] = SWIM_STAMINA
        rides[k] = 0
        did[Did.BACK]++
    }

    // ------------------------------------------------------------------ carried by bodies (bridges, rafts)

    /**
     * Standing on a body that moves (a raft on the water, a plank that slides), it goes along with it: the point it
     * stands on is kept in the body's own frame and found again after the body moved.
     */
    private fun ride(k: Int) {
        val id = rides[k]
        rides[k] = 0
        if (id == 0 || states[k] != WALK) return
        val b = world.bodies.body(id) ?: return
        val wx = b.px + b.cs * rideXs[k] - b.sn * rideYs[k]
        val wy = b.py + b.sn * rideXs[k] + b.cs * rideYs[k]
        val nx = Math.round(wx)
        val ny = Math.round(wy) - 1
        if (nx == xs[k] && ny == ys[k]) return
        if (nx < 1 || nx > w - 2) return
        for (up in 0..CLIMB) {
            if (free(nx, ny - up)) {
                xs[k] = nx
                ys[k] = ny - up
                return
            }
        }
    }

    /** Where a figure now stands on a body, its weight goes on it (a raft too small for two sinks deeper). */
    private fun weigh() {
        for (j in 0 until loadedCount) {
            val b = world.bodies.body(loaded[j]) ?: continue
            b.folkMass = 0f
            b.folkMoment = 0f
        }
        val before = loadedCount
        val old = loaded.copyOf(before)
        loadedCount = 0
        for (k in 0 until count) {
            if (states[k] != WALK && states[k] != BACK) continue
            val x = xs[k]
            val y = ys[k]
            if (y + 1 >= h) continue
            val id = world.bodyOf(at(x, y + 1))
            if (id == 0) continue
            val b = world.bodies.body(id) ?: continue
            // the point it stands on, in the body's frame
            val dx = x - b.px
            val dy = (y + 1) - b.py
            rides[k] = id
            rideXs[k] = b.cs * dx + b.sn * dy
            rideYs[k] = -b.sn * dx + b.cs * dy
            if (b.folkMass == 0f) {
                if (loadedCount == loaded.size) loaded = loaded.copyOf(loadedCount * 2)
                loaded[loadedCount++] = id
            }
            b.folkMass += FIGURE_MASS
            b.folkMoment += FIGURE_MASS * dx
        }
        // a body that took on a figure or lost one feels it
        for (j in 0 until loadedCount) {
            if (old.indexOf(loaded[j]) < 0) world.bodies.body(loaded[j])?.let { world.bodies.wake(it) }
        }
        for (j in 0 until before) {
            if ((0 until loadedCount).none { loaded[it] == old[j] }) world.bodies.body(old[j])?.let { world.bodies.wake(it) }
        }
    }

    // ------------------------------------------------------------------ undo and fingerprint

    class Saved internal constructor(internal val ints: IntArray, internal val floats: FloatArray, internal val n: Int, internal val home: IntArray)

    private val intFields get() = arrayOf(ids, xs, ys, dirs, states, timers, falls, staminas, runs, startXs, startYs, rides, faintCounts, causes)

    internal fun save(): Saved {
        val n = count
        val ints = IntArray(n * INT_FIELDS)
        val floats = FloatArray(n * FLOAT_FIELDS)
        intFields.forEachIndexed { f, a -> a.copyInto(ints, f * n, 0, n) }
        for (k in 0 until n) ints[INT_FIELDS * n - n + k] = if (stood[k]) 1 else 0
        floatFields.forEachIndexed { f, a -> a.copyInto(floats, f * n, 0, n) }
        return Saved(ints, floats, n, intArrayOf(homeX, homeY, nextId))
    }

    internal fun restore(s: Saved) {
        while (cap < s.n) grow()
        count = s.n
        intFields.forEachIndexed { f, a -> s.ints.copyInto(a, 0, f * s.n, (f + 1) * s.n) }
        for (k in 0 until s.n) stood[k] = s.ints[INT_FIELDS * s.n - s.n + k] == 1
        floatFields.forEachIndexed { f, a -> s.floats.copyInto(a, 0, f * s.n, (f + 1) * s.n) }
        homeX = s.home[0]
        homeY = s.home[1]
        nextId = s.home[2]
        loadedCount = 0
    }
    private val floatFields get() = arrayOf(speeds, drops, rideXs, rideYs)


    internal fun hash(mix: (Int) -> Unit) {
        mix(count)
        mix(homeX)
        mix(homeY)
        for (k in 0 until count) {
            mix(ids[k])
            mix(xs[k])
            mix(ys[k])
            mix(dirs[k])
            mix(states[k])
            mix(timers[k])
            mix(staminas[k])
            mix(faintCounts[k])
        }
    }

    /** Why a figure fainted. */
    object Cause {
        const val NONE = 0
        const val FALL = 1
        const val HIT = 2
        const val DROWN = 3
        const val BURIED = 4
        const val HEAT = 5
    }

    /** Rules that acted, counted in [did]. */
    object Did {
        const val TURN = 0
        const val SEE = 1
        const val ABYSS = 2
        const val DEEP = 3
        const val CLIMB = 4
        const val DODGE = 5
        const val HIT = 6
        const val SWIM = 7
        const val TIRED = 8
        const val FALL_HURT = 9
        const val FAINT = 10
        const val BACK = 11
        const val COUNT = 12
    }

    companion object {
        /** A figure is this many cells tall, one cell wide. */
        const val HEIGHT = 4

        /** It climbs steps this high. */
        const val CLIMB = 2

        // states
        const val WALK = 0
        const val FALL = 1
        const val SWIM = 2
        const val SINK = 3
        const val FAINT = 4
        const val BACK = 5

        /** At most this many figures. */
        const val MAX = 200

        /** A cell a step at walking pace every this many steps (7.5 cells a second), running every this many. */
        const val WALK_EVERY = 8
        const val RUN_EVERY = 3
        const val RUN_STEPS = 90
        const val SWIM_EVERY = 12

        /** Falling: cells a step more each step, at most this fast; from this high it faints (three times its height). */
        const val GRAVITY = 0.15f
        const val MAX_FALL = 2.5f
        const val FAINT_FALL = 12

        /** Into deep water it falls from this high without harm. */
        const val FAINT_FALL_WATER = 40

        /** A drop this deep it does not walk off; liquid this deep (from the chest) it swims in and does not wade into. */
        const val ABYSS = 9
        const val SWIM_DEPTH = 3

        /** It sees this far ahead, and looks every this many steps. */
        const val SIGHT = 10
        const val LOOK_EVERY = 4

        /** This hot is scorching: seen it turns back, in it it faints (°C). */
        const val SCORCHING = 90f

        /** Bodies faster than this (cells a step) hit; faster than this it runs from them, seen this far off. */
        const val HIT_SPEED = 0.2f
        const val DODGE_SPEED = 0.12f
        const val DODGE_EVERY = 4
        const val DODGE_ABOVE = 30
        const val DODGE_AHEAD = 14
        const val DODGE_MARGIN = 2

        /** Steps of swimming before it tires, and steps under water before it faints. */
        const val SWIM_STAMINA = 360
        const val SINK_EVERY = 8
        const val SINK_STEPS = 90

        /** Steps it lies fainted, and steps it takes to come back in the village. */
        const val FAINT_STEPS = 150
        const val BACK_STEPS = 45

        /** The village reaches this far to either side and up or down. */
        const val VILLAGE_HALF = 10
        const val VILLAGE_RISE = 8

        /** Weight of a figure on a body, as cells of water. */
        const val FIGURE_MASS = 4f

        private const val PLACE_LIFT = 12
        private const val BACK_SEARCH = 12
        private const val INT_FIELDS = 15
        private const val FLOAT_FIELDS = 4
    }
}
