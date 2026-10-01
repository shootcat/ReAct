package com.shootcat.react.ui.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.model.SolutionKind
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.Completion
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.PillButton
import com.shootcat.react.ui.theme.Palette

/** "Level complete" card: open door, the solution classes found so far, and two actions. */
@Composable
fun CompletionOverlay(
    completion: Completion,
    found: Set<String>,
    types: TypeCatalog,
    onNext: () -> Unit,
    onReplay: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow taps so the board underneath stays untouched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.surface)
                .border(1.dp, Palette.outline, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.35f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                ObjectIcon("DOOR", types, state = "UNLOCKED", size = 64.dp, background = Color.Transparent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Geschafft",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.text,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (spec in completion.level.solutions) {
                    SolutionSlot(
                        kind = spec.kind,
                        label = shortLabel(spec.kind),
                        found = spec.id in found,
                        new = spec.id in completion.newlyFound,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(Glyph.RESET, "Nochmal", onReplay, Modifier.weight(1f))
                PillButton(
                    if (completion.nextLevelId != null) Glyph.NEXT else Glyph.TARGET,
                    if (completion.nextLevelId != null) "Weiter" else "Karte",
                    onNext,
                    Modifier.weight(1f),
                    primary = true,
                )
            }
        }
    }
}

private fun shortLabel(kind: SolutionKind): String = when (kind) {
    SolutionKind.STANDARD -> "Standard"
    SolutionKind.MINIMAL -> "Minimal"
    SolutionKind.OVERRIDE -> "Override"
}

@Composable
private fun SolutionSlot(kind: SolutionKind, label: String, found: Boolean, new: Boolean) {
    val glyph = when (kind) {
        SolutionKind.STANDARD -> Glyph.CHECK
        SolutionKind.MINIMAL -> Glyph.MINIMAL
        SolutionKind.OVERRIDE -> Glyph.BOLT
    }
    val color = if (found) Palette.accent else Palette.outline
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (found) Palette.accent.copy(alpha = 0.15f) else Palette.surfaceHigh)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (found) {
                GlyphIcon(glyph, color = Palette.accent, size = 26.dp)
            } else {
                Text("?", color = Palette.textDim, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            if (new) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Palette.success),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (found) Palette.text else Palette.textDim,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
