package com.shootcat.react.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.Progress
import com.shootcat.react.engine.Reaction
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.model.ObjectType
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.ObjectIcon
import com.shootcat.react.ui.components.ScreenHeader
import com.shootcat.react.ui.theme.Palette

/**
 * The Discovery Log as a reaction matrix: known reactions in full, unknown ones as silhouettes.
 * It explains WHAT happens – never how a level is solved.
 */
@Composable
fun DiscoveryScreen(
    content: GameContent,
    progress: Progress,
    isUnlocked: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val types = content.world.types
    val reactions = remember(content) { content.allRules.map { Reactions.describe(it, types) } }
    val found = reactions.count { it.ruleId in progress.discoveries }
    val materials = remember(content, progress) { knownMaterials(content, progress, reactions, isUnlocked) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(overline = "Welt ${content.world.world} · ${content.world.title}", title = "Entdeckungen", onBack = onBack) {
            Text("$found/${reactions.size}", style = MaterialTheme.typography.titleMedium, color = Palette.accent)
            Spacer(Modifier.width(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Das Log hält fest, WAS passiert ist – nicht, WIE man ein Level löst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.textDim,
                )
            }
            item { SectionTitle("Reaktions-Matrix") }
            items(reactions, key = { it.ruleId }) { reaction ->
                ReactionCard(reaction, reaction.ruleId in progress.discoveries, types)
            }
            if (materials.isNotEmpty()) {
                item { SectionTitle("Materialien") }
                items(materials, key = { it.id }) { type -> MaterialRow(type, types) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun knownMaterials(
    content: GameContent,
    progress: Progress,
    reactions: List<Reaction>,
    isUnlocked: (String) -> Boolean,
): List<ObjectType> {
    val seen = content.levels.filter { isUnlocked(it.id) }.flatMap { level -> level.objects.map { it.type } }.toMutableSet()
    reactions.filter { it.ruleId in progress.discoveries }.forEach { r -> r.output.typeId?.let { seen += it } }
    return content.world.types.all.filter { it.id in seen }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Palette.textDim,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReactionCard(reaction: Reaction, discovered: Boolean, types: TypeCatalog) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (discovered) Palette.surfaceHigh else Palette.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            reaction.inputs.forEachIndexed { i, token ->
                if (i > 0) Operator("+")
                ObjectIcon(token.typeId, types, state = token.state, silhouette = !discovered)
            }
            Operator("→")
            ObjectIcon(reaction.output.typeId, types, state = reaction.output.state, silhouette = !discovered)
            Spacer(Modifier.weight(1f))
            Text(
                if (discovered) "Phase ${reaction.phase.number}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (discovered) {
            Text(reaction.text, style = MaterialTheme.typography.titleSmall, color = Palette.text, fontWeight = FontWeight.SemiBold)
            Text(reaction.name, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        } else {
            Text(
                reaction.inputs.joinToString(" + ") { "???" } + " → ???",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.textDim,
            )
            Text("Noch nicht beobachtet", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
    }
}

@Composable
private fun Operator(symbol: String) {
    Text(
        symbol,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.textDim,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun MaterialRow(type: ObjectType, types: TypeCatalog) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ObjectIcon(type.id, types, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(type.name, style = MaterialTheme.typography.titleSmall, color = Palette.text, fontWeight = FontWeight.SemiBold)
            if (type.description.isNotEmpty()) {
                Text(type.description, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
            }
        }
    }
}
