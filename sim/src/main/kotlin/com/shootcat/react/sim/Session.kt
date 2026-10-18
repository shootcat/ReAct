package com.shootcat.react.sim

/**
 * A world together with what the player does to it (Leitfaden V3, 4.7 and 4.3): painting with a brush,
 * building, the bucket and the hand, undo per action, reset. Single-threaded and deterministic; [SimRunner]
 * drives it from its own thread.
 *
 * Every input is written to [log] with the world step at which it took effect, so the same log played into a
 * new session with the same seed gives exactly the same world ([replay]). Undo and reset go back in world
 * time and cut the log back accordingly: the log is always the straight history of the current world.
 *
 * In [Supply.PROCURE] the player has nothing but what they get out of the world (Leitfaden V3, 4.3): the
 * bucket scoops up loose and liquid material and pours it out elsewhere, the hand carries loose objects,
 * and building uses up the [stock] the hand has brought in.
 */
class Session(
    val materials: MaterialTable,
    val width: Int,
    val height: Int,
    val seed: Long,
    build: (World) -> Unit = {},
) {
    /** How many cells the bucket holds (set per level). */
    var bucketSize = BUCKET_SIZE

    val world = World(width, height, materials, seed).also(build)
    private val start = world.snapshot()

    /** The world before an action, how long the log was then (or the whole log, if the action cleared it), and what the player had. */
    private class UndoEntry(val snapshot: World.Snapshot, val logSize: Int, val savedLog: List<Input>?, val supplies: Supplies)
    private val undoStack = ArrayDeque<UndoEntry>()

    enum class Supply { UNLIMITED, PROCURE }

    /** Unlimited supply as in the sandbox so far, or only what the player procures. */
    var supply = Supply.UNLIMITED
        private set

    /** Building material in store, cells per material. */
    val stock = IntArray(materials.count)

    /** What is in the bucket: one material at a time. */
    var bucketMaterial = MaterialTable.AIR
        private set
    var bucketAmount = 0
        private set
    var bucketTemp = materials.ambient
        private set

    /** What the hand holds, or null. */
    var held: Held? = null
        private set

    /**
     * While procuring (a level, or the sandbox in „nur Beschaffen“) the bucket and the hand only reach what
     * air or water connects to the open sky, and what lies right next to it: a chamber shut off by an ice
     * plug cannot be reached until the plug is gone (Leitfaden V3, 5.4; 3b.5). With unlimited supply the
     * sandbox reaches everywhere.
     */
    val reachLimited get() = supply == Supply.PROCURE

    /** The last thing worth telling the player (picked up, too heavy, …), and a counter that changes with it. */
    var notice = ""
        private set
    var noticeCount = 0
        private set

    /**
     * An object in the hand: its cells relative to where it was picked up, with material, temperature and
     * look. Never changed once made.
     */
    class Held(
        val dx: IntArray,
        val dy: IntArray,
        val mat: ByteArray,
        val temp: FloatArray,
        val variant: ByteArray,
        /** The material most of it is made of. */
        val main: Int = mat.groupBy { it }.maxByOrNull { it.value.size }!!.key.toInt(),
    ) {
        val size get() = dx.size
    }

    /** All the player has, for undo. */
    private class Supplies(
        val supply: Supply,
        val stock: IntArray,
        val bucketMaterial: Int,
        val bucketAmount: Int,
        val bucketTemp: Float,
        val held: Held?,
    )

    private fun supplies() = Supplies(supply, stock.copyOf(), bucketMaterial, bucketAmount, bucketTemp, held)

    /** What the player has at the start and after a reset: unlimited supply in the sandbox. */
    private var initial = Supplies(Supply.UNLIMITED, IntArray(materials.count), MaterialTable.AIR, 0, materials.ambient, null)

    /**
     * Sets what the player starts with, before anything happens: procuring with a store of [stock] (cells
     * per material), as a level does (Leitfaden V3, 5.1). A reset returns to it. Not an action.
     */
    fun begin(supply: Supply, stock: IntArray = IntArray(materials.count)) {
        initial = Supplies(supply, stock.copyOf(), MaterialTable.AIR, 0, materials.ambient, null)
        restoreSupplies(initial)
    }

    /** Called after every step, on the thread that steps (a level checks its goals here). */
    var onStep: ((Session) -> Unit)? = null

    private fun restoreSupplies(s: Supplies) {
        // the world went back in time: the same step may now look different
        reachStep = -1
        supply = s.supply
        s.stock.copyInto(stock)
        bucketMaterial = s.bucketMaterial
        bucketAmount = s.bucketAmount
        bucketTemp = s.bucketTemp
        held = s.held
    }

    /** What the player did, in order. */
    val log = mutableListOf<Input>()

    /** Material number to paint with, or [ERASER]. */
    var tool = materials.idOrNull("sand") ?: 1
        private set
    var radius = 4
        private set

    private var down = false
    private var bx = 0
    private var by = 0

    /** Where the last animal of this stroke was put down (x in the high, y in the low half), or [NO_ANIMAL]. */
    private var lastAnimal = NO_ANIMAL

    val canUndo get() = undoStack.isNotEmpty()
    val isPressed get() = down

    sealed interface Input {
        val step: Int
    }

    data class Press(override val step: Int, val x: Int, val y: Int, val tool: Int, val radius: Int) : Input
    data class Drag(override val step: Int, val x: Int, val y: Int) : Input
    data class Release(override val step: Int) : Input

    /** A built shape: a line, a filled rectangle or a frame (Leitfaden V3, 4.1), as one action. */
    data class Shape(
        override val step: Int,
        val kind: ShapeKind,
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int,
        val tool: Int,
        val size: Int,
    ) : Input

    enum class ShapeKind { LINE, RECT, FRAME }
    data class Clear(override val step: Int) : Input

    /** Switches between unlimited supply and procuring. */
    data class Mode(override val step: Int, val supply: Supply) : Input

    /** The object in the hand goes into the store. */
    data class Store(override val step: Int) : Input

    /**
     * The sandbox's weather changed (3c.6): condensation height (-1: no clouds), wind and gusts, random weather,
     * the temperature of the surroundings.
     */
    data class Weather(
        override val step: Int,
        val cloudBase: Int,
        val wind: Float,
        val gust: Float,
        val random: Boolean,
        val ambient: Float,
    ) : Input

    /** A thunderstorm called up over column [x] (3c.6). */
    data class Storm(override val step: Int, val x: Int) : Input

    /** A finger touches the world: one action begins (and can be undone as a whole). With the hand it picks up or puts down. */
    fun press(x: Int, y: Int, tool: Int, radius: Int) {
        if (down) release()
        pushUndo()
        log += Press(world.step, x, y, tool, radius)
        this.tool = tool
        this.radius = radius
        if (tool == HAND) {
            if (held == null) grab(x, y) else drop(x, y)
            return
        }
        down = true
        bx = x
        by = y
        lastAnimal = NO_ANIMAL
        // what one stroke builds is one piece (Phase 4b): it holds together, and only with itself
        world.startPiece()
        world.grain = World.GRAIN_ALONG
        strokeStart = true
        dab(x, y)
        strokeStart = false
    }

    /** The first dab of a stroke is being made. */
    private var strokeStart = false

    /** The finger moves: paints along the way, so quick strokes stay closed lines. */
    fun drag(x: Int, y: Int) {
        if (!down) return
        log += Drag(world.step, x, y)
        val dx = x - bx
        val dy = y - by
        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        // wood takes the direction of the stroke as its grain (Phase 4b)
        if (dist > 0) world.grain = World.grainOf(dx, dy)
        val stride = maxOf(1, radius / 2)
        var k = stride
        while (k < dist) {
            dab(bx + dx * k / dist, by + dy * k / dist)
            k += stride
        }
        dab(x, y)
        bx = x
        by = y
    }

    fun release() {
        if (!down) return
        log += Release(world.step)
        down = false
        world.endPiece()
    }

    /**
     * Builds a shape with [tool] at once, as one action that can be undone: a line [size] cells thick
     * (radius), a filled rectangle, or a frame [size] + 1 cells wide.
     */
    fun shape(kind: ShapeKind, x0: Int, y0: Int, x1: Int, y1: Int, tool: Int, size: Int) {
        if (down) release()
        pushUndo()
        log += Shape(world.step, kind, x0, y0, x1, y1, tool, size)
        this.tool = tool
        this.radius = size
        world.startPiece()
        world.grain = if (kind == ShapeKind.LINE) World.grainOf(x1 - x0, y1 - y0)
            else if (kotlin.math.abs(x1 - x0) >= kotlin.math.abs(y1 - y0)) World.GRAIN_ALONG else World.GRAIN_ACROSS
        applyShape(kind, x0, y0, x1, y1, tool, size)
        world.endPiece()
    }

    private fun applyShape(kind: ShapeKind, x0: Int, y0: Int, x1: Int, y1: Int, tool: Int, size: Int) {
        val w = world
        if (tool < 0 && tool != ERASER) return
        if (supply == Supply.PROCURE) {
            // only from the store, and no eraser
            if (tool == ERASER || !canBuild(tool)) return
            val n = when (kind) {
                ShapeKind.LINE -> w.paintLine(tool, x0, y0, x1, y1, size, stock[tool])
                ShapeKind.RECT -> w.paintRect(tool, x0, y0, x1, y1, false, 1, stock[tool])
                ShapeKind.FRAME -> w.paintRect(tool, x0, y0, x1, y1, true, size + 1, stock[tool])
            }
            stock[tool] -= n
            return
        }
        if (tool >= 0 && materials.animal[tool]) {
            // animals one by one, well apart: along the line or the frame, spread over the rectangle
            lastAnimal = NO_ANIMAL
            when (kind) {
                ShapeKind.LINE -> Shapes.line(x0, y0, x1, y1) { x, y -> animal(x, y) }
                ShapeKind.FRAME -> for ((a, b) in listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1, x0 to y0).zipWithNext()) {
                    Shapes.line(a.first, a.second, b.first, b.second) { x, y -> animal(x, y) }
                }
                ShapeKind.RECT -> {
                    for (y in minOf(y0, y1)..maxOf(y0, y1) step ANIMAL_SPACING) for (x in minOf(x0, x1)..maxOf(x0, x1) step ANIMAL_SPACING) {
                        w.placeAnimal(tool, x, y)
                    }
                }
            }
            return
        }
        when (kind) {
            ShapeKind.LINE -> if (tool == ERASER) w.eraseLine(x0, y0, x1, y1, size) else w.paintLine(tool, x0, y0, x1, y1, size)
            ShapeKind.RECT -> if (tool == ERASER) w.eraseRect(x0, y0, x1, y1, false, 1) else w.paintRect(tool, x0, y0, x1, y1, false, 1)
            ShapeKind.FRAME -> if (tool == ERASER) w.eraseRect(x0, y0, x1, y1, true, size + 1) else w.paintRect(tool, x0, y0, x1, y1, true, size + 1)
        }
    }

    /** One simulation step; a finger that stays down keeps pouring. What the player carries cools slowly. */
    fun tick() {
        if (down) dab(bx, by)
        cool()
        world.step()
        onStep?.invoke(this)
    }

    // ------------------------------------------------------------------ procuring (4.3)

    /** Sets the weather of the sandbox (3c.6): undoable, and written to the log so it plays back the same. */
    fun setWeather(cloudBase: Int, wind: Float, gust: Float, random: Boolean, ambient: Float) {
        if (down) release()
        pushUndo()
        log += Weather(world.step, cloudBase, wind, gust, random, ambient)
        world.setWeather(cloudBase = cloudBase, wind = wind, gust = gust, random = random)
        if (ambient != world.ambient) world.changeAmbient(ambient)
    }

    /** Calls up a thunderstorm over column [x] (3c.6): a bank of cloud, and lightning for a while. Undoable. */
    fun storm(x: Int) {
        if (down) release()
        pushUndo()
        log += Storm(world.step, x)
        world.callStorm(x)
        tell("Ein Gewitter zieht auf")
    }

    /** Switches the supply (an action of its own, undoable). */
    fun setSupply(mode: Supply) {
        if (mode == supply) return
        if (down) release()
        pushUndo()
        log += Mode(world.step, mode)
        supply = mode
        tell(if (mode == Supply.PROCURE) "Nur Beschaffen: Eimer, Hand und Lager" else "Unendlicher Vorrat")
    }

    /** Whether [m] can be built with now: anything with unlimited supply, only what is in store otherwise. */
    fun canBuild(m: Int): Boolean = supply == Supply.UNLIMITED || (storable(m) && stock[m] > 0)

    /** What goes into the store: solid objects from the hand, loose material from the bucket (3b.5). */
    private fun storable(m: Int) = materials.kind[m] == Kind.SOLID || materials.kind[m] == Kind.POWDER

    /** The bucket takes up loose or liquid material of one kind around (x, y), as much as fits. */
    private fun scoop(cx: Int, cy: Int) {
        val w = world
        if (bucketAmount >= bucketSize) return
        if (bucketAmount > 0 && cx == bx && cy == by && strokeStart) {
            // the first touch of a stroke on something else than the bucket holds: say so
            val i = if (w.inside(cx, cy)) w.index(cx, cy) else -1
            if (i >= 0 && w.mat[i].toInt() != bucketMaterial && bucketable(i)) tell("Im Eimer ist schon ${materials.spec(bucketMaterial).name}")
        }
        val r2 = radius * radius + radius
        // an empty bucket takes what is under the finger, or else the first thing it can take nearby
        if (bucketAmount == 0) {
            bucketMaterial = MaterialTable.AIR
            var best = Int.MAX_VALUE
            var shut = false
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val d = dx * dx + dy * dy
                if (d > r2 || d >= best || !w.inside(cx + dx, cy + dy)) continue
                val i = w.index(cx + dx, cy + dy)
                if (!bucketable(i)) continue
                if (!touchable(i)) {
                    shut = true
                    continue
                }
                best = d
                bucketMaterial = w.mat[i].toInt()
            }
            if (bucketMaterial == MaterialTable.AIR) {
                if (shut) tell(OUT_OF_REACH)
                return
            }
        }
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (bucketAmount >= bucketSize) return
                val x = cx + dx
                val y = cy + dy
                if (dx * dx + dy * dy > r2 || !w.inside(x, y)) continue
                val i = w.index(x, y)
                if (w.mat[i].toInt() != bucketMaterial || !bucketable(i) || !touchable(i)) continue
                bucketTemp = (bucketTemp * bucketAmount + w.temp[i]) / (bucketAmount + 1)
                bucketAmount++
                w.clear(x, y, i)
            }
        }
    }

    /** Loose or liquid, not burning, not part of the level ground. */
    private fun bucketable(i: Int): Boolean {
        val m = world.mat[i].toInt()
        val f = world.flags[i].toInt()
        return materials.bucketable[m] && (f and (Flag.BURNING or Flag.ANCHORED)) == 0 &&
            (materials.kind[m] == Kind.POWDER || materials.kind[m] == Kind.LIQUID)
    }

    private var reach = BooleanArray(0)
    private var reachStep = -1
    private var reachQueue = IntArray(0)

    /** Gas and liquid connected to the open sky through gas and liquid: where the player's arm gets to. */
    private fun reachMap(): BooleanArray {
        val w = world
        if (reachStep == w.step && reach.size == w.size) return reach
        if (reach.size != w.size) {
            reach = BooleanArray(w.size)
            reachQueue = IntArray(w.size)
        } else {
            reach.fill(false)
        }
        val kind = materials.kind
        fun passable(i: Int) = kind[w.mat[i].toInt()].let { it == Kind.GAS || it == Kind.LIQUID }
        var head = 0
        var tail = 0
        for (x in 0 until w.width) if (passable(x)) {
            reach[x] = true
            reachQueue[tail++] = x
        }
        while (head < tail) {
            val i = reachQueue[head++]
            val x = i % w.width
            for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w.width - 1) i + 1 else -1, i - w.width, i + w.width)) {
                if (j < 0 || j >= w.size || reach[j] || !passable(j)) continue
                reach[j] = true
                reachQueue[tail++] = j
            }
        }
        reachStep = w.step
        return reach
    }

    /** Open to the sky (always, without a limited reach). */
    private fun open(i: Int): Boolean = !reachLimited || reachMap()[i]

    /** Open, or right next to an open cell: the surface of a heap, the face of a wall. */
    private fun touchable(i: Int): Boolean {
        if (!reachLimited) return true
        val r = reachMap()
        if (r[i]) return true
        val w = world.width
        val x = i % w
        return (x > 0 && r[i - 1]) || (x < w - 1 && r[i + 1]) || (i >= w && r[i - w]) || (i + w < r.size && r[i + w])
    }

    /** Air or another gas (smoke, steam): what is poured or put down takes its place. */
    private fun free(i: Int): Boolean = materials.kind[world.mat[i].toInt()] == Kind.GAS && materials.spawnBurning[world.mat[i].toInt()].not()

    /** The bucket pours out into the air around (x, y), at the temperature its content has. */
    private fun pour(cx: Int, cy: Int) {
        if (bucketAmount == 0) return
        val w = world
        val fill = fillFor(bucketMaterial)
        val r2 = radius * radius + radius
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (bucketAmount == 0) break
                val x = cx + dx
                val y = cy + dy
                if (dx * dx + dy * dy > r2 || !w.inside(x, y)) continue
                if (!free(w.index(x, y)) || !open(w.index(x, y))) continue
                if (fill < 1f && !w.chance(fill)) continue
                w.set(x, y, bucketMaterial, bucketTemp)
                bucketAmount--
            }
        }
        if (bucketAmount == 0) bucketMaterial = MaterialTable.AIR
    }

    /** The hand picks up the object at (x, y), if it is loose, light and cool enough. */
    private fun grab(x: Int, y: Int) {
        val w = world
        var at = -1
        var shut = false
        // a finger is broad: the nearest cell of something it could hold
        var best = Int.MAX_VALUE
        for (dy in -REACH..REACH) for (dx in -REACH..REACH) {
            val d = dx * dx + dy * dy
            if (d >= best || !w.inside(x + dx, y + dy)) continue
            val i = w.index(x + dx, y + dy)
            val m = w.mat[i].toInt()
            if (m != MaterialTable.AIR && materials.kind[m] != Kind.GAS && materials.kind[m] != Kind.LIQUID && materials.kind[m] != Kind.STATIC) {
                if (!touchable(i)) {
                    shut = true
                    continue
                }
                best = d
                at = i
            }
        }
        if (at < 0) return tell(if (shut) OUT_OF_REACH else "Hier ist nichts zum Greifen")
        val found = w.pieceAt(at, LUMP, MAX_CELLS)
        refusal(found)?.let { return tell(it) }
        val piece = found ?: return
        val n = piece.cells.size
        val h = Held(
            IntArray(n) { piece.cells[it] % width - x },
            IntArray(n) { piece.cells[it] / width - y },
            ByteArray(n) { w.mat[piece.cells[it]] },
            FloatArray(n) { w.temp[piece.cells[it]] },
            ByteArray(n) { w.variant[piece.cells[it]] },
        )
        for (c in piece.cells) w.clear(c % width, c / width, c)
        held = h
        tell("In der Hand: ${materials.spec(h.main).name}, $n Zellen")
    }

    /** Why the hand cannot take [piece], or null if it can: loose, not too big, heavy or hot. */
    private fun refusal(piece: World.Piece?): String? {
        val w = world
        if (piece == null || piece.cells.any { !materials.grabbable[w.mat[it].toInt()] }) return "Das lässt sich nicht greifen"
        if (piece.tooBig) return "Zu groß zum Greifen"
        // what burns the hand is felt first, before whether it would come loose
        if (piece.cells.any { w.temp[it] > HAND_TEMP || (w.flags[it].toInt() and Flag.BURNING) != 0 }) return "Zu heiß zum Anfassen"
        if (piece.anchored || piece.cells.any { (w.flags[it].toInt() and Flag.ANCHORED) != 0 }) return "Das ist fest verankert"
        var load = 0f
        for (c in piece.cells) load += materials.density[w.mat[c].toInt()] / 1000f
        if (load > MAX_LOAD) return "Zu schwer"
        return null
    }

    /** What holding a finger on a cell tells the player (3b.1). [bucket] and [hand] are null if the tool takes it, else why not. */
    class CellInfo(
        val x: Int,
        val y: Int,
        val material: Int,
        val temp: Float,
        val burning: Boolean,
        val wet: Boolean,
        val anchored: Boolean,
        val reachable: Boolean,
        val bucket: String?,
        val hand: String?,
    )

    /** What is at (x, y), and whether the bucket and the hand could take it. Not an action: nothing changes. */
    fun inspect(x: Int, y: Int): CellInfo {
        val w = world
        val i = w.index(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
        val m = w.mat[i].toInt()
        val f = w.flags[i].toInt()
        val kind = materials.kind[m]
        val burning = (f and Flag.BURNING) != 0 || materials.spawnBurning[m]
        val anchored = materials.anchored[m] || (f and Flag.ANCHORED) != 0
        val reachable = touchable(i)
        val bucket = when {
            kind != Kind.POWDER && kind != Kind.LIQUID || !materials.bucketable[m] -> "nimmt der Eimer nicht"
            burning -> "brennt"
            anchored -> "fest verankert"
            !reachable -> OUT_OF_REACH
            else -> null
        }
        val hand = when {
            kind == Kind.GAS || kind == Kind.LIQUID -> "nicht zu greifen"
            kind == Kind.STATIC -> "Gelände"
            !reachable -> OUT_OF_REACH
            else -> refusal(w.pieceAt(i, LUMP, MAX_CELLS))
        }
        return CellInfo(i % width, i / width, m, w.temp[i], burning, (f and Flag.WET) != 0, anchored, reachable, bucket, hand)
    }

    /**
     * Marks in [into] every cell of an object the hand could take right now (3b.3): loose, light, cool, in
     * reach. Returns how many cells that is.
     */
    fun grabbable(into: BooleanArray): Int {
        val w = world
        into.fill(false)
        val seen = BooleanArray(w.size)
        var n = 0
        for (i in 0 until w.size) {
            val m = w.mat[i].toInt()
            if (seen[i] || !materials.grabbable[m] || materials.kind[m] == Kind.GAS || materials.kind[m] == Kind.LIQUID) continue
            val piece = w.pieceAt(i, LUMP, MAX_CELLS) ?: continue
            for (c in piece.cells) seen[c] = true
            if (refusal(piece) != null || piece.cells.none { touchable(it) }) continue
            for (c in piece.cells) into[c] = true
            n += piece.cells.size
        }
        return n
    }

    /** Puts the held object down at (x, y), where it was held from; there must be room for all of it. */
    private fun drop(x: Int, y: Int) {
        val h = held ?: return
        val w = world
        for (k in 0 until h.size) {
            val px = x + h.dx[k]
            val py = y + h.dy[k]
            if (!w.inside(px, py) || !free(w.index(px, py))) return tell("Kein Platz zum Ablegen")
            if (!open(w.index(px, py))) return tell(OUT_OF_REACH)
        }
        if ((0 until h.size).all { materials.kind[h.mat[it].toInt()] == Kind.SOLID }) {
            // a solid object is put down as a body (Phase 4): it falls or tips if nothing holds it
            w.placeBody(
                IntArray(h.size) { w.index(x + h.dx[it], y + h.dy[it]) },
                IntArray(h.size) { h.mat[it].toInt() },
                h.temp.copyOf(),
                h.variant.copyOf(),
            )
        } else {
            for (k in 0 until h.size) {
                val px = x + h.dx[k]
                val py = y + h.dy[k]
                w.set(px, py, h.mat[k].toInt(), h.temp[k])
                w.variant[w.index(px, py)] = h.variant[k]
            }
        }
        held = null
        tell("Abgelegt")
    }

    /**
     * What the player carries goes into the store, to build with: the object in the hand (solids only; a log
     * gives planks), or else what is in the bucket, if it is loose material (sand, earth, clay, salt …, 3b.5).
     */
    fun store() {
        val h = held
        if (h == null) {
            if (bucketAmount == 0) return
            if (!storable(bucketMaterial)) return tell("Flüssiges kommt nicht ins Lager")
            if (down) release()
            pushUndo()
            log += Store(world.step)
            stock[bucketMaterial] += bucketAmount
            tell("Ins Lager: $bucketAmount × ${materials.spec(bucketMaterial).name}")
            bucketAmount = 0
            bucketMaterial = MaterialTable.AIR
            return
        }
        if ((0 until h.size).any { materials.kind[h.mat[it].toInt()] != Kind.SOLID }) return tell("Das kommt nicht ins Lager")
        if (down) release()
        pushUndo()
        log += Store(world.step)
        val got = IntArray(materials.count)
        for (k in 0 until h.size) {
            val m = h.mat[k].toInt()
            got[materials.splitInto[m]] += materials.splitYield[m]
        }
        for (m in got.indices) stock[m] += got[m]
        held = null
        tell("Ins Lager: " + got.indices.filter { got[it] > 0 }.joinToString { "${got[it]} × ${materials.spec(it).name}" })
    }

    /** The bucket and what the hand holds come slowly to the temperature of the surroundings. */
    private fun cool() {
        val a = world.ambient
        if (bucketAmount > 0) bucketTemp += (a - bucketTemp) * CARRY_COOLING
        val h = held ?: return
        // the object itself never changes: a cooled copy takes its place
        var changed = false
        val t = FloatArray(h.size) { k ->
            val v = h.temp[k] + (a - h.temp[k]) * CARRY_COOLING
            if (v != h.temp[k]) changed = true
            v
        }
        if (changed) held = Held(h.dx, h.dy, h.mat, t, h.variant, h.main)
    }

    private fun tell(text: String) {
        notice = text
        noticeCount++
    }

    /** Takes back the last action: the world returns to the moment just before it. */
    fun undo(): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        down = false
        world.restore(entry.snapshot)
        restoreSupplies(entry.supplies)
        if (entry.savedLog != null) {
            log.clear()
            log.addAll(entry.savedLog)
        } else {
            while (log.size > entry.logSize) log.removeAt(log.size - 1)
        }
        return true
    }

    /** Back to the start, exactly as the world began, with an empty bucket, hand and store; this too can be undone. */
    fun reset() {
        down = false
        pushUndo(saveLog = true)
        log.clear()
        world.restore(start)
        restoreSupplies(initial)
    }

    /** Everything turned to air: an empty sandbox (undoable). */
    fun clear() {
        down = false
        pushUndo()
        log += Clear(world.step)
        world.clearAll()
        reachStep = -1
    }

    private fun pushUndo(saveLog: Boolean = false) {
        undoStack.addLast(UndoEntry(world.snapshot(), log.size, if (saveLog) log.toList() else null, supplies()))
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
    }

    private fun dab(x: Int, y: Int) {
        when {
            tool == SCOOP -> scoop(x, y)
            tool == POUR -> pour(x, y)
            tool == HAND -> {}
            supply == Supply.PROCURE -> {
                // building from the store; loose and liquid material only comes from the bucket
                if (tool >= 0 && canBuild(tool)) stock[tool] -= world.paint(tool, x, y, radius, fillFor(tool), stock[tool])
            }
            tool == ERASER -> world.erase(x, y, radius)
            materials.animal[tool] -> animal(x, y)
            else -> world.paint(tool, x, y, radius, fillFor(tool))
        }
    }

    /** Animals are put down one at a time, not painted: a tap gives one, a stroke one every [ANIMAL_SPACING] cells. */
    private fun animal(x: Int, y: Int) {
        if (lastAnimal != NO_ANIMAL) {
            val dx = x - (lastAnimal shr 16)
            val dy = y - (lastAnimal and 0xFFFF)
            if (dx * dx + dy * dy < ANIMAL_SPACING * ANIMAL_SPACING) return
        }
        if (world.placeAnimal(tool, x, y)) lastAnimal = (x shl 16) or (y and 0xFFFF)
    }

    /** Loose material is poured scattered, solid material is drawn as a closed shape; a one-cell brush is exact. */
    private fun fillFor(m: Int): Float = if (radius == 0) 1f else when (materials.kind[m]) {
        Kind.POWDER -> 0.3f
        Kind.LIQUID -> 0.4f
        Kind.GAS -> if (materials.spawnBurning[m]) 0.8f else 0.35f
        else -> 1f
    }

    /** What the placed material has cost so far. */
    fun cost(): Int {
        var c = 0
        for (m in 0 until materials.count) c += world.placed[m] * materials.cost[m]
        return c
    }

    companion object {
        const val ERASER = -1

        /** The bucket, taking up and pouring out. */
        const val SCOOP = -2
        const val POUR = -3

        /** The hand: picks up, or puts down what it holds. */
        const val HAND = -4

        const val MAX_UNDO = 25
        const val BUCKET_SIZE = 200

        /** The hand: how far around the finger it feels, the largest lump of grains, the most it can lift. */
        private const val REACH = 3
        private const val LUMP = 30
        private const val MAX_CELLS = 400

        /** Heaviest load: density / 1000 summed over the cells (a log of 170 cells, a stone of 46, a bar of metal of 15). */
        const val MAX_LOAD = 120f

        /** Hotter than this, the hand does not take it. */
        const val HAND_TEMP = 60f
        const val OUT_OF_REACH = "Da kommst du nicht hin"

        /** Share of the difference to the surroundings a carried thing loses per step (half in about 20 seconds). */
        private const val CARRY_COOLING = 0.0006f

        /** A stroke puts down an animal every so many cells, no closer. */
        const val ANIMAL_SPACING = 12
        private const val NO_ANIMAL = -1

        /**
         * Plays [log] into a fresh session (prepared by [setup]) until the world has run [steps] steps. Inputs
         * take effect before the step they were recorded at, in recorded order.
         */
        fun replay(
            materials: MaterialTable,
            width: Int,
            height: Int,
            seed: Long,
            build: (World) -> Unit,
            log: List<Input>,
            steps: Int,
            setup: (Session) -> Unit = {},
        ): Session {
            val s = Session(materials, width, height, seed, build).also(setup)
            var k = 0
            while (s.world.step < steps) {
                while (k < log.size && log[k].step <= s.world.step) {
                    when (val input = log[k]) {
                        is Press -> s.press(input.x, input.y, input.tool, input.radius)
                        is Drag -> s.drag(input.x, input.y)
                        is Release -> s.release()
                        is Shape -> s.shape(input.kind, input.x0, input.y0, input.x1, input.y1, input.tool, input.size)
                        is Clear -> s.clear()
                        is Mode -> s.setSupply(input.supply)
                        is Store -> s.store()
                        is Weather -> s.setWeather(input.cloudBase, input.wind, input.gust, input.random, input.ambient)
                        is Storm -> s.storm(input.x)
                    }
                    k++
                }
                s.tick()
            }
            return s
        }
    }
}
