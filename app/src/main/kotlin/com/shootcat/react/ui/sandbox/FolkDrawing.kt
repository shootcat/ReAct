package com.shootcat.react.ui.sandbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.SimRunner
import kotlin.math.cos
import kotlin.math.sin

/*
 * The inhabitants and their village (Phase 5) as the app draws them: little figures over the world, stylised, never
 * shown hurt. The simulation only says where a figure's feet are, which way it looks and what it does.
 */

private val SKIN = Color(0xFFF2C29B)
private val HAIR = Color(0xFF5A3A22)
private val TROUSERS = Color(0xFF3D3A4B)
private val TUNICS = listOf(Color(0xFF2F80ED), Color(0xFFE0533D), Color(0xFF27AE60), Color(0xFFF2A93B), Color(0xFF9B51E0), Color(0xFF00A3A3))
private val STAR = Color(0xFFFFE066)

/** The village and every figure of frame [f]; [o] is where the world's corner is on screen, [s] the size of a cell. */
internal fun DrawScope.drawFolk(f: SimRunner.Frame, o: Offset, s: Float) {
    if (f.homeX >= 0) drawVillage(o.x + (f.homeX + 0.5f) * s, o.y + (f.homeY + 1) * s, s)
    for (k in 0 until f.folkCount) {
        drawFigure(
            o.x + (f.folkX[k] + 0.5f) * s, o.y + (f.folkY[k] + 1) * s, s,
            f.folkDir[k], f.folkState[k], f.folkTimer[k], f.folkId[k], f.step,
        )
    }
}

/**
 * One figure standing with the soles of its feet at ([x], [y]) on screen, [s] a cell: [Folk.HEIGHT] cells tall.
 * Walking its legs and arms swing; falling it throws up its arms; swimming only head and arms are over the water;
 * fainted it lies on its back with stars over it; brought back to the village it fades in.
 */
internal fun DrawScope.drawFigure(x: Float, y: Float, s: Float, dir: Int, state: Int, timer: Int, id: Int, step: Int, alpha: Float = 1f) {
    val tunic = TUNICS[(id - 1).mod(TUNICS.size)]
    val a = when (state) {
        Folk.BACK -> alpha * (1f - timer.toFloat() / Folk.BACK_STEPS).coerceIn(0.15f, 1f)
        else -> alpha
    }
    val limb = s * 0.32f
    when (state) {
        Folk.FAINT -> {
            // on its back on the ground, the head to the side it went, and stars going round over it
            val hx = x + dir * s * 1.3f
            drawLine(TROUSERS.copy(alpha = a), Offset(x - dir * s * 1.6f, y - s * 0.3f), Offset(x - dir * s * 0.2f, y - s * 0.35f), strokeWidth = limb, cap = StrokeCap.Round)
            drawRoundRect(tunic.copy(alpha = a), Offset(x - s * 0.7f + dir * s * 0.2f, y - s * 0.75f), Size(s * 1.4f, s * 0.6f), CornerRadius(s * 0.2f))
            drawCircle(SKIN.copy(alpha = a), s * 0.5f, Offset(hx, y - s * 0.5f))
            for (j in 0 until 3) {
                val t = (step * 0.08f) + j * 2.094f
                star(Offset(hx + cos(t) * s * 0.9f, y - s * 1.6f + sin(t) * s * 0.3f), s * 0.28f, STAR.copy(alpha = a))
            }
        }
        Folk.SWIM, Folk.SINK -> {
            // the head over the water (lower when it goes under), arms reaching out by turns
            val sink = if (state == Folk.SINK) s * (0.6f + 0.4f * (timer % 30) / 30f) else 0f
            val head = Offset(x, y - s * 3.4f + sink)
            val stroke = if (((step + id) / 10) % 2 == 0) 1f else -1f
            drawLine(SKIN.copy(alpha = a), Offset(x, y - s * 2.7f + sink), Offset(x + dir * s * 0.9f * stroke, y - s * 3.1f + sink), strokeWidth = limb, cap = StrokeCap.Round)
            drawLine(SKIN.copy(alpha = a), Offset(x, y - s * 2.7f + sink), Offset(x - dir * s * 0.9f * stroke, y - s * 2.6f + sink), strokeWidth = limb, cap = StrokeCap.Round)
            drawCircle(SKIN.copy(alpha = a), s * 0.5f, head)
            hair(head, s, dir, a)
            if (state == Folk.SINK) for (j in 0 until 3) {
                drawCircle(Color.White.copy(alpha = 0.6f * a), s * (0.12f + 0.06f * j), Offset(x + (j - 1) * s * 0.4f, head.y - s * (0.9f + j * 0.5f + (timer % 20) / 40f)))
            }
        }
        else -> {
            val walking = state == Folk.WALK
            val falling = state == Folk.FALL
            val swing = if (walking && ((step + id) / Folk.WALK_EVERY) % 2 == 0) s * 0.35f else if (walking) -s * 0.35f else 0f
            val hip = Offset(x, y - s * 1.35f)
            // legs
            drawLine(TROUSERS.copy(alpha = a), hip, Offset(x + swing, y - s * 0.05f), strokeWidth = limb, cap = StrokeCap.Round)
            drawLine(TROUSERS.copy(alpha = a), hip, Offset(x - swing, y - s * 0.05f), strokeWidth = limb, cap = StrokeCap.Round)
            // body
            drawRoundRect(tunic.copy(alpha = a), Offset(x - s * 0.45f, y - s * 2.75f), Size(s * 0.9f, s * 1.5f), CornerRadius(s * 0.25f))
            // arms: swinging as it walks, up over the head as it falls
            val shoulder = Offset(x, y - s * 2.5f)
            if (falling) {
                drawLine(SKIN.copy(alpha = a), shoulder, Offset(x - s * 0.6f, y - s * 3.9f), strokeWidth = limb, cap = StrokeCap.Round)
                drawLine(SKIN.copy(alpha = a), shoulder, Offset(x + s * 0.6f, y - s * 3.9f), strokeWidth = limb, cap = StrokeCap.Round)
            } else {
                drawLine(SKIN.copy(alpha = a), shoulder, Offset(x - swing * 0.8f, y - s * 1.55f), strokeWidth = limb * 0.9f, cap = StrokeCap.Round)
            }
            // head, hair, an eye looking where it goes
            val head = Offset(x + dir * s * 0.05f, y - s * 3.3f)
            drawCircle(SKIN.copy(alpha = a), s * 0.5f, head)
            hair(head, s, dir, a)
            drawCircle(Color(0xFF2B2B2B).copy(alpha = a), s * 0.08f, Offset(head.x + dir * s * 0.25f, head.y - s * 0.02f))
        }
    }
}

private fun DrawScope.hair(head: Offset, s: Float, dir: Int, a: Float) {
    drawArc(
        HAIR.copy(alpha = a), startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(head.x - s * 0.52f - dir * s * 0.05f, head.y - s * 0.55f), size = Size(s * 1.04f, s * 0.8f),
    )
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (j in 0 until 10) {
        val t = -Math.PI / 2 + j * Math.PI / 5
        val rr = if (j % 2 == 0) r else r * 0.45f
        val px = c.x + (cos(t) * rr).toFloat()
        val py = c.y + (sin(t) * rr).toFloat()
        if (j == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    drawPath(p, color)
}

/** The village: a little hut with a flag beside it, its door on the ground at ([x], [y]). */
internal fun DrawScope.drawVillage(x: Float, y: Float, s: Float) {
    val wall = Color(0xFFB07A48)
    val roof = Color(0xFFB23A2E)
    drawRect(wall, Offset(x - s * 2.5f, y - s * 3f), Size(s * 5f, s * 3f))
    val r = Path().apply {
        moveTo(x - s * 3.2f, y - s * 3f)
        lineTo(x, y - s * 5.4f)
        lineTo(x + s * 3.2f, y - s * 3f)
        close()
    }
    drawPath(r, roof)
    drawRect(Color(0xFF5A3A22), Offset(x - s * 0.6f, y - s * 1.8f), Size(s * 1.2f, s * 1.8f))
    // a flag on a pole: the goal
    drawLine(Color(0xFF6B6B6B), Offset(x + s * 4f, y), Offset(x + s * 4f, y - s * 7f), strokeWidth = s * 0.25f)
    val flag = Path().apply {
        moveTo(x + s * 4f, y - s * 7f)
        lineTo(x + s * 6.4f, y - s * 6.3f)
        lineTo(x + s * 4f, y - s * 5.6f)
        close()
    }
    drawPath(flag, Color(0xFFF2A93B))
}

/** The tool for putting down inhabitants: a little figure. */
@Composable
internal fun FolkIcon() {
    Canvas(Modifier.size(30.dp)) {
        val u = size.width
        drawFigure(u * 0.5f, u * 0.95f, u * 0.22f, 1, Folk.WALK, 0, 1, 0)
    }
}

/** The tool for the village: the hut with its flag. */
@Composable
internal fun VillageIcon() {
    Canvas(Modifier.size(30.dp)) {
        val u = size.width
        drawVillage(u * 0.38f, u * 0.92f, u * 0.11f)
    }
}
