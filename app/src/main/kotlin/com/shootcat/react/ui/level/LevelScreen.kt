package com.shootcat.react.ui.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.SimulationResult
import com.shootcat.react.engine.model.Phase
import com.shootcat.react.ui.GameEvent
import com.shootcat.react.ui.GameViewModel
import com.shootcat.react.ui.LevelSession
import com.shootcat.react.ui.Mode
import com.shootcat.react.ui.components.DiscoveryBadge
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ScreenHeader
import com.shootcat.react.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun LevelScreen(
    session: LevelSession,
    content: GameContent,
    progress: Progress,
    onEvent: (GameEvent) -> Unit,
) {
    val level = session.level
    val types = content.world.types
    val sim = session.simulation?.takeIf { session.mode == Mode.SIMULATION }

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
            anim.animateTo(1f, tween((GameViewModel.STEP_MILLIS * 0.85f).toInt(), easing = FastOutSlowInEasing))
        } else {
            animFrom = null
            anim.snapTo(1f)
        }
    }
    val previous = if (sim != null) animFrom?.let { sim.frames.getOrNull(it)?.state } else null
    val overload = sim != null && sim.outcome == Outcome.OVERLOAD && session.frameIndex == sim.lastIndex

    val index = content.indexOf(level.id)
    val goal = level.goals.joinToString(", ") { g ->
        val type = level.objects.firstOrNull { it.id == g.objectId }?.type
        val name = type?.let { types.name(it) } ?: g.objectId
        val state = type?.let { types[it]?.stateName(g.requiredState) } ?: g.requiredState
        "$name $state"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            overline = "Welt ${level.world} · Level $index",
            title = level.title,
            onBack = { onEvent(GameEvent.OpenMap) },
        ) {
            DiscoveryBadge(progress.discoveries.size, content.allRules.size) { onEvent(GameEvent.OpenDiscoveries) }
        }
        Text(level.intro, style = MaterialTheme.typography.bodyMedium, color = Palette.textDim)
        Spacer(Modifier.height(4.dp))
        Text("Ziel: $goal", style = MaterialTheme.typography.labelLarge, color = Palette.accent)

        Board(
            state = session.shownState,
            previous = previous,
            progress = anim.value,
            rules = level.rules,
            editable = session.mode == Mode.SETUP,
            overload = overload,
            onMove = { id, to -> onEvent(GameEvent.Move(id, to)) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
        )

        if (sim == null) {
            SetupControls(session.movedCount, onEvent)
        } else {
            SimulationControls(session, sim, content, onEvent)
        }
    }
}

@Composable
private fun SetupControls(moved: Int, onEvent: (GameEvent) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (moved == 0) {
                "Ziehe die markierten Objekte – oder tippe eins an und dann ein freies Feld."
            } else {
                "Verschoben: $moved ${if (moved == 1) "Objekt" else "Objekte"}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.textDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onEvent(GameEvent.Reset) }, enabled = moved > 0) {
                GlyphIcon(Glyph.RESET, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
            Button(onClick = { onEvent(GameEvent.Start) }, modifier = Modifier.weight(1f)) {
                GlyphIcon(Glyph.PLAY, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Start", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SimulationControls(
    session: LevelSession,
    sim: SimulationResult,
    content: GameContent,
    onEvent: (GameEvent) -> Unit,
) {
    val last = sim.lastIndex
    val frame = sim.frames[session.frameIndex]
    val atEnd = session.frameIndex == last

    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StepLog(session.frameIndex, frame.events.map { event ->
            "Phase ${event.phase.number} · ${event.phase.label}: " +
                Reactions.describeEvent(event, session.level.rules, content.world.types)
        })

        if (atEnd && session.finished) OutcomeBanner(sim)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Schritt ${session.frameIndex} / $last",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEvent(GameEvent.Seek(0)) }, enabled = session.frameIndex > 0) {
                GlyphIcon(Glyph.TO_START, color = Palette.text)
            }
            IconButton(onClick = { onEvent(GameEvent.StepBack) }, enabled = session.frameIndex > 0) {
                GlyphIcon(Glyph.STEP_BACK, color = Palette.text)
            }
            IconButton(onClick = { onEvent(GameEvent.TogglePlay) }, enabled = last > 0) {
                GlyphIcon(if (session.playing) Glyph.PAUSE else Glyph.PLAY, color = Palette.accent, size = 24.dp)
            }
            IconButton(onClick = { onEvent(GameEvent.StepForward) }, enabled = !atEnd) {
                GlyphIcon(Glyph.STEP_FORWARD, color = Palette.text)
            }
            IconButton(onClick = { onEvent(GameEvent.Seek(last)) }, enabled = !atEnd) {
                GlyphIcon(Glyph.TO_END, color = Palette.text)
            }
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
            OutlinedButton(onClick = { onEvent(GameEvent.Edit) }, modifier = Modifier.weight(1f)) {
                GlyphIcon(Glyph.EDIT, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Bearbeiten")
            }
            OutlinedButton(onClick = { onEvent(GameEvent.Reset) }, modifier = Modifier.weight(1f)) {
                GlyphIcon(Glyph.RESET, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}

/** What happened in the shown step – the player's debugging aid. */
@Composable
private fun StepLog(frameIndex: Int, lines: List<String>) {
    val shown = when {
        frameIndex == 0 -> listOf("Startaufstellung")
        lines.isEmpty() -> listOf("Phase ${Phase.PHYSICS.number} · ${Phase.PHYSICS.label}: Dinge fallen und fließen")
        lines.size > 3 -> lines.take(3) + "… und ${lines.size - 3} weitere Reaktionen"
        else -> lines
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        for (line in shown) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = Palette.text)
        }
    }
}

@Composable
private fun OutcomeBanner(sim: SimulationResult) {
    val (color, text) = when (sim.outcome) {
        Outcome.SUCCESS -> Palette.success to "Ziel erreicht!"
        Outcome.STABLE -> Palette.textDim to
            "Stillstand: Nichts verändert sich mehr. Bearbeite den Aufbau und versuch etwas anderes."
        Outcome.OVERLOAD -> Palette.danger to
            "Kurzschluss! Mehr als 100 Reaktionen in einem Schritt – die Kettenreaktion wurde abgebrochen."
        Outcome.TIMEOUT -> Palette.accent to
            "Zeitlimit: Nach ${sim.lastIndex} Schritten ist noch nichts entschieden."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
