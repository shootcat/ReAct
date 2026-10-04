package com.shootcat.react.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.WorldLooks
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** A painted piece of a world's landscape, [h] high, from [top] down: meadow, coast, volcano or frost. */
internal fun DrawScope.drawWorldBand(world: Int, top: Float, h: Float, w: Float, time: Float) {
    when (world) {
        2 -> drawCoastBand(top, h, w, time)
        3 -> drawVolcanoBand(top, h, w, time)
        4 -> drawFrostBand(top, h, w, time)
        else -> drawForestBand(top, h, w, time)
    }
}

private fun DrawScope.drawForestBand(top: Float, h: Float, w: Float, time: Float) {
    val look = WorldLooks.forest
    val meadow = lerp(look.cover, look.coverDark, 0.45f)
    drawRect(Brush.verticalGradient(listOf(meadow, lerp(look.cover, look.coverDark, 0.25f), meadow), top, top + h), Offset(0f, top), Size(w, h))
    // A stream winding through the meadow; it leaves at the bottom where a mirrored piece below picks it up.
    val stream = Path().apply {
        moveTo(w * 0.8f, top)
        cubicTo(w * 0.55f, top + h * 0.3f, w * 0.95f, top + h * 0.65f, w * 0.2f, top + h)
    }
    drawPath(stream, Palette.water, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round), alpha = 0.8f)
    drawPath(stream, Palette.waterLight, style = Stroke(width = w * 0.012f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 30f), -time * 200f)), alpha = 0.7f)
    // Trees.
    val spots = listOf(0.08f to 0.2f, 0.18f to 0.75f, 0.55f to 0.15f, 0.7f to 0.55f, 0.88f to 0.85f, 0.12f to 0.45f, 0.45f to 0.85f, 0.82f to 0.3f)
    for ((x, y) in spots) {
        val base = Offset(w * x, top + h * y)
        drawOval(Color.Black, Offset(base.x - w * 0.045f, base.y - w * 0.012f), Size(w * 0.09f, w * 0.024f), alpha = 0.2f)
        drawRect(look.earthDark, Offset(base.x - w * 0.008f, base.y - w * 0.06f), Size(w * 0.016f, w * 0.06f))
        drawCircle(look.coverDark, w * 0.05f, base - Offset(0f, w * 0.09f))
        drawCircle(look.cover, w * 0.036f, base - Offset(w * 0.012f, w * 0.105f))
    }
    // Campfire smoke.
    val fire = Offset(w * 0.62f, top + h * 0.82f)
    drawCircle(Palette.fire, w * 0.012f, fire)
    for (k in 0 until 3) {
        val p = (time + k / 3f) % 1f
        drawCircle(Color.White, w * (0.01f + 0.02f * p), fire - Offset(-w * 0.02f * p, h * 0.25f * p), alpha = 0.3f * (1f - p))
    }
}

private fun DrawScope.drawCoastBand(top: Float, h: Float, w: Float, time: Float) {
    val look = WorldLooks.coast
    drawRect(Brush.horizontalGradient(listOf(look.cover, look.coverDark), 0f, w * 0.55f), Offset(0f, top), Size(w, h))
    // The sea on the right, with a curving beach line.
    val sea = Path().apply {
        moveTo(w * 0.52f, top)
        cubicTo(w * 0.4f, top + h * 0.35f, w * 0.65f, top + h * 0.6f, w * 0.52f, top + h)
        lineTo(w, top + h)
        lineTo(w, top)
        close()
    }
    drawPath(sea, Brush.horizontalGradient(listOf(Palette.seaLight, Palette.sea, look.horizon), w * 0.45f, w))
    for (k in 0 until 5) {
        val y = top + h * (0.12f + 0.18f * k)
        val shift = ((time + k * 0.2f) % 1f) * w * 0.1f
        drawLine(Color.White, Offset(w * 0.68f + shift, y), Offset(w * 0.76f + shift, y), strokeWidth = 3f, cap = StrokeCap.Round, alpha = 0.4f)
    }
    // Rocks on the beach.
    for ((x, y) in listOf(0.12f to 0.3f, 0.25f to 0.7f, 0.4f to 0.5f)) {
        drawOval(look.rock, Offset(w * x, top + h * y), Size(w * 0.07f, h * 0.06f))
        drawOval(look.rockDark, Offset(w * x, top + h * y + h * 0.03f), Size(w * 0.07f, h * 0.03f))
    }
}

private fun DrawScope.drawVolcanoBand(top: Float, h: Float, w: Float, time: Float) {
    val look = WorldLooks.volcano
    drawRect(Brush.verticalGradient(listOf(look.earth, look.skyBottom, look.earth), top, top + h), Offset(0f, top), Size(w, h))
    val peak = Offset(w * 0.75f, top + h * 0.2f)
    val cone = Path().apply {
        moveTo(peak.x - w * 0.06f, peak.y)
        lineTo(peak.x + w * 0.06f, peak.y)
        lineTo(peak.x + w * 0.35f, top + h)
        lineTo(peak.x - w * 0.4f, top + h)
        close()
    }
    val glow = 0.7f + 0.3f * sin(time * TAU * 2f)
    drawCircle(Brush.radialGradient(listOf(Palette.lava.copy(alpha = 0.6f * glow), Color.Transparent), peak, w * 0.2f), w * 0.2f, peak)
    drawPath(cone, look.rock)
    val flow = Path().apply {
        moveTo(peak.x, peak.y)
        cubicTo(peak.x - w * 0.05f, peak.y + h * 0.3f, peak.x - w * 0.2f, peak.y + h * 0.5f, peak.x - w * 0.45f, top + h * 0.95f)
    }
    drawPath(flow, Palette.lava, style = Stroke(width = w * 0.025f, cap = StrokeCap.Round), alpha = 0.6f + 0.3f * glow)
    for (k in 0 until 3) {
        val p = (time + k / 3f) % 1f
        drawCircle(Color(0xFF4A3F3F), w * (0.03f + 0.05f * p), peak - Offset(-w * 0.05f * p, h * 0.3f * p), alpha = 0.5f * (1f - p))
    }
}

private fun DrawScope.drawFrostBand(top: Float, h: Float, w: Float, time: Float) {
    val look = WorldLooks.frost
    drawRect(Brush.verticalGradient(listOf(look.skyBottom, look.skyTop, look.skyBottom), top, top + h), Offset(0f, top), Size(w, h))
    val peaks = listOf(0.1f to 0.45f, 0.35f to 0.25f, 0.62f to 0.4f, 0.88f to 0.2f)
    for ((x, y) in peaks) {
        val tip = Offset(w * x, top + h * y)
        val mountain = Path().apply {
            moveTo(tip.x - w * 0.25f, top + h)
            lineTo(tip.x, tip.y)
            lineTo(tip.x + w * 0.25f, top + h)
            close()
        }
        drawPath(mountain, look.rock)
        val cap = Path().apply {
            moveTo(tip.x - w * 0.08f, tip.y + h * 0.18f)
            lineTo(tip.x, tip.y)
            lineTo(tip.x + w * 0.08f, tip.y + h * 0.18f)
            lineTo(tip.x, tip.y + h * 0.14f)
            close()
        }
        drawPath(cap, look.cover)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, look.cover), top + h * 0.75f, top + h), Offset(0f, top + h * 0.75f), Size(w, h * 0.25f))
    for (i in 0 until 14) {
        val x = w * ((i * 0.37f) % 1f)
        val y = top + h * ((i * 0.23f + time) % 1f)
        drawCircle(Color.White, 3f, Offset(x, y), alpha = 0.7f)
    }
}
