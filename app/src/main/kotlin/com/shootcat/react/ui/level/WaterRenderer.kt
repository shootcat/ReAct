package com.shootcat.react.ui.level

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (2 * PI).toFloat()

/** Below this visual fill a cell counts as dry. */
private const val DRY = 0.02f

/** How fast the drawn fill follows the simulated one (seconds to get about two thirds of the way). */
private const val LEVEL_TAU = 0.12f

/** Gravity for drawn streams and falling water, in cells per second squared. */
private const val GRAVITY = 32f

/** How deep a floating thing lies in the water, in cells. */
private const val FLOAT_DEPTH = 0.42f

/** How one kind of liquid looks. */
private class LiquidLook(
    val light: Color,
    val deep: Color,
    val crest: Color,
    val crestAlpha: Float,
    /** Glowing liquids (lava) light up their surroundings. */
    val glow: Color?,
    /** Thick liquids (lava) move in slower, smaller waves. */
    val waveScale: Float,
    val alpha: Float,
)

private val waterLook = LiquidLook(Palette.waterLight, Palette.water, Color.White, 0.55f, null, 1f, 0.9f)
private val seaLook = LiquidLook(Palette.seaLight, Palette.sea, Color(0xFFE8FFFB), 0.7f, null, 1.2f, 0.94f)
private val lavaLook = LiquidLook(Palette.lava, Palette.lavaDeep, Palette.fireCore, 0.8f, Palette.lava, 0.45f, 0.97f)

private fun lookOf(type: String): LiquidLook = when (type) {
    "LAVA" -> lavaLook
    "SEAWATER" -> seaLook
    else -> waterLook
}

private data class Cell(val type: String, val x: Int, val y: Int)

private enum class SurfaceKind {
    /** Open to the air: waves, a bright crest. */
    OPEN,

    /** Under another, lighter liquid (fresh water on sea water): a faint boundary. */
    COVERED,

    /** Against rock or a fixed block: no crest. */
    CAPPED,
}

/** One continuous water line over neighbouring columns, in cell units. */
private class Chain(val points: List<Offset>, val kind: SurfaceKind, val x0: Float, val x1: Float)

/**
 * One connected body of one liquid: its liquid cells plus the cells of solid things lying in it
 * ([submerged]), and its water lines.
 */
private class Body(
    val type: String,
    val cells: List<Position>,
    val submerged: Set<Position>,
    val chains: List<Chain>,
    val top: Float,
    val bottom: Float,
)

/**
 * A stream hanging at a lip: an edge water spills over ([dir] ±1) or a hole a pool drains through
 * ([dir] 0). Its lower end falls with gravity until it reaches what it lands on; once the source
 * runs dry its upper end lets go and falls after it.
 */
private class Stream(val type: String, var column: Int, var dir: Int, var bottom: Float) {
    var startX = 0f
    var startY = 0f
    var top = 0f
    var bottomSpeed = 0f
    var topSpeed = 0f
    var width = 0f
    var attached = true
    var seen = false
    var impact = 0f
    var impactLiquid = false
}

/** A falling piece of liquid without a source (melt water from a ledge, a raindrop). */
private class Piece(val type: String, val x: Int, val top: Float, val bottom: Float, val size: Float, val alpha: Float)

/** A ring of waves where something fell into a body of liquid. */
private class Impulse(val type: String, val x: Float, val y: Float, val strength: Float, var age: Float = 0f)

/** A few droplets where water hits solid ground. */
private class Splash(val type: String, val at: Offset, val strength: Float, var age: Float = 0f)

/**
 * The liquids of the board, drawn as they would look in nature rather than cell by cell.
 *
 * - Connected liquid forms **bodies** with one smooth water line (a spline over all columns). Solid
 *   things lying in a body – a stone in a pond, wood floating on it – have water behind them, so the
 *   liquid never shows a hole around them; floating things lie half in the water.
 * - Falling liquid that has a source is one **stream** from the lip to where it lands: it spills over
 *   edges in a short arc, accelerates and narrows as it falls, and makes rings where it hits a pool.
 * - What the board shows follows the simulation smoothly in time, not in steps: fill levels ease
 *   towards the simulated ones, streams grow and let go with gravity. Undo, redo and reset jump.
 *
 * The engine is not involved: everything here is derived from the simulated states.
 */
internal class WaterView {
    private val levels = HashMap<Cell, Float>()
    private val streams = mutableListOf<Stream>()
    private val impulses = mutableListOf<Impulse>()
    private val splashes = mutableListOf<Splash>()
    private var lastNanos = -1L
    private var shown: GameState? = null
    private var lastSnap = 0
    private var stillFrames = 0
    private var clock = 0f

    private var bodies: List<Body> = emptyList()
    private var pieces: List<Piece> = emptyList()
    private var sinks: Map<String, Float> = emptyMap()
    private var burning: List<Pair<Position, Float>> = emptyList()

    /** How far the floating object [id] is drawn below its cell, in cells (0 for everything else). */
    fun sinkOf(id: String): Float = sinks[id] ?: 0f

    /**
     * Brings the drawn liquid one frame closer to [state]; [nanos] is the frame time. A new [snapKey]
     * (undo, redo, reset) shows the new world at once instead of letting the water flow there.
     */
    fun update(state: GameState, previous: GameState?, progress: Float, nanos: Long, snapKey: Int = 0) {
        val dt = if (lastNanos < 0) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        // Without a running frame clock (paused animations) there is nothing to flow with: show the world as it is.
        stillFrames = if (lastNanos >= 0 && nanos == lastNanos) stillFrames + 1 else 0
        lastNanos = nanos
        clock += dt
        val changed = state !== shown
        val jump = shown == null || snapKey != lastSnap || (changed && stillFrames > 2)
        shown = state
        lastSnap = snapKey

        val falling = state.objects.filter { it.isLiquid && isFalling(state, it) }
        val fallingIds = falling.mapTo(HashSet()) { it.id }

        // Fill levels ease towards the simulation, independent of its steps.
        val target = HashMap<Cell, Float>()
        for (o in state.objects) {
            if (o.isLiquid && o.id !in fallingIds) target[Cell(o.type, o.position.x, o.position.y)] = fill(o)
        }
        if (jump) {
            levels.clear()
            levels.putAll(target)
            streams.clear()
            impulses.clear()
            splashes.clear()
        } else {
            val k = 1f - exp(-dt / LEVEL_TAU)
            for (key in (levels.keys + target.keys).toList()) {
                val now = levels[key] ?: 0f
                val goal = target[key] ?: 0f
                val next = now + (goal - now) * k
                if (goal == 0f && next < DRY / 2) levels.remove(key) else levels[key] = next
            }
        }

        updateStreams(state, previous, progress, falling, dt, instant = jump)
        if (changed && !jump && previous != null) addLandings(state, previous)
        for (i in impulses) i.age += dt
        impulses.removeAll { it.age > 1.6f }
        for (s in splashes) s.age += dt
        splashes.removeAll { it.age > 0.6f }

        pieces = detachedPieces(state, previous, progress, falling)
        sinks = floaterSinks(state)
        bodies = buildBodies(state)
        burning = state.objects.filter { it.isLiquid && it.state == "BURNING" && it.id !in fallingIds }
            .map { it.position to (levels[Cell(it.type, it.position.x, it.position.y)] ?: fill(it)) }
    }

    private fun level(type: String, p: Position): Float = levels[Cell(type, p.x, p.y)] ?: 0f

    private fun resting(state: GameState, type: String, p: Position): Boolean {
        val o = state.objectAt(p) ?: return false
        return o.type == type && o.isLiquid && !isFalling(state, o)
    }

    // ---------------------------------------------------------------- streams

    private class Run(val type: String, val x: Int, val top: Int, val bottom: Int, val cells: List<GameObject>)

    private fun runsOf(falling: List<GameObject>): List<Run> {
        val runs = mutableListOf<Run>()
        for ((key, column) in falling.groupBy { it.type to it.position.x }) {
            var current = mutableListOf<GameObject>()
            for (o in column.sortedBy { it.position.y }) {
                if (current.isNotEmpty() && o.position.y != current.last().position.y + 1) {
                    runs += Run(key.first, key.second, current.first().position.y, current.last().position.y, current)
                    current = mutableListOf()
                }
                current += o
            }
            if (current.isNotEmpty()) runs += Run(key.first, key.second, current.first().position.y, current.last().position.y, current)
        }
        return runs
    }

    /** Where liquid falling down column [x] below row [from] lands: its y in cells, and whether that is liquid. */
    private fun landing(state: GameState, type: String, x: Int, from: Int): Pair<Float, Boolean> {
        var p = Position(x, from + 1)
        while (state.inBounds(p) && !state.isWall(p)) {
            val o = state.objectAt(p)
            if (o == null || o.isGas || o.isCloud || (o.isLiquid && o.type == type && isFalling(state, o))) {
                p = p.down()
                continue
            }
            if (o.isLiquid) {
                val lv = levels[Cell(o.type, p.x, p.y)] ?: fill(o)
                return p.y + 1f - lv.coerceIn(0f, 1f) to true
            }
            return p.y.toFloat() to false
        }
        return (if (state.inBounds(p)) p.y else state.height).toFloat() to false
    }

    /** Streams follow their water; [instant] (a jump) shows them fully formed right away. */
    private fun updateStreams(state: GameState, previous: GameState?, progress: Float, falling: List<GameObject>, dt: Float, instant: Boolean) {
        for (s in streams) s.seen = false
        val runs = runsOf(falling)
        for (run in runs) {
            val x = run.x
            val type = run.type
            val sideL = resting(state, type, Position(x - 1, run.top))
            val sideR = resting(state, type, Position(x + 1, run.top))
            val above = Position(x, run.top - 1)
            val freeAbove = state.inBounds(above) && !state.isWall(above) && state.objectAt(above) == null
            val upL = freeAbove && resting(state, type, Position(x - 1, run.top - 1))
            val upR = freeAbove && resting(state, type, Position(x + 1, run.top - 1))
            val dir: Int
            val lipRow: Int
            when {
                sideL && sideR -> { dir = 0; lipRow = run.top }
                sideL || sideR -> { dir = if (sideL) 1 else -1; lipRow = run.top }
                upL || upR -> { dir = if (upL) 1 else -1; lipRow = run.top - 1 }
                else -> continue
            }
            val size = run.cells.map { fill(it) }.average().toFloat()
            val (impact, liquid) = landing(state, type, x, run.bottom)
            val startY: Float
            val startX: Float
            if (dir == 0) {
                val l = level(type, Position(x - 1, lipRow))
                val r = level(type, Position(x + 1, lipRow))
                startY = lipRow + 1f - max(l, r).coerceIn(0f, 1f)
                startX = x + 0.5f
            } else {
                val source = Position(x - dir, lipRow)
                startY = lipRow + 1f - level(type, source).coerceIn(0.15f, 1f)
                startX = if (dir > 0) x.toFloat() else x + 1f
            }
            // The same stream as before (also when the pool sank a row), otherwise a new one at the lip.
            val s = streams.firstOrNull { !it.seen && it.type == type && it.column == x && it.attached && it.dir == dir }
                ?: streams.firstOrNull { !it.seen && it.type == type && it.column == x && it.attached }
                ?: Stream(type, x, dir, if (instant) impact else startY).also { streams += it }
            val fresh = !s.seen && s.width == 0f
            s.seen = true
            s.attached = true
            s.dir = dir
            // The lip follows the falling water level smoothly.
            val ease = 1f - exp(-dt / LEVEL_TAU)
            s.startX = if (fresh) startX else s.startX + (startX - s.startX) * ease
            s.startY = if (fresh) startY else s.startY + (startY - s.startY) * ease
            s.top = s.startY
            s.impact = impact
            s.impactLiquid = liquid
            val goalWidth = 0.16f + 0.5f * sqrt(size.coerceIn(0f, 1f))
            s.width = if (s.width == 0f) goalWidth else s.width + (goalWidth - s.width) * (1f - exp(-dt / 0.15f))
        }
        for (s in streams) {
            if (!s.seen && s.attached) {
                // The source ran dry: the stream lets go of the lip and falls after its lower end.
                s.attached = false
                s.topSpeed = 0f
            }
            if (s.bottom < s.impact) {
                s.bottomSpeed += GRAVITY * dt
                s.bottom = min(s.impact, s.bottom + s.bottomSpeed * dt)
            } else {
                s.bottom = s.impact
            }
            if (!s.attached) {
                // The upper end falls after the last of the water, never ahead of it.
                val last = runs.filter { it.type == s.type && it.x == s.column && it.bottom + 1f > s.top }
                    .minOfOrNull { it.top + fallShift(it, previous, progress) }
                s.topSpeed += GRAVITY * dt
                s.top = min(s.top + s.topSpeed * dt, last ?: s.bottom)
            }
        }
        streams.removeAll { !it.attached && it.top >= it.bottom - 0.02f }
        for (s in streams) {
            if (s.attached && !s.impactLiquid && s.bottom >= s.impact - 0.01f && (clock * 7f + s.column) % 1f < 0.05f) {
                splashes += Splash(s.type, Offset(s.column + 0.5f, s.impact), s.width)
            }
        }
    }

    /** Falling pieces of the last step that have merged into a pool make rings; on rock they splash. */
    private fun addLandings(state: GameState, previous: GameState) {
        for (o in previous.objects) {
            if (!o.isLiquid || !isFalling(previous, o) || state.objectById(o.id) != null) continue
            if (streams.any { it.column == o.position.x && it.type == o.type && it.attached }) continue
            val below = state.objectAt(o.position.down())
            val strength = fill(o)
            if (below != null && below.isLiquid) {
                val lv = levels[Cell(below.type, below.position.x, below.position.y)] ?: fill(below)
                impulses += Impulse(below.type, o.position.x + 0.5f, below.position.y + 1f - lv, strength)
            } else {
                splashes += Splash(o.type, Offset(o.position.x + 0.5f, o.position.y + 1f), strength)
            }
        }
    }

    /** How far [run] is drawn above its cell: between its last and its current cell, accelerating. */
    private fun fallShift(run: Run, previous: GameState?, progress: Float): Float {
        val lead = run.cells.last()
        val from = previous?.objectById(lead.id)?.position?.takeIf { it.x == lead.position.x && it.y < lead.position.y }
        return if (from != null) (from.y - lead.position.y) * (1f - progress * progress) else 0f
    }

    /** Falling liquid no stream accounts for, between its last and its current cell, accelerating. */
    private fun detachedPieces(state: GameState, previous: GameState?, progress: Float, falling: List<GameObject>): List<Piece> {
        val result = mutableListOf<Piece>()
        val t = progress * progress
        for (run in runsOf(falling)) {
            val covered = streams.any { s ->
                s.type == run.type && s.column == run.x && run.top + 1f >= s.top - 0.3f && run.top <= s.bottom + 0.3f
            }
            if (covered) continue
            val shift = fallShift(run, previous, progress)
            val size = run.cells.map { fill(it) }.average().toFloat()
            result += Piece(run.type, run.x, run.top + shift, run.bottom + 1f + shift, size, 1f)
        }
        // Pieces that landed in this step still fall the last bit and dissolve into what they hit.
        if (previous != null) {
            for (o in previous.objects) {
                if (!o.isLiquid || !isFalling(previous, o) || state.objectById(o.id) != null) continue
                if (streams.any { it.column == o.position.x && it.type == o.type }) continue
                val y = o.position.y + t
                result += Piece(o.type, o.position.x, y, y + 1f, fill(o), 1f - progress)
            }
        }
        return result
    }

    // ---------------------------------------------------------------- floating things

    private fun floaterSinks(state: GameState): Map<String, Float> {
        val result = HashMap<String, Float>()
        for (o in state.objects) {
            if (!o.falls || o.isFluid || o.isCloud) continue
            val below = state.objectAt(o.position.down()) ?: continue
            if (!below.isLiquid || below.density <= o.density || isFalling(state, below)) continue
            val lv = (levels[Cell(below.type, below.position.x, below.position.y)] ?: fill(below)).coerceIn(0f, 1f)
            result[o.id] = min(1f, 1f - lv + FLOAT_DEPTH)
        }
        return result
    }

    // ---------------------------------------------------------------- bodies

    private fun isSolidThing(o: GameObject?) = o != null && o.falls && !o.isFluid && !o.isCloud

    private fun buildBodies(state: GameState): List<Body> {
        val result = mutableListOf<Body>()
        val wet = levels.entries.filter { it.value > DRY }
        for ((type, entries) in wet.groupBy { it.key.type }) {
            val lv = entries.associate { Position(it.key.x, it.key.y) to it.value.coerceIn(0f, 1f) }
            val open = lv.keys.toMutableSet()
            val parts = mutableListOf<Pair<MutableSet<Position>, MutableSet<Position>>>()
            while (open.isNotEmpty()) {
                val start = open.first()
                open.remove(start)
                val liquid = mutableSetOf(start)
                val queue = ArrayDeque(listOf(start))
                while (queue.isNotEmpty()) {
                    val p = queue.removeFirst()
                    for (n in p.neighbours()) if (open.remove(n)) { liquid += n; queue += n }
                }
                parts += liquid to submergedIn(state, liquid, lv)
            }
            // Two pools around the same stone are one body.
            val merged = mutableListOf<Pair<MutableSet<Position>, MutableSet<Position>>>()
            for (part in parts) {
                val other = merged.firstOrNull { m -> m.second.any { it in part.second } }
                if (other != null) {
                    other.first += part.first
                    other.second += part.second
                } else {
                    merged += part
                }
            }
            for ((liquid, submerged) in merged) result += body(state, type, liquid, submerged, lv)
        }
        return result
    }

    /** The water line in each column of [liquid] (y in cells, from the top). */
    private fun columnSurfaces(liquid: Set<Position>, lv: Map<Position, Float>): Map<Int, Float> =
        liquid.groupBy { it.x }.mapValues { (_, cells) ->
            val top = cells.minBy { it.y }
            top.y + 1f - (lv[top] ?: 1f)
        }

    private fun surfaceNear(x: Int, surfaces: Map<Int, Float>): Float? {
        surfaces[x]?.let { return it }
        val left = surfaces.keys.filter { it < x }.maxOrNull()
        val right = surfaces.keys.filter { it > x }.minOrNull()
        return when {
            left != null && right != null -> {
                val f = (x - left).toFloat() / (right - left)
                surfaces.getValue(left) + (surfaces.getValue(right) - surfaces.getValue(left)) * f
            }
            left != null -> surfaces.getValue(left)
            right != null -> surfaces.getValue(right)
            else -> null
        }
    }

    /**
     * Solid things lying in the liquid: beside it below the water line, or under it in a basin (not a
     * stone on open ground that water only runs over).
     */
    private fun submergedIn(state: GameState, liquid: Set<Position>, lv: Map<Position, Float>): MutableSet<Position> {
        val surfaces = columnSurfaces(liquid, lv)
        val result = mutableSetOf<Position>()
        fun body(p: Position) = p in liquid || p in result
        fun enclosed(p: Position) = !state.inBounds(p) || state.isWall(p) || body(p) || isSolidThing(state.objectAt(p))
        repeat(2) {
            for (p in (liquid + result).toList()) {
                for (n in listOf(p.left(), p.right(), p.down())) {
                    if (body(n) || !state.inBounds(n) || state.isWall(n)) continue
                    if (!isSolidThing(state.objectAt(n))) continue
                    val keep = if (n == p.down()) {
                        enclosed(n.left()) && enclosed(n.right())
                    } else {
                        val surface = surfaceNear(n.x, surfaces) ?: continue
                        n.y + 1f > surface + 0.05f
                    }
                    if (keep) result += n
                }
            }
        }
        return result
    }

    private fun body(state: GameState, type: String, liquid: Set<Position>, submerged: Set<Position>, lv: Map<Position, Float>): Body {
        val cells = liquid + submerged
        val surfaces = columnSurfaces(liquid, lv)
        val look = lookOf(type)

        class Top(val p: Position, val y: Float, val kind: SurfaceKind, val depth: Float)

        val tops = cells.filter { it.up() !in cells }.map { p ->
            val y = if (p in liquid) {
                p.y + 1f - (lv[p] ?: 1f)
            } else {
                // A stone sticking out of the water: the water line runs on behind it.
                val near = listOfNotNull(
                    lv[p.left()]?.let { p.y + 1f - it }.takeIf { p.left() in liquid && p.left().up() !in cells },
                    lv[p.right()]?.let { p.y + 1f - it }.takeIf { p.right() in liquid && p.right().up() !in cells },
                )
                if (near.isNotEmpty()) near.average().toFloat() else surfaceNear(p.x, surfaces) ?: (p.y + 0.5f)
            }.coerceIn(p.y.toFloat(), p.y + 1f)
            val up = p.up()
            val o = if (state.inBounds(up)) state.objectAt(up) else null
            val kind = when {
                !state.inBounds(up) -> SurfaceKind.OPEN
                state.isWall(up) -> SurfaceKind.CAPPED
                o == null || o.isGas || o.isCloud -> SurfaceKind.OPEN
                o.isLiquid -> if (o.type == type) SurfaceKind.OPEN else SurfaceKind.COVERED
                sinkOf(o.id) > 0f -> SurfaceKind.OPEN
                else -> SurfaceKind.CAPPED
            }
            Top(p, y, kind, p.y + 1f - y)
        }

        // Link the water lines of neighbouring columns into chains.
        val chains = mutableListOf<MutableList<Top>>()
        for (t in tops.sortedWith(compareBy({ it.p.x }, { it.y }))) {
            val candidates = chains.filter { ch ->
                val last = ch.last()
                last.p.x == t.p.x - 1 && last.kind == t.kind && abs(last.y - t.y) < 1.05f &&
                    (last.p.y == t.p.y || Position(t.p.x - 1, t.p.y) in cells || Position(t.p.x, last.p.y) in cells)
            }
            val chain = candidates.minByOrNull { abs(it.last().y - t.y) }
            if (chain != null && chains.none { it !== chain && it.last() === t }) chain += t else chains += mutableListOf(t)
        }

        val waveBase = 0.035f * look.waveScale
        val result = chains.map { ch ->
            val raw = ArrayList<Offset>(ch.size + 2)
            raw += Offset(ch.first().p.x.toFloat(), ch.first().y)
            for (t in ch) raw += Offset(t.p.x + 0.5f, t.y)
            raw += Offset(ch.last().p.x + 1f, ch.last().y)
            val smooth = catmullRom(raw, 5)
            val shallow = ch.minOf { it.depth }
            val amp = if (ch.first().kind == SurfaceKind.OPEN) min(waveBase, shallow * 0.3f) else 0f
            val pts = smooth.map { pt ->
                if (amp <= 0f) {
                    pt
                } else {
                    val u = pt.x
                    val t = clock * look.waveScale
                    val wave = amp * sin(u * TAU * 0.8f + t * TAU * 0.42f) + amp * 0.45f * sin(u * TAU * 2.1f - t * TAU * 0.83f)
                    Offset(pt.x, pt.y + wave + ripple(type, pt.x, pt.y))
                }
            }
            Chain(pts, ch.first().kind, ch.first().p.x.toFloat(), ch.last().p.x + 1f)
        }
        val top = result.minOfOrNull { ch -> ch.points.minOf { it.y } } ?: cells.minOf { it.y }.toFloat()
        val bottom = cells.maxOf { it.y } + 1f
        return Body(type, cells.toList(), submerged, result, top, bottom)
    }

    /** How much the water line at [x] is lifted or lowered by rings from streams and things falling in. */
    private fun ripple(type: String, x: Float, y: Float): Float {
        var d = 0f
        for (s in streams) {
            if (s.type != type || !s.impactLiquid || s.bottom < s.impact - 0.05f || abs(s.impact - y) > 0.8f) continue
            val dx = abs(x - (s.column + 0.5f))
            val strength = s.width
            d += 0.05f * strength * sin(dx * 6f - clock * 14f) * exp(-dx / 1.2f)
            d += 0.07f * strength * exp(-dx * dx / 0.08f)
        }
        for (i in impulses) {
            if (i.type != type || abs(i.y - y) > 0.8f) continue
            val dx = abs(x - i.x)
            val front = i.age * 2.4f
            val envelope = exp(-(dx - front) * (dx - front) / 0.35f) * exp(-i.age / 0.45f)
            d += 0.14f * i.strength * envelope * sin(dx * 7f - i.age * 16f)
        }
        return d
    }

    // ---------------------------------------------------------------- drawing

    private class BodyPaths(val body: Body, val fill: Path)

    private var painted: List<BodyPaths> = emptyList()

    /** Everything behind the objects: the bodies of liquid with their water lines, streams and falling water. */
    fun drawBehind(scope: DrawScope, cell: Float) = with(scope) {
        val c = cell
        painted = bodies.map { b -> BodyPaths(b, fillPath(b, c)) }
        for (bp in painted) {
            val look = lookOf(bp.body.type)
            if (look.glow != null) {
                for (p in bp.body.cells) {
                    if (p in bp.body.submerged) continue
                    val center = Offset((p.x + 0.5f) * c, (p.y + 0.6f) * c)
                    val r = c * (1.1f + 0.08f * sin(clock * TAU * 0.8f + p.x))
                    drawCircle(Brush.radialGradient(listOf(look.glow.copy(alpha = 0.22f), Color.Transparent), center, r), r, center)
                }
            }
        }
        for (bp in painted) drawBody(bp, c)
        for (s in streams) drawStream(s, c)
        for (p in pieces) drawPiece(p, c)
        for (s in splashes) drawSplash(s, c)
        for ((p, lv) in burning) drawSurfaceFlames(Offset(p.x * c, p.y * c), c, lv, clock)
    }

    /** In front of the objects: the water in front of whatever lies in it, a thin veil. */
    fun drawInFront(scope: DrawScope, cell: Float, state: GameState, offsetOf: (GameObject) -> Offset) = with(scope) {
        val c = cell
        for (bp in painted) {
            val look = lookOf(bp.body.type)
            // Cells (in cell units) of what lies in this body: submerged things and the lower part of floating ones.
            val rects = bp.body.submerged.map { Rect(it.x.toFloat(), it.y.toFloat(), it.x + 1f, it.y + 1f) }.toMutableList()
            for (o in state.objects) {
                if (sinkOf(o.id) <= 0f || o.position.down() !in bp.body.cells) continue
                val at = offsetOf(o)
                rects += Rect(at.x, at.y, at.x + 1f, at.y + 1f)
            }
            if (rects.isEmpty()) continue
            val veil = Path()
            for (r in rects) veil.addRect(Rect(r.left * c, r.top * c, r.right * c, r.bottom * c))
            val front = Path.combine(PathOperation.Intersect, bp.fill, veil)
            drawPath(front, Brush.verticalGradient(listOf(look.light, look.deep), bp.body.top * c, bp.body.bottom * c), alpha = if (look === lavaLook) 0.32f else 0.3f)
            // The water line runs on in front of what lies in the water.
            for (ch in bp.body.chains) {
                if (ch.kind != SurfaceKind.OPEN) continue
                val line = Path()
                var drawing = false
                for (pt in ch.points) {
                    val inside = rects.any { pt.x >= it.left && pt.x <= it.right && pt.y >= it.top - 0.1f && pt.y <= it.bottom }
                    if (inside) {
                        if (drawing) line.lineTo(pt.x * c, pt.y * c) else line.moveTo(pt.x * c, pt.y * c)
                    }
                    drawing = inside
                }
                drawPath(line, look.crest, alpha = look.crestAlpha * 0.8f, style = Stroke(width = c * 0.03f, cap = StrokeCap.Round))
            }
        }
    }

    private fun fillPath(b: Body, c: Float): Path {
        val cells = Path()
        for (p in b.cells) cells.addRect(Rect(p.x * c, p.y * c, (p.x + 1) * c, (p.y + 1) * c))
        val below = Path()
        val floor = (b.bottom + 1f) * c
        for (ch in b.chains) {
            below.moveTo(ch.points.first().x * c, ch.points.first().y * c)
            for (pt in ch.points.drop(1)) below.lineTo(pt.x * c, pt.y * c)
            below.lineTo(ch.x1 * c, floor)
            below.lineTo(ch.x0 * c, floor)
            below.close()
        }
        return Path.combine(PathOperation.Intersect, cells, below)
    }

    private fun DrawScope.drawBody(bp: BodyPaths, c: Float) {
        val b = bp.body
        val look = lookOf(b.type)
        drawPath(bp.fill, Brush.verticalGradient(listOf(look.light, look.deep), b.top * c - c * 0.2f, b.bottom * c + c * 0.8f), alpha = look.alpha)
        for (ch in b.chains) {
            if (ch.kind == SurfaceKind.CAPPED) continue
            val line = Path().apply {
                moveTo(ch.points.first().x * c, ch.points.first().y * c)
                for (pt in ch.points.drop(1)) lineTo(pt.x * c, pt.y * c)
            }
            if (ch.kind == SurfaceKind.OPEN) {
                drawPath(line, look.crest, alpha = look.crestAlpha, style = Stroke(width = c * 0.03f, cap = StrokeCap.Round))
                // Light glinting just under the water line.
                val span = ch.x1 - ch.x0
                for (k in 0 until max(1, span.toInt())) {
                    val f = ((clock * 0.18f + k * 0.61f + ch.x0 * 0.37f) % 1f)
                    val x = ch.x0 + span * f
                    val y = yOn(ch, x) + 0.28f
                    if (Position(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt()) !in b.cells) continue
                    val fade = sin(f * PI.toFloat())
                    drawLine(Color.White, Offset((x - 0.08f) * c, y * c), Offset((x + 0.08f) * c, y * c), strokeWidth = c * 0.02f, cap = StrokeCap.Round, alpha = 0.22f * fade)
                }
                if (look === lavaLook) drawCrust(ch, c)
            } else {
                // Sea water under fresh water: only a soft boundary.
                drawPath(line, look.light, alpha = 0.35f, style = Stroke(width = c * 0.02f))
            }
        }
    }

    private fun yOn(ch: Chain, x: Float): Float {
        val pts = ch.points
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (x >= a.x && x <= b.x) return if (b.x == a.x) a.y else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
        }
        return pts.last().y
    }

    /** Dark crust plates drifting on lava. */
    private fun DrawScope.drawCrust(ch: Chain, c: Float) {
        val span = ch.x1 - ch.x0
        for (k in 0 until max(1, (span * 1.5f).toInt())) {
            val phase = (clock * 0.12f + k * 0.37f + ch.x0 * 0.21f) % 1f
            val x = ch.x0 + span * phase
            val y = yOn(ch, x)
            drawOval(Palette.lavaCrust, topLeft = Offset((x - 0.12f) * c, (y + 0.06f + (k % 2) * 0.16f) * c), size = Size(c * 0.24f, c * 0.08f), alpha = 0.55f)
        }
    }

    /** A stream from its lip down to where it lands: one ribbon, arcing over an edge, narrowing as it falls. */
    private fun DrawScope.drawStream(s: Stream, c: Float) {
        val look = lookOf(s.type)
        val top = if (s.attached) s.startY else max(s.startY, s.top)
        val bottom = s.bottom
        if (bottom - top < 0.02f) return
        val centre = ArrayList<Offset>()
        val widths = ArrayList<Float>()
        fun x(h: Float): Float = if (s.dir == 0) s.startX else s.startX + s.dir * (0.06f + 0.44f * (1f - exp(-3f * h)))
        fun w(h: Float): Float = max(0.07f, s.width / sqrt(1f + 1.4f * h))
        if (s.dir != 0 && s.attached) {
            // The short run over the lip before the water bends down.
            centre += Offset(s.startX - s.dir * 0.14f, top + s.width * 0.35f)
            widths += s.width * 0.75f
        }
        val steps = max(6, ((bottom - top) / 0.12f).toInt())
        for (i in 0..steps) {
            val y = top + (bottom - top) * i / steps
            val h = y - s.startY
            centre += Offset(x(h), y + if (s.dir != 0 && s.attached) s.width * 0.35f * (1f - min(1f, h * 3f)) else 0f)
            widths += w(h)
        }
        val left = ArrayList<Offset>(centre.size)
        val right = ArrayList<Offset>(centre.size)
        for (i in centre.indices) {
            val a = centre[max(0, i - 1)]
            val b = centre[min(centre.size - 1, i + 1)]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = sqrt(dx * dx + dy * dy).takeIf { it > 1e-4f } ?: 1f
            val n = Offset(-dy / len, dx / len)
            val ripple = 0.025f * sin(centre[i].y * TAU * 1.4f - clock * TAU * 3f)
            val half = widths[i] / 2f
            left += centre[i] + n * (half + ripple)
            right += centre[i] - n * (half - ripple * 0.6f)
        }
        val ribbon = Path().apply {
            moveTo(left.first().x * c, left.first().y * c)
            for (p in left.drop(1)) lineTo(p.x * c, p.y * c)
            // Rounded lower end.
            val end = centre.last()
            quadraticTo(end.x * c, (end.y + widths.last() * 0.3f) * c, right.last().x * c, right.last().y * c)
            for (p in right.asReversed().drop(1)) lineTo(p.x * c, p.y * c)
            close()
        }
        val cx = centre.last().x * c
        drawPath(ribbon, Brush.horizontalGradient(listOf(look.deep, look.light, look.deep), cx - c * 0.35f, cx + c * 0.35f), alpha = 0.9f)
        // Bright streaks sliding down the stream, faster further down.
        for (k in 0 until 3) {
            val phase = (clock * 1.6f + k / 3f) % 1f
            val y0 = top + (bottom - top) * phase * phase
            val y1 = min(bottom, y0 + 0.3f + 0.4f * phase)
            val h0 = y0 - s.startY
            val h1 = y1 - s.startY
            val dx = (k - 1) * 0.04f
            drawLine(look.crest, Offset((x(h0) + dx) * c, y0 * c), Offset((x(h1) + dx) * c, y1 * c), strokeWidth = c * 0.022f, cap = StrokeCap.Round, alpha = look.crestAlpha * 0.6f)
        }
        if (look.glow != null) {
            val mid = Offset(cx, (top + bottom) / 2f * c)
            val r = (bottom - top) * c * 0.7f + c * 0.5f
            drawCircle(Brush.radialGradient(listOf(look.glow.copy(alpha = 0.18f), Color.Transparent), mid, r), r, mid)
        }
    }

    /**
     * Falling liquid without a source. A little is a thin falling thread, more is a short falling column
     * with rounded ends; never a drop shape.
     */
    private fun DrawScope.drawPiece(p: Piece, c: Float) {
        val look = lookOf(p.type)
        val cx = (p.x + 0.5f) * c
        if (p.size < 0.25f && p.bottom - p.top <= 1.05f) {
            val w = c * (0.04f + 0.07f * sqrt(p.size / 0.25f))
            val bottom = (p.bottom - 0.1f) * c
            val top = bottom - c * 0.8f
            drawLine(
                Brush.verticalGradient(listOf(look.light.copy(alpha = 0f), look.light, look.deep), top, bottom),
                Offset(cx, top + w),
                Offset(cx, bottom - w),
                strokeWidth = w * 2f,
                cap = StrokeCap.Round,
                alpha = p.alpha * 0.85f,
            )
            return
        }
        // The tail of a pour: thin at the top where it tore off, full and rounded at its lower end.
        val w = c * (0.18f + 0.4f * sqrt(p.size.coerceIn(0f, 1f)))
        val top = (p.top + 0.05f) * c
        val bottom = (p.bottom - 0.05f) * c
        val neck = top + (bottom - top) * 0.55f
        val body = Path().apply {
            moveTo(cx, top)
            cubicTo(cx + w * 0.12f, top + (neck - top) * 0.4f, cx + w / 2, neck - (neck - top) * 0.2f, cx + w / 2, neck)
            lineTo(cx + w / 2, bottom - w / 2)
            cubicTo(cx + w / 2, bottom, cx - w / 2, bottom, cx - w / 2, bottom - w / 2)
            lineTo(cx - w / 2, neck)
            cubicTo(cx - w / 2, neck - (neck - top) * 0.2f, cx - w * 0.12f, top + (neck - top) * 0.4f, cx, top)
            close()
        }
        drawPath(body, Brush.horizontalGradient(listOf(look.deep, look.light, look.deep), cx - w / 2, cx + w / 2), alpha = p.alpha * 0.92f)
        drawLine(look.crest, Offset(cx - w * 0.1f, neck), Offset(cx - w * 0.1f, bottom - w * 0.6f), strokeWidth = c * 0.02f, cap = StrokeCap.Round, alpha = p.alpha * 0.4f)
    }

    /** Where water hits solid ground: a thin film that spreads sideways and fades, no flying drops. */
    private fun DrawScope.drawSplash(s: Splash, c: Float) {
        val look = lookOf(s.type)
        val f = (s.age / 0.6f).coerceIn(0f, 1f)
        val strength = 0.4f + 0.6f * s.strength.coerceIn(0f, 1f)
        val half = (0.12f + 0.3f * f) * strength * c
        val y = s.at.y * c - c * 0.03f
        val x = s.at.x * c
        drawLine(look.light, Offset(x - half, y), Offset(x + half, y), strokeWidth = c * 0.05f * (1f - 0.6f * f), cap = StrokeCap.Round, alpha = 0.65f * (1f - f))
        drawLine(look.crest, Offset(x - half * 0.6f, y - c * 0.02f), Offset(x + half * 0.6f, y - c * 0.02f), strokeWidth = c * 0.015f, cap = StrokeCap.Round, alpha = 0.4f * (1f - f))
    }
}

/**
 * Liquid is falling if nothing holds it: free space, gas, falling liquid or a not yet full pool of its
 * own kind below. Water lying on water that spills over an edge is part of the spill.
 */
private fun isFalling(s: GameState, o: GameObject): Boolean {
    val below = o.position.down()
    if (!s.inBounds(below) || s.isWall(below)) return false
    val b = s.objectAt(below) ?: return true
    if (b.isGas || b.isCloud) return true
    if (b.isLiquid && o.density > b.density) return true
    if (b.type == o.type) {
        if (isFalling(s, b)) return true
        if (b.amount < b.capacity) {
            // Inside a settling pool the cells just top each other up; that is not a fall.
            val beside = listOf(o.position.left(), o.position.right()).any { s.objectAt(it)?.type == o.type }
            return !beside
        }
    }
    return false
}

private fun fill(o: GameObject): Float = (o.amount.toFloat() / o.capacity).coerceIn(0f, 1f)

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)

/** A smooth line through [points] (Catmull-Rom), [samples] points per segment. */
private fun catmullRom(points: List<Offset>, samples: Int): List<Offset> {
    if (points.size < 3) return points
    val out = ArrayList<Offset>(points.size * samples)
    for (i in 0 until points.size - 1) {
        val p0 = points[max(0, i - 1)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(points.size - 1, i + 2)]
        for (k in 0 until samples) {
            val t = k / samples.toFloat()
            val t2 = t * t
            val t3 = t2 * t
            fun axis(a: Float, b: Float, cc: Float, d: Float) =
                0.5f * (2f * b + (-a + cc) * t + (2f * a - 5f * b + 4f * cc - d) * t2 + (-a + 3f * b - 3f * cc + d) * t3)
            out += Offset(axis(p0.x, p1.x, p2.x, p3.x), axis(p0.y, p1.y, p2.y, p3.y))
        }
    }
    out += points.last()
    return out
}

/** Flames dancing on a burning liquid surface. */
private fun DrawScope.drawSurfaceFlames(tl: Offset, c: Float, lv: Float, time: Float) {
    val surface = tl.y + c * (1f - min(1f, lv))
    val glowCenter = Offset(tl.x + c / 2, surface)
    drawCircle(Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.45f), Color.Transparent), glowCenter, c * 0.9f), c * 0.9f, glowCenter)
    for (i in 0..2) {
        val fx = tl.x + c * (0.2f + 0.3f * i)
        val flicker = 0.75f + 0.3f * sin(time * TAU * 3f + i * 1.9f + tl.x / c)
        val h = c * 0.45f * flicker
        val flame = Path().apply {
            moveTo(fx, surface - h)
            cubicTo(fx + c * 0.12f, surface - h * 0.4f, fx + c * 0.1f, surface, fx, surface + c * 0.02f)
            cubicTo(fx - c * 0.1f, surface, fx - c * 0.12f, surface - h * 0.4f, fx, surface - h)
            close()
        }
        drawPath(flame, Palette.fire, alpha = 0.9f)
        drawCircle(Palette.fireCore, c * 0.04f, Offset(fx, surface - h * 0.2f), alpha = 0.9f)
    }
}

/**
 * Liquid in the player's hand: a rounded, gently sloshing body whose size follows the amount, so a
 * carried puddle looks like water and not like a box.
 */
internal fun DrawScope.drawCarriedLiquid(type: String, amount: Int, capacity: Int, center: Offset, c: Float, time: Float, alpha: Float = 0.95f) {
    val look = lookOf(type)
    val share = (amount.toFloat() / capacity.coerceAtLeast(1)).coerceIn(0.1f, 1f)
    val rx = c * (0.2f + 0.24f * sqrt(share))
    val ry = rx * 0.78f
    val slosh = sin(time * TAU * 2f)
    val steps = 24
    val body = Path()
    for (i in 0..steps) {
        val a = TAU * i / steps
        // A slightly flattened top that tilts back and forth, a full rounded bottom.
        val wobble = 1f + 0.06f * sin(a * 3f + time * TAU * 3f)
        var y = center.y + ry * sin(a) * wobble
        if (sin(a) < 0f) y = center.y + ry * sin(a) * 0.75f * wobble + slosh * c * 0.02f * cos(a)
        val x = center.x + rx * cos(a) * wobble
        if (i == 0) body.moveTo(x, y) else body.lineTo(x, y)
    }
    body.close()
    look.glow?.let { drawCircle(Brush.radialGradient(listOf(it.copy(alpha = 0.3f), Color.Transparent), center, rx * 2f), rx * 2f, center, alpha = alpha) }
    drawPath(body, Brush.verticalGradient(listOf(look.light, look.deep), center.y - ry, center.y + ry), alpha = alpha * look.alpha)
    drawLine(
        look.crest,
        Offset(center.x - rx * 0.45f, center.y - ry * 0.55f + slosh * c * 0.015f),
        Offset(center.x + rx * 0.2f, center.y - ry * 0.62f - slosh * c * 0.015f),
        strokeWidth = c * 0.03f,
        cap = StrokeCap.Round,
        alpha = alpha * look.crestAlpha,
    )
}
