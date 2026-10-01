package com.shootcat.react.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small vector glyphs drawn by hand, so the app needs no icon library and no emoji fonts. */
enum class Glyph {
    PLAY, UNDO, REDO, RESET, BACK, NEXT, LOG, CHECK, LOCK, GEAR, SPARK, BOLT, MINIMAL, TARGET, PLUS, ARROW,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private fun DrawScope.triangle(left: Float, right: Float, pointsRight: Boolean): Path {
    val h = size.height
    return Path().apply {
        if (pointsRight) {
            moveTo(left, h * 0.2f)
            lineTo(right, h * 0.5f)
            lineTo(left, h * 0.8f)
        } else {
            moveTo(right, h * 0.2f)
            lineTo(left, h * 0.5f)
            lineTo(right, h * 0.8f)
        }
        close()
    }
}

/** A curved arrow that turns back on itself: undo points left, redo right. */
private fun DrawScope.drawTurnArrow(color: Color, stroke: Stroke, towardsLeft: Boolean) {
    val w = size.width
    val h = size.height
    fun x(f: Float) = if (towardsLeft) w * f else w * (1f - f)
    val curve = Path().apply {
        moveTo(x(0.78f), h * 0.78f)
        cubicTo(x(0.86f), h * 0.42f, x(0.64f), h * 0.3f, x(0.34f), h * 0.36f)
    }
    drawPath(curve, color, style = stroke)
    val head = Path().apply {
        moveTo(x(0.14f), h * 0.37f)
        lineTo(x(0.4f), h * 0.16f)
        lineTo(x(0.42f), h * 0.56f)
        close()
    }
    drawPath(head, color)
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
        Glyph.PLAY -> drawPath(triangle(w * 0.28f, w * 0.82f, true), color)
        Glyph.UNDO -> drawTurnArrow(color, stroke, towardsLeft = true)
        Glyph.REDO -> drawTurnArrow(color, stroke, towardsLeft = false)
        Glyph.RESET -> {
            drawArc(
                color = color,
                startAngle = -40f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = stroke,
            )
            val tip = Path().apply {
                moveTo(w * 0.66f, h * 0.08f)
                lineTo(w * 0.86f, h * 0.3f)
                lineTo(w * 0.58f, h * 0.36f)
                close()
            }
            drawPath(tip, color)
        }
        Glyph.BACK -> {
            val arrow = Path().apply {
                moveTo(w * 0.8f, h * 0.5f)
                lineTo(w * 0.22f, h * 0.5f)
                moveTo(w * 0.46f, h * 0.24f)
                lineTo(w * 0.2f, h * 0.5f)
                lineTo(w * 0.46f, h * 0.76f)
            }
            drawPath(arrow, color, style = stroke)
        }
        Glyph.NEXT -> {
            val arrow = Path().apply {
                moveTo(w * 0.2f, h * 0.5f)
                lineTo(w * 0.78f, h * 0.5f)
                moveTo(w * 0.54f, h * 0.24f)
                lineTo(w * 0.8f, h * 0.5f)
                lineTo(w * 0.54f, h * 0.76f)
            }
            drawPath(arrow, color, style = stroke)
        }
        Glyph.ARROW -> {
            val arrow = Path().apply {
                moveTo(w * 0.18f, h * 0.5f)
                lineTo(w * 0.8f, h * 0.5f)
                moveTo(w * 0.58f, h * 0.3f)
                lineTo(w * 0.82f, h * 0.5f)
                lineTo(w * 0.58f, h * 0.7f)
            }
            drawPath(arrow, color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.PLUS -> {
            val t = w * 0.08f
            drawLine(color, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.72f), strokeWidth = t, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), strokeWidth = t, cap = StrokeCap.Round)
        }
        Glyph.GEAR -> {
            val center = Offset(w / 2, h / 2)
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                drawLine(
                    color,
                    center + Offset(cos(a) * w * 0.26f, sin(a) * h * 0.26f),
                    center + Offset(cos(a) * w * 0.42f, sin(a) * h * 0.42f),
                    strokeWidth = w * 0.15f,
                )
            }
            drawCircle(color, w * 0.22f, center, style = Stroke(width = w * 0.13f))
        }
        Glyph.SPARK -> {
            val star = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                lineTo(w * 0.6f, h * 0.4f)
                lineTo(w * 0.92f, h * 0.5f)
                lineTo(w * 0.6f, h * 0.6f)
                lineTo(w * 0.5f, h * 0.92f)
                lineTo(w * 0.4f, h * 0.6f)
                lineTo(w * 0.08f, h * 0.5f)
                lineTo(w * 0.4f, h * 0.4f)
                close()
            }
            drawPath(star, color)
        }
        Glyph.BOLT -> {
            val bolt = Path().apply {
                moveTo(w * 0.6f, h * 0.06f)
                lineTo(w * 0.22f, h * 0.56f)
                lineTo(w * 0.48f, h * 0.56f)
                lineTo(w * 0.4f, h * 0.94f)
                lineTo(w * 0.8f, h * 0.4f)
                lineTo(w * 0.54f, h * 0.4f)
                close()
            }
            drawPath(bolt, color)
        }
        Glyph.MINIMAL -> {
            drawCircle(color, w * 0.36f, Offset(w / 2, h / 2), style = Stroke(width = w * 0.08f))
            drawCircle(color, w * 0.14f, Offset(w / 2, h / 2))
        }
        Glyph.TARGET -> {
            drawCircle(color, w * 0.38f, Offset(w / 2, h / 2), style = Stroke(width = w * 0.09f))
            drawCircle(color, w * 0.2f, Offset(w / 2, h / 2), style = Stroke(width = w * 0.09f))
            drawCircle(color, w * 0.06f, Offset(w / 2, h / 2))
        }
        Glyph.LOG -> {
            drawRoundRect(color, Offset(w * 0.2f, h * 0.14f), Size(w * 0.6f, h * 0.72f), CornerRadius(w * 0.08f), style = stroke)
            for (i in 0..2) {
                val y = h * (0.36f + i * 0.15f)
                drawLine(color, Offset(w * 0.34f, y), Offset(w * 0.66f, y), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
        }
        Glyph.CHECK -> {
            val check = Path().apply {
                moveTo(w * 0.2f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.74f)
                lineTo(w * 0.82f, h * 0.28f)
            }
            drawPath(check, color, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.LOCK -> {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.32f, h * 0.16f),
                size = Size(w * 0.36f, h * 0.4f),
                style = stroke,
            )
            drawRoundRect(color, Offset(w * 0.24f, h * 0.42f), Size(w * 0.52f, h * 0.42f), CornerRadius(w * 0.08f))
        }
    }
}
