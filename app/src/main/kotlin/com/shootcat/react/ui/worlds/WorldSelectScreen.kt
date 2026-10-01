package com.shootcat.react.ui.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.model.WorldData
import com.shootcat.react.ui.components.DiscoveryBadge
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette

/** The four worlds as cards: element icon, progress through the main levels and the bonus star. */
@Composable
fun WorldSelectScreen(
    content: GameContent,
    progress: Progress,
    isWorldUnlocked: (Int) -> Boolean,
    solvedIn: (Int) -> Int,
    onOpenWorld: (Int) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(title = "Welten", onBack = onBack) {
            DiscoveryBadge(progress.discoveries.size, content.allRules.size, onOpenLog)
            RoundIconButton(Glyph.GEAR, "Einstellungen", onOpenSettings, size = 40.dp)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for (world in content.worlds) {
                WorldCard(
                    world = world,
                    content = content,
                    unlocked = isWorldUnlocked(world.world),
                    solved = solvedIn(world.world),
                    bonusSolved = world.bonusLevelId in progress.completed,
                    onClick = { onOpenWorld(world.world) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun accentOf(world: Int): Color = when (world) {
    2 -> Palette.steam
    3 -> Palette.power
    4 -> Palette.lava
    else -> Palette.ice
}

@Composable
private fun WorldCard(
    world: WorldData,
    content: GameContent,
    unlocked: Boolean,
    solved: Int,
    bonusSolved: Boolean,
    onClick: () -> Unit,
) {
    val accent = accentOf(world.world)
    val total = world.levelIds.size
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (unlocked) 0.18f else 0.05f), Palette.surface)))
            .border(1.dp, if (unlocked) accent.copy(alpha = 0.5f) else Palette.outline, RoundedCornerShape(24.dp))
            .clickable(enabled = unlocked, onClick = onClick)
            .testTag("world_${world.world}")
            .semantics { contentDescription = "Welt ${world.world} ${world.title}" }
            .padding(16.dp)
            .alpha(if (unlocked) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Palette.background).border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked) {
                ObjectIcon(world.icon, content.types, size = 48.dp, background = Color.Transparent)
            } else {
                GlyphIcon(Glyph.LOCK, color = Palette.textDim, size = 26.dp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "WELT ${world.world}",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = Palette.textDim,
            )
            Text(world.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.text)
            Spacer(Modifier.height(8.dp))
            // Progress through the main levels.
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.outline)) {
                Box(
                    Modifier
                        .fillMaxWidth(if (total == 0) 0f else solved / total.toFloat())
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$solved/$total", style = MaterialTheme.typography.labelLarge, color = Palette.text)
            if (world.bonusLevelId != null) {
                GlyphIcon(Glyph.SPARK, color = if (bonusSolved) Palette.signal else Palette.outline, size = 20.dp)
            }
        }
    }
}
