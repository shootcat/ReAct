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
import com.shootcat.react.engine.model.Props
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
    /** Electric current flows through or into the object right now. */
    val powered: Boolean = false,
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
        "WATER" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.waterLight, Palette.water)
        "STEAM" -> drawSteam(topLeft, cell, alpha, time, obj.amount, obj.capacity)
        "STONE" -> drawStone(topLeft, cell, alpha)
        "WOOD" -> drawWood(topLeft, cell, alpha, time, obj)
        "METAL" -> drawMetal(topLeft, cell, alpha, time, obj, info)
        "GATE" -> drawGate(topLeft, cell, alpha)
        "LAVA" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.lava, Palette.lavaDeep)
        "OIL" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.oilLight, Palette.oil)
        "SAND" -> drawSand(topLeft, cell, alpha, obj.state == "WET", info)
        "BATTERY" -> drawBattery(topLeft, cell, alpha, time)
        "CABLE" -> drawCable(topLeft, cell, alpha, time, info)
        "LAMP" -> drawLamp(topLeft, cell, alpha, time, obj.state == "ON")
        "COIL" -> drawCoil(topLeft, cell, alpha, time, obj.state == "HOT")
        "RELAY" -> drawRelay(topLeft, cell, alpha, time, obj.state == "CLOSED", info.powered)
        "TURBINE" -> drawTurbine(topLeft, cell, alpha, time, obj.state == "SPINNING")
        "MEMBRANE" -> drawMembrane(topLeft, cell, alpha)
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
private fun DrawScope.drawLiquidTile(tl: Offset, c: Float, alpha: Float, time: Float, info: ObjectInfo, light: Color, deep: Color) {
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
        Brush.verticalGradient(listOf(light, deep), top - c * 0.1f, bottom + c * 0.6f),
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

/** Soft, slowly drifting puffs; the more steam a cell holds, the denser it looks. */
private fun DrawScope.drawSteam(tl: Offset, c: Float, alpha: Float, time: Float, amount: Int, capacity: Int) {
    val t = time * TAU
    val fill = if (amount <= 0) 1f else (amount.toFloat() / capacity.coerceAtLeast(1)).coerceIn(0f, 1f)
    val density = 0.3f + 0.7f * fill
    val puffs = listOf(
        Triple(0.34f, 0.6f, 0.27f),
        Triple(0.64f, 0.52f, 0.3f),
        Triple(0.48f, 0.32f, 0.25f),
        Triple(0.22f, 0.3f, 0.2f),
        Triple(0.76f, 0.26f, 0.22f),
    )
    val count = if (fill > 0.6f) puffs.size else 3
    for (i in 0 until count) {
        val (x, y, r) = puffs[i]
        val radius = c * r * (0.75f + 0.35f * fill)
        val center = tl + Offset(
            c * (x + 0.05f * sin(t + i * 2.1f)),
            c * (y + 0.04f * cos(t * 2f + i)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.steam.copy(alpha = 0.75f), Palette.steam.copy(alpha = 0f)),
                center,
                radius,
            ),
            radius = radius,
            center = center,
            alpha = alpha * density,
        )
    }
}

/** A wooden block: planks with grain. It burns down (darker, flames on top) or ends up charred. */
private fun DrawScope.drawWood(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject) {
    val burning = obj.state == "BURNING"
    val charred = obj.state == "CHARRED"
    val fuel = obj.int(Props.FUEL, 1).coerceAtLeast(1)
    val burnt = if (burning) (obj.burnt.toFloat() / fuel).coerceIn(0f, 1f) else 0f
    // Burning wood slowly shrinks into its embers.
    val shrink = c * 0.12f * burnt
    val inset = c * 0.08f
    val origin = tl + Offset(inset + shrink * 0.5f, inset + shrink)
    val s = Size(c - 2 * inset - shrink, c - 2 * inset - shrink)
    val r = CornerRadius(c * 0.1f)
    val (light, dark) = when {
        charred -> Palette.charcoal to Color(0xFF151110)
        burning -> lerp(Palette.wood, Palette.charcoal, burnt) to lerp(Palette.woodDark, Color(0xFF151110), burnt)
        else -> Palette.woodLight to Palette.woodDark
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(light, dark), origin.y, origin.y + s.height),
        topLeft = origin,
        size = s,
        cornerRadius = r,
        alpha = alpha,
    )
    // Planks and grain.
    for (i in 1..2) {
        val y = origin.y + s.height * i / 3
        drawLine(dark, Offset(origin.x, y), Offset(origin.x + s.width, y), strokeWidth = c * 0.035f, alpha = alpha)
    }
    drawLine(
        if (charred) Palette.glow.copy(alpha = 0.25f) else light,
        origin + Offset(s.width * 0.2f, s.height * 0.16f),
        origin + Offset(s.width * 0.62f, s.height * 0.16f),
        strokeWidth = c * 0.025f,
        cap = StrokeCap.Round,
        alpha = alpha * 0.7f,
    )
    drawRoundRect(dark, origin, s, r, style = Stroke(width = c * 0.035f), alpha = alpha)
    if (charred) {
        // Cracks with a last faint glow.
        val crack = Path().apply {
            moveTo(origin.x + s.width * 0.3f, origin.y + s.height * 0.4f)
            lineTo(origin.x + s.width * 0.45f, origin.y + s.height * 0.55f)
            lineTo(origin.x + s.width * 0.38f, origin.y + s.height * 0.75f)
        }
        drawPath(crack, Palette.glow, alpha = alpha * 0.35f, style = Stroke(width = c * 0.025f, cap = StrokeCap.Round))
    }
    if (burning) {
        val glowCenter = origin + Offset(s.width / 2, s.height * 0.3f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.5f), Color.Transparent), glowCenter, c * 0.8f),
            radius = c * 0.8f,
            center = glowCenter,
            alpha = alpha,
        )
        // Small tongues of flame along the top edge.
        for (i in 0..2) {
            val fx = origin.x + s.width * (0.22f + 0.28f * i)
            val flicker = 0.8f + 0.25f * sin(time * TAU * 3f + i * 1.7f)
            val h = c * 0.32f * flicker * (1f - burnt * 0.4f)
            val flame = Path().apply {
                moveTo(fx, origin.y - h)
                cubicTo(fx + c * 0.1f, origin.y - h * 0.4f, fx + c * 0.1f, origin.y + c * 0.04f, fx, origin.y + c * 0.06f)
                cubicTo(fx - c * 0.1f, origin.y + c * 0.04f, fx - c * 0.1f, origin.y - h * 0.4f, fx, origin.y - h)
                close()
            }
            drawPath(flame, Palette.fire, alpha = alpha)
            drawCircle(Palette.fireCore, c * 0.035f, Offset(fx, origin.y - h * 0.15f), alpha = alpha)
        }
    }
}

/** A metal block or rod. Heat makes it glow from dull red to bright orange. */
private fun DrawScope.drawMetal(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject, info: ObjectInfo) {
    // Icons have no temperature, only the state.
    val heat = when {
        obj.temp > 0 -> (obj.temp / 6f).coerceIn(0.25f, 1f)
        obj.state == "HOT" -> 0.7f
        else -> 0f
    }
    val hot = heat > 0f
    val fixed = !obj.falls
    // Fixed rods run through walls: they reach the cell edges to join their neighbours.
    val inset = if (fixed) 0f else c * 0.06f
    val origin = tl + Offset(inset, inset + if (fixed) 0f else c * 0.04f)
    val s = Size(c - 2 * inset, c - 2 * inset - if (fixed) 0f else c * 0.04f)
    val base = lerp(Palette.metal, Palette.glow, heat * 0.85f)
    val shade = lerp(Palette.metalDark, Color(0xFF8A2A0C), heat)
    if (hot) {
        val center = tl + Offset(c / 2, c / 2)
        val pulse = 0.85f + 0.15f * sin(time * TAU * 2f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.glow.copy(alpha = 0.45f * heat * pulse), Color.Transparent), center, c * 0.85f),
            radius = c * 0.85f,
            center = center,
            alpha = alpha,
        )
    }
    if (fixed) {
        drawRect(Brush.verticalGradient(listOf(base, shade), origin.y, origin.y + s.height), origin, s, alpha = alpha)
        // Rivets and seams show the rod's direction of travel.
        val seam = Color.Black.copy(alpha = 0.25f)
        if (!info.joinLeft) drawLine(seam, origin, origin + Offset(0f, s.height), strokeWidth = c * 0.04f, alpha = alpha)
        if (!info.joinRight) drawLine(seam, origin + Offset(s.width, 0f), origin + Offset(s.width, s.height), strokeWidth = c * 0.04f, alpha = alpha)
        if (!info.joinAbove) drawLine(seam, origin, origin + Offset(s.width, 0f), strokeWidth = c * 0.04f, alpha = alpha)
        if (!info.joinBelow) drawLine(seam, origin + Offset(0f, s.height), origin + Offset(s.width, s.height), strokeWidth = c * 0.04f, alpha = alpha)
        drawCircle(Color.White, c * 0.045f, tl + Offset(c * 0.5f, c * 0.5f), alpha = alpha * 0.3f)
    } else {
        val r = CornerRadius(c * 0.08f)
        drawRoundRect(Brush.verticalGradient(listOf(base, shade), origin.y, origin.y + s.height), origin, s, r, alpha = alpha)
        // Bevel: a bright top edge and a dark bottom edge, like a steel ingot.
        drawLine(Color.White, origin + Offset(s.width * 0.12f, s.height * 0.12f), origin + Offset(s.width * 0.88f, s.height * 0.12f), strokeWidth = c * 0.04f, cap = StrokeCap.Round, alpha = alpha * (0.55f - 0.3f * heat))
        drawRoundRect(Color.Black, origin, s, r, style = Stroke(width = c * 0.03f), alpha = alpha * 0.35f)
        for (x in listOf(0.22f, 0.78f)) {
            drawCircle(shade, c * 0.045f, origin + Offset(s.width * x, s.height * 0.7f), alpha = alpha)
        }
    }
}

/** A heavy steel slide: hazard stripes along the edge, it moves when steam pressure pushes it. */
private fun DrawScope.drawGate(tl: Offset, c: Float, alpha: Float) {
    val inset = c * 0.04f
    val origin = tl + Offset(inset, inset)
    val s = Size(c - 2 * inset, c - 2 * inset)
    drawRoundRect(
        Brush.linearGradient(listOf(Palette.metal, Palette.metalDark), origin, origin + Offset(s.width, s.height)),
        origin,
        s,
        CornerRadius(c * 0.06f),
        alpha = alpha,
    )
    // Diagonal hazard stripes in the middle band.
    val band = Offset(origin.x, origin.y + s.height * 0.36f)
    val bandSize = Size(s.width, s.height * 0.28f)
    drawRect(Palette.accent, band, bandSize, alpha = alpha * 0.9f)
    val stripe = c * 0.12f
    var x = band.x - bandSize.height
    while (x < band.x + bandSize.width) {
        val path = Path().apply {
            moveTo(maxOf(x, band.x), band.y + bandSize.height)
            lineTo(minOf(x + stripe, band.x + bandSize.width), band.y + bandSize.height)
            lineTo(minOf(x + stripe + bandSize.height, band.x + bandSize.width), band.y)
            lineTo(minOf(x + bandSize.height, band.x + bandSize.width), band.y)
            close()
        }
        drawPath(path, Color(0xFF1A1206), alpha = alpha * 0.85f)
        x += stripe * 2
    }
    for (cx in listOf(0.18f, 0.82f)) for (cy in listOf(0.18f, 0.82f)) {
        drawCircle(Palette.metalDark, c * 0.05f, origin + Offset(s.width * cx, s.height * cy), alpha = alpha)
        drawCircle(Color.White, c * 0.02f, origin + Offset(s.width * cx - c * 0.01f, s.height * cy - c * 0.01f), alpha = alpha * 0.4f)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

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

/** Sand: a heap of grains. Wet sand is darker and, carrying current, sparkles faintly. */
/**
 * Sand: a heap on its own, a dune surface next to other sand, and a solid fill under more sand.
 */
private fun DrawScope.drawSand(tl: Offset, c: Float, alpha: Float, wet: Boolean, info: ObjectInfo) {
    val base = if (wet) Palette.sandWet else Palette.sand
    val shade = if (wet) Color(0xFF5E4626) else Palette.sandDark
    if (info.joinAbove) {
        drawRect(Brush.verticalGradient(listOf(shade, base, shade), tl.y, tl.y + c), tl, Size(c, c), alpha = alpha)
    } else {
        // Where sand continues to the side, the surface meets it halfway up instead of at the floor.
        val leftY = if (info.joinLeft) c * 0.42f else c
        val rightY = if (info.joinRight) c * 0.42f else c
        val heap = Path().apply {
            moveTo(tl.x, tl.y + c)
            lineTo(tl.x + (if (info.joinLeft) 0f else c * 0.02f), tl.y + leftY)
            cubicTo(tl.x + c * 0.15f, tl.y + c * 0.2f, tl.x + c * 0.85f, tl.y + c * 0.2f, tl.x + c - (if (info.joinRight) 0f else c * 0.02f), tl.y + rightY)
            lineTo(tl.x + c, tl.y + c)
            close()
        }
        drawPath(heap, Brush.verticalGradient(listOf(base, shade), tl.y + c * 0.25f, tl.y + c), alpha = alpha)
    }
    val grains = if (info.joinAbove) {
        listOf(0.2f to 0.15f, 0.55f to 0.25f, 0.8f to 0.12f, 0.35f to 0.42f, 0.75f to 0.48f, 0.25f to 0.62f, 0.5f to 0.7f, 0.7f to 0.75f, 0.38f to 0.88f, 0.82f to 0.9f, 0.12f to 0.85f)
    } else {
        listOf(0.25f to 0.62f, 0.5f to 0.45f, 0.7f to 0.7f, 0.38f to 0.82f, 0.62f to 0.88f, 0.82f to 0.84f, 0.18f to 0.9f)
    }
    for ((gx, gy) in grains) {
        drawCircle(shade, c * 0.035f, tl + Offset(c * gx, c * gy), alpha = alpha * 0.8f)
    }
    if (wet) {
        drawCircle(Color.White, c * 0.03f, tl + Offset(c * 0.45f, c * 0.5f), alpha = alpha * 0.35f)
    }
    if (info.powered) {
        val top = if (info.joinAbove) 0f else c * 0.55f
        drawRect(Palette.power, tl + Offset(0f, top), Size(c, c - top), alpha = alpha * 0.18f)
    }
}

/** A battery block with its two poles. */
private fun DrawScope.drawBattery(tl: Offset, c: Float, alpha: Float, time: Float) {
    val body = Offset(tl.x + c * 0.14f, tl.y + c * 0.26f)
    val size = Size(c * 0.72f, c * 0.66f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3C4656), Color(0xFF232A35)), body.y, body.y + size.height), body, size, CornerRadius(c * 0.08f), alpha = alpha)
    drawRoundRect(Palette.success, body + Offset(0f, size.height * 0.62f), Size(size.width, size.height * 0.38f), CornerRadius(c * 0.06f), alpha = alpha * 0.85f)
    // Poles.
    drawRect(Palette.copper, Offset(tl.x + c * 0.26f, tl.y + c * 0.16f), Size(c * 0.12f, c * 0.1f), alpha = alpha)
    drawRect(Palette.copper, Offset(tl.x + c * 0.62f, tl.y + c * 0.16f), Size(c * 0.12f, c * 0.1f), alpha = alpha)
    val plus = tl + Offset(c * 0.32f, c * 0.46f)
    drawLine(Palette.text, plus + Offset(-c * 0.07f, 0f), plus + Offset(c * 0.07f, 0f), strokeWidth = c * 0.04f, alpha = alpha)
    drawLine(Palette.text, plus + Offset(0f, -c * 0.07f), plus + Offset(0f, c * 0.07f), strokeWidth = c * 0.04f, alpha = alpha)
    val minus = tl + Offset(c * 0.68f, c * 0.46f)
    drawLine(Palette.text, minus + Offset(-c * 0.07f, 0f), minus + Offset(c * 0.07f, 0f), strokeWidth = c * 0.04f, alpha = alpha)
    val pulse = 0.5f + 0.5f * sin(time * TAU * 2f)
    drawCircle(Brush.radialGradient(listOf(Palette.power.copy(alpha = 0.25f * pulse), Color.Transparent), tl + Offset(c / 2, c / 2), c * 0.7f), c * 0.7f, tl + Offset(c / 2, c / 2), alpha = alpha)
}

/** A cable laid in the rock, joining its neighbours. Live, it glows and a spark runs along it. */
private fun DrawScope.drawCable(tl: Offset, c: Float, alpha: Float, time: Float, info: ObjectInfo) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val center = tl + Offset(c / 2, c / 2)
    val color = if (info.powered) Palette.power else Palette.copper
    val width = c * 0.16f
    val ends = buildList {
        if (info.joinLeft) add(Offset(tl.x, center.y))
        if (info.joinRight) add(Offset(tl.x + c, center.y))
        if (info.joinAbove) add(Offset(center.x, tl.y))
        if (info.joinBelow) add(Offset(center.x, tl.y + c))
        if (isEmpty()) {
            add(Offset(tl.x + c * 0.15f, center.y))
            add(Offset(tl.x + c * 0.85f, center.y))
        }
    }
    if (info.powered) {
        drawCircle(Brush.radialGradient(listOf(Palette.power.copy(alpha = 0.35f), Color.Transparent), center, c * 0.7f), c * 0.7f, center, alpha = alpha)
    }
    for (end in ends) {
        drawLine(Color.Black, center, end, strokeWidth = width * 1.5f, cap = StrokeCap.Round, alpha = alpha * 0.4f)
        drawLine(color, center, end, strokeWidth = width, cap = StrokeCap.Round, alpha = alpha)
    }
    drawCircle(color, width * 0.8f, center, alpha = alpha)
    if (info.powered) {
        val end = ends.first()
        val phase = (time * 3f + (tl.x + tl.y) / c * 0.3f) % 1f
        drawCircle(Color.White, c * 0.06f, center + (end - center) * phase, alpha = alpha * 0.9f)
    }
}

/** A light bulb in a wall socket. On, it shines and sends its signal. */
private fun DrawScope.drawLamp(tl: Offset, c: Float, alpha: Float, time: Float, on: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val center = tl + Offset(c / 2, c * 0.44f)
    if (on) {
        val r = c * (0.95f + 0.05f * sin(time * TAU * 4f))
        drawCircle(Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.6f), Color.Transparent), center, r), r, center, alpha = alpha)
    }
    drawRect(Palette.metalDark, Offset(tl.x + c * 0.36f, tl.y + c * 0.66f), Size(c * 0.28f, c * 0.2f), alpha = alpha)
    drawCircle(if (on) Palette.signal else Color(0xFF4A5363), c * 0.25f, center, alpha = alpha)
    drawCircle(Color.White, c * 0.07f, center + Offset(-c * 0.08f, -c * 0.08f), alpha = alpha * (if (on) 0.9f else 0.3f))
    if (!on) {
        drawLine(Palette.textDim, center + Offset(-c * 0.08f, c * 0.06f), center + Offset(c * 0.08f, c * 0.06f), strokeWidth = c * 0.03f, alpha = alpha * 0.6f)
    }
}

/** A heating rod: a coiled element that glows when current flows. */
private fun DrawScope.drawCoil(tl: Offset, c: Float, alpha: Float, time: Float, hot: Boolean) {
    val color = if (hot) Palette.glow else Palette.copper
    if (hot) {
        val center = tl + Offset(c / 2, c * 0.55f)
        val r = c * (0.8f + 0.06f * sin(time * TAU * 3f))
        drawCircle(Brush.radialGradient(listOf(Palette.glow.copy(alpha = 0.5f), Color.Transparent), center, r), r, center, alpha = alpha)
    }
    drawRoundRect(Palette.metalDark, tl + Offset(c * 0.1f, c * 0.18f), Size(c * 0.8f, c * 0.14f), CornerRadius(c * 0.05f), alpha = alpha)
    val coil = Path().apply {
        moveTo(tl.x + c * 0.2f, tl.y + c * 0.32f)
        var x = tl.x + c * 0.2f
        val step = c * 0.12f
        var down = true
        while (x < tl.x + c * 0.8f) {
            lineTo(x + step / 2, tl.y + c * (if (down) 0.88f else 0.4f))
            x += step / 2
            down = !down
        }
    }
    drawPath(coil, color, alpha = alpha, style = Stroke(width = c * 0.07f, cap = StrokeCap.Round))
}

/** A relay box: the contact closes on a signal. */
private fun DrawScope.drawRelay(tl: Offset, c: Float, alpha: Float, time: Float, closed: Boolean, powered: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val box = tl + Offset(c * 0.1f, c * 0.18f)
    drawRoundRect(Color(0xFF2B3340), box, Size(c * 0.8f, c * 0.64f), CornerRadius(c * 0.08f), alpha = alpha)
    val wire = if (powered && closed) Palette.power else Palette.copper
    val left = tl + Offset(0f, c * 0.5f)
    val pivot = tl + Offset(c * 0.32f, c * 0.5f)
    val contact = tl + Offset(c * 0.68f, c * 0.5f)
    drawLine(wire, left, pivot, strokeWidth = c * 0.12f, alpha = alpha)
    drawLine(wire, contact, tl + Offset(c, c * 0.5f), strokeWidth = c * 0.12f, alpha = alpha)
    val tip = if (closed) contact else tl + Offset(c * 0.64f, c * 0.24f)
    drawLine(if (closed) Palette.signal else Palette.textDim, pivot, tip, strokeWidth = c * 0.08f, cap = StrokeCap.Round, alpha = alpha)
    drawCircle(Palette.text, c * 0.05f, pivot, alpha = alpha)
    if (powered && closed) {
        drawCircle(Color.White, c * 0.05f, pivot + (contact - pivot) * ((time * 3f) % 1f), alpha = alpha * 0.9f)
    }
}

/** A steam turbine in the ceiling: blades that spin while steam pushes from below. */
private fun DrawScope.drawTurbine(tl: Offset, c: Float, alpha: Float, time: Float, spinning: Boolean) {
    drawRect(Palette.wall, tl, Size(c, c), alpha = alpha)
    val center = tl + Offset(c / 2, c * 0.58f)
    if (spinning) {
        drawCircle(Brush.radialGradient(listOf(Palette.power.copy(alpha = 0.4f), Color.Transparent), center, c * 0.75f), c * 0.75f, center, alpha = alpha)
    }
    drawCircle(Color(0xFF2B3340), c * 0.4f, center, alpha = alpha)
    val angle = if (spinning) time * TAU * 4f else 0.3f
    for (i in 0 until 4) {
        val a = angle + i * TAU / 4f
        val tip = center + Offset(cos(a) * c * 0.34f, sin(a) * c * 0.34f)
        drawLine(if (spinning) Palette.metal else Palette.metalDark, center, tip, strokeWidth = c * 0.12f, cap = StrokeCap.Round, alpha = alpha)
    }
    drawCircle(Palette.brass, c * 0.08f, center, alpha = alpha)
}

/** A thin bursting disc set into the ceiling. */
private fun DrawScope.drawMembrane(tl: Offset, c: Float, alpha: Float) {
    drawRect(Palette.wall, tl, Size(c, c * 0.55f), alpha = alpha)
    val disc = Offset(tl.x, tl.y + c * 0.55f)
    drawRect(Brush.verticalGradient(listOf(Palette.ice.copy(alpha = 0.8f), Palette.iceDeep.copy(alpha = 0.6f)), disc.y, disc.y + c * 0.16f), disc, Size(c, c * 0.16f), alpha = alpha)
    drawLine(Palette.brass, disc, disc + Offset(c, 0f), strokeWidth = c * 0.05f, alpha = alpha)
    // Hairline cracks show it will not hold forever.
    drawLine(Color.White, disc + Offset(c * 0.3f, c * 0.04f), disc + Offset(c * 0.42f, c * 0.13f), strokeWidth = c * 0.015f, alpha = alpha * 0.6f)
    drawLine(Color.White, disc + Offset(c * 0.42f, c * 0.13f), disc + Offset(c * 0.55f, c * 0.06f), strokeWidth = c * 0.015f, alpha = alpha * 0.6f)
}
