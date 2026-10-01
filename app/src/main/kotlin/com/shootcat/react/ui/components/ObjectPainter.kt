package com.shootcat.react.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Extra data some objects visualise: the weight resting on a plate, and for fluids whether
 * the same fluid continues to the left/right/below/above (so pools render as one body).
 */
data class ObjectInfo(
    val load: Int = 0,
    val threshold: Int = 0,
    val joinLeft: Boolean = false,
    val joinRight: Boolean = false,
    val joinBelow: Boolean = false,
    val joinAbove: Boolean = false,
)

private const val TAU = (2 * PI).toFloat()

/**
 * Minimal diorama look: every object type gets a clear, simple shape.
 * [time] runs from 0 to 1 repeatedly and drives small idle animations.
 */
fun DrawScope.drawGameObject(
    obj: GameObject,
    topLeft: Offset,
    cell: Float,
    alpha: Float = 1f,
    time: Float = 0f,
    info: ObjectInfo = ObjectInfo(),
) {
    when (obj.type) {
        "FIRE" -> if (obj.state == "OUT") drawFireOut(topLeft, cell, alpha, time) else drawFire(topLeft, cell, alpha, time)
        "ICE" -> drawIce(topLeft, cell, alpha)
        "WATER" -> drawWater(topLeft, cell, alpha, time, info)
        "STEAM" -> drawSteam(topLeft, cell, alpha, time)
        "STONE" -> drawStone(topLeft, cell, alpha)
        "BUTTON" -> drawButton(topLeft, cell, alpha, obj.state == "PRESSED")
        "PLATE" -> drawPlate(topLeft, cell, alpha, obj.state == "PRESSED", info)
        "DOOR" -> drawDoor(topLeft, cell, alpha, obj.state == "UNLOCKED")
        "PISTON" -> drawPiston(topLeft, cell, alpha, obj.state == "PUSHED")
        "HATCH" -> drawHatch(topLeft, cell, alpha)
        else -> drawCircle(Palette.textDim, cell * 0.3f, topLeft + Offset(cell / 2, cell / 2), alpha)
    }
}

private fun DrawScope.drawFire(tl: Offset, c: Float, alpha: Float, time: Float) {
    val cx = tl.x + c / 2
    val base = tl.y + c * 0.84f
    val glowCenter = Offset(cx, tl.y + c * 0.6f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.45f), Color.Transparent), glowCenter, c * 0.75f),
        radius = c * 0.75f,
        center = glowCenter,
        alpha = alpha,
    )
    val flicker = 1f + 0.07f * sin(time * TAU * 3f)
    val sway = c * 0.03f * sin(time * TAU * 2f)
    val h = c * 0.66f * flicker
    val w = c * 0.27f
    val outer = Path().apply {
        moveTo(cx + sway, base - h)
        cubicTo(cx + w * 0.9f, base - h * 0.55f, cx + w * 1.3f, base - h * 0.2f, cx + w * 0.8f, base - h * 0.04f)
        cubicTo(cx + w * 0.4f, base + c * 0.03f, cx - w * 0.4f, base + c * 0.03f, cx - w * 0.8f, base - h * 0.04f)
        cubicTo(cx - w * 1.3f, base - h * 0.2f, cx - w * 0.9f, base - h * 0.55f, cx + sway, base - h)
        close()
    }
    drawPath(outer, Palette.fire, alpha = alpha)
    val ih = h * 0.55f
    val iw = w * 0.55f
    val inner = Path().apply {
        moveTo(cx + sway * 0.5f, base - ih)
        cubicTo(cx + iw, base - ih * 0.5f, cx + iw * 1.2f, base - ih * 0.1f, cx, base - c * 0.02f)
        cubicTo(cx - iw * 1.2f, base - ih * 0.1f, cx - iw, base - ih * 0.5f, cx + sway * 0.5f, base - ih)
        close()
    }
    drawPath(inner, Palette.fireCore, alpha = alpha)
    // The bowl the flame burns in.
    drawArc(
        color = Palette.stoneDark,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - c * 0.3f, base - c * 0.1f),
        size = Size(c * 0.6f, c * 0.22f),
        alpha = alpha,
    )
}

private fun DrawScope.drawIce(tl: Offset, c: Float, alpha: Float) {
    val inset = c * 0.07f
    val origin = tl + Offset(inset, inset)
    val s = Size(c - 2 * inset, c - 2 * inset)
    val r = CornerRadius(c * 0.12f)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Palette.ice, Palette.iceDeep), origin, origin + Offset(s.width, s.height)),
        topLeft = origin,
        size = s,
        cornerRadius = r,
        alpha = alpha * 0.92f,
    )
    drawRoundRect(Color.White, origin, s, r, style = Stroke(width = c * 0.03f), alpha = alpha * 0.55f)
    drawLine(
        Color.White,
        origin + Offset(s.width * 0.18f, s.height * 0.2f),
        origin + Offset(s.width * 0.5f, s.height * 0.2f),
        strokeWidth = c * 0.05f,
        cap = StrokeCap.Round,
        alpha = alpha * 0.8f,
    )
    drawLine(
        Color.White,
        origin + Offset(s.width * 0.18f, s.height * 0.2f),
        origin + Offset(s.width * 0.18f, s.height * 0.42f),
        strokeWidth = c * 0.05f,
        cap = StrokeCap.Round,
        alpha = alpha * 0.8f,
    )
    drawLine(
        Palette.iceDeep,
        origin + Offset(s.width * 0.55f, s.height * 0.62f),
        origin + Offset(s.width * 0.78f, s.height * 0.8f),
        strokeWidth = c * 0.03f,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
}

/**
 * Water fills its cell and merges with neighbouring water. The top of a pool is a moving wave;
 * the wave uses the absolute x position, so it runs seamlessly across neighbouring cells.
 */
private fun DrawScope.drawWater(tl: Offset, c: Float, alpha: Float, time: Float, info: ObjectInfo) {
    val gap = c * 0.04f
    val left = tl.x + if (info.joinLeft) 0f else gap
    val right = tl.x + c - if (info.joinRight) 0f else gap
    val bottom = tl.y + c - if (info.joinBelow) 0f else gap * 0.5f
    val surface = !info.joinAbove
    val top = tl.y + if (surface) c * 0.2f else 0f

    fun waveY(x: Float): Float {
        val u = x / c
        return top + c * 0.045f * sin(u * TAU * 0.8f + time * TAU) +
            c * 0.02f * sin(u * TAU * 1.9f - time * TAU * 2f)
    }

    val steps = 10
    val body = Path().apply {
        moveTo(left, bottom)
        if (surface) {
            lineTo(left, waveY(left))
            for (i in 1..steps) {
                val x = left + (right - left) * i / steps
                lineTo(x, waveY(x))
            }
        } else {
            lineTo(left, top)
            lineTo(right, top)
        }
        lineTo(right, bottom)
        close()
    }
    drawPath(
        body,
        Brush.verticalGradient(listOf(Palette.waterLight, Palette.water), top - c * 0.1f, bottom + c * 0.6f),
        alpha = alpha * 0.92f,
    )
    if (surface) {
        val crest = Path().apply {
            moveTo(left, waveY(left))
            for (i in 1..steps) {
                val x = left + (right - left) * i / steps
                lineTo(x, waveY(x))
            }
        }
        drawPath(crest, Color.White, alpha = alpha * 0.6f, style = Stroke(width = c * 0.035f, cap = StrokeCap.Round))
    }
    // Drifting light reflections inside the water.
    val shimmerX = tl.x + c * (0.25f + 0.5f * ((time + tl.x / c * 0.37f) % 1f))
    drawLine(
        Color.White,
        Offset(shimmerX - c * 0.08f, tl.y + c * 0.62f),
        Offset(shimmerX + c * 0.08f, tl.y + c * 0.62f),
        strokeWidth = c * 0.025f,
        cap = StrokeCap.Round,
        alpha = alpha * 0.25f,
    )
}

/** Soft, slowly drifting puffs. */
private fun DrawScope.drawSteam(tl: Offset, c: Float, alpha: Float, time: Float) {
    val t = time * TAU
    val puffs = listOf(
        Triple(0.34f, 0.6f, 0.27f),
        Triple(0.64f, 0.52f, 0.3f),
        Triple(0.48f, 0.32f, 0.25f),
    )
    puffs.forEachIndexed { i, (x, y, r) ->
        val center = tl + Offset(
            c * (x + 0.05f * sin(t + i * 2.1f)),
            c * (y + 0.04f * cos(t * 2f + i)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.steam.copy(alpha = 0.75f), Palette.steam.copy(alpha = 0f)),
                center,
                c * r,
            ),
            radius = c * r,
            center = center,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawStone(tl: Offset, c: Float, alpha: Float) {
    val center = tl + Offset(c / 2, c * 0.54f)
    val radii = floatArrayOf(0.44f, 0.4f, 0.46f, 0.42f, 0.45f, 0.39f, 0.44f, 0.41f)
    val path = Path().apply {
        radii.forEachIndexed { i, r ->
            val a = (i / radii.size.toFloat()) * TAU - PI.toFloat() / 2
            val p = center + Offset(cos(a) * r * c, sin(a) * r * c * 0.9f)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(
        path,
        Brush.linearGradient(listOf(Palette.stone, Palette.stoneDark), tl, tl + Offset(c, c)),
        alpha = alpha,
    )
    drawPath(path, Color.Black, alpha = alpha * 0.35f, style = Stroke(width = c * 0.035f))
    drawCircle(Color.White, c * 0.06f, center + Offset(-c * 0.14f, -c * 0.16f), alpha = alpha * 0.35f)
    drawLine(
        Color.Black,
        center + Offset(c * 0.05f, c * 0.02f),
        center + Offset(c * 0.2f, c * 0.18f),
        strokeWidth = c * 0.03f,
        cap = StrokeCap.Round,
        alpha = alpha * 0.3f,
    )
}

private fun DrawScope.drawFloorTile(tl: Offset, c: Float, alpha: Float) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    drawRect(Palette.wallTop, tl, Size(c, c * 0.1f), alpha = alpha)
}

private fun DrawScope.drawButton(tl: Offset, c: Float, alpha: Float, pressed: Boolean) {
    drawFloorTile(tl, c, alpha)
    val color = if (pressed) Palette.success else Palette.buttonUp
    val height = if (pressed) c * 0.08f else c * 0.2f
    val width = c * 0.56f
    val origin = Offset(tl.x + (c - width) / 2, tl.y - height + c * 0.1f)
    if (pressed) {
        val glowCenter = Offset(tl.x + c / 2, tl.y + c * 0.1f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.success.copy(alpha = 0.5f), Color.Transparent), glowCenter, c * 0.6f),
            radius = c * 0.6f,
            center = glowCenter,
            alpha = alpha,
        )
    }
    drawRoundRect(color, origin, Size(width, height), CornerRadius(c * 0.08f), alpha = alpha)
    drawRect(Palette.woodDark, Offset(tl.x + c * 0.15f, tl.y + c * 0.1f), Size(c * 0.7f, c * 0.06f), alpha = alpha)
}

private fun DrawScope.drawPlate(tl: Offset, c: Float, alpha: Float, pressed: Boolean, info: ObjectInfo) {
    drawFloorTile(tl, c, alpha)
    val slabHeight = if (pressed) c * 0.07f else c * 0.16f
    val slab = Offset(tl.x + c * 0.06f, tl.y + c * 0.1f - slabHeight)
    if (pressed) {
        val glowCenter = Offset(tl.x + c / 2, tl.y + c * 0.05f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.5f), Color.Transparent), glowCenter, c * 0.7f),
            radius = c * 0.7f,
            center = glowCenter,
            alpha = alpha,
        )
    }
    drawRoundRect(Palette.brass, slab, Size(c * 0.88f, slabHeight), CornerRadius(c * 0.04f), alpha = alpha)
    // Gauge: how much of the needed weight rests on the plate right now.
    if (info.threshold > 0) {
        val fill = (info.load.toFloat() / info.threshold).coerceIn(0f, 1f)
        val barTopLeft = Offset(tl.x + c * 0.15f, tl.y + c * 0.42f)
        val bar = Size(c * 0.7f, c * 0.1f)
        drawRoundRect(Palette.wallTop, barTopLeft, bar, CornerRadius(c * 0.05f), alpha = alpha)
        if (fill > 0f) {
            drawRoundRect(Palette.signal, barTopLeft, Size(bar.width * fill, bar.height), CornerRadius(c * 0.05f), alpha = alpha)
        }
    }
}

private fun DrawScope.drawDoor(tl: Offset, c: Float, alpha: Float, open: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val frame = Offset(tl.x + c * 0.12f, tl.y + c * 0.06f)
    val frameSize = Size(c * 0.76f, c * 0.94f)
    if (open) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Palette.signal.copy(alpha = 0.9f), Palette.accent.copy(alpha = 0.4f)),
                frame.y,
                frame.y + frameSize.height,
            ),
            topLeft = frame,
            size = frameSize,
            alpha = alpha,
        )
        drawRect(Palette.woodDark, frame, Size(c * 0.14f, frameSize.height), alpha = alpha)
        val glowCenter = tl + Offset(c / 2, c / 2)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.35f), Color.Transparent), glowCenter, c),
            radius = c,
            center = glowCenter,
            alpha = alpha,
        )
    } else {
        drawRect(Palette.wood, frame, frameSize, alpha = alpha)
        for (i in 1..2) {
            val x = frame.x + frameSize.width * i / 3
            drawLine(Palette.woodDark, Offset(x, frame.y), Offset(x, frame.y + frameSize.height), strokeWidth = c * 0.03f, alpha = alpha)
        }
        drawRect(Palette.woodDark, Offset(frame.x, frame.y + frameSize.height * 0.3f), Size(frameSize.width, c * 0.06f), alpha = alpha)
        drawRect(Palette.woodDark, Offset(frame.x, frame.y + frameSize.height * 0.7f), Size(frameSize.width, c * 0.06f), alpha = alpha)
        // Padlock.
        val lockCenter = Offset(frame.x + frameSize.width * 0.72f, frame.y + frameSize.height * 0.52f)
        drawArc(
            color = Palette.textDim,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = lockCenter + Offset(-c * 0.07f, -c * 0.13f),
            size = Size(c * 0.14f, c * 0.14f),
            style = Stroke(width = c * 0.03f),
            alpha = alpha,
        )
        drawRoundRect(Palette.textDim, lockCenter + Offset(-c * 0.1f, -c * 0.06f), Size(c * 0.2f, c * 0.14f), CornerRadius(c * 0.03f), alpha = alpha)
    }
}

/** An extinguished bowl: dark coals and a thin wisp of smoke. */
private fun DrawScope.drawFireOut(tl: Offset, c: Float, alpha: Float, time: Float) {
    val cx = tl.x + c / 2
    val base = tl.y + c * 0.84f
    drawArc(
        color = Palette.stoneDark,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - c * 0.3f, base - c * 0.1f),
        size = Size(c * 0.6f, c * 0.22f),
        alpha = alpha,
    )
    for (i in -1..1) {
        drawCircle(Color(0xFF2B2B2B), c * 0.07f, Offset(cx + i * c * 0.11f, base - c * 0.06f), alpha = alpha)
    }
    val rise = time % 1f
    drawCircle(
        Palette.textDim,
        c * (0.06f + 0.06f * rise),
        Offset(cx + c * 0.05f * sin(rise * TAU), base - c * (0.2f + 0.45f * rise)),
        alpha = alpha * 0.35f * (1f - rise),
    )
}

/** A steam piston in the ceiling. Pushed up, it glows and sends its signal. */
private fun DrawScope.drawPiston(tl: Offset, c: Float, alpha: Float, pushed: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val housing = Offset(tl.x + c * 0.18f, tl.y)
    drawRect(Palette.stoneDark, housing, Size(c * 0.64f, c * 0.32f), alpha = alpha)
    val headY = tl.y + if (pushed) c * 0.42f else c * 0.72f
    drawRect(Palette.stone, Offset(tl.x + c * 0.44f, tl.y + c * 0.3f), Size(c * 0.12f, headY - tl.y - c * 0.3f), alpha = alpha)
    val head = if (pushed) Palette.signal else Palette.brass
    if (pushed) {
        val glow = Offset(tl.x + c / 2, headY + c * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.45f), Color.Transparent), glow, c * 0.6f),
            radius = c * 0.6f,
            center = glow,
            alpha = alpha,
        )
    }
    drawRoundRect(head, Offset(tl.x + c * 0.1f, headY), Size(c * 0.8f, c * 0.16f), CornerRadius(c * 0.04f), alpha = alpha)
    drawLine(Palette.stoneDark, Offset(tl.x + c * 0.1f, headY + c * 0.16f), Offset(tl.x + c * 0.9f, headY + c * 0.16f), strokeWidth = c * 0.03f, alpha = alpha)
}

/** A trapdoor: a reinforced plank that drops away on a signal. */
private fun DrawScope.drawHatch(tl: Offset, c: Float, alpha: Float) {
    val plank = Offset(tl.x, tl.y)
    drawRect(Palette.woodDark, plank, Size(c, c * 0.36f), alpha = alpha)
    drawRect(Palette.wood, Offset(tl.x, tl.y + c * 0.04f), Size(c, c * 0.24f), alpha = alpha)
    for (x in listOf(0.18f, 0.82f)) {
        drawRect(Palette.stoneDark, Offset(tl.x + c * (x - 0.06f), tl.y), Size(c * 0.12f, c * 0.36f), alpha = alpha)
        drawCircle(Palette.stone, c * 0.04f, Offset(tl.x + c * x, tl.y + c * 0.18f), alpha = alpha)
    }
}
