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
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.Completion
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.PillButton
import com.shootcat.react.ui.theme.Palette

/** "Aufgabe erfüllt": every task with its tick, the optional ones with their stars, and two actions. */
@Composable
fun CompletionOverlay(
    completion: Completion,
    types: TypeCatalog,
    onNext: () -> Unit,
    onReplay: () -> Unit,
) {
    val level = completion.level
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Swallow taps so the board underneath stays untouched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.surface)
                .border(1.dp, Palette.outline, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Palette.success.copy(alpha = 0.4f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Palette.success),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.CHECK, color = Palette.background, size = 30.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Aufgabe erfüllt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.text,
                maxLines = 1,
            )
            Text(
                level.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textDim,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((i, goal) in level.goals.withIndex().sortedBy { it.value.optional }) {
                    TaskResult(
                        goal = goal,
                        level = level,
                        met = !goal.optional || i in completion.achieved,
                        new = i in completion.newlyAchieved,
                        types = types,
                    )
                }
            }
            if (level.optionalGoals.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val stars = completion.everAchieved.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (k in level.optionalGoals.indices) {
                        GlyphIcon(
                            if (k < stars) Glyph.STAR else Glyph.STAR_OUTLINE,
                            color = if (k < stars) Palette.accent else Palette.outline,
                            size = 22.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (completion.moves == 1) "1 Zug" else "${completion.moves} Züge",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.textDim,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(Glyph.RESET, "Nochmal", onReplay, Modifier.weight(1f))
                PillButton(
                    if (completion.nextLevelId != null) Glyph.NEXT else Glyph.MAP,
                    if (completion.nextLevelId != null) "Weiter" else "Karte",
                    onNext,
                    Modifier.weight(1f),
                    primary = true,
                )
            }
        }
    }
}
