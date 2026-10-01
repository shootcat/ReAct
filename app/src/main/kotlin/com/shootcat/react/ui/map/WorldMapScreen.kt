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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.BuildConfig
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.MapNode
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin

private val NodeSize = 60.dp
private val NodeLabelWidth = 128.dp

@Composable
fun WorldMapScreen(
    content: GameContent,
    progress: Progress,
    isUnlocked: (String) -> Boolean,
    onOpenLevel: (String) -> Unit,
    onOpenLog: () -> Unit,
) {
    val world = content.world
    val transition = rememberInfiniteTransition(label = "map")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val nodes = world.map.filter { content.level(it.levelId) != null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(
            "REACT",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 10.sp,
            color = Palette.accent,
        )
        Text("Welt ${world.world} · ${world.title}", style = MaterialTheme.typography.titleMedium, color = Palette.text)

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 14.dp)
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
                    level = level,
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

        Text(
            "„Du lernst nicht die Lösungen. Du lernst die Welt.“",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = Palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
            GlyphIcon(Glyph.LOG, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Entdeckungen ${progress.discoveries.size}/${content.allRules.size}", fontWeight = FontWeight.Bold)
        }
        Text(
            "Beta ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun MapNodeView(
    index: Int,
    level: LevelData,
    unlocked: Boolean,
    completed: Boolean,
    found: Int,
    pulse: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = when {
        completed -> Palette.success
        unlocked -> Palette.accent.copy(alpha = 0.6f + 0.4f * sin(pulse * 2f * PI.toFloat() * 2f))
        else -> Palette.outline
    }
    Column(modifier.width(NodeLabelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(NodeSize)
                .clip(CircleShape)
                .background(if (unlocked) Palette.surfaceHigh else Palette.surface)
                .border(3.dp, ring, CircleShape)
                .clickable(enabled = unlocked, onClick = onClick)
                .testTag("level_${level.id}"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !unlocked -> GlyphIcon(Glyph.LOCK, color = Palette.textDim, size = 22.dp)
                completed -> GlyphIcon(Glyph.CHECK, color = Palette.success, size = 26.dp)
                else -> Text("$index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.text)
            }
        }
        Text(
            if (unlocked) level.title else "???",
            style = MaterialTheme.typography.labelLarge,
            color = if (unlocked) Palette.text else Palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (level.solutions.isNotEmpty() && unlocked) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
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

/** A small, slightly mysterious diorama: ice peaks, a lake and drifting embers. */
private fun DrawScope.drawLandscape(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2433), Color(0xFF111821), Color(0xFF0F1A16))))

    // Unknown regions further up, hidden in fog.
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), Offset(w * 0.6f, 0f), w * 0.7f),
        radius = w * 0.7f,
        center = Offset(w * 0.6f, 0f),
    )

    val peaks = Path().apply {
        moveTo(0f, h * 0.36f)
        lineTo(w * 0.14f, h * 0.2f)
        lineTo(w * 0.28f, h * 0.34f)
        lineTo(w * 0.46f, h * 0.13f)
        lineTo(w * 0.66f, h * 0.33f)
        lineTo(w * 0.82f, h * 0.18f)
        lineTo(w, h * 0.32f)
        lineTo(w, h * 0.42f)
        lineTo(0f, h * 0.42f)
        close()
    }
    drawPath(peaks, Color(0xFF243044))
    val caps = Path().apply {
        moveTo(w * 0.41f, h * 0.18f)
        lineTo(w * 0.46f, h * 0.13f)
        lineTo(w * 0.51f, h * 0.18f)
        close()
        moveTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.82f, h * 0.18f)
        lineTo(w * 0.86f, h * 0.22f)
        close()
    }
    drawPath(caps, Palette.ice, alpha = 0.7f)

    // Lake.
    drawOval(
        brush = Brush.radialGradient(
            listOf(Palette.water.copy(alpha = 0.5f), Color.Transparent),
            Offset(w * 0.78f, h * 0.84f),
            w * 0.3f,
        ),
        topLeft = Offset(w * 0.52f, h * 0.76f),
        size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.16f),
    )

    // Drifting embers.
    for (i in 0 until 7) {
        val phase = (time + i / 7f) % 1f
        val x = w * (0.08f + 0.13f * i) + w * 0.02f * sin(phase * 2f * PI.toFloat() * 2f + i)
        val y = h * (0.95f - 0.5f * phase)
        drawCircle(Palette.fire, w * 0.006f, Offset(x, y), alpha = 0.5f * (1f - phase))
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
            alpha = if (done) 0.8f else 0.35f,
            style = Stroke(
                width = 6f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f)),
            ),
        )
    }
    // A last, faint path leading off the map: more is coming.
    val lastNode = nodes.last()
    if (content.levels.lastOrNull()?.id == lastNode.levelId) {
        val a = Offset(lastNode.x * w, lastNode.y * h)
        drawLine(
            Palette.textDim,
            a,
            Offset(a.x + w * 0.25f, a.y - h * 0.2f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 18f)),
            alpha = 0.2f,
        )
    }
}
