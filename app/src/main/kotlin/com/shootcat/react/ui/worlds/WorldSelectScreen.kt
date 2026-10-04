package com.shootcat.react.ui.worlds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.model.WorldData
import com.shootcat.react.ui.components.DiscoveryBadge
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.map.drawWorldBand
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.WorldLooks
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()
private val RegionHeight = 210.dp
private val NodeSize = 86.dp
private val NodeWidth = 170.dp

/** Where each world's node sits on the overworld, from the forest at the bottom to the frost at the top. */
private val NODE_X = listOf(0.3f, 0.7f, 0.32f, 0.66f)

/**
 * The overworld: one painted landscape you travel upwards through – the forest at the foot, the coast,
 * the volcano and the frozen peaks at the top. A path links the four worlds; closed ones lie in fog.
 */
@Composable
fun WorldSelectScreen(
    content: GameContent,
    progress: Progress,
    isWorldUnlocked: (Int) -> Boolean,
    solvedIn: (Int) -> Int,
    onOpenWorld: (Int) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "overworld")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val worlds = content.worlds
    val total = content.allRules.size + content.merges.size
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(title = "Weltkarte", onBack = onBack) {
            DiscoveryBadge(progress.discoveries.size, total, onOpenLog)
            RoundIconButton(Glyph.GEAR, "Einstellungen", onOpenSettings, size = 40.dp)
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            val contentHeight = maxOf(maxHeight, RegionHeight * worlds.size)
            val mapWidth = maxWidth
            val viewport = maxHeight
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // Start at the newest open world.
            val focus = worlds.indices.lastOrNull { isWorldUnlocked(worlds[it].world) } ?: 0
            LaunchedEffect(scroll.maxValue, focus) {
                if (scroll.maxValue > 0) {
                    val y = with(density) { (contentHeight * nodeY(focus, worlds.size) - viewport / 2).toPx() }
                    scroll.scrollTo(y.toInt().coerceIn(0, scroll.maxValue))
                }
            }
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(contentHeight)) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (size.minDimension <= 0f) return@Canvas
                        drawOverworld(worlds.size, time)
                        drawTrail(worlds.indices.map { Offset(NODE_X[it % NODE_X.size] * size.width, nodeY(it, worlds.size) * size.height) }, worlds, isWorldUnlocked)
                        // Fog over the worlds that are still closed.
                        for ((i, world) in worlds.withIndex()) {
                            if (isWorldUnlocked(world.world)) continue
                            val top = size.height * (1f - (i + 1f) / worlds.size)
                            drawRect(
                                Brush.verticalGradient(listOf(Color(0x00E8EEF4), Color(0x99D8E0E8), Color(0x00E8EEF4)), top, top + size.height / worlds.size),
                                Offset(0f, top),
                                Size(size.width, size.height / worlds.size),
                            )
                        }
                    }
                    for ((i, world) in worlds.withIndex()) {
                        WorldNode(
                            world = world,
                            content = content,
                            unlocked = isWorldUnlocked(world.world),
                            solved = solvedIn(world.world),
                            stars = world.allLevelIds.sumOf { progress.extrasFor(it).size },
                            bonusSolved = world.bonusLevelId in progress.completed,
                            pulse = time,
                            onClick = { onOpenWorld(world.world) },
                            modifier = Modifier.offset(
                                x = mapWidth * NODE_X[i % NODE_X.size] - NodeWidth / 2,
                                y = contentHeight * nodeY(i, worlds.size) - NodeSize / 2,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** World [index] (0 = first, at the bottom) sits in the middle of its band. */
private fun nodeY(index: Int, count: Int): Float = 1f - (index + 0.5f) / count

@Composable
private fun WorldNode(
    world: WorldData,
    content: GameContent,
    unlocked: Boolean,
    solved: Int,
    stars: Int,
    bonusSolved: Boolean,
    pulse: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = WorldLooks.of(world.world)
    val total = world.levelIds.size
    val done = solved == total && total > 0
    val glow = 0.5f + 0.5f * sin(pulse * TAU * 2f)
    val ring = when {
        done -> Palette.success
        unlocked -> Palette.accent.copy(alpha = 0.6f + 0.4f * glow)
        else -> Palette.outline
    }
    Column(modifier.width(NodeWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(NodeSize)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(look.skyBottom, look.skyTop)))
                .border(3.dp, ring, CircleShape)
                .clickable(enabled = unlocked, onClick = onClick)
                .testTag("world_${world.world}")
                .semantics { contentDescription = "Welt ${world.world} ${world.title}" },
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked) {
                ObjectIcon(world.icon, content.types, size = 56.dp, background = Color.Transparent)
            } else {
                GlyphIcon(Glyph.LOCK, color = Palette.textDim, size = 28.dp)
            }
        }
        Column(
            Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.background.copy(alpha = 0.72f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .alpha(if (unlocked) 1f else 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Welt ${world.world} · ${world.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$solved/$total", style = MaterialTheme.typography.labelMedium, color = Palette.textDim, maxLines = 1)
                GlyphIcon(Glyph.STAR, color = if (stars > 0) Palette.accent else Palette.outline, size = 14.dp)
                Text("$stars", style = MaterialTheme.typography.labelMedium, color = Palette.textDim, maxLines = 1)
                if (world.bonusLevelId != null) {
                    GlyphIcon(Glyph.SPARK, color = if (bonusSolved) Palette.signal else Palette.outline, size = 14.dp)
                }
            }
        }
    }
}

private fun DrawScope.drawTrail(points: List<Offset>, worlds: List<WorldData>, unlocked: (Int) -> Boolean) {
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val path = Path().apply {
            moveTo(a.x, a.y)
            cubicTo(a.x, (a.y + b.y) / 2, b.x, (a.y + b.y) / 2, b.x, b.y)
        }
        val open = unlocked(worlds[i + 1].world)
        drawPath(path, Color(0xFF2A1E14), alpha = 0.5f, style = Stroke(width = 16f, cap = StrokeCap.Round))
        drawPath(
            path,
            if (open) Color(0xFFF4E2B8) else Color(0xFF8C8478),
            alpha = 0.85f,
            style = Stroke(width = 7f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f))),
        )
    }
}

/** Four bands of landscape, from the forest at the bottom to the frozen peaks at the top. */
private fun DrawScope.drawOverworld(count: Int, time: Float) {
    val w = size.width
    val band = size.height / count.coerceAtLeast(1)
    for (i in 0 until count) drawWorldBand(i + 1, size.height - (i + 1) * band, band, w, time)
    // Soft seams between the bands.
    for (i in 1 until count) {
        val y = size.height - i * band
        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x33000000), Color(0x00000000)), y - 30f, y + 30f), Offset(0f, y - 30f), Size(w, 60f))
    }
}
