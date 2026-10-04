package com.shootcat.react.ui.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.LevelGoal
import com.shootcat.react.engine.model.TargetCleared
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetExtinguished
import com.shootcat.react.engine.model.TargetMaxMoves
import com.shootcat.react.engine.model.TargetPreserved
import com.shootcat.react.engine.model.TargetRainTriggered
import com.shootcat.react.engine.model.TargetState
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.theme.Palette

/** The element a task is about, in the state it asks for (a sprouted seed, a raining cloud, …). */
private data class TaskIcon(val typeId: String?, val state: String? = null, val glyph: Glyph? = null)

private fun iconOf(goal: LevelGoal, level: LevelData): TaskIcon {
    fun typeOf(id: String) = level.objects.firstOrNull { it.id == id }?.type
    return when (goal) {
        is TargetContainerFilled -> TaskIcon(goal.liquid)
        is TargetExtinguished -> TaskIcon("FIRE")
        is TargetRainTriggered -> TaskIcon("CLOUD", "RAINING")
        is TargetState -> TaskIcon(typeOf(goal.objectId), goal.state)
        is TargetPreserved -> TaskIcon(typeOf(goal.objectId), goal.state)
        is TargetCleared -> TaskIcon(goal.types.sorted().firstOrNull())
        is TargetMaxMoves -> TaskIcon(null, glyph = Glyph.HAND)
    }
}

/** How far a task has come, where that can be counted: "60 %" of a pond, "2/4" moves. */
private fun progressOf(goal: LevelGoal, state: GameState, moves: Int): String? = when (goal) {
    is TargetContainerFilled -> "${(goal.amount(state) * 100 / goal.min.coerceAtLeast(1)).coerceAtMost(100)} %"
    is TargetMaxMoves -> "$moves/${goal.moves}"
    else -> null
}

@Composable
private fun TaskSymbol(icon: TaskIcon, types: TypeCatalog, size: Dp) {
    if (icon.glyph != null) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(Palette.surface),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(icon.glyph, color = Palette.accent, size = size * 0.7f)
        }
    } else {
        ObjectIcon(icon.typeId, types, state = icon.state, size = size, background = Palette.surface)
    }
}

/**
 * The level's tasks, live: main tasks get a tick once they hold, optional ones (marked with a star)
 * light up their star.
 */
@Composable
fun TaskPanel(
    level: LevelData,
    state: GameState,
    moves: Int,
    met: List<Boolean>,
    types: TypeCatalog,
    modifier: Modifier = Modifier,
) {
    val order = level.goals.indices.sortedBy { level.goals[it].optional }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surfaceHigh.copy(alpha = 0.92f))
            .semantics { contentDescription = "Aufgaben" }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in order) {
            val goal = level.goals[i]
            TaskRow(
                goal = goal,
                icon = iconOf(goal, level),
                met = met.getOrElse(i) { false },
                progress = progressOf(goal, state, moves),
                types = types,
                modifier = Modifier.testTag("task_$i"),
            )
        }
    }
}

@Composable
private fun TaskRow(
    goal: LevelGoal,
    icon: TaskIcon,
    met: Boolean,
    progress: String?,
    types: TypeCatalog,
    modifier: Modifier = Modifier,
) {
    Row(modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskSymbol(icon, types, 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            goal.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (goal.optional) Palette.textDim else Palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (progress != null && !met) {
            Text(
                progress,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.accent,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        StatusMark(goal.optional, met)
    }
}

/** A tick in a circle for main tasks, a star for optional ones; filled once achieved. */
@Composable
private fun StatusMark(optional: Boolean, met: Boolean) {
    val description = if (met) "erfüllt" else "offen"
    Box(
        Modifier.size(24.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (optional) {
            GlyphIcon(if (met) Glyph.STAR else Glyph.STAR_OUTLINE, color = if (met) Palette.accent else Palette.textDim, size = 20.dp)
        } else {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (met) Palette.success else Color.Transparent)
                    .border(2.dp, if (met) Palette.success else Palette.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (met) GlyphIcon(Glyph.CHECK, color = Palette.background, size = 14.dp)
            }
        }
    }
}

/** One task on the completion card. */
@Composable
internal fun TaskResult(goal: LevelGoal, level: LevelData, met: Boolean, new: Boolean, types: TypeCatalog) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskSymbol(iconOf(goal, level), types, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            goal.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (met) Palette.text else Palette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (new) {
            Text(
                "NEU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.success,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        StatusMark(goal.optional, met)
    }
}
