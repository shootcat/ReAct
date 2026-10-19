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
 * It needs warmth, water and air (5b, see [needs]): in the cold it freezes, walks slower and at last faints; a fire
 * it sees it goes up to and warms itself at; with no fire near it kindles firewood at a flame that is already there,
 * never from nothing; thirsty, it drinks fresh water it comes to, never salt water; in smoke or firedamp it runs for
 * clean air. These are its only doings of its own: the player can use them, they never come by chance and never
 * solve a puzzle by themselves.
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

    /** Fearsome to see but giving no warmth: smoke and steam. */
    private val murky = BooleanArray(mt.count) { m ->
        val id = mt.spec(m).id
        id == "smoke" || id == "black_smoke" || id == "steam"
    }

    /** Air one cannot breathe (5b): smoke, firedamp, the gas oil gives off. */
    private val foul = BooleanArray(mt.count) { m ->
        val id = mt.spec(m).id
        id == "smoke" || id == "black_smoke" || id == "firedamp" || id == "oil_gas"
    }

    /** Warmth one feels a few cells off (5b): flames, embers, lava, molten metal. */
    private val warming = BooleanArray(mt.count) { m ->
        val id = mt.spec(m).id
        mt.spawnBurning[m] || id == "ember" || id == "lava" || id == "molten_metal" || id == "magma"
    }

    /** Firewood (5b): wood, dead wood, charcoal, withered plants. */
    private val firewood = BooleanArray(mt.count) { m ->
        val id = mt.spec(m).id
        id == "wood" || id == "deadwood" || id == "charcoal" || id == "dead_plant"
    }

    /** A flame one can carry over to firewood: fire and embers (and anything burning). */
    private val flame = BooleanArray(mt.count) { m -> mt.spawnBurning[m] || mt.spec(m).id == "ember" }

    private val waterId = mt.id("water")
    private val saltId = mt.id("saltwater")
    private val blackSmokeId = mt.id("black_smoke")

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

    // needs (5b): cold and thirst count up, breath counts down; steps until it may kindle again; chilled (1): it froze
    // and seeks warmth until it is quite warm again
    private var colds = IntArray(cap)
    private var chills = IntArray(cap)
    private var thirsts = IntArray(cap)
    private var breaths = IntArray(cap)
    private var waits = IntArray(cap)

    // found anew in every step before they are read: the warmth it feels (a material, -1 none) and from which side,
    // foul air at its head
    private var warmBy = IntArray(cap)
    private var warmSide = IntArray(cap)
    private var fouled = BooleanArray(cap)

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
        /** Cold (0 to [COLD_MAX]), thirst (0 to [THIRST_MAX]), breath ([BREATH_MAX] down to 0). */
        val cold: Int,
        val thirst: Int,
        val breath: Int,
    )

    fun figures(): List<Figure> = (0 until count).map { k ->
        Figure(ids[k], xs[k], ys[k], dirs[k], states[k], faintCounts[k], causes[k], atHome(k), staminas[k], colds[k], thirsts[k], breaths[k])
    }

    /** For drawing: position (feet), direction, state and timer of figure [k] in slot order. */
    fun x(k: Int) = xs[k]
    fun y(k: Int) = ys[k]
    fun dir(k: Int) = dirs[k]
    fun state(k: Int) = states[k]
    fun timer(k: Int) = timers[k]
    fun id(k: Int) = ids[k]

    /** What figure [k] lacks, to show over its head: [Need] bits. */
    fun needs(k: Int): Int {
        var n = 0
        if (states[k] == FAINT || states[k] == BACK) return 0
        if (chills[k] != 0) n = n or Need.COLD
        if (colds[k] >= COLD_SLOW) n = n or Need.FROZEN
        if (thirsts[k] >= THIRSTY) n = n or Need.THIRST
        if (fouled[k] || breaths[k] < BREATH_SHOW) n = n or Need.AIR
        return n
    }

    /** Sets the needs of the figure with [id] (for scenes and tests); -1 leaves one as it is. */
    fun setNeeds(id: Int, cold: Int = -1, thirst: Int = -1, breath: Int = -1) {
        for (k in 0 until count) if (ids[k] == id) {
            if (cold >= 0) {
                colds[k] = cold.coerceAtMost(COLD_MAX)
                chills[k] = if (colds[k] >= COLD_SHOW) 1 else 0
            }
            if (thirst >= 0) thirsts[k] = thirst.coerceAtMost(THIRST_MAX)
            if (breath >= 0) breaths[k] = breath.coerceAtMost(BREATH_MAX)
        }
    }

    /** Turns the figure with [id] to look left (-1) or right (1). */
    fun face(id: Int, dir: Int) {
        for (k in 0 until count) if (ids[k] == id) dirs[k] = if (dir < 0) -1 else 1
    }

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
        colds[k] = 0
        chills[k] = 0
        thirsts[k] = 0
        breaths[k] = BREATH_MAX
        waits[k] = 0
        warmBy[k] = -1
        warmSide[k] = 0
        fouled[k] = false
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
        colds = colds.copyOf(cap)
        chills = chills.copyOf(cap)
        thirsts = thirsts.copyOf(cap)
        breaths = breaths.copyOf(cap)
        waits = waits.copyOf(cap)
        warmBy = warmBy.copyOf(cap)
        warmSide = warmSide.copyOf(cap)
        fouled = fouled.copyOf(cap)
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
        if (needs(k, step)) return
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
            WARM -> warm(k, step)
            KINDLE -> kneel(k)
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
                if (dirs[k] == from) {
                    did[Did.DODGE]++
                    world.note(Seen.FOLK_DODGE, threatOf)
                }
                dirs[k] = -from
                runs[k] = RUN_STEPS
            }
        }
        // foul air at its head for a while: it runs towards the nearer clean air, as long as it is in it
        if (fouled[k] && breaths[k] <= BREATH_MAX - FLEE_AFTER && (step + ids[k]) % DODGE_EVERY == 0) {
            val to = clean(k)
            if (to != 0 && to != dirs[k]) {
                dirs[k] = to
                did[Did.FLEE]++
            }
            runs[k] = maxOf(runs[k], FLEE_RUN)
        }
        // freezing and it feels warmth: it stops and warms itself, facing the fire
        val chilled = chills[k] != 0
        if (chilled && warmBy[k] >= 0 && runs[k] == 0) {
            if (warmSide[k] != 0) dirs[k] = warmSide[k]
            states[k] = WARM
            return
        }
        // freezing, it looks out for the glow of a fire on either side and turns towards it
        if (chilled && runs[k] == 0 && (step + ids[k]) % LOOK_EVERY == 0) {
            val to = glow(k)
            if (to != 0 && to != dirs[k]) {
                dirs[k] = to
                did[Did.TURN]++
            }
        }
        // what it sees ahead; out of breath it runs through smoke and steam rather than back into them
        if ((step + ids[k]) % LOOK_EVERY == 0) {
            val d = sees(k, murk = breaths[k] >= BREATH_MAX)
            if (chilled && sawFire > 0) {
                // freezing, it goes up to a fire, past its smoke, and warms itself a little way off
                if (sawFire <= WARM_STOP) {
                    states[k] = WARM
                    return
                }
            } else if (d > 0) {
                dirs[k] = -dirs[k]
                did[Did.TURN]++
                world.note(Seen.FOLK_TURN, sawWhat)
            }
        }
        // freezing with no fire near: it kindles firewood at a flame
        if (chilled && warmBy[k] < 0 && waits[k] == 0 && (step + ids[k]) % LOOK_EVERY == 0 && kindle(k)) return
        val every = if (runs[k] > 0) RUN_EVERY else if (colds[k] >= COLD_SLOW) COLD_WALK_EVERY else WALK_EVERY
        if ((step + ids[k]) % every != 0) return
        if (!stride(k)) dirs[k] = -dirs[k]
    }

    /** At a fire, warming itself: until it is warm, the fire is out or comes too near, or a body comes at it. */
    private fun warm(k: Int, step: Int) {
        if (!bearsAt(xs[k], ys[k] + 1) || colds[k] == 0) {
            states[k] = WALK
            return
        }
        if ((step + ids[k]) % DODGE_EVERY == 0) {
            val from = threat(k)
            if (from != 0) {
                did[Did.DODGE]++
                dirs[k] = -from
                runs[k] = RUN_STEPS
                states[k] = WALK
                return
            }
        }
        // it keeps an eye on the fire every step
        sees(k, murk = true)
        if (sawFire in 1..WARM_CLOSE) {
            // flames or hot air came up to it: back at running pace, still facing the fire; with no room behind, it goes
            if ((step + ids[k]) % RUN_EVERY != 0) return
            if (free(xs[k] - dirs[k], ys[k]) && bearsAt(xs[k] - dirs[k], ys[k] + 1)) {
                xs[k] -= dirs[k]
            } else {
                dirs[k] = -dirs[k]
                states[k] = WALK
            }
        } else if (warmBy[k] < 0 && (sawFire == 0 || sawFire > WARM_STOP)) {
            // the fire is out, or drew back (it goes after it)
            states[k] = WALK
        }
    }

    /** Kneeling at the firewood it kindled; then it warms itself at it (or steps back at once if the fire comes at it). */
    private fun kneel(k: Int) {
        if (!bearsAt(xs[k], ys[k] + 1)) {
            states[k] = WALK
            return
        }
        sees(k, murk = true)
        if (--timers[k] <= 0 || sawFire in 1..WARM_CLOSE) states[k] = WARM
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

    /**
     * What [sees] saw last: the material of the nearest fearsome thing, and how far off the nearest sign of a fire is
     * (0: none): flames, embers, anything burning or glowing, and the hot air round a fire.
     */
    private var sawWhat = 0
    private var sawFire = 0

    /** How far off [sees] saw flames, embers or anything burning or glowing itself (0: none). */
    private var sawFlame = 0

    /**
     * Fire, lava, smoke or anything scorching within [SIGHT] cells ahead, as far as nothing blocks the view (smoke
     * and hot air do not): the distance to the nearest (0: nothing). With [murk] false smoke and steam do not count.
     * It also looks for the nearest sign of a fire ([sawFire]), which a freezing figure goes up to.
     */
    private fun sees(k: Int, murk: Boolean): Int {
        val x = xs[k]
        val y = ys[k]
        val d = dirs[k]
        var best = 0
        sawFire = 0
        sawFlame = 0
        for (r in -1 until HEIGHT + 1) {
            val yy = y - r
            for (s in 1..SIGHT) {
                val xx = x + d * s
                if (xx < 0 || xx >= w || yy < 0 || yy >= h) break
                val i = at(xx, yy)
                val m = world.mat[i].toInt()
                if (fearsomeAt(xx, yy) && (murk || !murky[m]) && (best == 0 || s < best)) {
                    best = s
                    sawWhat = m
                }
                val t = world.temp[i]
                val lit = flame[m] || world.flags[i].toInt() and Flag.BURNING != 0 || t >= GLOWING
                if (lit || (kind[m] == Kind.GAS && !murky[m] && t >= SCORCHING)) {
                    if (sawFire == 0 || s < sawFire) sawFire = s
                    if (lit) {
                        if (sawFlame == 0 || s < sawFlame) sawFlame = s
                        break
                    }
                }
                if (bearsAt(xx, yy)) break
            }
        }
        if (best > 0) did[Did.SEE]++
        return best
    }

    /** The material of the body [threat] found last. */
    private var threatOf = 0

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
                threatOf = b.main
                return@each
            }
            // along the ground: at its height and coming its way
            if (b.y1 >= y - HEIGHT + 1 && b.y0 <= y + 1) {
                val gap = if (side > 0) b.x0 - x else x - b.x1
                if (gap in 0..DODGE_AHEAD && b.vx * side < -DODGE_SPEED / 2f) {
                    from = side
                    threatOf = b.main
                }
            }
        }
        return from
    }

    // ------------------------------------------------------------------ warmth, water and air (5b)

    /**
     * Warmth, water and air, each a count per figure, all by rules without chance. Returns whether it fainted.
     * - Cold rises while the air (or water) in its cells is below [COLD_TEMP], twice as fast below [FREEZING_TEMP];
     *   it falls in milder air, and fast where it feels a fire ([warmth]) or the air round it is [WARM_AIR].
     *   Freezing ([COLD_SHOW]) it shivers and seeks warmth until it is quite warm again, from [COLD_SLOW] it walks
     *   at half pace, at [COLD_MAX] it faints.
     * - Thirst rises every step and falls in the village (it has a well). Thirsty ([THIRSTY]) it drinks fresh water
     *   it comes to ([drink]); at [THIRST_MAX] it faints.
     * - Breath falls while its head is in smoke, firedamp or the gas of oil (twice as fast in black smoke) and comes
     *   back in clean air; it runs for clean air ([clean]); out of breath it faints.
     */
    private fun needs(k: Int, step: Int): Boolean {
        val x = xs[k]
        val y = ys[k]
        if (waits[k] > 0) waits[k]--
        // warmth: the air or water in its cells, and a fire it can feel
        var sum = 0f
        var n = 0
        for (r in 0 until HEIGHT) {
            val yy = y - r
            if (yy in 0 until h) {
                sum += world.temp[at(x, yy)]
                n++
            }
        }
        val felt = if (n > 0) sum / n else world.ambient
        val hot = warmth(k).let { if (it < 0 && felt >= WARM_AIR) MaterialTable.AIR else it }
        warmBy[k] = hot
        val was = colds[k]
        colds[k] = when {
            hot >= 0 -> maxOf(0, was - WARM_RATE)
            felt < FREEZING_TEMP -> was + 2
            felt < COLD_TEMP -> was + 1
            felt >= MILD_TEMP -> maxOf(0, was - 2)
            else -> maxOf(0, was - 1)
        }
        if (hot >= 0 && was >= COLD_SHOW) world.note(Seen.FOLK_WARM, hot)
        if (hot >= 0 && was > 0 && colds[k] == 0) did[Did.WARMED]++
        if (was < COLD_SHOW && colds[k] >= COLD_SHOW) {
            did[Did.COLD]++
            world.note(Seen.FOLK_COLD, world.mat[at(x, maxOf(0, y - HEIGHT + 1))].toInt())
        }
        if (colds[k] >= COLD_SHOW) chills[k] = 1 else if (colds[k] == 0) chills[k] = 0
        if (colds[k] >= COLD_MAX) {
            faint(k, Cause.COLD)
            return true
        }
        // water
        if (atHome(k)) thirsts[k] = maxOf(0, thirsts[k] - WELL) else thirsts[k]++
        if (thirsts[k] >= THIRSTY && (step + ids[k]) % LOOK_EVERY == 0) drink(k)
        if (thirsts[k] >= THIRST_MAX) {
            faint(k, Cause.THIRST)
            return true
        }
        // air: the two upper cells, head and chest
        var bad = -1
        for (r in HEIGHT - 2 until HEIGHT) {
            val yy = y - r
            if (yy < 0) continue
            val m = world.mat[at(x, yy)].toInt()
            if (foul[m]) {
                bad = m
                break
            }
        }
        val before = fouled[k]
        fouled[k] = bad >= 0
        if (bad >= 0) {
            breaths[k] -= if (bad == blackSmokeId) 2 else 1
            if (!before) {
                did[Did.CHOKE]++
                world.note(Seen.FOLK_CHOKE, bad)
            }
            if (breaths[k] <= 0) {
                faint(k, Cause.AIR)
                return true
            }
            if ((states[k] == WARM || states[k] == KINDLE) && breaths[k] <= BREATH_MAX - FLEE_AFTER) states[k] = WALK
        } else {
            breaths[k] = minOf(BREATH_MAX, breaths[k] + BREATH_BACK)
        }
        return false
    }

    /**
     * Warmth it feels: flames, embers, lava, anything burning or glowing within [WARM_REACH] cells to either side,
     * from the row under its feet to the row over its head, as far as nothing blocks it. The material of the
     * nearest, or -1; its side goes to [warmSide].
     */
    private fun warmth(k: Int): Int {
        val x = xs[k]
        val y = ys[k]
        var best = WARM_REACH + 1
        var what = -1
        warmSide[k] = 0
        for (side in 0..1) {
            val d = if (side == 0) dirs[k] else -dirs[k]
            for (r in -1..HEIGHT) {
                val yy = y - r
                if (yy < 0 || yy >= h) continue
                for (s in 1 until best) {
                    val xx = x + d * s
                    if (xx < 0 || xx >= w) break
                    val i = at(xx, yy)
                    val m = world.mat[i].toInt()
                    if (warming[m] || world.flags[i].toInt() and Flag.BURNING != 0 || world.temp[i] >= GLOWING) {
                        best = s
                        what = m
                        warmSide[k] = d
                        break
                    }
                    if (bearsAt(xx, yy)) break
                }
            }
        }
        return what
    }

    /**
     * The side (-1, 1) of the nearest glow of a fire, flames, embers or anything burning or glowing, within
     * [GLOW_SIGHT] cells along its rows, from under its feet to over its head, as far as nothing blocks the view; its
     * own way first if both are as near. 0 if it sees none.
     */
    private fun glow(k: Int): Int {
        val x = xs[k]
        val y = ys[k]
        var near = GLOW_SIGHT + 1
        var to = 0
        for (side in 0..1) {
            val d = if (side == 0) dirs[k] else -dirs[k]
            for (r in -1..HEIGHT) {
                val yy = y - r
                if (yy < 0 || yy >= h) continue
                for (s in 1 until near) {
                    val xx = x + d * s
                    if (xx < 0 || xx >= w) break
                    val i = at(xx, yy)
                    val m = world.mat[i].toInt()
                    if (flame[m] || world.flags[i].toInt() and Flag.BURNING != 0 || world.temp[i] >= GLOWING) {
                        near = s
                        to = d
                        break
                    }
                    if (bearsAt(xx, yy)) break
                }
            }
        }
        return to
    }

    /** Thirsty, it drinks fresh water at its feet or a cell to either side: that cell of water is gone. Never salt water. */
    private fun drink(k: Int) {
        val x = xs[k]
        val y = ys[k]
        var salt = false
        for (c in 0 until 6) {
            val xx = x + when (c / 2) {
                0 -> 0
                1 -> dirs[k]
                else -> -dirs[k]
            }
            val yy = y + c % 2
            if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue
            val m = world.mat[at(xx, yy)].toInt()
            if (m == waterId) {
                world.set(xx, yy, MaterialTable.AIR, world.ambient)
                thirsts[k] = 0
                did[Did.DRINK]++
                world.note(Seen.FOLK_DRINK, m)
                return
            }
            if (m == saltId) salt = true
        }
        if (salt) {
            did[Did.NO_SALT]++
            world.note(Seen.FOLK_NO_SALT, saltId)
        }
    }

    /**
     * Out of foul air: the side where clean air at head height is nearer, looking along its own rows up to
     * [FLEE_LOOK] cells as far as walls let it; its own way first if both are as near. 0 if there is none.
     */
    private fun clean(k: Int): Int {
        val x = xs[k]
        val y2 = ys[k] - (HEIGHT - 2)
        val y3 = ys[k] - (HEIGHT - 1)
        if (y3 < 0) return 0
        var near = Int.MAX_VALUE
        var to = 0
        for (side in 0..1) {
            val d = if (side == 0) dirs[k] else -dirs[k]
            for (s in 1..FLEE_LOOK) {
                if (s >= near) break
                val xx = x + d * s
                if (xx < 1 || xx > w - 2) break
                val b2 = bearsAt(xx, y2)
                val b3 = bearsAt(xx, y3)
                if (b2 && b3) break
                if (!b2 && !b3 && !foul[world.mat[at(xx, y2)].toInt()] && !foul[world.mat[at(xx, y3)].toInt()]) {
                    near = s
                    to = d
                    break
                }
            }
        }
        return to
    }

    /**
     * Freezing with no fire near, it kindles firewood at a flame that is already there, never from nothing: a flame
     * or embers and firewood, both within [KINDLE_REACH] columns and about its height (the wood beside it, not
     * under its feet, nor anchored ground). It takes the nearest of each and brings the wood to its burning heat;
     * whether it burns (wet wood does not) is the world's affair. Returns whether it did.
     */
    private fun kindle(k: Int): Boolean {
        val x = xs[k]
        val y = ys[k]
        var fire = -1
        var fireD = Int.MAX_VALUE
        var wood = -1
        var woodD = Int.MAX_VALUE
        for (yy in maxOf(0, y - HEIGHT - 1)..minOf(h - 1, y + 1)) {
            for (xx in maxOf(0, x - KINDLE_REACH)..minOf(w - 1, x + KINDLE_REACH)) {
                val i = at(xx, yy)
                val m = world.mat[i].toInt()
                val f = world.flags[i].toInt()
                val d = Math.abs(xx - x) + Math.abs(yy - y)
                if (flame[m] || f and Flag.BURNING != 0) {
                    if (d < fireD) {
                        fire = i
                        fireD = d
                    }
                } else if (firewood[m] && yy <= y && f and Flag.ANCHORED == 0 && world.temp[i] < mt.igniteTemp[m]) {
                    if (d < woodD) {
                        wood = i
                        woodD = d
                    }
                }
            }
        }
        if (fire < 0 || wood < 0) return false
        val wx = wood % w
        val wy = wood / w
        val m = world.mat[wood].toInt()
        world.setTemperature(wx, wy, maxOf(world.temp[wood], mt.igniteTemp[m] + KINDLE_OVER))
        world.wake(wx, wy)
        if (wx != x) dirs[k] = if (wx > x) 1 else -1
        states[k] = KINDLE
        timers[k] = KINDLE_STEPS
        waits[k] = KINDLE_PAUSE
        did[Did.KINDLE]++
        world.note(Seen.FOLK_KINDLE, m)
        return true
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
            world.note(Seen.FOLK_FALL, if (ys[k] + 1 < h) world.mat[at(xs[k], ys[k] + 1)].toInt() else MaterialTable.AIR)
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
            world.note(Seen.FOLK_TIRED, world.mat[at(x, ys[k])].toInt())
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
        // looked after in the village: warm, with water and breath again
        colds[k] = 0
        chills[k] = 0
        thirsts[k] = 0
        breaths[k] = BREATH_MAX
        waits[k] = 0
        fouled[k] = false
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

    private val intFields get() = arrayOf(ids, xs, ys, dirs, states, timers, falls, staminas, runs, startXs, startYs, rides, faintCounts, causes, colds, thirsts, breaths, waits, chills)

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
            mix(colds[k])
            mix(thirsts[k])
            mix(breaths[k])
            mix(waits[k])
            mix(chills[k])
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
        const val COLD = 6
        const val THIRST = 7
        const val AIR = 8
    }

    /** What a figure lacks, shown over its head: [needs]. */
    object Need {
        const val COLD = 1
        const val THIRST = 2
        const val AIR = 4

        /** So cold it walks slower. */
        const val FROZEN = 8
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
        const val COLD = 12
        const val WARMED = 13
        const val KINDLE = 14
        const val DRINK = 15
        const val NO_SALT = 16
        const val CHOKE = 17
        const val FLEE = 18
        const val COUNT = 19
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
        const val WARM = 6
        const val KINDLE = 7

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

        /** It sees this far ahead, and looks every this many steps; freezing it sees the glow of a fire this far on either side. */
        const val SIGHT = 10
        const val GLOW_SIGHT = 40
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

        /** Air colder than this (°C) chills it, twice as fast below [FREEZING_TEMP]; from [MILD_TEMP] it warms up quickly. */
        const val COLD_TEMP = 5f
        const val FREEZING_TEMP = -10f
        const val MILD_TEMP = 15f

        /** Cold: from [COLD_SHOW] it freezes (and shows it), from [COLD_SLOW] it walks at half pace, at [COLD_MAX] it faints. */
        const val COLD_SHOW = 600
        const val COLD_SLOW = 1200
        const val COLD_MAX = 2400
        const val COLD_WALK_EVERY = 16

        /** A fire warms it from this many cells off, by this much cold a step; freezing it goes this near, but not nearer than [WARM_CLOSE]. */
        const val WARM_REACH = 6
        const val WARM_RATE = 6
        const val WARM_STOP = 4
        const val WARM_CLOSE = 2

        /** This hot (°C), a cell warms like a fire; air this warm round it warms it as fast. */
        const val GLOWING = 300f
        const val WARM_AIR = 25f

        /** Thirst: from [THIRSTY] (2 minutes) it drinks fresh water it comes to; at [THIRST_MAX] (5 minutes) it faints; the well of the village takes this much a step. */
        const val THIRSTY = 7200
        const val THIRST_MAX = 18000
        const val WELL = 30

        /** Breath: steps of foul air it bears; shown while it is in foul air and below [BREATH_SHOW]; clean air gives back this much a step. */
        const val BREATH_MAX = 480
        const val BREATH_SHOW = 360
        const val BREATH_BACK = 4

        /** After this many steps of foul air it runs for clean air, looking this far for it. */
        const val FLEE_AFTER = 60
        const val FLEE_LOOK = 40

        /** Running from foul air it keeps running this many steps after it last found itself in it. */
        const val FLEE_RUN = 2 * DODGE_EVERY

        /** Kindling: flame and firewood this many columns off at most; it kneels this long, and kindles at most once in [KINDLE_PAUSE] steps. */
        const val KINDLE_REACH = 12
        const val KINDLE_STEPS = 45
        const val KINDLE_PAUSE = 600
        const val KINDLE_OVER = 120f

        private const val PLACE_LIFT = 12
        private const val BACK_SEARCH = 12
        private const val INT_FIELDS = 20
        private const val FLOAT_FIELDS = 4
    }
}
