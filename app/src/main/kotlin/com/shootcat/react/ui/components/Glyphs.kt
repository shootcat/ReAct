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

/** Small vector glyphs drawn by hand, so the app needs no icon library and no emoji fonts. */
enum class Glyph { PLAY, PAUSE, STEP_FORWARD, STEP_BACK, TO_START, TO_END, RESET, BACK, LOG, EDIT, CHECK, LOCK }

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

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val bar = Size(w * 0.13f, h * 0.6f)
    val radius = CornerRadius(w * 0.04f)
    when (glyph) {
        Glyph.PLAY -> drawPath(triangle(w * 0.28f, w * 0.82f, true), color)
        Glyph.PAUSE -> {
            drawRoundRect(color, Offset(w * 0.26f, h * 0.2f), Size(w * 0.16f, h * 0.6f), radius)
            drawRoundRect(color, Offset(w * 0.58f, h * 0.2f), Size(w * 0.16f, h * 0.6f), radius)
        }
        Glyph.STEP_FORWARD -> {
            drawPath(triangle(w * 0.2f, w * 0.64f, true), color)
            drawRoundRect(color, Offset(w * 0.67f, h * 0.2f), bar, radius)
        }
        Glyph.STEP_BACK -> {
            drawPath(triangle(w * 0.36f, w * 0.8f, false), color)
            drawRoundRect(color, Offset(w * 0.2f, h * 0.2f), bar, radius)
        }
        Glyph.TO_START -> {
            drawRoundRect(color, Offset(w * 0.12f, h * 0.2f), bar, radius)
            drawPath(triangle(w * 0.27f, w * 0.57f, false), color)
            drawPath(triangle(w * 0.55f, w * 0.85f, false), color)
        }
        Glyph.TO_END -> {
            drawPath(triangle(w * 0.15f, w * 0.45f, true), color)
            drawPath(triangle(w * 0.43f, w * 0.73f, true), color)
            drawRoundRect(color, Offset(w * 0.75f, h * 0.2f), bar, radius)
        }
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
        Glyph.LOG -> {
            drawRoundRect(color, Offset(w * 0.2f, h * 0.14f), Size(w * 0.6f, h * 0.72f), CornerRadius(w * 0.08f), style = stroke)
            for (i in 0..2) {
                val y = h * (0.36f + i * 0.15f)
                drawLine(color, Offset(w * 0.34f, y), Offset(w * 0.66f, y), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
        }
        Glyph.EDIT -> {
            drawLine(color, Offset(w * 0.24f, h * 0.76f), Offset(w * 0.72f, h * 0.28f), strokeWidth = w * 0.18f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, h * 0.84f), Offset(w * 0.3f, h * 0.84f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
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
