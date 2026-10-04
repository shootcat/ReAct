package com.shootcat.react.ui.level

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (2 * PI).toFloat()

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
)

private val waterLook = LiquidLook(Palette.waterLight, Palette.water, Color.White, 0.55f, null, 1f)
private val seaLook = LiquidLook(Palette.seaLight, Palette.sea, Color(0xFFE8FFFB), 0.7f, null, 1.2f)
private val lavaLook = LiquidLook(Palette.lava, Palette.lavaDeep, Palette.fireCore, 0.8f, Palette.lava, 0.45f)

private fun lookOf(type: String): LiquidLook = when (type) {
    "LAVA" -> lavaLook
    "SEAWATER" -> seaLook
    else -> waterLook
}

/**
 * Draws all liquid as continuous bodies instead of single tiles – fresh water, sea water and lava each
 * in their own colours. Fill levels are interpolated between two simulation frames, so pools rise and
 * drain smoothly. Resting liquid gets a moving surface wave and streaks where it flows towards a lower
 * neighbour. Liquid in free fall is drawn where it really is between the two frames: a single splash of
 * water as a falling drop with a trail of droplets, a steady pour as one rippling ribbon that ends in
 * spray where it lands.
 */
internal fun DrawScope.drawLiquids(
    state: GameState,
    previous: GameState?,
    progress: Float,
    cell: Float,
    time: Float,
) {
    val types = (state.objects + previous?.objects.orEmpty()).filter { it.isLiquid }.map { it.type }.distinct().sorted()
    for (type in types) drawLiquid(type, state, previous, progress, cell, time)
}

private fun DrawScope.drawLiquid(
    type: String,
    state: GameState,
    previous: GameState?,
    progress: Float,
    cell: Float,
    time: Float,
) {
    val look = lookOf(type)
    val mine = state.objects.filter { it.isLiquid && it.type == type }
    val earlier = previous?.objects?.filter { it.isLiquid && it.type == type }.orEmpty()
    val earlierById = earlier.associateBy { it.id }

    fun hasNothingBelow(s: GameState, o: GameObject): Boolean {
        val below = o.position.down()
        return s.inBounds(below) && !s.isWall(below) && s.objectAt(below) == null
    }

    // Liquid in free fall: it has nothing below it now, or it dropped down a cell since the last frame.
    val falling = mine.filter { o ->
        val before = earlierById[o.id]
        hasNothingBelow(state, o) || (before != null && before.position.x == o.position.x && before.position.y < o.position.y)
    }
    val fallingIds = falling.map { it.id }.toSet()
    // Falling liquid of the last frame that has landed in a pool since then (it merged into it).
    val landed = earlier.filter { o ->
        state.objectById(o.id) == null && previous != null && hasNothingBelow(previous, o) &&
            state.objectAt(o.position.down())?.type == type
    }
    val landedIds = landed.map { it.id }.toSet()

    val now = mine.filter { it.id !in fallingIds }.associate { it.position to it.amount.toFloat() / it.capacity }
    val before = previous?.let {
        earlier.filter { it.id !in fallingIds && it.id !in landedIds }.associate { it.position to it.amount.toFloat() / it.capacity }
    }
    val positions = now.keys + (before?.keys ?: emptySet())

    fun level(p: Position): Float {
        val current = now[p] ?: 0f
        if (before == null) return current
        val was = before[p] ?: 0f
        return was + (current - was) * progress
    }

    val sorted = positions.sortedWith(compareBy({ it.y }, { it.x }))
    if (look.glow != null) {
        // Heat shimmer around glowing liquid.
        for (p in sorted) {
            if (level(p) <= 0.02f) continue
            val center = Offset((p.x + 0.5f) * cell, (p.y + 0.6f) * cell)
            val r = cell * (1.1f + 0.08f * sin(time * TAU * 2f + p.x))
            drawCircle(Brush.radialGradient(listOf(look.glow.copy(alpha = 0.22f), Color.Transparent), center, r), r, center)
        }
    }
    for (p in sorted) {
        val lv = level(p)
        if (lv <= 0.02f) continue
        val tl = Offset(p.x * cell, p.y * cell)
        val fedFromAbove = level(p.up()) > 0.02f
        drawPool(
            look = look,
            tl = tl,
            c = cell,
            lv = lv,
            time = time,
            full = fedFromAbove,
            leftLevel = level(p.left()).takeIf { it > 0.02f && !state.isWall(p.left()) },
            rightLevel = level(p.right()).takeIf { it > 0.02f && !state.isWall(p.right()) },
        )
        if (type == "LAVA") drawCrust(tl, cell, lv, time)
    }

    // Falling liquid, drawn at its position between the two frames.
    val falls = falling.map { o ->
        val from = earlierById[o.id]?.position?.takeIf { it.x == o.position.x && it.y < o.position.y } ?: o.position
        val y = from.y + (o.position.y - from.y) * progress
        Fall(o.position.x, y, o.amount.toFloat() / o.capacity, 1f)
    } + landed.map { o ->
        Fall(o.position.x, o.position.y + progress, o.amount.toFloat() / o.capacity, 1f - progress)
    }
    // Neighbouring falling cells in one column form a continuous pour.
    for ((x, column) in falls.groupBy { it.x }) {
        val runs = mutableListOf<MutableList<Fall>>()
        for (f in column.sortedBy { it.y }) {
            val run = runs.lastOrNull()
            if (run != null && f.y - run.last().y <= 1.05f) run += f else runs += mutableListOf(f)
        }
        for (run in runs) {
            val top = run.first()
            val sourceAbove = level(Position(x, kotlin.math.floor(top.y).toInt() - 1)) > 0.02f ||
                level(Position(x, kotlin.math.ceil(top.y).toInt() - 1)) > 0.02f
            if (run.size == 1 && !sourceAbove) {
                drawDrop(look, x, top.y, top.size, top.alpha, cell, time)
            } else {
                drawPour(look, x, if (sourceAbove) kotlin.math.floor(top.y) else top.y, run, cell, time)
            }
            val last = run.last()
            val landing = Position(x, kotlin.math.floor(last.y).toInt() + 1)
            val target = state.objectAt(landing)
            if (state.isWall(landing) || (target != null && !target.isGas)) {
                drawSpray(look, Offset((x + 0.5f) * cell, (last.y + 1f) * cell), cell, time, last.size)
            }
        }
    }
    for (o in mine) {
        if (o.state != "BURNING" || o.id in fallingIds || level(o.position.up()) > 0.02f) continue
        drawSurfaceFlames(Offset(o.position.x * cell, o.position.y * cell), cell, level(o.position), time)
    }
}

/** Liquid in free fall in column [x], [y] cells from the top (between two frames), [size] of a full cell. */
private data class Fall(val x: Int, val y: Float, val size: Float, val alpha: Float)

/** A single splash of liquid in free fall: a round drop with a pointed top and droplets trailing behind it. */
private fun DrawScope.drawDrop(look: LiquidLook, x: Int, y: Float, size: Float, alpha: Float, c: Float, time: Float) {
    val r = c * (0.12f + 0.2f * sqrt(size.coerceIn(0f, 1f)))
    val cx = (x + 0.5f) * c + c * 0.02f * sin(time * TAU * 2f + x)
    val cy = (y + 0.62f) * c
    val drop = Path().apply {
        moveTo(cx, cy - r * 2.3f)
        cubicTo(cx + r * 0.35f, cy - r * 1.3f, cx + r, cy - r * 0.7f, cx + r, cy)
        cubicTo(cx + r, cy + r * 0.6f, cx + r * 0.55f, cy + r, cx, cy + r)
        cubicTo(cx - r * 0.55f, cy + r, cx - r, cy + r * 0.6f, cx - r, cy)
        cubicTo(cx - r, cy - r * 0.7f, cx - r * 0.35f, cy - r * 1.3f, cx, cy - r * 2.3f)
        close()
    }
    drawPath(drop, Brush.verticalGradient(listOf(look.light, look.deep), cy - r * 2.3f, cy + r), alpha = alpha * 0.92f)
    drawCircle(Color.White, r * 0.28f, Offset(cx - r * 0.35f, cy - r * 0.25f), alpha = alpha * 0.7f)
    for (k in 1..3) {
        val dy = r * (2.3f + 0.9f * k) + c * 0.04f * sin(time * TAU * 3f + k)
        drawCircle(look.light, r * (0.32f - 0.07f * k), Offset(cx + (if (k % 2 == 0) 1 else -1) * r * 0.25f, cy - dy), alpha = alpha * (0.75f - 0.18f * k))
    }
}

/**
 * A steady pour from [startY] down through the cells of [run]: one ribbon whose width follows how much
 * is falling, with ripples running down its edges and a bright core.
 */
private fun DrawScope.drawPour(look: LiquidLook, x: Int, startY: Float, run: List<Fall>, c: Float, time: Float) {
    val endY = run.last().y + 1f
    val top = startY * c
    val bottom = endY * c
    val cx = (x + 0.5f) * c
    fun width(yCells: Float): Float {
        val f = run.minByOrNull { kotlin.math.abs(it.y + 0.5f - yCells) } ?: run.last()
        return c * (0.16f + 0.42f * sqrt(f.size.coerceIn(0f, 1f)))
    }
    val steps = ((bottom - top) / (c * 0.12f)).toInt().coerceAtLeast(4)
    val left = ArrayList<Offset>(steps + 1)
    val right = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val y = top + (bottom - top) * i / steps
        val yc = y / c
        // The stream narrows as it accelerates away from the lip.
        val narrowing = 1f - 0.25f * ((y - top) / c).coerceIn(0f, 1f)
        val w = width(yc) * narrowing / 2f
        val ripple = c * 0.03f * sin(yc * TAU * 1.6f - time * TAU * 4f)
        left += Offset(cx - w + ripple, y)
        right += Offset(cx + w + ripple * 0.6f, y)
    }
    val ribbon = Path().apply {
        moveTo(left.first().x, left.first().y)
        left.drop(1).forEach { lineTo(it.x, it.y) }
        right.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    val alpha = run.maxOf { it.alpha }
    drawPath(ribbon, Brush.horizontalGradient(listOf(look.deep, look.light, look.deep), cx - c * 0.3f, cx + c * 0.3f), alpha = alpha * 0.88f)
    // Bright streaks sliding down the pour.
    for (k in 0 until 3) {
        val phase = (time * 2.5f + k / 3f) % 1f
        val y0 = top + (bottom - top) * phase
        val y1 = (y0 + c * 0.35f).coerceAtMost(bottom)
        val dx = (k - 1) * c * 0.05f
        drawLine(look.crest, Offset(cx + dx, y0), Offset(cx + dx, y1), strokeWidth = c * 0.025f, cap = StrokeCap.Round, alpha = alpha * look.crestAlpha * 0.7f)
    }
}

/** Spray and rings where falling liquid hits the ground or a pool. */
private fun DrawScope.drawSpray(look: LiquidLook, at: Offset, c: Float, time: Float, size: Float) {
    val strength = 0.5f + 0.5f * size.coerceIn(0f, 1f)
    for (k in 0 until 2) {
        val phase = (time * 2.5f + k * 0.5f) % 1f
        val r = c * (0.12f + 0.3f * phase) * strength
        drawOval(
            look.crest,
            topLeft = Offset(at.x - r, at.y - r * 0.25f),
            size = Size(r * 2, r * 0.5f),
            alpha = 0.45f * (1f - phase),
            style = Stroke(width = c * 0.02f),
        )
    }
    for (k in 0 until 4) {
        val phase = (time * 3f + k * 0.27f) % 1f
        val dir = if (k % 2 == 0) 1f else -1f
        val spread = c * (0.12f + 0.1f * k) * phase * strength
        val lift = c * 0.3f * strength * phase * (1f - phase) * 4f * 0.5f
        drawCircle(look.light, c * 0.035f, Offset(at.x + dir * spread, at.y - lift), alpha = 0.8f * (1f - phase))
    }
}

/** Dark crust plates drifting on lava. */
private fun DrawScope.drawCrust(tl: Offset, c: Float, lv: Float, time: Float) {
    val top = tl.y + c * (1f - min(1f, lv))
    for (k in 0 until 2) {
        val phase = (time * 0.3f + k * 0.5f + tl.x / c * 0.21f) % 1f
        val x = tl.x + c * (0.15f + 0.7f * phase)
        drawOval(
            Palette.lavaCrust,
            topLeft = Offset(x - c * 0.12f, top + c * 0.06f + k * c * 0.18f),
            size = Size(c * 0.24f, c * 0.08f),
            alpha = 0.55f,
        )
    }
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

private fun DrawScope.drawPool(
    look: LiquidLook,
    tl: Offset,
    c: Float,
    lv: Float,
    time: Float,
    full: Boolean,
    leftLevel: Float?,
    rightLevel: Float?,
) {
    val bottom = tl.y + c
    fun surfaceY(level: Float) = tl.y + c * (1f - min(1f, level))
    val own = if (full) tl.y else surfaceY(lv)
    // Meet the neighbours halfway so the surface is one continuous line.
    val leftY = if (full) tl.y else leftLevel?.let { (own + surfaceY(it)) / 2f } ?: own
    val rightY = if (full) tl.y else rightLevel?.let { (own + surfaceY(it)) / 2f } ?: own
    val amplitude = if (full) 0f else c * 0.04f * min(1f, lv * 2.5f) * look.waveScale

    fun waveY(x: Float, baseY: Float): Float {
        val u = x / c
        val t = time * look.waveScale
        return baseY + amplitude * sin(u * TAU * 0.8f + t * TAU) +
            amplitude * 0.45f * sin(u * TAU * 2.1f - t * TAU * 2f)
    }

    val steps = 10
    val points = (0..steps).map { i ->
        val f = i / steps.toFloat()
        val x = tl.x + c * f
        val base = if (f < 0.5f) leftY + (own - leftY) * (f * 2f) else own + (rightY - own) * ((f - 0.5f) * 2f)
        Offset(x, waveY(x, base))
    }
    val body = Path().apply {
        moveTo(tl.x, bottom)
        points.forEach { lineTo(it.x, it.y) }
        lineTo(tl.x + c, bottom)
        close()
    }
    val top = points.minOf { it.y }
    drawPath(
        body,
        Brush.verticalGradient(listOf(look.light, look.deep), top - c * 0.2f, bottom + c * 0.8f),
        alpha = if (look === waterLook) 0.9f else 0.97f,
    )
    if (!full) {
        val crest = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(crest, look.crest, alpha = look.crestAlpha, style = Stroke(width = c * 0.03f, cap = StrokeCap.Round))
        // Flow streaks run downhill along a sloped surface.
        val slope = rightY - leftY
        if (abs(slope) > c * 0.04f) {
            val dir = if (slope > 0) 1f else -1f
            for (k in 0 until 2) {
                val phase = (time * 2f + k * 0.5f) % 1f
                val x = tl.x + c * (if (dir > 0) phase else 1f - phase)
                val y = waveY(x, if (x < tl.x + c / 2) leftY + (own - leftY) * ((x - tl.x) / (c / 2)) else own + (rightY - own) * ((x - tl.x - c / 2) / (c / 2))) + c * 0.08f
                drawLine(Color.White, Offset(x - dir * c * 0.08f, y), Offset(x + dir * c * 0.08f, y), strokeWidth = c * 0.025f, cap = StrokeCap.Round, alpha = 0.35f)
            }
        }
    }
    // Light glinting deeper in the water.
    val shimmerX = tl.x + c * (0.2f + 0.6f * ((time + tl.x / c * 0.37f) % 1f))
    val shimmerY = max(top + c * 0.25f, bottom - c * 0.3f)
    if (shimmerY < bottom - c * 0.05f) {
        drawLine(Color.White, Offset(shimmerX - c * 0.07f, shimmerY), Offset(shimmerX + c * 0.07f, shimmerY), strokeWidth = c * 0.02f, cap = StrokeCap.Round, alpha = 0.2f)
    }
}
