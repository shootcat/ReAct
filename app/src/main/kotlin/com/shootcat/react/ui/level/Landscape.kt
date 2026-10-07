package com.shootcat.react.ui.level

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Terrain
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.WorldLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** A stable pseudo-random number in 0..1 for a cell, so textures do not flicker. */
private fun hash(x: Int, y: Int, salt: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + salt * 2147483647
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

/**
 * Everything the level is drawn on: the open sky with the world's scenery on the horizon, the dark of
 * caves under overhangs, and the landscape itself as a cut through the ground – soil that gets darker
 * with depth, boulders, and grass, sand, ash or snow wherever the ground meets the air.
 */
internal class Landscape(
    private val state: GameState,
    private val terrain: Map<Position, Terrain>,
    private val world: Int,
    private val look: WorldLook,
) {
    private val width = state.width
    private val height = state.height

    /** Open cells that see the sky straight above them. */
    private val skyLit: Set<Position> = buildSet {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val p = Position(x, y)
                if (state.isWall(p)) break
                add(p)
            }
        }
    }

    /** How many cells of ground lie above each wall cell (0 at the surface). */
    private val depth: Map<Position, Int> = buildMap {
        for (x in 0 until width) {
            var d = 0
            for (y in 0 until height) {
                val p = Position(x, y)
                if (state.isWall(p)) {
                    put(p, d)
                    d++
                } else {
                    d = 0
                }
            }
        }
    }

    /**
     * The typical height of the ground, where the horizon scenery stands: per column the top of the solid
     * ground at the bottom (an overhang or a roof does not count).
     */
    private val horizonRow: Float = run {
        val grounds = (0 until width).map { x ->
            var y = height
            while (y > 0 && state.isWall(Position(x, y - 1))) y--
            y
        }
        grounds.sorted()[grounds.size / 2].toFloat()
    }

    private fun wall(p: Position) = !state.inBounds(p) || state.isWall(p)

    private fun isRock(p: Position) = terrain[p] == Terrain.ROCK

    private fun earthColor(p: Position): Color {
        val d = depth[p] ?: 0
        val base = lerp(look.earth, look.earthDark, (d / 5f).coerceIn(0f, 1f))
        return if (hash(p.x, p.y, 7) > 0.8f) lerp(base, look.earthDark, 0.25f) else base
    }

    // ------------------------------------------------------------------ back: sky, scenery, caves

    /**
     * Fills the space around the grid ([left]/[top]/[right]/[bottom] in grid pixels, the grid itself
     * spans 0..width*cell and 0..height*cell): the sky goes on above, the ground below, and each row
     * continues sideways as it ends at the edge.
     */
    fun drawSurroundings(scope: DrawScope, cell: Float, left: Float, top: Float, right: Float, bottom: Float) = with(scope) {
        val w = width * cell
        val h = height * cell
        val horizon = horizonRow * cell
        drawRect(
            Brush.verticalGradient(listOf(look.skyTop, look.skyBottom), 0f, horizon.coerceAtLeast(cell * 3)),
            Offset(left, top),
            Size(right - left, h - top),
        )
        if (bottom > h) {
            drawRect(Brush.verticalGradient(listOf(look.earthDark, lerp(look.earthDark, Color.Black, 0.35f)), h, bottom), Offset(left, h), Size(right - left, bottom - h))
        }
        for (y in 0 until height) {
            for ((x, from, to) in listOf(Triple(0, left, 0f), Triple(width - 1, w, right))) {
                if (to <= from) continue
                val p = Position(x, y)
                val tl = Offset(from, y * cell)
                val size = Size(to - from, cell + 0.5f)
                when {
                    state.isWall(p) -> drawRect(if (isRock(p)) look.rockDark else earthColor(p), tl, size)
                    p !in skyLit -> drawRect(look.cave, tl, size, alpha = 0.84f)
                }
            }
        }
    }

    fun drawSky(scope: DrawScope, cell: Float, time: Float, slow: Float) = with(scope) {
        val w = width * cell
        val h = height * cell
        val horizon = horizonRow * cell
        drawRect(Brush.verticalGradient(listOf(look.skyTop, look.skyBottom), 0f, horizon.coerceAtLeast(cell * 3)), Offset.Zero, Size(w, h))
        when (world) {
            2 -> drawCoastScenery(cell, w, horizon, time, slow)
            3 -> drawVolcanoScenery(cell, w, horizon, time, slow)
            4 -> drawFrostScenery(cell, w, horizon, time, slow)
            5 -> drawMineScenery(cell, w, h, time, slow)
            else -> drawForestScenery(cell, w, horizon, time, slow)
        }
    }

    private fun DrawScope.drawForestScenery(c: Float, w: Float, horizon: Float, time: Float, slow: Float) {
        // Stars and a moon over the treetops.
        for (i in 0 until 14) {
            val x = hash(i, 1, 3) * w
            val y = hash(i, 2, 3) * horizon * 0.55f
            val twinkle = 0.4f + 0.6f * abs(sin((time + hash(i, 3, 3)) * TAU))
            drawCircle(Color.White, c * 0.035f, Offset(x, y), alpha = 0.5f * twinkle)
        }
        val moon = Offset(w * 0.78f, horizon * 0.18f + c * 0.6f)
        drawCircle(Brush.radialGradient(listOf(Color(0x55FFF4D6), Color.Transparent), moon, c * 1.6f), c * 1.6f, moon)
        drawCircle(Color(0xFFF4EBCB), c * 0.55f, moon)
        drawCircle(look.skyTop, c * 0.5f, moon + Offset(c * 0.25f, -c * 0.12f), alpha = 0.85f)
        // Two rows of fir trees, the far one fading into the sky.
        drawFirs(c, w, horizon - c * 0.2f, lerp(look.horizon, look.skyBottom, 0.45f), 0.8f, 11)
        drawFirs(c, w, horizon + c * 0.3f, look.horizon, 1.15f, 23)
        // Fireflies.
        for (i in 0 until 7) {
            val x = ((hash(i, 4, 5) + slow * (if (i % 2 == 0) 1 else -1)) % 1f + 1f) % 1f * w
            val y = horizon - c * (0.5f + 2.5f * hash(i, 5, 5)) + c * 0.4f * sin((slow * 6 + hash(i, 6, 5)) * TAU)
            val glow = 0.3f + 0.7f * abs(sin((time + hash(i, 7, 5)) * TAU))
            drawCircle(Brush.radialGradient(listOf(Color(0x99FFE98A), Color.Transparent), Offset(x, y), c * 0.25f), c * 0.25f, Offset(x, y), alpha = glow)
            drawCircle(Color(0xFFFFF3B0), c * 0.04f, Offset(x, y), alpha = glow)
        }
    }

    private fun DrawScope.drawFirs(c: Float, w: Float, base: Float, color: Color, scale: Float, salt: Int) {
        var x = -c * 0.4f
        var i = 0
        while (x < w + c) {
            val h = c * scale * (1.4f + 1.2f * hash(i, salt, 9))
            val half = c * scale * (0.35f + 0.15f * hash(i, salt + 1, 9))
            val tree = Path().apply {
                moveTo(x, base - h)
                lineTo(x + half, base - h * 0.45f)
                lineTo(x + half * 0.6f, base - h * 0.45f)
                lineTo(x + half * 1.2f, base)
                lineTo(x - half * 1.2f, base)
                lineTo(x - half * 0.6f, base - h * 0.45f)
                lineTo(x - half, base - h * 0.45f)
                close()
            }
            drawPath(tree, color)
            drawRect(color, Offset(x - half * 1.3f, base - c * 0.02f), Size(half * 2.6f, c * 2f))
            x += c * scale * (0.55f + 0.5f * hash(i, salt + 2, 9))
            i++
        }
    }

    private fun DrawScope.drawCoastScenery(c: Float, w: Float, horizon: Float, time: Float, slow: Float) {
        val sun = Offset(w * 0.22f, horizon * 0.3f + c * 0.5f)
        drawCircle(Brush.radialGradient(listOf(Color(0x88FFF1C4), Color.Transparent), sun, c * 2.2f), c * 2.2f, sun)
        drawCircle(Color(0xFFFFF4D2), c * 0.6f, sun)
        // Drifting clouds far away.
        for (i in 0 until 3) {
            val x = ((hash(i, 1, 2) + slow * (0.5f + i * 0.25f)) % 1f) * (w + c * 4) - c * 2
            val y = horizon * (0.15f + 0.2f * i)
            for (k in 0 until 3) drawCircle(Color.White, c * (0.35f + 0.1f * k), Offset(x + k * c * 0.4f, y - k % 2 * c * 0.12f), alpha = 0.35f)
        }
        // The open sea with an island and rolling wave lines.
        val sea = horizon - c * 1.2f
        drawRect(Brush.verticalGradient(listOf(look.horizon, Palette.sea), sea, horizon + c), Offset(0f, sea), Size(w, c * 3f))
        val island = Path().apply {
            moveTo(w * 0.55f, sea)
            cubicTo(w * 0.6f, sea - c * 0.9f, w * 0.72f, sea - c * 1.1f, w * 0.8f, sea)
            close()
        }
        drawPath(island, lerp(look.horizon, Color.Black, 0.3f))
        for (k in 0 until 4) {
            val y = sea + c * (0.25f + 0.3f * k)
            val shift = ((time + k * 0.3f) % 1f) * c * 1.2f
            var x = -c * 1.2f + shift
            while (x < w) {
                drawLine(Color.White, Offset(x, y), Offset(x + c * 0.4f, y), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = 0.3f)
                x += c * 1.2f
            }
        }
        // A pair of gulls.
        for (i in 0 until 2) {
            val gx = ((hash(i, 8, 4) + slow * 2f) % 1f) * (w + c * 2) - c
            val gy = horizon * 0.35f + i * c * 0.6f + c * 0.15f * sin((slow * 8 + i) * TAU)
            val flap = c * 0.12f * sin((time * 2 + i * 0.4f) * TAU)
            val gull = Path().apply {
                moveTo(gx - c * 0.3f, gy - flap)
                quadraticBezierTo(gx - c * 0.12f, gy - c * 0.12f, gx, gy)
                quadraticBezierTo(gx + c * 0.12f, gy - c * 0.12f, gx + c * 0.3f, gy - flap)
            }
            drawPath(gull, Color(0xFF2C3E50), style = Stroke(width = c * 0.04f, cap = StrokeCap.Round), alpha = 0.7f)
        }
    }

    private fun DrawScope.drawVolcanoScenery(c: Float, w: Float, horizon: Float, time: Float, slow: Float) {
        val peak = Offset(w * 0.62f, horizon - c * 3.2f)
        val cone = Path().apply {
            moveTo(peak.x - c * 0.7f, peak.y)
            lineTo(peak.x + c * 0.7f, peak.y)
            lineTo(peak.x + c * 5f, horizon + c)
            lineTo(peak.x - c * 5f, horizon + c)
            close()
        }
        val glowR = c * (2.2f + 0.2f * sin(time * TAU))
        drawCircle(Brush.radialGradient(listOf(Color(0x99FF6A1A), Color.Transparent), peak, glowR), glowR, peak)
        drawPath(cone, look.horizon)
        // Lava running down one flank.
        val flow = Path().apply {
            moveTo(peak.x - c * 0.2f, peak.y)
            cubicTo(peak.x - c * 0.6f, peak.y + c, peak.x - c * 0.2f, peak.y + c * 1.8f, peak.x - c * 1.1f, peak.y + c * 3f)
        }
        drawPath(flow, Palette.lava, style = Stroke(width = c * 0.12f, cap = StrokeCap.Round), alpha = 0.6f + 0.3f * sin(time * TAU))
        // Smoke above the crater, embers rising and ash falling.
        for (k in 0 until 4) {
            val p = (slow * 3 + k / 4f) % 1f
            val center = peak + Offset(c * 0.8f * p + c * 0.3f * sin((p + k) * TAU), -c * 2.5f * p)
            drawCircle(Color(0xFF4A3F3F), c * (0.4f + 0.7f * p), center, alpha = 0.5f * (1f - p))
        }
        for (i in 0 until 10) {
            val x = hash(i, 2, 6) * w + c * 0.3f * sin((slow * 5 + hash(i, 3, 6)) * TAU)
            val y = ((hash(i, 4, 6) + slow * 4f) % 1f) * height * c
            drawCircle(Color(0xFF8A8080), c * 0.035f, Offset(x, y), alpha = 0.45f)
        }
    }

    private fun DrawScope.drawFrostScenery(c: Float, w: Float, horizon: Float, time: Float, slow: Float) {
        // A pale aurora.
        val aurora = Path().apply {
            moveTo(0f, horizon * 0.25f)
            for (i in 0..12) {
                val x = w * i / 12f
                lineTo(x, horizon * 0.25f + c * 0.5f * sin((i / 12f * 1.5f + slow * 2f) * TAU))
            }
        }
        drawPath(aurora, Color(0xFF7CF2C4), style = Stroke(width = c * 0.5f, cap = StrokeCap.Round), alpha = 0.18f)
        drawPath(aurora, Color(0xFFB4FFE4), style = Stroke(width = c * 0.12f, cap = StrokeCap.Round), alpha = 0.25f)
        // Snowy mountains.
        val far = lerp(look.horizon, look.skyBottom, 0.35f)
        drawPeaks(c, w, horizon + c * 0.2f, far, 2.6f, 3)
        drawPeaks(c, w, horizon + c * 0.6f, look.horizon, 1.8f, 7)
        // Falling snow.
        for (i in 0 until 16) {
            val x = hash(i, 2, 8) * w + c * 0.4f * sin((slow * 6 + hash(i, 3, 8)) * TAU)
            val y = ((hash(i, 4, 8) + slow * 5f) % 1f) * height * c
            drawCircle(Color.White, c * (0.03f + 0.03f * hash(i, 5, 8)), Offset(x, y), alpha = 0.7f)
        }
    }

    /**
     * Deep in a mine: a rock face instead of a sky, timber frames holding up the gallery, lanterns that
     * flicker on the beams, glinting veins of ore and dust drifting in the lamplight.
     */
    private fun DrawScope.drawMineScenery(c: Float, w: Float, h: Float, time: Float, slow: Float) {
        // Strata and veins in the far rock.
        for (i in 0 until 9) {
            val y = h * hash(i, 1, 11)
            drawLine(look.horizon, Offset(0f, y), Offset(w, y + c * (hash(i, 2, 11) - 0.5f)), strokeWidth = c * (0.08f + 0.12f * hash(i, 3, 11)), alpha = 0.5f)
        }
        for (i in 0 until 12) {
            val at = Offset(hash(i, 4, 11) * w, hash(i, 5, 11) * h)
            val glint = 0.3f + 0.7f * abs(sin((time * 0.7f + hash(i, 6, 11)) * TAU))
            drawCircle(Color(0xFFD9895A), c * 0.04f, at, alpha = 0.35f)
            drawCircle(Color(0xFFFFE2B8), c * 0.02f, at, alpha = 0.5f * glint)
        }
        // Timber frames: two posts and a beam, every few cells.
        val beam = Color(0xFF5A3E26)
        val beamDark = Color(0xFF3A2716)
        var x = c * 1.5f
        var k = 0
        while (x < w) {
            val top = c * (0.6f + 0.3f * hash(k, 7, 11))
            drawRect(Brush.horizontalGradient(listOf(beam, beamDark), x - c * 0.12f, x + c * 0.12f), Offset(x - c * 0.12f, top), Size(c * 0.24f, h - top), alpha = 0.85f)
            drawRect(Brush.verticalGradient(listOf(beam, beamDark), top - c * 0.2f, top + c * 0.1f), Offset(x - c * 0.9f, top - c * 0.2f), Size(c * 1.8f, c * 0.3f), alpha = 0.85f)
            // A lantern hanging from the beam.
            val lamp = Offset(x + c * 0.5f, top + c * 0.45f)
            val flicker = 0.75f + 0.25f * sin(time * TAU * 3f + k) * sin(time * TAU * 1.3f + k * 2)
            drawLine(Color(0xFF1E1A18), Offset(lamp.x, top + c * 0.1f), Offset(lamp.x, lamp.y - c * 0.12f), strokeWidth = c * 0.025f, alpha = 0.8f)
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFB15A), Color.Transparent), lamp, c * 2.4f), c * 2.4f, lamp, alpha = flicker)
            drawRoundRect(Color(0xFF2A2420), Offset(lamp.x - c * 0.1f, lamp.y - c * 0.14f), Size(c * 0.2f, c * 0.26f), CornerRadius(c * 0.04f))
            drawCircle(Color(0xFFFFD27A), c * 0.06f, lamp, alpha = flicker)
            x += c * (4f + 1.5f * hash(k, 8, 11))
            k++
        }
        // Dust in the lamplight.
        for (i in 0 until 14) {
            val dx = hash(i, 9, 11) * w + c * 0.5f * sin((slow * 4 + hash(i, 10, 11)) * TAU)
            val dy = ((hash(i, 12, 11) + slow * 1.5f) % 1f) * h
            drawCircle(Color(0xFFE8D2B0), c * 0.02f, Offset(dx, dy), alpha = 0.3f)
        }
    }

    private fun DrawScope.drawPeaks(c: Float, w: Float, base: Float, color: Color, scale: Float, salt: Int) {
        var x = -c
        var i = 0
        while (x < w + c) {
            val h = c * scale * (0.8f + 0.6f * hash(i, salt, 1))
            val half = c * scale * (0.7f + 0.4f * hash(i, salt + 1, 1))
            val mountain = Path().apply {
                moveTo(x - half, base + c)
                lineTo(x, base - h)
                lineTo(x + half, base + c)
                close()
            }
            drawPath(mountain, color)
            val cap = Path().apply {
                moveTo(x - half * 0.3f, base - h * 0.6f)
                lineTo(x, base - h)
                lineTo(x + half * 0.3f, base - h * 0.6f)
                lineTo(x + half * 0.1f, base - h * 0.66f)
                lineTo(x - half * 0.08f, base - h * 0.58f)
                close()
            }
            drawPath(cap, Color.White, alpha = 0.85f)
            x += half * 1.3f
            i++
        }
    }

    /** Open cells under an overhang lie in shadow; the shadow fades out towards the open sky. */
    fun drawCaves(scope: DrawScope, cell: Float) = with(scope) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = Position(x, y)
                if (state.isWall(p) || p in skyLit) continue
                val tl = Offset(x * cell, y * cell)
                drawRect(look.cave, tl, Size(cell + 0.5f, cell + 0.5f), alpha = 0.84f)
                for (dx in listOf(-1, 1)) {
                    val n = Position(x + dx, y)
                    if (n !in skyLit) continue
                    val from = if (dx < 0) tl.x else tl.x + cell
                    val to = from + dx * cell * 0.6f
                    drawRect(
                        Brush.horizontalGradient(listOf(look.cave.copy(alpha = 0.6f), look.cave.copy(alpha = 0f)), from, to),
                        Offset(minOf(from, to), tl.y),
                        Size(cell * 0.6f, cell + 0.5f),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ the ground

    fun drawGround(scope: DrawScope, cell: Float, time: Float) = with(scope) {
        val r = cell * 0.32f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = Position(x, y)
                if (!state.isWall(p)) continue
                val tl = Offset(x * cell, y * cell)
                val up = wall(p.up())
                val down = wall(p.down())
                val left = wall(p.left())
                val right = wall(p.right())
                fun corner(a: Boolean, b: Boolean) = if (!a && !b) CornerRadius(r) else CornerRadius.Zero
                val shape = Path().apply {
                    addRoundRect(
                        RoundRect(
                            tl.x, tl.y, tl.x + cell + 0.5f, tl.y + cell + 0.5f,
                            topLeftCornerRadius = corner(up, left),
                            topRightCornerRadius = corner(up, right),
                            bottomRightCornerRadius = corner(down, right),
                            bottomLeftCornerRadius = corner(down, left),
                        ),
                    )
                }
                if (isRock(p)) drawRock(shape, p, tl, cell) else drawEarth(shape, p, tl, cell)
                // Edges that face open space catch a little light or shadow.
                if (!left) drawLine(Color.Black, tl, tl + Offset(0f, cell), strokeWidth = cell * 0.05f, alpha = 0.18f)
                if (!right) drawLine(Color.Black, tl + Offset(cell, 0f), tl + Offset(cell, cell), strokeWidth = cell * 0.05f, alpha = 0.18f)
                if (!down) drawOverhang(p, tl, cell, time)
            }
        }
        drawFillets(cell)
        // Cover last, so grass and snow lap over the rounded corners.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = Position(x, y)
                if (!state.isWall(p) || wall(p.up())) continue
                drawCover(p, Offset(x * cell, y * cell), cell, time)
            }
        }
    }

    private fun DrawScope.drawEarth(shape: Path, p: Position, tl: Offset, c: Float) {
        val color = earthColor(p)
        drawPath(shape, color)
        // Pebbles and grit.
        for (k in 0 until 3) {
            val hx = hash(p.x, p.y, 11 + k)
            val hy = hash(p.x, p.y, 21 + k)
            if (hash(p.x, p.y, 31 + k) < 0.45f) continue
            val size = c * (0.06f + 0.08f * hash(p.x, p.y, 41 + k))
            drawOval(
                lerp(color, look.rockDark, 0.55f),
                Offset(tl.x + c * (0.1f + 0.75f * hx), tl.y + c * (0.12f + 0.75f * hy)),
                Size(size * 1.5f, size),
                alpha = 0.7f,
            )
        }
        // A faint layer line every few rows.
        if ((p.y + p.x / 4) % 3 == 0) {
            val y = tl.y + c * (0.3f + 0.4f * hash(p.x, p.y, 51))
            drawLine(lerp(color, Color.Black, 0.2f), Offset(tl.x, y), Offset(tl.x + c, y + c * 0.05f), strokeWidth = c * 0.03f, alpha = 0.5f)
        }
        // Roots in forest soil.
        if (world == 1 && (depth[p] ?: 0) in 1..2 && hash(p.x, p.y, 61) > 0.7f) {
            val root = Path().apply {
                moveTo(tl.x + c * 0.3f, tl.y)
                cubicTo(tl.x + c * 0.5f, tl.y + c * 0.3f, tl.x + c * 0.2f, tl.y + c * 0.5f, tl.x + c * 0.45f, tl.y + c * 0.85f)
            }
            drawPath(root, Palette.barkDark, style = Stroke(width = c * 0.035f, cap = StrokeCap.Round), alpha = 0.6f)
        }
    }

    private fun DrawScope.drawRock(shape: Path, p: Position, tl: Offset, c: Float) {
        drawPath(shape, look.rockDark)
        val inset = c * 0.05f
        val boulder = Path().apply {
            addRoundRect(
                RoundRect(
                    tl.x + inset, tl.y + inset, tl.x + c - inset, tl.y + c - inset,
                    CornerRadius(c * (0.2f + 0.15f * hash(p.x, p.y, 3))),
                ),
            )
        }
        drawPath(boulder, Brush.linearGradient(listOf(look.rock, look.rockDark), tl, tl + Offset(c, c)))
        drawLine(Color.White, tl + Offset(c * 0.2f, c * 0.18f), tl + Offset(c * 0.5f, c * 0.14f), strokeWidth = c * 0.04f, cap = StrokeCap.Round, alpha = 0.18f)
        if (hash(p.x, p.y, 5) > 0.5f) {
            drawLine(Color.Black, tl + Offset(c * 0.55f, c * 0.4f), tl + Offset(c * 0.75f, c * 0.7f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = 0.3f)
        }
        if (world == 3 && hash(p.x, p.y, 9) > 0.75f) {
            // Glowing seams in volcanic rock.
            drawLine(Palette.ember, tl + Offset(c * 0.2f, c * 0.7f), tl + Offset(c * 0.6f, c * 0.55f), strokeWidth = c * 0.03f, cap = StrokeCap.Round, alpha = 0.55f)
        }
    }

    /** Fills the inner corners of open space with ground, so hollows look scooped out rather than boxy. */
    private fun DrawScope.drawFillets(c: Float) {
        val r = c * 0.24f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = Position(x, y)
                if (state.isWall(p)) continue
                val tl = Offset(x * c, y * c)
                for ((dx, dy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                    val a = Position(x + dx, y)
                    val b = Position(x, y + dy)
                    if (!state.inBounds(a) || !state.inBounds(b) || !state.isWall(a) || !state.isWall(b)) continue
                    val corner = Offset(if (dx < 0) tl.x else tl.x + c, if (dy < 0) tl.y else tl.y + c)
                    // A concave fillet: the area between the corner and the curve.
                    val curve = Path().apply {
                        moveTo(corner.x - dx * r, corner.y)
                        quadraticBezierTo(corner.x, corner.y, corner.x, corner.y - dy * r)
                        lineTo(corner.x, corner.y)
                        close()
                    }
                    val wallCell = if (isRock(b)) look.rockDark else earthColor(b)
                    drawPath(curve, wallCell)
                }
            }
        }
    }

    private fun DrawScope.drawCover(p: Position, tl: Offset, c: Float, time: Float) {
        val rock = isRock(p)
        val leftOpen = !wall(p.left())
        val rightOpen = !wall(p.right())
        val inset = c * 0.04f
        val x0 = tl.x + if (leftOpen) inset else 0f
        val x1 = tl.x + c - if (rightOpen) inset else 0f
        when (world) {
            2 -> if (!rock) {
                // A sandy crust with ripples.
                drawRoundRect(look.cover, Offset(x0, tl.y - c * 0.02f), Size(x1 - x0, c * 0.18f), CornerRadius(c * 0.08f))
                drawLine(look.coverDark, Offset(x0 + c * 0.15f, tl.y + c * 0.08f), Offset(x0 + c * 0.45f, tl.y + c * 0.06f), strokeWidth = c * 0.025f, cap = StrokeCap.Round, alpha = 0.7f)
                if (hash(p.x, p.y, 70) > 0.6f) drawCircle(Color(0xFFF7E6D0), c * 0.05f, Offset(x0 + c * 0.7f, tl.y + c * 0.02f))
            }
            3 -> {
                drawRoundRect(look.cover, Offset(x0, tl.y - c * 0.01f), Size(x1 - x0, c * 0.12f), CornerRadius(c * 0.06f), alpha = 0.9f)
                for (k in 0 until 3) {
                    drawCircle(look.coverDark, c * 0.025f, Offset(x0 + (x1 - x0) * (0.2f + 0.3f * k), tl.y + c * 0.05f))
                }
            }
            4 -> {
                // A pillow of snow that bulges over open edges.
                val snow = Path().apply {
                    moveTo(x0 - (if (leftOpen) c * 0.04f else 0f), tl.y + c * 0.16f)
                    cubicTo(x0, tl.y - c * 0.1f, x1, tl.y - c * 0.1f, x1 + (if (rightOpen) c * 0.04f else 0f), tl.y + c * 0.16f)
                    close()
                }
                drawPath(snow, look.cover)
                drawRect(look.cover, Offset(x0, tl.y), Size(x1 - x0, c * 0.16f))
                drawLine(look.coverDark, Offset(x0, tl.y + c * 0.17f), Offset(x1, tl.y + c * 0.17f), strokeWidth = c * 0.03f, alpha = 0.7f)
            }
            else -> {
                val grass = if (rock) look.coverDark else look.cover
                if (rock && hash(p.x, p.y, 72) < 0.5f) return
                drawRoundRect(grass, Offset(x0, tl.y - c * 0.02f), Size(x1 - x0, c * 0.15f), CornerRadius(c * 0.06f))
                drawRect(look.coverDark, Offset(x0, tl.y + c * 0.1f), Size(x1 - x0, c * 0.04f), alpha = 0.7f)
                if (rock) return
                // Blades of grass swaying a little, now and then a flower.
                val sway = c * 0.025f * sin((time + p.x * 0.17f) * TAU)
                for (k in 0 until 5) {
                    val bx = x0 + (x1 - x0) * (0.1f + 0.2f * k) + c * 0.04f * hash(p.x, k, 73)
                    val bh = c * (0.1f + 0.1f * hash(p.x, k, 74))
                    drawLine(grass, Offset(bx, tl.y + c * 0.02f), Offset(bx + sway, tl.y - bh), strokeWidth = c * 0.035f, cap = StrokeCap.Round)
                }
                if (hash(p.x, p.y, 75) > 0.72f) {
                    val fx = x0 + (x1 - x0) * (0.3f + 0.4f * hash(p.x, p.y, 76))
                    val center = Offset(fx + sway, tl.y - c * 0.14f)
                    drawLine(look.coverDark, Offset(fx, tl.y), center, strokeWidth = c * 0.025f)
                    val petal = if (hash(p.x, p.y, 77) > 0.5f) Color(0xFFFFF3B0) else Color(0xFFF2B6D2)
                    for (i in 0 until 4) {
                        val a = i * TAU / 4
                        drawCircle(petal, c * 0.035f, center + Offset(sin(a) * c * 0.04f, kotlin.math.cos(a) * c * 0.04f))
                    }
                    drawCircle(Palette.fireCore, c * 0.025f, center)
                }
            }
        }
    }

    /** Under an overhang: roots in the forest, icicles in the frost world. */
    private fun DrawScope.drawOverhang(p: Position, tl: Offset, c: Float, time: Float) {
        drawLine(Color.Black, tl + Offset(0f, c), tl + Offset(c, c), strokeWidth = c * 0.06f, alpha = 0.25f)
        when (world) {
            1 -> if (!isRock(p) && hash(p.x, p.y, 80) > 0.55f) {
                val x = tl.x + c * (0.25f + 0.5f * hash(p.x, p.y, 81))
                val len = c * (0.15f + 0.25f * hash(p.x, p.y, 82))
                val sway = c * 0.03f * sin((time + p.x * 0.3f) * TAU)
                val root = Path().apply {
                    moveTo(x, tl.y + c)
                    quadraticBezierTo(x + c * 0.06f, tl.y + c + len * 0.5f, x + sway, tl.y + c + len)
                }
                drawPath(root, Palette.barkDark, style = Stroke(width = c * 0.03f, cap = StrokeCap.Round))
            }
            4 -> if (hash(p.x, p.y, 83) > 0.4f) {
                val x = tl.x + c * (0.2f + 0.6f * hash(p.x, p.y, 84))
                val len = c * (0.12f + 0.2f * hash(p.x, p.y, 85))
                val icicle = Path().apply {
                    moveTo(x - c * 0.05f, tl.y + c)
                    lineTo(x + c * 0.05f, tl.y + c)
                    lineTo(x, tl.y + c + len)
                    close()
                }
                drawPath(icicle, Palette.ice, alpha = 0.85f)
            }
        }
    }
}
