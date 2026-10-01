package com.shootcat.react.ui.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.data.Settings
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.SimulationResult
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Phase
import com.shootcat.react.ui.GameEvent
import com.shootcat.react.ui.LevelSession
import com.shootcat.react.ui.Mode
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.PhaseBadge
import com.shootcat.react.ui.components.PillButton
import com.shootcat.react.ui.components.PlayButton
import com.shootcat.react.ui.components.ReactionSymbols
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun LevelScreen(
    session: LevelSession,
    content: GameContent,
    progress: Progress,
    settings: Settings,
    onEvent: (GameEvent) -> Unit,
) {
    val level = session.level
    val sim = session.simulation?.takeIf { session.mode == Mode.SIMULATION }
    val stepMillis = settings.speed.stepMillis

    // Animate each single step forward; jumps (scrubbing, reset) snap.
    val anim = remember { Animatable(1f) }
    var animFrom by remember { mutableStateOf<Int?>(null) }
    var lastFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.frameIndex, sim) {
        val forward = sim != null && session.frameIndex == lastFrame + 1
        lastFrame = session.frameIndex
        if (forward) {
            animFrom = session.frameIndex - 1
            anim.snapTo(0f)
            anim.animateTo(1f, tween((stepMillis * 0.85f).toInt(), easing = FastOutSlowInEasing))
        } else {
            animFrom = null
            anim.snapTo(1f)
        }
    }
    val previous = if (sim != null) animFrom?.let { sim.frames.getOrNull(it)?.state } else null
    val overload = sim != null && sim.outcome == Outcome.OVERLOAD && session.frameIndex == sim.lastIndex
    val shown = session.shownState

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(
            title = level.title,
            overline = "${level.world}·${content.indexOf(level.id)}",
            onBack = { onEvent(GameEvent.OpenMap) },
        ) {
            GoalChip(level, shown, content)
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
            Text(level.intro, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }

        Board(
            state = shown,
            previous = previous,
            progress = anim.value,
            rules = level.rules,
            editable = session.mode == Mode.SETUP,
            overload = overload,
            onMove = { id, to -> onEvent(GameEvent.Move(id, to)) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            showMarkers = settings.markers,
            showPreview = settings.reactionPreview,
            haptics = settings.haptics,
        )

        if (sim == null) {
            SetupDock(session.movedCount, onEvent)
        } else {
            SimulationDock(session, sim, content, settings, onEvent)
        }
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

@Composable
private fun SetupDock(moved: Int, onEvent: (GameEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Glyph.RESET, "Reset", { onEvent(GameEvent.Reset) }, size = 52.dp, enabled = moved > 0, tint = Palette.accent)
        Spacer(Modifier.width(32.dp))
        PlayButton(onClick = { onEvent(GameEvent.Start) })
        Spacer(Modifier.width(32.dp))
        // Keeps the play button centred.
        Spacer(Modifier.size(52.dp))
    }
}

@Composable
private fun SimulationDock(
    session: LevelSession,
    sim: SimulationResult,
    content: GameContent,
    settings: Settings,
    onEvent: (GameEvent) -> Unit,
) {
    val last = sim.lastIndex
    val atEnd = session.frameIndex == last

    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val counter = "${session.frameIndex}/$last"
        if (atEnd && session.finished && sim.outcome != Outcome.SUCCESS) {
            OutcomeChip(sim.outcome, counter)
        } else if (settings.stepDetails) {
            StepSymbols(session, sim, content, counter)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Glyph.TO_START, "Zum Anfang", { onEvent(GameEvent.Seek(0)) }, size = 40.dp, enabled = session.frameIndex > 0, background = Palette.surface)
            Spacer(Modifier.width(6.dp))
            RoundIconButton(Glyph.STEP_BACK, "Schritt zurück", { onEvent(GameEvent.StepBack) }, size = 40.dp, enabled = session.frameIndex > 0, background = Palette.surface)
            Spacer(Modifier.width(10.dp))
            RoundIconButton(
                if (session.playing) Glyph.PAUSE else Glyph.PLAY,
                if (session.playing) "Pause" else "Abspielen",
                { onEvent(GameEvent.TogglePlay) },
                size = 52.dp,
                enabled = last > 0,
                tint = Palette.accent,
            )
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Glyph.STEP_FORWARD, "Schritt vor", { onEvent(GameEvent.StepForward) }, size = 40.dp, enabled = !atEnd, background = Palette.surface)
            Spacer(Modifier.width(6.dp))
            RoundIconButton(Glyph.TO_END, "Zum Ende", { onEvent(GameEvent.Seek(last)) }, size = 40.dp, enabled = !atEnd, background = Palette.surface)
        }
        if (last > 0) {
            Slider(
                value = session.frameIndex.toFloat(),
                onValueChange = { onEvent(GameEvent.Seek(it.roundToInt())) },
                valueRange = 0f..last.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Palette.accent,
                    activeTrackColor = Palette.accent,
                    inactiveTrackColor = Palette.surfaceHigh,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(Glyph.EDIT, "Bearbeiten", { onEvent(GameEvent.Edit) }, Modifier.weight(1f))
            PillButton(Glyph.RESET, "Reset", { onEvent(GameEvent.Reset) }, Modifier.weight(1f))
        }
    }
}

private const val MAX_STEP_SYMBOLS = 2

/** What happened in the shown step, as symbols with their phase number. */
@Composable
private fun StepSymbols(session: LevelSession, sim: SimulationResult, content: GameContent, counter: String) {
    val events = sim.frames[session.frameIndex].events
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.surface)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            session.frameIndex == 0 -> GlyphIcon(Glyph.TARGET, color = Palette.textDim, size = 18.dp)
            events.isEmpty() -> {
                PhaseBadge(Phase.PHYSICS.number)
                GlyphIcon(Glyph.STEP_FORWARD, color = Palette.textDim, size = 16.dp, modifier = Modifier.padding(start = 2.dp))
            }
            else -> {
                for (event in events.take(MAX_STEP_SYMBOLS)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhaseBadge(event.phase.number)
                        ReactionSymbols(
                            Reactions.describeEvent(event, session.level.rules, content.world.types),
                            content.world.types,
                            iconSize = 22.dp,
                        )
                    }
                }
                if (events.size > MAX_STEP_SYMBOLS) {
                    Text("+${events.size - MAX_STEP_SYMBOLS}", color = Palette.textDim, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(counter, style = MaterialTheme.typography.labelMedium, color = Palette.textDim, maxLines = 1)
    }
}

@Composable
private fun OutcomeChip(outcome: Outcome, counter: String) {
    val (glyph, color, word) = when (outcome) {
        Outcome.SUCCESS -> Triple(Glyph.CHECK, Palette.success, "Geschafft")
        Outcome.STABLE -> Triple(Glyph.PAUSE, Palette.textDim, "Stillstand")
        Outcome.OVERLOAD -> Triple(Glyph.BOLT, Palette.danger, "Kurzschluss")
        Outcome.TIMEOUT -> Triple(Glyph.TO_END, Palette.accent, "Zeitlimit")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, color = color, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(word, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(counter, style = MaterialTheme.typography.labelMedium, color = Palette.textDim, maxLines = 1)
    }
}
