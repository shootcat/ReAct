package com.shootcat.react.ui.map

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.MapNode
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.engine.model.WorldData
import com.shootcat.react.ui.components.DiscoveryBadge
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin

private val NodeSize = 68.dp
private val NodeLabelWidth = 128.dp
private val NodeSpacing = 104.dp
private const val TAU = (2 * PI).toFloat()

@Composable
fun WorldMapScreen(
    content: GameContent,
    world: WorldData,
    progress: Progress,
    isUnlocked: (String) -> Boolean,
    onOpenLevel: (String) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "map")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val nodes = world.map.filter { content.level(it.levelId) != null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(title = world.title, overline = "Welt ${world.world}", onBack = onBack) {
            DiscoveryBadge(progress.discoveries.size, content.allRules.size, onOpenLog)
            RoundIconButton(Glyph.GEAR, "Einstellungen", onOpenSettings, size = 40.dp)
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            // The map is taller than the screen: it scrolls, starting at the next open level.
            val contentHeight = maxOf(maxHeight, NodeSpacing * nodes.size + 40.dp)
            val viewport = maxHeight
            val mapWidth = maxWidth
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            val focus = nodes.firstOrNull { isUnlocked(it.levelId) && it.levelId !in progress.completed } ?: nodes.lastOrNull { isUnlocked(it.levelId) }
            LaunchedEffect(scroll.maxValue, focus?.levelId) {
                if (focus != null && scroll.maxValue > 0) {
                    val target = with(density) { (contentHeight * focus.y - viewport / 2).toPx() }
                    scroll.scrollTo(target.toInt().coerceIn(0, scroll.maxValue))
                }
            }
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(contentHeight)) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (size.minDimension <= 0f) return@Canvas
                        drawLandscape(world.world, time)
                        drawRoute(nodes, progress, world.bonusLevelId)
                    }
                    for (node in nodes) {
                        val level = content.level(node.levelId) ?: continue
                        MapNodeView(
                            label = content.label(level.id),
                            bonus = level.id == world.bonusLevelId,
                            node = node,
                            level = level,
                            types = world.types,
                            unlocked = isUnlocked(level.id),
                            completed = level.id in progress.completed,
                            found = progress.solutionsFor(level.id).size,
                            pulse = time,
                            onClick = { onOpenLevel(level.id) },
                            modifier = Modifier.offset(
                                x = mapWidth * node.x - NodeLabelWidth / 2,
                                y = contentHeight * node.y - NodeSize / 2,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapNodeView(
    label: String,
    bonus: Boolean,
    node: MapNode,
    level: LevelData,
    types: TypeCatalog,
    unlocked: Boolean,
    completed: Boolean,
    found: Int,
    pulse: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = 0.5f + 0.5f * sin(pulse * TAU * 3f)
    val ring = when {
        completed -> Palette.success
        bonus && unlocked -> Palette.signal.copy(alpha = 0.6f + 0.4f * glow)
        unlocked -> Palette.accent.copy(alpha = 0.55f + 0.45f * glow)
        bonus -> Palette.signal.copy(alpha = 0.35f)
        else -> Palette.outline
    }
    Column(modifier.width(NodeLabelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(NodeSize)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (unlocked) listOf(Palette.surfaceHigh, Palette.background) else listOf(Palette.surface, Palette.background),
                        ),
                    )
                    .border(3.dp, ring, CircleShape)
                    .clickable(enabled = unlocked, onClick = onClick)
                    .testTag("level_${level.id}")
                    .semantics { contentDescription = "Level $label ${level.title}" },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    unlocked -> ObjectIcon(node.icon, types, size = 42.dp, background = Color.Transparent)
                    bonus -> GlyphIcon(Glyph.SPARK, color = Palette.signal.copy(alpha = 0.6f), size = 26.dp)
                    else -> GlyphIcon(Glyph.LOCK, color = Palette.textDim, size = 22.dp)
                }
            }
            if (completed) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Palette.success),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.CHECK, color = Palette.background, size = 16.dp)
                }
            }
        }
        Text(
            when {
                unlocked -> level.title
                bonus -> "Bonus"
                else -> "· · ·"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = if (unlocked) Palette.text else Palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (level.solutions.isNotEmpty() && unlocked) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (i in level.solutions.indices) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (i < found) Palette.accent else Palette.outline),
                    )
                }
            }
        }
    }
}

/** Colours and features of a world's backdrop. */
private class Scenery(
    val sky: List<Color>,
    val peaksTop: Color,
    val peaksBottom: Color,
    val caps: Color,
    val pool: Color,
    val particles: Color,
)

private fun scenery(world: Int): Scenery = when (world) {
    2 -> Scenery(
        listOf(Color(0xFF232027), Color(0xFF17161B), Color(0xFF121214)),
        Color(0xFF3A3540), Color(0xFF1E1C23), Palette.steam, Palette.metal, Palette.steam,
    )
    3 -> Scenery(
        listOf(Color(0xFF141A2E), Color(0xFF0E1324), Color(0xFF0A0F1A)),
        Color(0xFF26304E), Color(0xFF141B30), Palette.signal, Palette.power, Palette.power,
    )
    4 -> Scenery(
        listOf(Color(0xFF2A1612), Color(0xFF1B0F0D), Color(0xFF140B09)),
        Color(0xFF3E2219), Color(0xFF22120E), Palette.lava, Palette.lava, Palette.fire,
    )
    else -> Scenery(
        listOf(Color(0xFF1A2232), Color(0xFF111821), Color(0xFF0E1814)),
        Color(0xFF2A3850), Color(0xFF172030), Palette.ice, Palette.water, Palette.fire,
    )
}

/** A small, slightly mysterious diorama per world: peaks, a glowing pool, drifting particles and fog. */
private fun DrawScope.drawLandscape(world: Int, time: Float) {
    val w = size.width
    val h = size.height
    val look = scenery(world)
    drawRect(Brush.verticalGradient(look.sky))

    val peaks = Path().apply {
        moveTo(0f, h * 0.24f)
        lineTo(w * 0.14f, h * 0.12f)
        lineTo(w * 0.28f, h * 0.22f)
        lineTo(w * 0.46f, h * 0.07f)
        lineTo(w * 0.66f, h * 0.21f)
        lineTo(w * 0.82f, h * 0.1f)
        lineTo(w, h * 0.2f)
        lineTo(w, h * 0.32f)
        lineTo(0f, h * 0.32f)
        close()
    }
    drawPath(peaks, Brush.verticalGradient(listOf(look.peaksTop, look.peaksBottom), h * 0.06f, h * 0.32f))
    val caps = Path().apply {
        moveTo(w * 0.41f, h * 0.1f)
        lineTo(w * 0.46f, h * 0.07f)
        lineTo(w * 0.51f, h * 0.1f)
        close()
        moveTo(w * 0.78f, h * 0.125f)
        lineTo(w * 0.82f, h * 0.1f)
        lineTo(w * 0.86f, h * 0.125f)
        close()
        moveTo(w * 0.1f, h * 0.145f)
        lineTo(w * 0.14f, h * 0.12f)
        lineTo(w * 0.18f, h * 0.145f)
        close()
    }
    drawPath(caps, look.caps, alpha = 0.75f)
    if (world == 4) {
        // A smoking crater on the highest peak.
        val crater = Offset(w * 0.46f, h * 0.07f)
        drawCircle(
            Brush.radialGradient(listOf(Palette.lava.copy(alpha = 0.6f), Color.Transparent), crater, w * 0.12f),
            w * 0.12f,
            crater,
        )
    }

    // Fog over the unexplored regions.
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), 0f, h * 0.12f))

    // A pool (lake, molten metal, light, lava) with a slow shimmer.
    val poolCenter = Offset(w * 0.8f, h * 0.86f)
    drawOval(
        brush = Brush.radialGradient(listOf(look.pool.copy(alpha = 0.5f), Color.Transparent), poolCenter, w * 0.32f),
        topLeft = Offset(w * 0.5f, h * 0.78f),
        size = Size(w * 0.6f, h * 0.16f),
    )
    for (i in 0 until 3) {
        val x = w * (0.66f + 0.1f * i) + w * 0.02f * sin(time * TAU + i)
        drawLine(Color.White, Offset(x, h * (0.84f + 0.02f * i)), Offset(x + w * 0.05f, h * (0.84f + 0.02f * i)), strokeWidth = 2f, alpha = 0.25f)
    }

    // Drifting particles: embers, steam puffs, sparks, ash.
    for (i in 0 until 9) {
        val phase = (time + i / 9f) % 1f
        val x = w * (0.06f + 0.11f * i) + w * 0.02f * sin(phase * TAU * 2f + i)
        val y = h * (0.98f - 0.5f * phase)
        val r = if (world == 2) w * 0.014f else w * 0.006f
        drawCircle(look.particles, r, Offset(x, y), alpha = (if (world == 2) 0.25f else 0.55f) * (1f - phase))
    }
}

private fun DrawScope.drawRoute(nodes: List<MapNode>, progress: Progress, bonusId: String?) {
    if (nodes.size < 2) return
    val w = size.width
    val h = size.height
    val main = nodes.filter { it.levelId != bonusId }
    for (i in 0 until main.size - 1) {
        segment(main[i], main[i + 1], main[i].levelId in progress.completed, Palette.accent, w, h)
    }
    // The bonus level branches off the last main level, golden and dotted.
    val bonus = nodes.firstOrNull { it.levelId == bonusId }
    val last = main.lastOrNull()
    if (bonus != null && last != null) {
        segment(last, bonus, main.all { it.levelId in progress.completed }, Palette.signal, w, h)
    }
}

private fun DrawScope.segment(from: MapNode, to: MapNode, done: Boolean, color: Color, w: Float, h: Float) {
    val a = Offset(from.x * w, from.y * h)
    val b = Offset(to.x * w, to.y * h)
    val path = Path().apply {
        moveTo(a.x, a.y)
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        cubicTo(mid.x, a.y, mid.x, b.y, b.x, b.y)
    }
    drawPath(
        path,
        color = if (done) color else Palette.textDim,
        alpha = if (done) 0.8f else 0.3f,
        style = Stroke(width = 6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f))),
    )
}
