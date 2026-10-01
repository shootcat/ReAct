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

/**
 * Draws all liquid as one continuous body instead of single tiles.
 *
 * Fill levels are interpolated between two simulation frames, so pools rise and drain smoothly.
 * Resting water gets a moving surface wave and streaks where it flows towards a lower neighbour;
 * water with nothing below it is drawn as a falling stream with droplets and splashes where it lands.
 */
internal fun DrawScope.drawLiquids(
    state: GameState,
    previous: GameState?,
    progress: Float,
    cell: Float,
    time: Float,
) {
    val now = levels(state)
    val before = previous?.let { levels(it) }
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

    for (p in positions.sortedWith(compareBy({ it.y }, { it.x }))) {
        val lv = level(p)
        if (lv <= 0.02f) continue
        val tl = Offset(p.x * cell, p.y * cell)
        if (isFalling(p)) {
            drawStream(tl, cell, lv, time, capped = level(p.up()) <= 0.02f)
        } else {
            val fedFromAbove = level(p.up()) > 0.02f
            drawPool(
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
    }
}

private fun levels(state: GameState): Map<Position, Float> =
    state.objects.filter { it.isLiquid }.associate { it.position to it.amount.toFloat() / it.capacity }

private fun DrawScope.drawPool(
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
    val amplitude = if (full) 0f else c * 0.04f * min(1f, lv * 2.5f)

    fun waveY(x: Float, baseY: Float): Float {
        val u = x / c
        return baseY + amplitude * sin(u * TAU * 0.8f + time * TAU) +
            amplitude * 0.45f * sin(u * TAU * 2.1f - time * TAU * 2f)
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
        Brush.verticalGradient(listOf(Palette.waterLight, Palette.water), top - c * 0.2f, bottom + c * 0.8f),
        alpha = 0.9f,
    )
    if (!full) {
        val crest = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(crest, Color.White, alpha = 0.55f, style = Stroke(width = c * 0.03f, cap = StrokeCap.Round))
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

/** Water with nothing below it: a narrowing stream with droplets. */
private fun DrawScope.drawStream(tl: Offset, c: Float, lv: Float, time: Float, capped: Boolean) {
    val width = c * (0.22f + 0.5f * min(1f, lv))
    val cx = tl.x + c / 2 + c * 0.03f * sin(time * TAU * 3f + tl.y / c)
    val top = if (capped) tl.y + c * (1f - min(1f, lv)) * 0.6f else tl.y
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Palette.waterLight, Palette.water), top, tl.y + c),
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
        drawCircle(Palette.waterLight, c * 0.045f, Offset(x, y), alpha = 0.7f * (1f - phase * 0.5f))
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
