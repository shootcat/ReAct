package com.shootcat.react.sim

/**
 * A world together with what the player does to it (Leitfaden V3, 4.7): painting with a brush, undo per
 * action, reset. Single-threaded and deterministic; [SimRunner] drives it from its own thread.
 *
 * Every input is written to [log] with the world step at which it took effect, so the same log played into a
 * new session with the same seed gives exactly the same world ([replay]). Undo and reset go back in world
 * time and cut the log back accordingly: the log is always the straight history of the current world.
 */
class Session(
    val materials: MaterialTable,
    val width: Int,
    val height: Int,
    val seed: Long,
    build: (World) -> Unit = {},
) {
    val world = World(width, height, materials, seed).also(build)
    private val start = world.snapshot()
    /** The world before an action, and how long the log was then (or the whole log, if the action cleared it). */
    private class UndoEntry(val snapshot: World.Snapshot, val logSize: Int, val savedLog: List<Input>?)
    private val undoStack = ArrayDeque<UndoEntry>()

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

    /** A finger touches the world: one action begins (and can be undone as a whole). */
    fun press(x: Int, y: Int, tool: Int, radius: Int) {
        if (down) release()
        pushUndo()
        log += Press(world.step, x, y, tool, radius)
        this.tool = tool
        this.radius = radius
        down = true
        bx = x
        by = y
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
        when (kind) {
            ShapeKind.LINE -> if (tool == ERASER) w.eraseLine(x0, y0, x1, y1, size) else w.paintLine(tool, x0, y0, x1, y1, size)
            ShapeKind.RECT -> if (tool == ERASER) w.eraseRect(x0, y0, x1, y1, false, 1) else w.paintRect(tool, x0, y0, x1, y1, false, 1)
            ShapeKind.FRAME -> if (tool == ERASER) w.eraseRect(x0, y0, x1, y1, true, size + 1) else w.paintRect(tool, x0, y0, x1, y1, true, size + 1)
        }
    }

    /** One simulation step; a finger that stays down keeps pouring. */
    fun tick() {
        if (down) dab(bx, by)
        world.step()
    }

    /** Takes back the last action: the world returns to the moment just before it. */
    fun undo(): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        down = false
        world.restore(entry.snapshot)
        if (entry.savedLog != null) {
            log.clear()
            log.addAll(entry.savedLog)
        } else {
            while (log.size > entry.logSize) log.removeAt(log.size - 1)
        }
        return true
    }

    /** Back to the start, exactly as the world began; this too can be undone. */
    fun reset() {
        down = false
        pushUndo(saveLog = true)
        log.clear()
        world.restore(start)
    }

    /** Everything turned to air: an empty sandbox (undoable). */
    fun clear() {
        down = false
        pushUndo()
        log += Clear(world.step)
        world.clearAll()
    }

    private fun pushUndo(saveLog: Boolean = false) {
        undoStack.addLast(UndoEntry(world.snapshot(), log.size, if (saveLog) log.toList() else null))
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
    }

    private fun dab(x: Int, y: Int) {
        if (tool == ERASER) {
            world.erase(x, y, radius)
        } else {
            world.paint(tool, x, y, radius, fillFor(tool))
        }
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
        const val MAX_UNDO = 25

        /**
         * Plays [log] into a fresh session until the world has run [steps] steps. Inputs take effect before
         * the step they were recorded at, in recorded order.
         */
        fun replay(
            materials: MaterialTable,
            width: Int,
            height: Int,
            seed: Long,
            build: (World) -> Unit,
            log: List<Input>,
            steps: Int,
        ): Session {
            val s = Session(materials, width, height, seed, build)
            var k = 0
            while (s.world.step < steps) {
                while (k < log.size && log[k].step <= s.world.step) {
                    when (val input = log[k]) {
                        is Press -> s.press(input.x, input.y, input.tool, input.radius)
                        is Drag -> s.drag(input.x, input.y)
                        is Release -> s.release()
                        is Shape -> s.shape(input.kind, input.x0, input.y0, input.x1, input.y1, input.tool, input.size)
                        is Clear -> s.clear()
                    }
                    k++
                }
                s.tick()
            }
            return s
        }
    }
}
