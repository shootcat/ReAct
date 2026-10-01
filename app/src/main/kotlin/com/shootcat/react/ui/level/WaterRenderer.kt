package com.shootcat.react.ui.level

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** How one kind of liquid looks. */
private class LiquidLook(
    val light: Color,
    val deep: Color,
    val crest: Color,
    val crestAlpha: Float,
    /** Glowing liquids (lava) light up their surroundings. */
    val glow: Color?,
    /** Thick liquids (lava, oil) move in slower, smaller waves. */
    val waveScale: Float,
)

private val waterLook = LiquidLook(Palette.waterLight, Palette.water, Color.White, 0.55f, null, 1f)
private val lavaLook = LiquidLook(Palette.lava, Palette.lavaDeep, Palette.fireCore, 0.8f, Palette.lava, 0.45f)
private val oilLook = LiquidLook(Palette.oilLight, Palette.oil, Palette.oilLight, 0.7f, null, 0.6f)

private fun lookOf(type: String): LiquidLook = when (type) {
    "LAVA" -> lavaLook
    "OIL" -> oilLook
    else -> waterLook
}

/**
 * Draws all liquid as continuous bodies instead of single tiles – water, lava and oil each in their own
 * colours. Fill levels are interpolated between two simulation frames, so pools rise and drain smoothly.
 * Resting liquid gets a moving surface wave and streaks where it flows towards a lower neighbour;
 * liquid with nothing below it is drawn as a falling stream with droplets and splashes where it lands.
 * Burning oil carries flames on its surface.
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
    val now = levels(state, type)
    val before = previous?.let { levels(it, type) }
    val positions = now.keys + (before?.keys ?: emptySet())

    fun level(p: Position): Float {
        val current = now[p] ?: 0f
        if (before == null) return current
        val earlier = before[p] ?: 0f
        return earlier + (current - earlier) * progress
    }

    fun isFalling(p: Position): Boolean {
        val below = p.down()
        return state.inBounds(below) && !state.isWall(below) && state.objectAt(below) == null && (now[p] ?: 0f) > 0f
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
        if (isFalling(p)) {
            drawStream(look, tl, cell, lv, time, capped = level(p.up()) <= 0.02f)
        } else {
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
            if (fedFromAbove && isFalling(p.up())) drawSplash(tl, cell, time)
        }
        if (type == "LAVA") drawCrust(tl, cell, lv, time)
        val o = state.objectAt(p)
        if (o != null && o.type == type && o.state == "BURNING" && level(p.up()) <= 0.02f) {
            drawSurfaceFlames(tl, cell, lv, time)
        }
    }
}

private fun levels(state: GameState, type: String): Map<Position, Float> =
    state.objects.filter { it.isLiquid && it.type == type }.associate { it.position to it.amount.toFloat() / it.capacity }

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

/** Flames dancing on a burning oil surface. */
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

/** Liquid with nothing below it: a narrowing stream with droplets. */
private fun DrawScope.drawStream(look: LiquidLook, tl: Offset, c: Float, lv: Float, time: Float, capped: Boolean) {
    val width = c * (0.22f + 0.5f * min(1f, lv))
    val cx = tl.x + c / 2 + c * 0.03f * sin(time * TAU * 3f + tl.y / c)
    val top = if (capped) tl.y + c * (1f - min(1f, lv)) * 0.6f else tl.y
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(look.light, look.deep), top, tl.y + c),
        topLeft = Offset(cx - width / 2, top),
        size = Size(width, tl.y + c - top),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2),
        alpha = 0.85f,
    )
    drawLine(Color.White, Offset(cx - width * 0.2f, top + c * 0.1f), Offset(cx - width * 0.2f, tl.y + c), strokeWidth = c * 0.025f, alpha = 0.4f)
    for (k in 0 until 3) {
        val phase = (time * 3f + k / 3f) % 1f
        val x = cx + (k - 1) * width * 0.6f
        val y = tl.y + c * phase
        drawCircle(look.light, c * 0.045f, Offset(x, y), alpha = 0.7f * (1f - phase * 0.5f))
    }
}

/** Rings where a stream hits a pool. */
private fun DrawScope.drawSplash(tl: Offset, c: Float, time: Float) {
    for (k in 0 until 2) {
        val phase = (time * 2.5f + k * 0.5f) % 1f
        val r = c * (0.12f + 0.3f * phase)
        drawOval(
            Color.White,
            topLeft = Offset(tl.x + c / 2 - r, tl.y + c * 0.04f - r * 0.25f),
            size = Size(r * 2, r * 0.5f),
            alpha = 0.45f * (1f - phase),
            style = Stroke(width = c * 0.02f),
        )
    }
}
