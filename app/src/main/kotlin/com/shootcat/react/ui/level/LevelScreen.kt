package com.shootcat.react.ui.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.data.Settings
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.ui.GameEvent
import com.shootcat.react.ui.LevelSession
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette

/**
 * One level in live mode: the board reacts to every move right away. Below it there are only
 * undo and redo, plus a small reset.
 */
@Composable
fun LevelScreen(
    session: LevelSession,
    content: GameContent,
    progress: Progress,
    settings: Settings,
    onEvent: (GameEvent) -> Unit,
) {
    val level = session.level
    val stepMillis = settings.speed.stepMillis

    // Every simulation step glides from the previous world to the new one; jumps snap.
    val anim = remember { Animatable(1f) }
    var animatedTick by remember { mutableIntStateOf(session.tick) }
    LaunchedEffect(session.tick) {
        val glide = session.previous != null
        anim.snapTo(if (glide) 0f else 1f)
        animatedTick = session.tick
        if (glide) anim.animateTo(1f, tween(stepMillis.toInt(), easing = LinearEasing))
    }
    // Until the effect has restarted the animation, a new step starts at its beginning (no flicker).
    val progress = when {
        animatedTick == session.tick -> anim.value
        session.previous != null -> 0f
        else -> 1f
    }
    val overload = session.run.outcome == Outcome.OVERLOAD

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = level.title,
            overline = "${level.world}·${content.indexOf(level.id)}",
            onBack = { onEvent(GameEvent.OpenMap) },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            GoalChip(level, session.state, content)
            RoundIconButton(
                Glyph.LOG,
                "Entdeckungen",
                { onEvent(GameEvent.OpenDiscoveries) },
                size = 40.dp,
                tint = Palette.accent,
                modifier = Modifier.testTag("discoveries"),
            )
            RoundIconButton(Glyph.GEAR, "Einstellungen", { onEvent(GameEvent.OpenSettings) }, size = 40.dp)
        }
        if (settings.levelTexts && level.intro.isNotEmpty()) {
            Text(
                level.intro,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.textDim,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Edge to edge: the board gets the full width of the screen.
        Board(
            state = session.state,
            previous = session.previous,
            progress = progress,
            rules = level.rules,
            types = content.world.types,
            interactive = session.canMove,
            overload = overload,
            onMove = { id, to -> onEvent(GameEvent.Move(id, to)) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp),
            showMarkers = settings.markers,
            showPreview = settings.reactionPreview,
            haptics = settings.haptics,
        )

        HistoryDock(session, overload, onEvent)
    }
}

/** The goal as symbols: target ring + the goal object in its required state; glows when reached. */
@Composable
private fun GoalChip(level: LevelData, shown: GameState, content: GameContent) {
    val reached = level.goals.all { shown.objectById(it.objectId)?.state == it.requiredState }
    val color = if (reached) Palette.success else Palette.accent
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .semantics { contentDescription = "Ziel" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(Glyph.TARGET, color = color, size = 18.dp)
        for (goal in level.goals) {
            val type = level.objects.firstOrNull { it.id == goal.objectId }?.type
            ObjectIcon(type, content.world.types, state = goal.requiredState, size = 26.dp, background = Palette.surface)
        }
    }
}

/** [Rückgängig ←] [Wiederholen →] in the middle, an unobtrusive reset at the side. */
@Composable
private fun HistoryDock(session: LevelSession, overload: Boolean, onEvent: (GameEvent) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                Glyph.UNDO,
                "Rückgängig",
                { onEvent(GameEvent.Undo) },
                size = 60.dp,
                enabled = session.canUndo,
                // After a short circuit, undo is the way out.
                tint = if (overload) Palette.danger else Palette.accent,
            )
            Spacer(Modifier.width(28.dp))
            RoundIconButton(
                Glyph.REDO,
                "Wiederholen",
                { onEvent(GameEvent.Redo) },
                size = 60.dp,
                enabled = session.canRedo,
                tint = Palette.accent,
            )
        }
        RoundIconButton(
            Glyph.RESET,
            "Level neu starten",
            { onEvent(GameEvent.Reset) },
            modifier = Modifier.align(Alignment.CenterEnd),
            size = 40.dp,
            enabled = !session.atStart,
            tint = Palette.textDim,
            background = Palette.surface,
        )
    }
}
