package com.shootcat.react.ui.sandbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.level.Limit
import com.shootcat.react.level.Medal
import com.shootcat.react.level.Play
import com.shootcat.react.level.Result
import com.shootcat.react.level.STEPS_PER_SECOND
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * A level played on the sandbox screen (Leitfaden V3, Phase 3): its rules and goals, and where the player
 * goes when it is over. The screen then offers only the level's tools, shows the goal, the time and the
 * actions, and the medal at the end.
 */
class LevelMode(
    val play: Play,
    val hasNext: Boolean,
    val onSolved: (Result) -> Unit,
    val onRestart: () -> Unit,
    val onNext: () -> Unit,
    val onLevels: () -> Unit,
)

/** Medal colours: gold, silver, bronze, and done without a medal. */
fun medalColor(medal: Medal?): Color = when (medal) {
    Medal.GOLD -> Color(0xFFF2C14E)
    Medal.SILVER -> Color(0xFFC9D1DB)
    Medal.BRONZE -> Color(0xFFCD8B55)
    else -> Palette.textDim
}

/** A round medal in its colour; a hollow ring if there is none yet. */
@Composable
fun MedalBadge(medal: Medal?, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (medal == null || medal == Medal.NONE) {
            drawCircle(Palette.textDim.copy(alpha = 0.6f), r * 0.82f, style = Stroke(width = r * 0.16f))
            if (medal == Medal.NONE) drawCircle(Palette.success, r * 0.35f)
            return@Canvas
        }
        val c = medalColor(medal)
        drawCircle(c, r)
        drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.72f, style = Stroke(width = r * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, Offset(r * 0.72f, r * 0.7f))
    }
}

/** Minutes and seconds of world time, 1:05. */
fun clock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * What the level asks and how it is going: each goal with its progress, the time of the world, the actions
 * taken, and the three medals with their limits; a medal whose limit is already passed is dimmed.
 */
@Composable
fun LevelHud(play: Play, stats: SimRunner.Stats, modifier: Modifier = Modifier) {
    var progress by remember(play) { mutableStateOf(play.progress) }
    var actions by remember(play) { mutableIntStateOf(play.actions) }
    LaunchedEffect(play) {
        while (true) {
            progress = play.progress
            actions = play.actions
            delay(120)
        }
    }
    val seconds = stats.step / STEPS_PER_SECOND.toFloat()
    val rating = play.level.spec.rating
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("level_hud"),
    ) {
        for ((k, goal) in play.goals.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.TARGET, color = Palette.accent, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(goal.text, color = Palette.text, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            val p = progress.getOrElse(k) { 0f }
            Canvas(Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp).testTag("goal_progress")) {
                val h = size.height
                drawRoundRect(Palette.surfaceHigh, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                if (p > 0f) {
                    drawRoundRect(
                        if (p >= 1f) Palette.success else Palette.accent,
                        size = androidx.compose.ui.geometry.Size(size.width * p.coerceIn(0f, 1f), h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(clock(seconds), color = Palette.text, fontSize = 12.sp, modifier = Modifier.testTag("level_time"))
            Text(if (actions == 1) "1 Griff" else "$actions Griffe", color = Palette.text, fontSize = 12.sp, modifier = Modifier.testTag("level_actions"))
            Spacer(Modifier.weight(1f))
            for ((limit, medal) in listOf(rating.gold to Medal.GOLD, rating.silver to Medal.SILVER, rating.bronze to Medal.BRONZE)) {
                LimitChip(medal, limit, seconds <= limit.seconds && actions <= limit.actions)
            }
        }
    }
}

/** A medal and its limit, "1:30 · 8": time and actions both have to stay within it. */
@Composable
private fun LimitChip(medal: Medal, limit: Limit, reachable: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("limit_${medal.name}"),
    ) {
        MedalBadge(if (reachable) medal else null, size = 11.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            "${clock(limit.seconds.toFloat())} · ${limit.actions}",
            color = if (reachable) Palette.text else Palette.textDim.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
    }
}

/** The outline of each goal area on the world, dashed: where, never how. */
fun DrawScope.drawGoalAreas(areas: List<List<List<Int>>>, o: Offset, s: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
    for (area in areas) {
        if (area.size < 3) continue
        val path = Path()
        for ((k, p) in area.withIndex()) {
            val x = o.x + p[0] * s
            val y = o.y + p[1] * s
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Palette.signal.copy(alpha = 0.75f), style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
    }
}

/**
 * The end of a level: solved, with the medal, the time and the actions; or lost, when something the level
 * guards was not kept. Again from the start, on to the next level, or stay and look.
 */
@Composable
fun LevelEndDialog(
    result: Result?,
    lostGoal: String?,
    hasNext: Boolean,
    onLook: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
    onLevels: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLook,
        containerColor = Palette.surface,
        modifier = Modifier.testTag("level_end"),
        title = {
            Text(if (result != null) "Geschafft" else "Verloren", color = Palette.text, fontWeight = FontWeight.Bold)
        },
        text = {
            if (result != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    MedalBadge(result.medal, size = 64.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (result.medal == Medal.NONE) "ohne Medaille" else result.medal.label,
                        color = medalColor(result.medal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("medal"),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Zeit ${clock(result.seconds)} · ${result.actions} ${if (result.actions == 1) "Griff" else "Griffe"}",
                        color = Palette.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text("Nicht gehalten: ${lostGoal ?: "ein Ziel"}", color = Palette.textDim)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onLook) { Text("Ansehen") }
                TextButton(onClick = onRestart, modifier = Modifier.testTag("level_again")) { Text("Nochmal") }
                if (result != null && hasNext) {
                    TextButton(onClick = onNext, modifier = Modifier.testTag("level_next")) { Text("Nächstes") }
                } else {
                    TextButton(onClick = onLevels, modifier = Modifier.testTag("level_list")) { Text("Auswahl") }
                }
            }
        },
    )
}
