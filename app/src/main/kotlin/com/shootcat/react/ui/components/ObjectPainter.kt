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

/** Extra data some objects visualise, e.g. how much weight rests on a plate. */
data class ObjectInfo(val load: Int = 0, val threshold: Int = 0)

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
        "FIRE" -> drawFire(topLeft, cell, alpha, time)
        "ICE" -> drawIce(topLeft, cell, alpha)
        "WATER" -> drawWater(topLeft, cell, alpha, time)
        "STONE" -> drawStone(topLeft, cell, alpha)
        "BUTTON" -> drawButton(topLeft, cell, alpha, obj.state == "PRESSED")
        "PLATE" -> drawPlate(topLeft, cell, alpha, obj.state == "PRESSED", info)
        "DOOR" -> drawDoor(topLeft, cell, alpha, obj.state == "UNLOCKED")
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
    val flicker = 1f + 0.07f * sin(time * 2f * PI.toFloat() * 3f)
    val sway = c * 0.03f * sin(time * 2f * PI.toFloat() * 2f)
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

private fun DrawScope.drawWater(tl: Offset, c: Float, alpha: Float, time: Float) {
    val inset = c * 0.03f
    val origin = tl + Offset(inset, c * 0.12f)
    val s = Size(c - 2 * inset, c * 0.85f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Palette.waterLight, Palette.water), origin.y, origin.y + s.height),
        topLeft = origin,
        size = s,
        cornerRadius = CornerRadius(c * 0.1f),
        alpha = alpha * 0.9f,
    )
    val phase = time * 2f * PI.toFloat()
    val wave = Path().apply {
        moveTo(origin.x + c * 0.08f, origin.y + c * 0.12f)
        val steps = 6
        for (i in 1..steps) {
            val x = origin.x + c * 0.08f + (s.width - c * 0.16f) * i / steps
            val y = origin.y + c * 0.12f + c * 0.025f * cos(phase + i)
            lineTo(x, y)
        }
    }
    drawPath(wave, Color.White, alpha = alpha * 0.55f, style = Stroke(width = c * 0.035f, cap = StrokeCap.Round))
}

private fun DrawScope.drawStone(tl: Offset, c: Float, alpha: Float) {
    val center = tl + Offset(c / 2, c * 0.54f)
    val radii = floatArrayOf(0.44f, 0.4f, 0.46f, 0.42f, 0.45f, 0.39f, 0.44f, 0.41f)
    val path = Path().apply {
        radii.forEachIndexed { i, r ->
            val a = (i / radii.size.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2
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
    // Notches: how much weight is needed, and how much rests on the plate right now.
    val n = info.threshold
    if (n > 0) {
        val gap = c * 0.8f / n
        for (i in 0 until n) {
            val center = Offset(tl.x + c * 0.1f + gap * (i + 0.5f), tl.y + c * 0.45f)
            val filled = i < info.load
            drawCircle(if (filled) Palette.signal else Palette.wallTop, c * 0.07f, center, alpha = alpha)
        }
    }
}

private fun DrawScope.drawDoor(tl: Offset, c: Float, alpha: Float, open: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val frame = Offset(tl.x + c * 0.12f, tl.y + c * 0.06f)
    val frameSize = Size(c * 0.76f, c * 0.94f)
    if (open) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Palette.signal.copy(alpha = 0.9f), Palette.accent.copy(alpha = 0.4f)), frame.y, frame.y + frameSize.height),
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
