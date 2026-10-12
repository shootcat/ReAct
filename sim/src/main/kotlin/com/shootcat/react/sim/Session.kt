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
        dab(x, y)
    }

    /** The finger moves: paints along the way, so quick strokes stay closed lines. */
    fun drag(x: Int, y: Int) {
        if (!down) return
        log += Drag(world.step, x, y)
        val dx = x - bx
        val dy = y - by
        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
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
        applyShape(kind, x0, y0, x1, y1, tool, size)
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
    fun canBuild(m: Int): Boolean = supply == Supply.UNLIMITED || (materials.kind[m] == Kind.SOLID && stock[m] > 0)

    /** The bucket takes up loose or liquid material of one kind around (x, y), as much as fits. */
    private fun scoop(cx: Int, cy: Int) {
        val w = world
        if (bucketAmount >= bucketSize) return
        val r2 = radius * radius + radius
        // an empty bucket takes what is under the finger, or else the first thing it can take nearby
        if (bucketAmount == 0) {
            bucketMaterial = MaterialTable.AIR
            var best = Int.MAX_VALUE
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val d = dx * dx + dy * dy
                if (d > r2 || d >= best || !w.inside(cx + dx, cy + dy)) continue
                val i = w.index(cx + dx, cy + dy)
                if (bucketable(i)) {
                    best = d
                    bucketMaterial = w.mat[i].toInt()
                }
            }
            if (bucketMaterial == MaterialTable.AIR) return
        }
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (bucketAmount >= bucketSize) return
                val x = cx + dx
                val y = cy + dy
                if (dx * dx + dy * dy > r2 || !w.inside(x, y)) continue
                val i = w.index(x, y)
                if (w.mat[i].toInt() != bucketMaterial || !bucketable(i)) continue
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
                if (!free(w.index(x, y))) continue
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
        // a finger is broad: the nearest cell of something it could hold
        var best = Int.MAX_VALUE
        for (dy in -REACH..REACH) for (dx in -REACH..REACH) {
            val d = dx * dx + dy * dy
            if (d >= best || !w.inside(x + dx, y + dy)) continue
            val i = w.index(x + dx, y + dy)
            val m = w.mat[i].toInt()
            if (m != MaterialTable.AIR && materials.kind[m] != Kind.GAS && materials.kind[m] != Kind.LIQUID && materials.kind[m] != Kind.STATIC) {
                best = d
                at = i
            }
        }
        if (at < 0) return tell("Hier ist nichts zum Greifen")
        val piece = w.pieceAt(at, LUMP, MAX_CELLS)
        if (piece == null || piece.cells.any { !materials.grabbable[w.mat[it].toInt()] }) return tell("Das lässt sich nicht greifen")
        if (piece.tooBig) return tell("Zu groß zum Greifen")
        if (piece.anchored || piece.cells.any { (w.flags[it].toInt() and Flag.ANCHORED) != 0 }) return tell("Das ist fest verankert")
        var load = 0f
        for (c in piece.cells) load += materials.density[w.mat[c].toInt()] / 1000f
        if (load > MAX_LOAD) return tell("Zu schwer")
        if (piece.cells.any { w.temp[it] > HAND_TEMP || (w.flags[it].toInt() and Flag.BURNING) != 0 }) return tell("Zu heiß zum Anfassen")
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

    /** Puts the held object down at (x, y), where it was held from; there must be room for all of it. */
    private fun drop(x: Int, y: Int) {
        val h = held ?: return
        val w = world
        for (k in 0 until h.size) {
            val px = x + h.dx[k]
            val py = y + h.dy[k]
            if (!w.inside(px, py) || !free(w.index(px, py))) return tell("Kein Platz zum Ablegen")
        }
        for (k in 0 until h.size) {
            val px = x + h.dx[k]
            val py = y + h.dy[k]
            w.set(px, py, h.mat[k].toInt(), h.temp[k])
            w.variant[w.index(px, py)] = h.variant[k]
        }
        held = null
        tell("Abgelegt")
    }

    /** The object in the hand goes into the store, to build with: a log gives planks. Solids only. */
    fun store() {
        val h = held ?: return
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
        val a = materials.ambient
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
                    }
                    k++
                }
                s.tick()
            }
            return s
        }
    }
}
