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
 * Extra data some objects visualise: whether the same material continues to the left/right/below/above
 * (sand heaps, liquid tiles in icons).
 */
data class ObjectInfo(
    val joinLeft: Boolean = false,
    val joinRight: Boolean = false,
    val joinBelow: Boolean = false,
    val joinAbove: Boolean = false,
)

private const val TAU = (2 * PI).toFloat()

/**
 * A small, warm diorama look: every element of nature gets a clear, recognisable shape.
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
    val phase = (topLeft.x * 0.013f + topLeft.y * 0.007f)
    when (obj.type) {
        "FIRE" -> drawCampfire(topLeft, cell, alpha, time + phase, big = false)
        "BIG_FIRE" -> drawCampfire(topLeft, cell, alpha, time + phase, big = true)
        "WOOD" -> drawLog(topLeft, cell, alpha, time + phase, obj)
        "CHARCOAL" -> drawCharcoal(topLeft, cell, alpha)
        "EMBER" -> drawEmbers(topLeft, cell, alpha, time + phase)
        "ASH" -> drawAsh(topLeft, cell, alpha, time + phase)
        "ORE" -> drawOre(topLeft, cell, alpha, time + phase)
        "SMELT" -> drawSmelt(topLeft, cell, alpha)
        "METAL" -> drawMetal(topLeft, cell, alpha, time + phase, obj)
        "GLASS" -> drawGlass(topLeft, cell, alpha, time + phase)
        "EARTH" -> drawEarth(topLeft, cell, alpha)
        "MUD" -> drawMud(topLeft, cell, alpha, time + phase)
        "HUT" -> drawHut(topLeft, cell, alpha, time + phase, obj)
        "ICE" -> drawIce(topLeft, cell, alpha, time + phase)
        "STONE" -> drawStone(topLeft, cell, alpha, Palette.stone, Palette.stoneDark)
        "SEED" -> drawSeed(topLeft, cell, alpha, time + phase, obj.state)
        "TREE" -> drawTree(topLeft, cell, alpha, time + phase, obj)
        "STEAM" -> drawSteam(topLeft, cell, alpha, time, obj.amount, obj.capacity)
        "CLOUD" -> drawCloud(topLeft, cell, alpha, time + phase, obj.amount, obj.capacity, obj.isRaining)
        "WATER" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.waterLight, Palette.water)
        "SEAWATER" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.seaLight, Palette.sea)
        "LAVA" -> drawLiquidTile(topLeft, cell, alpha, time, info, Palette.lava, Palette.lavaDeep)
        "SAND" -> drawGrains(topLeft, cell, alpha, info, wet = obj.state == "WET", Palette.sand, Palette.sandDark)
        "SNOW" -> if (obj.state == "BALL") drawSnowball(topLeft, cell, alpha) else drawGrains(topLeft, cell, alpha, info, wet = false, Palette.snow, Palette.snowShade)
        "PUMICE" -> drawPumice(topLeft, cell, alpha)
        "SALT" -> drawSalt(topLeft, cell, alpha)
        else -> drawCircle(Palette.textDim, cell * 0.3f, topLeft + Offset(cell / 2, cell / 2), alpha)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

// ------------------------------------------------------------------ fire

/** One flame tongue from [base] upwards, [h] high and [w] wide. */
private fun DrawScope.drawFlame(cx: Float, base: Float, h: Float, w: Float, sway: Float, alpha: Float) {
    val outer = Path().apply {
        moveTo(cx + sway, base - h)
        cubicTo(cx + w * 0.9f, base - h * 0.55f, cx + w * 1.3f, base - h * 0.2f, cx + w * 0.8f, base - h * 0.04f)
        cubicTo(cx + w * 0.4f, base + h * 0.05f, cx - w * 0.4f, base + h * 0.05f, cx - w * 0.8f, base - h * 0.04f)
        cubicTo(cx - w * 1.3f, base - h * 0.2f, cx - w * 0.9f, base - h * 0.55f, cx + sway, base - h)
        close()
    }
    drawPath(outer, Palette.fire, alpha = alpha)
    val ih = h * 0.55f
    val iw = w * 0.55f
    val inner = Path().apply {
        moveTo(cx + sway * 0.5f, base - ih)
        cubicTo(cx + iw, base - ih * 0.5f, cx + iw * 1.2f, base - ih * 0.1f, cx, base - h * 0.02f)
        cubicTo(cx - iw * 1.2f, base - ih * 0.1f, cx - iw, base - ih * 0.5f, cx + sway * 0.5f, base - ih)
        close()
    }
    drawPath(inner, Palette.fireCore, alpha = alpha)
}

/** A campfire: crossed sticks with a flame. The big fire has more wood, three tongues and flying sparks. */
private fun DrawScope.drawCampfire(tl: Offset, c: Float, alpha: Float, time: Float, big: Boolean) {
    val cx = tl.x + c / 2
    val base = tl.y + c * 0.86f
    val glowR = c * (if (big) 1.5f else 0.85f)
    val glowCenter = Offset(cx, tl.y + c * 0.6f)
    val pulse = 0.85f + 0.15f * sin(time * TAU * 2f)
    drawCircle(
        Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.42f * pulse), Color.Transparent), glowCenter, glowR),
        glowR, glowCenter, alpha = alpha,
    )
    // Sticks.
    val stick = c * (if (big) 0.11f else 0.08f)
    drawLine(Palette.bark, Offset(cx - c * 0.34f, base), Offset(cx + c * 0.3f, base - c * 0.12f), strokeWidth = stick, cap = StrokeCap.Round, alpha = alpha)
    drawLine(Palette.barkDark, Offset(cx + c * 0.34f, base), Offset(cx - c * 0.3f, base - c * 0.12f), strokeWidth = stick, cap = StrokeCap.Round, alpha = alpha)
    if (big) {
        drawLine(Palette.bark, Offset(cx - c * 0.42f, base - c * 0.02f), Offset(cx + c * 0.42f, base - c * 0.02f), strokeWidth = stick, cap = StrokeCap.Round, alpha = alpha)
    }
    val flicker = 1f + 0.08f * sin(time * TAU * 3f)
    val sway = c * 0.03f * sin(time * TAU * 2f)
    if (big) {
        drawFlame(cx - c * 0.2f, base - c * 0.06f, c * 0.6f * (1f + 0.1f * sin(time * TAU * 3f + 1f)), c * 0.18f, -sway, alpha)
        drawFlame(cx + c * 0.2f, base - c * 0.06f, c * 0.62f * (1f + 0.1f * sin(time * TAU * 3f + 2.2f)), c * 0.18f, sway, alpha)
        drawFlame(cx, base - c * 0.05f, c * 0.95f * flicker, c * 0.28f, sway, alpha)
        for (k in 0 until 4) {
            val p = (time * 1.4f + k * 0.25f) % 1f
            val sx = cx + c * 0.3f * sin(p * TAU + k * 1.3f)
            drawCircle(Palette.fireCore, c * 0.025f, Offset(sx, base - c * (0.5f + 0.9f * p)), alpha = alpha * (1f - p))
        }
    } else {
        drawFlame(cx, base - c * 0.05f, c * 0.62f * flicker, c * 0.24f, sway, alpha)
    }
}

// ------------------------------------------------------------------ wood

private fun woodColors(obj: GameObject): Pair<Color, Color> {
    val moves = obj.int(Props.BURN_MOVES, 1).coerceAtLeast(1)
    val burnt = if (obj.state == "BURNING") (obj.burnt.toFloat() / moves).coerceIn(0f, 1f) else 0f
    return when (obj.state) {
        "BURNING" -> lerp(Palette.wood, Palette.charcoal, burnt) to lerp(Palette.woodDark, Color(0xFF151110), burnt)
        else -> Palette.woodLight to Palette.woodDark
    }
}

/** A lying log: bark on the side, rings on the cut end. */
private fun DrawScope.drawLogShape(origin: Offset, w: Float, h: Float, light: Color, dark: Color, alpha: Float, charred: Boolean) {
    drawRoundRect(
        Brush.verticalGradient(listOf(light, dark), origin.y, origin.y + h),
        origin, Size(w, h), CornerRadius(h / 2), alpha = alpha,
    )
    for (i in 1..2) {
        val y = origin.y + h * i / 3f
        drawLine(dark, Offset(origin.x + h * 0.4f, y), Offset(origin.x + w - h * 0.7f, y), strokeWidth = h * 0.06f, alpha = alpha * 0.7f)
    }
    val end = Offset(origin.x + w - h / 2, origin.y + h / 2)
    drawCircle(if (charred) Color(0xFF241B17) else Palette.woodLight, h * 0.46f, end, alpha = alpha)
    drawCircle(dark, h * 0.3f, end, alpha = alpha * 0.6f, style = Stroke(width = h * 0.06f))
    drawCircle(dark, h * 0.12f, end, alpha = alpha * 0.6f, style = Stroke(width = h * 0.06f))
}

private fun DrawScope.drawFlamesOnTop(left: Float, right: Float, top: Float, c: Float, time: Float, alpha: Float, strength: Float) {
    val center = Offset((left + right) / 2, top)
    drawCircle(Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.45f), Color.Transparent), center, c * 0.9f), c * 0.9f, center, alpha = alpha)
    for (i in 0..2) {
        val fx = left + (right - left) * (0.2f + 0.3f * i)
        val flicker = 0.8f + 0.25f * sin(time * TAU * 3f + i * 1.7f)
        drawFlame(fx, top + c * 0.06f, c * 0.36f * flicker * strength, c * 0.1f, c * 0.02f * sin(time * TAU * 2f + i), alpha)
    }
}

/** Flames shrink with every move something burns; a full flame has all its moves left. */
private fun flameStrength(obj: GameObject): Float {
    val moves = obj.int(Props.BURN_MOVES)
    return if (moves <= 0) 1f else 0.55f + 0.45f * obj.burnMovesLeft / moves
}

/**
 * How many more moves something burns before it crumbles to ash: glowing dots on a small dark badge
 * in the cell's lower left corner, one per move.
 */
private fun DrawScope.drawBurnMoves(tl: Offset, c: Float, alpha: Float, time: Float, left: Int) {
    if (left <= 0) return
    val r = c * 0.07f
    val gap = c * 0.19f
    val w = gap * (left - 1) + r * 3.6f
    val h = r * 3f
    val origin = Offset(tl.x + c * 0.03f, tl.y + c - h - c * 0.03f)
    drawRoundRect(Color(0xCC1E1410), origin, Size(w, h), CornerRadius(h / 2), alpha = alpha)
    for (i in 0 until left) {
        val center = Offset(origin.x + r * 1.8f + gap * i, origin.y + h / 2)
        val glow = 0.8f + 0.2f * sin(time * TAU * 2f + i * 1.3f)
        drawCircle(Palette.fire.copy(alpha = 0.5f), r * 1.6f, center, alpha = alpha * glow)
        drawCircle(Palette.fireCore, r, center, alpha = alpha)
    }
}

private fun DrawScope.drawLog(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject) {
    val (light, dark) = woodColors(obj)
    val h = c * 0.42f
    val origin = Offset(tl.x + c * 0.06f, tl.y + c - h - c * 0.04f)
    drawLogShape(origin, c * 0.88f, h, light, dark, alpha, charred = false)
    if (obj.state == "BURNING") {
        drawFlamesOnTop(origin.x, origin.x + c * 0.88f, origin.y, c, time, alpha, flameStrength(obj))
        drawBurnMoves(tl, c, alpha, time, obj.burnMovesLeft)
    }
}

// ------------------------------------------------------------------ ice, stone, minerals

private fun DrawScope.drawIce(tl: Offset, c: Float, alpha: Float, time: Float) {
    val inset = c * 0.07f
    val origin = tl + Offset(inset, inset)
    val s = Size(c - 2 * inset, c - 2 * inset)
    val r = CornerRadius(c * 0.1f)
    drawRoundRect(
        Brush.linearGradient(listOf(Palette.ice, Palette.iceDeep), origin, origin + Offset(s.width, s.height)),
        origin, s, r, alpha = alpha * 0.9f,
    )
    // Facets.
    val facet = Path().apply {
        moveTo(origin.x, origin.y + s.height * 0.55f)
        lineTo(origin.x + s.width * 0.45f, origin.y + s.height * 0.35f)
        lineTo(origin.x + s.width, origin.y + s.height * 0.6f)
        lineTo(origin.x + s.width, origin.y + s.height)
        lineTo(origin.x, origin.y + s.height)
        close()
    }
    drawPath(facet, Palette.iceDeep, alpha = alpha * 0.25f)
    drawRoundRect(Color.White, origin, s, r, style = Stroke(width = c * 0.03f), alpha = alpha * 0.6f)
    drawLine(Color.White, origin + Offset(s.width * 0.18f, s.height * 0.2f), origin + Offset(s.width * 0.48f, s.height * 0.2f), strokeWidth = c * 0.05f, cap = StrokeCap.Round, alpha = alpha * 0.8f)
    val sparkle = 0.5f + 0.5f * sin(time * TAU * 1.5f)
    val sp = origin + Offset(s.width * 0.72f, s.height * 0.3f)
    drawLine(Color.White, sp + Offset(-c * 0.06f, 0f), sp + Offset(c * 0.06f, 0f), strokeWidth = c * 0.02f, alpha = alpha * sparkle)
    drawLine(Color.White, sp + Offset(0f, -c * 0.06f), sp + Offset(0f, c * 0.06f), strokeWidth = c * 0.02f, alpha = alpha * sparkle)
}

private fun DrawScope.stonePath(tl: Offset, c: Float): Path {
    val center = tl + Offset(c / 2, c * 0.56f)
    val radii = floatArrayOf(0.44f, 0.4f, 0.46f, 0.42f, 0.45f, 0.39f, 0.44f, 0.41f)
    return Path().apply {
        radii.forEachIndexed { i, r ->
            val a = (i / radii.size.toFloat()) * TAU - PI.toFloat() / 2
            val p = center + Offset(cos(a) * r * c, sin(a) * r * c * 0.88f)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

private fun DrawScope.drawStone(tl: Offset, c: Float, alpha: Float, light: Color, dark: Color) {
    val path = stonePath(tl, c)
    val center = tl + Offset(c / 2, c * 0.56f)
    drawPath(path, Brush.linearGradient(listOf(light, dark), tl, tl + Offset(c, c)), alpha = alpha)
    drawPath(path, Color.Black, alpha = alpha * 0.35f, style = Stroke(width = c * 0.035f))
    drawCircle(Color.White, c * 0.06f, center + Offset(-c * 0.14f, -c * 0.16f), alpha = alpha * 0.35f)
    drawLine(Color.Black, center + Offset(c * 0.05f, c * 0.02f), center + Offset(c * 0.2f, c * 0.18f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha * 0.3f)
    // A little moss on top.
    drawArc(Palette.leafDark, 200f, 140f, useCenter = false, topLeft = tl + Offset(c * 0.18f, c * 0.14f), size = Size(c * 0.5f, c * 0.3f), style = Stroke(width = c * 0.05f, cap = StrokeCap.Round), alpha = alpha * 0.5f)
}

private fun DrawScope.drawPumice(tl: Offset, c: Float, alpha: Float) {
    val path = stonePath(tl, c)
    drawPath(path, Brush.linearGradient(listOf(Palette.pumice, Palette.pumiceDark), tl, tl + Offset(c, c)), alpha = alpha)
    drawPath(path, Color.Black, alpha = alpha * 0.25f, style = Stroke(width = c * 0.03f))
    val holes = listOf(0.3f to 0.45f, 0.55f to 0.38f, 0.66f to 0.62f, 0.4f to 0.7f, 0.5f to 0.55f, 0.24f to 0.62f)
    for ((x, y) in holes) drawCircle(Palette.pumiceDark, c * 0.04f, tl + Offset(c * x, c * y), alpha = alpha * 0.8f)
}

/** A packed snowball: it keeps its shape and does not trickle away like powder snow. */
private fun DrawScope.drawSnowball(tl: Offset, c: Float, alpha: Float) {
    val center = tl + Offset(c / 2, c * 0.62f)
    val r = c * 0.32f
    drawOval(Color.Black, tl + Offset(c * 0.2f, c * 0.9f), Size(c * 0.6f, c * 0.08f), alpha = alpha * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color.White, Palette.snow, Palette.snowShade), center - Offset(r * 0.35f, r * 0.4f), r * 1.5f), r, center, alpha = alpha)
    drawCircle(Palette.snowShade, r, center, alpha = alpha * 0.8f, style = Stroke(width = c * 0.02f))
    for ((x, y) in listOf(0.12f to 0.1f, -0.15f to 0.18f, 0.05f to -0.12f)) {
        drawCircle(Palette.snowShade, c * 0.025f, center + Offset(r * x * 2, r * y * 2), alpha = alpha * 0.7f)
    }
}

/** A cluster of salt crystals. */
private fun DrawScope.drawSalt(tl: Offset, c: Float, alpha: Float) {
    val cubes = listOf(Triple(0.18f, 0.48f, 0.32f), Triple(0.48f, 0.4f, 0.36f), Triple(0.34f, 0.2f, 0.26f))
    for ((x, y, s) in cubes) {
        val o = tl + Offset(c * x, c * (y + 0.14f))
        drawRoundRect(Palette.salt, o, Size(c * s, c * s), CornerRadius(c * 0.03f), alpha = alpha)
        drawRoundRect(Palette.snowShade, o, Size(c * s, c * s), CornerRadius(c * 0.03f), style = Stroke(width = c * 0.025f), alpha = alpha)
        drawLine(Color.White, o + Offset(c * s * 0.2f, c * s * 0.25f), o + Offset(c * s * 0.6f, c * s * 0.25f), strokeWidth = c * 0.025f, alpha = alpha)
    }
}

/** Sand or snow: a heap on its own, a dune surface next to more of it, solid under more of it. */
private fun DrawScope.drawGrains(tl: Offset, c: Float, alpha: Float, info: ObjectInfo, wet: Boolean, light: Color, dark: Color) {
    val base = if (wet) Palette.sandWet else light
    val shade = if (wet) Color(0xFF5E4626) else dark
    if (info.joinAbove) {
        drawRect(Brush.verticalGradient(listOf(shade, base, shade), tl.y, tl.y + c), tl, Size(c, c), alpha = alpha)
    } else {
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
    val grains = listOf(0.25f to 0.62f, 0.5f to 0.45f, 0.7f to 0.7f, 0.38f to 0.82f, 0.62f to 0.88f, 0.82f to 0.84f, 0.18f to 0.9f)
    for ((gx, gy) in grains) drawCircle(shade, c * 0.035f, tl + Offset(c * gx, c * gy), alpha = alpha * 0.8f)
}

// ------------------------------------------------------------------ living things

/** A seed in the ground; sprouted, a green shoot with two leaves; salted, a wilted husk. */
private fun DrawScope.drawSeed(tl: Offset, c: Float, alpha: Float, time: Float, state: String) {
    val cx = tl.x + c / 2
    val ground = tl.y + c * 0.92f
    // A little mound of soil.
    drawOval(Palette.barkDark, Offset(cx - c * 0.3f, ground - c * 0.12f), Size(c * 0.6f, c * 0.2f), alpha = alpha)
    val husk = if (state == "WITHERED") Color(0xFF8A7A5A) else Palette.woodLight
    drawOval(husk, Offset(cx - c * 0.13f, ground - c * 0.3f), Size(c * 0.26f, c * 0.22f), alpha = alpha)
    drawOval(Palette.woodDark, Offset(cx - c * 0.13f, ground - c * 0.3f), Size(c * 0.26f, c * 0.22f), style = Stroke(width = c * 0.025f), alpha = alpha)
    when (state) {
        "SPROUTED" -> {
            val sway = c * 0.04f * sin(time * TAU)
            val top = Offset(cx + sway, ground - c * 0.72f)
            drawLine(Palette.leafDark, Offset(cx, ground - c * 0.22f), top, strokeWidth = c * 0.05f, cap = StrokeCap.Round, alpha = alpha)
            for (side in listOf(-1f, 1f)) {
                val leaf = Path().apply {
                    moveTo(top.x, top.y + c * 0.08f)
                    cubicTo(top.x + side * c * 0.12f, top.y - c * 0.06f, top.x + side * c * 0.3f, top.y, top.x + side * c * 0.3f, top.y + c * 0.06f)
                    cubicTo(top.x + side * c * 0.2f, top.y + c * 0.16f, top.x + side * c * 0.06f, top.y + c * 0.14f, top.x, top.y + c * 0.08f)
                    close()
                }
                drawPath(leaf, Palette.leafLight, alpha = alpha)
            }
        }
        "WITHERED" -> drawLine(Color(0xFF8A7A5A), Offset(cx, ground - c * 0.26f), Offset(cx + c * 0.16f, ground - c * 0.4f), strokeWidth = c * 0.035f, cap = StrokeCap.Round, alpha = alpha)
        else -> drawLine(Palette.woodDark, Offset(cx, ground - c * 0.3f), Offset(cx + c * 0.04f, ground - c * 0.38f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha)
    }
}

/** A tree: trunk in its cell, crown reaching up into the cell above. Burning, it flares. */
private fun DrawScope.drawTree(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject) {
    val cx = tl.x + c / 2
    val ground = tl.y + c
    val burning = obj.state == "BURNING"
    drawRect(Palette.bark, Offset(cx - c * 0.08f, ground - c * 0.62f), Size(c * 0.16f, c * 0.62f), alpha = alpha)
    drawLine(Palette.barkDark, Offset(cx - c * 0.02f, ground - c * 0.5f), Offset(cx - c * 0.02f, ground - c * 0.1f), strokeWidth = c * 0.025f, alpha = alpha * 0.6f)
    val sway = c * 0.025f * sin(time * TAU)
    val crown = listOf(
        Triple(0f, -0.95f, 0.36f), Triple(-0.24f, -0.72f, 0.28f), Triple(0.24f, -0.72f, 0.28f),
        Triple(-0.12f, -1.2f, 0.26f), Triple(0.14f, -1.18f, 0.25f),
    )
    for ((dx, dy, r) in crown) {
        val center = Offset(cx + dx * c + sway, ground + dy * c)
        val light = if (burning) Color(0xFF8A6A2A) else Palette.leaf
        val dark = if (burning) Color(0xFF4A3018) else Palette.leafDark
        drawCircle(Brush.radialGradient(listOf(light, dark), center - Offset(c * 0.06f, c * 0.08f), r * c * 1.3f), r * c, center, alpha = alpha)
    }
    if (burning) {
        val strength = flameStrength(obj)
        for (i in 0..2) {
            val fx = cx + (i - 1) * c * 0.24f
            val flicker = 0.8f + 0.25f * sin(time * TAU * 3f + i * 1.7f)
            drawFlame(fx, ground - c * 0.78f, c * 0.6f * flicker * strength, c * 0.14f, c * 0.03f * sin(time * TAU * 2f + i), alpha)
        }
        drawBurnMoves(tl, c, alpha, time, obj.burnMovesLeft)
    }
}

// ------------------------------------------------------------------ air and weather

/** Soft, slowly drifting puffs; the more steam a cell holds, the denser it looks. */
private fun DrawScope.drawSteam(tl: Offset, c: Float, alpha: Float, time: Float, amount: Int, capacity: Int) {
    val t = time * TAU
    val fill = if (amount <= 0) 1f else (amount.toFloat() / capacity.coerceAtLeast(1)).coerceIn(0f, 1f)
    val density = 0.3f + 0.7f * fill
    val puffs = listOf(Triple(0.34f, 0.6f, 0.27f), Triple(0.64f, 0.52f, 0.3f), Triple(0.48f, 0.32f, 0.25f), Triple(0.22f, 0.3f, 0.2f), Triple(0.76f, 0.26f, 0.22f))
    val count = if (fill > 0.6f) puffs.size else 3
    for (i in 0 until count) {
        val (x, y, r) = puffs[i]
        val radius = c * r * (0.75f + 0.35f * fill)
        val center = tl + Offset(c * (x + 0.05f * sin(t + i * 2.1f)), c * (y + 0.04f * cos(t * 2f + i)))
        drawCircle(
            Brush.radialGradient(listOf(Palette.steam.copy(alpha = 0.75f), Palette.steam.copy(alpha = 0f)), center, radius),
            radius, center, alpha = alpha * density,
        )
    }
}

/**
 * A cloud grows with what it holds; when it rains it turns grey and lets drops fall below it.
 */
private fun DrawScope.drawCloud(tl: Offset, c: Float, alpha: Float, time: Float, amount: Int, capacity: Int, raining: Boolean) {
    val fill = (amount.toFloat() / capacity.coerceAtLeast(1)).coerceIn(0f, 1f)
    val scale = 0.7f + 0.5f * fill
    val cx = tl.x + c / 2 + c * 0.03f * sin(time * TAU)
    val cy = tl.y + c * 0.55f
    val body = if (raining) Palette.cloudDark else Palette.cloud
    val shade = if (raining) Color(0xFF5F6B78) else Color(0xFFC9D3DD)
    if (raining) {
        for (k in 0 until 5) {
            val p = (time * 2.2f + k * 0.21f) % 1f
            val x = cx + c * (k - 2) * 0.16f * scale
            val y = cy + c * 0.2f + c * 1.1f * p
            drawLine(Palette.rain, Offset(x, y), Offset(x - c * 0.03f, y + c * 0.16f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha * (1f - p) * 0.9f)
        }
    }
    val puffs = listOf(Triple(-0.28f, 0.08f, 0.24f), Triple(0.28f, 0.08f, 0.24f), Triple(-0.1f, -0.1f, 0.3f), Triple(0.14f, -0.06f, 0.27f), Triple(0f, 0.12f, 0.28f))
    for ((dx, dy, r) in puffs) {
        drawCircle(shade, r * c * scale, Offset(cx + dx * c * scale, cy + dy * c * scale + c * 0.04f), alpha = alpha)
    }
    for ((dx, dy, r) in puffs) {
        drawCircle(body, r * c * scale * 0.92f, Offset(cx + dx * c * scale, cy + dy * c * scale), alpha = alpha)
    }
    drawCircle(Color.White, c * 0.08f * scale, Offset(cx - c * 0.12f * scale, cy - c * 0.16f * scale), alpha = alpha * (if (raining) 0.2f else 0.6f))
}

/**
 * A liquid tile for icons and the discovery log: on the board, liquids are drawn as whole bodies by the
 * water renderer instead.
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
        return top + c * 0.045f * sin(u * TAU * 0.8f + time * TAU) + c * 0.02f * sin(u * TAU * 1.9f - time * TAU * 2f)
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
    drawPath(body, Brush.verticalGradient(listOf(light, deep), top - c * 0.1f, bottom + c * 0.6f), alpha = alpha * 0.92f)
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
}

// ------------------------------------------------------------------ materials of the sandbox

/** Charcoal: a blackened log, cracked into the typical little squares, with a dull sheen. */
private fun DrawScope.drawCharcoal(tl: Offset, c: Float, alpha: Float) {
    val h = c * 0.44f
    val origin = Offset(tl.x + c * 0.06f, tl.y + c - h - c * 0.04f)
    drawLogShape(origin, c * 0.88f, h, Color(0xFF5C524D), Color(0xFF1A1513), alpha, charred = true)
    for (i in 1..4) {
        val x = origin.x + c * 0.88f * i / 5.2f
        drawLine(Color(0xFF0C0A09), Offset(x, origin.y + h * 0.12f), Offset(x - h * 0.08f, origin.y + h * 0.88f), strokeWidth = c * 0.022f, alpha = alpha * 0.8f)
    }
    drawLine(Color(0xFFB5ADA8), origin + Offset(h * 0.5f, h * 0.2f), origin + Offset(c * 0.5f, h * 0.2f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha * 0.55f)
}

/**
 * Embers: a small heap of coals glowing from inside. They breathe, but they never flame – that is what
 * sets them apart from fire at a glance.
 */
private fun DrawScope.drawEmbers(tl: Offset, c: Float, alpha: Float, time: Float) {
    val pulse = 0.6f + 0.4f * sin(time * TAU)
    val ground = tl.y + c * 0.96f
    val center = Offset(tl.x + c / 2, ground - c * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Palette.ember.copy(alpha = 0.45f * pulse), Color.Transparent), center, c * 0.75f), c * 0.75f, center, alpha = alpha)
    val coals = listOf(Triple(0.3f, 0.16f, 0.19f), Triple(0.68f, 0.16f, 0.18f), Triple(0.5f, 0.3f, 0.2f), Triple(0.5f, 0.12f, 0.16f))
    coals.forEachIndexed { i, (x, y, r) ->
        val at = Offset(tl.x + c * x, ground - c * y)
        val glow = 0.55f + 0.45f * sin(time * TAU + i * 1.9f)
        drawCircle(Brush.radialGradient(listOf(lerp(Palette.emberDeep, Palette.fireCore, glow * 0.6f), Color(0xFF2A1410)), at, c * r * 1.1f), c * r, at, alpha = alpha)
        drawArc(Color(0xFF1A0E0B), 200f, 140f, useCenter = false, topLeft = at - Offset(c * r, c * r), size = Size(c * r * 2, c * r * 2), style = Stroke(width = c * 0.035f), alpha = alpha * 0.7f)
    }
    // A spark now and then.
    val p = (time * 1.3f) % 1f
    drawCircle(Palette.fireCore, c * 0.025f, Offset(tl.x + c * (0.42f + 0.12f * sin(time * TAU * 2)), ground - c * (0.45f + 0.4f * p)), alpha = alpha * (1f - p) * 0.9f)
}

/** Ash: a low, pale heap; a thin thread of smoke still rises from it. */
private fun DrawScope.drawAsh(tl: Offset, c: Float, alpha: Float, time: Float) {
    val ground = tl.y + c
    val heap = Path().apply {
        moveTo(tl.x + c * 0.08f, ground)
        cubicTo(tl.x + c * 0.2f, ground - c * 0.3f, tl.x + c * 0.75f, ground - c * 0.34f, tl.x + c * 0.92f, ground)
        close()
    }
    drawPath(heap, Brush.verticalGradient(listOf(Palette.ash, Palette.ashDark), ground - c * 0.3f, ground), alpha = alpha)
    for ((x, y) in listOf(0.3f to 0.1f, 0.5f to 0.18f, 0.62f to 0.08f, 0.42f to 0.06f, 0.72f to 0.12f)) {
        drawCircle(Color(0xFF3E3A37), c * 0.022f, Offset(tl.x + c * x, ground - c * y), alpha = alpha * 0.7f)
    }
    val p = (time * 0.6f) % 1f
    drawCircle(Palette.ash, c * (0.04f + 0.06f * p), Offset(tl.x + c * (0.5f + 0.08f * sin(time * TAU)), ground - c * (0.3f + 0.5f * p)), alpha = alpha * 0.35f * (1f - p))
}

/** Ore: a dark, heavy rock with warm metal flecks that catch the light. */
private fun DrawScope.drawOre(tl: Offset, c: Float, alpha: Float, time: Float) {
    val path = stonePath(tl, c)
    drawPath(path, Brush.linearGradient(listOf(Palette.ore, Palette.oreDark), tl, tl + Offset(c, c)), alpha = alpha)
    drawPath(path, Color.Black, alpha = alpha * 0.35f, style = Stroke(width = c * 0.035f))
    val flecks = listOf(0.34f to 0.42f, 0.58f to 0.36f, 0.66f to 0.62f, 0.4f to 0.7f, 0.5f to 0.54f, 0.26f to 0.6f)
    flecks.forEachIndexed { i, (x, y) ->
        val glint = 0.6f + 0.4f * sin(time * TAU + i * 1.4f)
        val at = tl + Offset(c * x, c * y)
        drawCircle(Palette.oreFleck, c * 0.04f, at, alpha = alpha * 0.9f)
        drawCircle(Palette.metalLight, c * 0.016f, at - Offset(c * 0.01f, c * 0.01f), alpha = alpha * glint)
    }
}

/** Smelt: lumps of ore pressed into charcoal, ready for the heat. */
private fun DrawScope.drawSmelt(tl: Offset, c: Float, alpha: Float) {
    val path = stonePath(tl, c)
    drawPath(path, Brush.linearGradient(listOf(Color(0xFF3A3230), Color(0xFF141110)), tl, tl + Offset(c, c)), alpha = alpha)
    drawPath(path, Color.Black, alpha = alpha * 0.4f, style = Stroke(width = c * 0.035f))
    val lumps = listOf(Triple(0.36f, 0.48f, 0.11f), Triple(0.6f, 0.42f, 0.1f), Triple(0.5f, 0.66f, 0.12f), Triple(0.3f, 0.7f, 0.07f), Triple(0.7f, 0.66f, 0.07f))
    for ((x, y, r) in lumps) {
        val at = tl + Offset(c * x, c * y)
        drawCircle(Brush.linearGradient(listOf(Palette.ore, Palette.oreDark), at - Offset(c * r, c * r), at + Offset(c * r, c * r)), c * r, at, alpha = alpha)
        drawCircle(Palette.oreFleck, c * r * 0.3f, at - Offset(c * r * 0.3f, c * r * 0.3f), alpha = alpha * 0.8f)
    }
}

/** Metal: an ingot; hot, it glows red to orange and shimmers. */
private fun DrawScope.drawMetal(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject) {
    val hot = obj.state == "HOT"
    val ground = tl.y + c * 0.94f
    val bar = Path().apply {
        moveTo(tl.x + c * 0.1f, ground)
        lineTo(tl.x + c * 0.24f, ground - c * 0.36f)
        lineTo(tl.x + c * 0.76f, ground - c * 0.36f)
        lineTo(tl.x + c * 0.9f, ground)
        close()
    }
    val pulse = 0.75f + 0.25f * sin(time * TAU * 1.5f)
    if (hot) {
        val center = Offset(tl.x + c / 2, ground - c * 0.18f)
        drawCircle(Brush.radialGradient(listOf(Palette.ember.copy(alpha = 0.5f * pulse), Color.Transparent), center, c * 0.7f), c * 0.7f, center, alpha = alpha)
    }
    val (light, dark) = if (hot) lerp(Palette.fireCore, Palette.ember, 1f - pulse) to Palette.emberDeep else Palette.metalLight to Palette.metalDark
    drawPath(bar, Brush.verticalGradient(listOf(light, dark), ground - c * 0.36f, ground), alpha = alpha)
    drawPath(bar, Color.Black, alpha = alpha * 0.35f, style = Stroke(width = c * 0.03f))
    val top = Path().apply {
        moveTo(tl.x + c * 0.24f, ground - c * 0.36f)
        lineTo(tl.x + c * 0.76f, ground - c * 0.36f)
        lineTo(tl.x + c * 0.7f, ground - c * 0.3f)
        lineTo(tl.x + c * 0.3f, ground - c * 0.3f)
        close()
    }
    drawPath(top, Color.White, alpha = alpha * (if (hot) 0.35f else 0.45f))
    if (hot) {
        for (i in 0..2) {
            val p = (time * 1.2f + i / 3f) % 1f
            val x = tl.x + c * (0.32f + 0.18f * i) + c * 0.03f * sin((p + i) * TAU * 2)
            drawLine(Palette.fireCore, Offset(x, ground - c * (0.42f + 0.3f * p)), Offset(x, ground - c * (0.48f + 0.3f * p)), strokeWidth = c * 0.02f, cap = StrokeCap.Round, alpha = alpha * 0.5f * (1f - p))
        }
    }
}

/** Glass: a clear block with a cool tint, bright edges and a travelling glint. */
private fun DrawScope.drawGlass(tl: Offset, c: Float, alpha: Float, time: Float) {
    val origin = tl + Offset(c * 0.12f, c * 0.28f)
    val size = Size(c * 0.76f, c * 0.66f)
    drawRoundRect(Brush.linearGradient(listOf(Palette.glass, Palette.glassDeep), origin, origin + Offset(size.width, size.height)), origin, size, CornerRadius(c * 0.06f), alpha = alpha * 0.55f)
    drawRoundRect(Palette.glass, origin, size, CornerRadius(c * 0.06f), style = Stroke(width = c * 0.03f), alpha = alpha * 0.9f)
    drawLine(Color.White, origin + Offset(c * 0.08f, c * 0.1f), origin + Offset(c * 0.08f, c * 0.5f), strokeWidth = c * 0.035f, cap = StrokeCap.Round, alpha = alpha * 0.7f)
    val p = (time * 0.5f) % 1f
    val x = origin.x + size.width * p
    drawLine(Color.White, Offset(x, origin.y + c * 0.04f), Offset(x - c * 0.12f, origin.y + size.height - c * 0.04f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha * 0.4f * sin(p * PI.toFloat()))
}

/** A clump of earth with crumbs and a tuft of grass. */
private fun DrawScope.drawEarth(tl: Offset, c: Float, alpha: Float) {
    val ground = tl.y + c * 0.96f
    val clump = Path().apply {
        moveTo(tl.x + c * 0.1f, ground)
        cubicTo(tl.x + c * 0.08f, ground - c * 0.45f, tl.x + c * 0.9f, ground - c * 0.52f, tl.x + c * 0.9f, ground)
        close()
    }
    drawPath(clump, Brush.verticalGradient(listOf(Palette.soil, Palette.soilDark), ground - c * 0.45f, ground), alpha = alpha)
    for ((x, y) in listOf(0.3f to 0.12f, 0.55f to 0.22f, 0.7f to 0.1f, 0.42f to 0.3f, 0.22f to 0.24f)) {
        drawCircle(Palette.soilDark, c * 0.03f, Offset(tl.x + c * x, ground - c * y), alpha = alpha * 0.9f)
    }
    for (k in -1..1) {
        drawLine(Palette.leaf, Offset(tl.x + c * (0.5f + 0.06f * k), ground - c * 0.36f), Offset(tl.x + c * (0.5f + 0.12f * k), ground - c * 0.5f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = alpha)
    }
}

/** Mud: a flat, glossy puddle of wet earth with a slow bubble. */
private fun DrawScope.drawMud(tl: Offset, c: Float, alpha: Float, time: Float) {
    val ground = tl.y + c * 0.98f
    val blob = Path().apply {
        moveTo(tl.x + c * 0.04f, ground)
        cubicTo(tl.x + c * 0.04f, ground - c * 0.42f, tl.x + c * 0.96f, ground - c * 0.48f, tl.x + c * 0.96f, ground)
        close()
    }
    drawPath(blob, Brush.verticalGradient(listOf(Palette.mud, Palette.mudDark), ground - c * 0.4f, ground), alpha = alpha)
    drawPath(blob, Color.Black, alpha = alpha * 0.3f, style = Stroke(width = c * 0.025f))
    drawArc(Color.White, 200f, 70f, useCenter = false, topLeft = Offset(tl.x + c * 0.18f, ground - c * 0.36f), size = Size(c * 0.42f, c * 0.24f), style = Stroke(width = c * 0.03f, cap = StrokeCap.Round), alpha = alpha * 0.45f)
    for ((x, y) in listOf(0.3f to 0.12f, 0.72f to 0.16f, 0.5f to 0.08f)) {
        drawCircle(Palette.mudDark, c * 0.03f, Offset(tl.x + c * x, ground - c * y), alpha = alpha * 0.8f)
    }
    val p = (time * 0.4f) % 1f
    val r = c * 0.06f * sin(p * PI.toFloat())
    drawCircle(Palette.mud, r, Offset(tl.x + c * 0.64f, ground - c * 0.3f), alpha = alpha, style = Stroke(width = c * 0.022f))
}

/** A small wooden hut: plank walls, a red roof, a door. Burning, flames lick the roof; charred, it is black. */
private fun DrawScope.drawHut(tl: Offset, c: Float, alpha: Float, time: Float, obj: GameObject) {
    val charred = obj.state == "CHARRED"
    val ground = tl.y + c
    val wallTop = ground - c * 0.48f
    val (plank, plankDark) = if (charred) Color(0xFF3A302C) to Color(0xFF1A1513) else Palette.woodLight to Palette.woodDark
    drawRect(Brush.verticalGradient(listOf(plank, plankDark), wallTop, ground), Offset(tl.x + c * 0.14f, wallTop), Size(c * 0.72f, c * 0.48f), alpha = alpha)
    for (i in 1..3) {
        val y = wallTop + c * 0.12f * i
        drawLine(plankDark, Offset(tl.x + c * 0.14f, y), Offset(tl.x + c * 0.86f, y), strokeWidth = c * 0.015f, alpha = alpha * 0.7f)
    }
    drawRect(if (charred) Color(0xFF0E0B0A) else Color(0xFF3B2716), Offset(tl.x + c * 0.42f, ground - c * 0.3f), Size(c * 0.16f, c * 0.3f), alpha = alpha)
    val roof = Path().apply {
        moveTo(tl.x + c * 0.04f, wallTop + c * 0.02f)
        lineTo(tl.x + c * 0.5f, ground - c * 0.86f)
        lineTo(tl.x + c * 0.96f, wallTop + c * 0.02f)
        close()
    }
    val (roofLight, roofDark) = if (charred) Color(0xFF2A2220) to Color(0xFF120E0D) else Palette.roof to Palette.roofDark
    drawPath(roof, Brush.verticalGradient(listOf(roofLight, roofDark), ground - c * 0.86f, wallTop), alpha = alpha)
    drawPath(roof, Color.Black, alpha = alpha * 0.3f, style = Stroke(width = c * 0.025f))
    if (obj.state == "BURNING") {
        drawFlamesOnTop(tl.x + c * 0.18f, tl.x + c * 0.82f, wallTop - c * 0.12f, c, time, alpha, 1.1f * flameStrength(obj))
        drawBurnMoves(tl, c, alpha, time, obj.burnMovesLeft)
    }
}
