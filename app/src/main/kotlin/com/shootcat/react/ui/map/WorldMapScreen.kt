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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
private const val TAU = (2 * PI).toFloat()

@Composable
fun WorldMapScreen(
    content: GameContent,
    progress: Progress,
    isUnlocked: (String) -> Boolean,
    onOpenLevel: (String) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val world = content.world
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
            Canvas(Modifier.fillMaxSize()) {
                if (size.minDimension <= 0f) return@Canvas
                drawLandscape(time)
                drawRoute(nodes, progress, content)
            }
            for ((i, node) in nodes.withIndex()) {
                val level = content.level(node.levelId) ?: continue
                MapNodeView(
                    index = i,
                    node = node,
                    level = level,
                    types = world.types,
                    unlocked = isUnlocked(level.id),
                    completed = level.id in progress.completed,
                    found = progress.solutionsFor(level.id).size,
                    pulse = time,
                    onClick = { onOpenLevel(level.id) },
                    modifier = Modifier.offset(
                        x = maxWidth * node.x - NodeLabelWidth / 2,
                        y = maxHeight * node.y - NodeSize / 2,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MapNodeView(
    index: Int,
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
        unlocked -> Palette.accent.copy(alpha = 0.55f + 0.45f * glow)
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
                    .semantics { contentDescription = "Level $index ${level.title}" },
                contentAlignment = Alignment.Center,
            ) {
                if (unlocked) {
                    ObjectIcon(node.icon, types, size = 42.dp, background = Color.Transparent)
                } else {
                    GlyphIcon(Glyph.LOCK, color = Palette.textDim, size = 22.dp)
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
            if (unlocked) level.title else "· · ·",
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

/** A small, slightly mysterious diorama: ice peaks, a lake, drifting embers and fog above. */
private fun DrawScope.drawLandscape(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2232), Color(0xFF111821), Color(0xFF0E1814))))

    val peaks = Path().apply {
        moveTo(0f, h * 0.36f)
        lineTo(w * 0.14f, h * 0.2f)
        lineTo(w * 0.28f, h * 0.34f)
        lineTo(w * 0.46f, h * 0.13f)
        lineTo(w * 0.66f, h * 0.33f)
        lineTo(w * 0.82f, h * 0.18f)
        lineTo(w, h * 0.32f)
        lineTo(w, h * 0.46f)
        lineTo(0f, h * 0.46f)
        close()
    }
    drawPath(peaks, Brush.verticalGradient(listOf(Color(0xFF2A3850), Color(0xFF172030)), h * 0.12f, h * 0.46f))
    val caps = Path().apply {
        moveTo(w * 0.41f, h * 0.18f)
        lineTo(w * 0.46f, h * 0.13f)
        lineTo(w * 0.51f, h * 0.18f)
        close()
        moveTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.82f, h * 0.18f)
        lineTo(w * 0.86f, h * 0.22f)
        close()
        moveTo(w * 0.1f, h * 0.24f)
        lineTo(w * 0.14f, h * 0.2f)
        lineTo(w * 0.18f, h * 0.24f)
        close()
    }
    drawPath(caps, Palette.ice, alpha = 0.75f)

    // Fog over the unexplored regions.
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), 0f, h * 0.25f))

    // Lake with a slow shimmer.
    val lakeCenter = Offset(w * 0.8f, h * 0.86f)
    drawOval(
        brush = Brush.radialGradient(listOf(Palette.water.copy(alpha = 0.55f), Color.Transparent), lakeCenter, w * 0.32f),
        topLeft = Offset(w * 0.5f, h * 0.78f),
        size = Size(w * 0.6f, h * 0.16f),
    )
    for (i in 0 until 3) {
        val x = w * (0.66f + 0.1f * i) + w * 0.02f * sin(time * TAU + i)
        drawLine(Color.White, Offset(x, h * (0.84f + 0.02f * i)), Offset(x + w * 0.05f, h * (0.84f + 0.02f * i)), strokeWidth = 2f, alpha = 0.25f)
    }

    // Drifting embers.
    for (i in 0 until 9) {
        val phase = (time + i / 9f) % 1f
        val x = w * (0.06f + 0.11f * i) + w * 0.02f * sin(phase * TAU * 2f + i)
        val y = h * (0.98f - 0.5f * phase)
        drawCircle(Palette.fire, w * 0.006f, Offset(x, y), alpha = 0.55f * (1f - phase))
    }
}

private fun DrawScope.drawRoute(nodes: List<MapNode>, progress: Progress, content: GameContent) {
    if (nodes.size < 2) return
    val w = size.width
    val h = size.height
    for (i in 0 until nodes.size - 1) {
        val a = Offset(nodes[i].x * w, nodes[i].y * h)
        val b = Offset(nodes[i + 1].x * w, nodes[i + 1].y * h)
        val done = nodes[i].levelId in progress.completed
        val path = Path().apply {
            moveTo(a.x, a.y)
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            cubicTo(mid.x, a.y, mid.x, b.y, b.x, b.y)
        }
        drawPath(
            path,
            color = if (done) Palette.accent else Palette.textDim,
            alpha = if (done) 0.8f else 0.3f,
            style = Stroke(width = 6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f))),
        )
    }
    // A faint path leading off the map: more is coming.
    val lastNode = nodes.last()
    if (content.levels.lastOrNull()?.id == lastNode.levelId) {
        val a = Offset(lastNode.x * w, lastNode.y * h)
        drawLine(
            Palette.textDim,
            a,
            Offset(a.x + w * 0.25f, a.y - h * 0.22f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 18f)),
            alpha = 0.2f,
        )
    }
}
