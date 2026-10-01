package com.shootcat.react.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.ui.Completion
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.theme.Palette

/** "LEVEL COMPLETE" with the solution classes found so far (Standard / Minimal / System-Override). */
@Composable
fun CompletionDialog(
    completion: Completion,
    found: Set<String>,
    onNext: () -> Unit,
    onStay: () -> Unit,
) {
    val solutions = completion.level.solutions
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = Palette.surface,
        title = { Text("Level geschafft!", fontWeight = FontWeight.Bold, color = Palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(completion.level.title, style = MaterialTheme.typography.bodyMedium, color = Palette.textDim)
                for (spec in solutions) {
                    val ok = spec.id in found
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            spec.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ok) Palette.text else Palette.textDim,
                        )
                        if (spec.id in completion.newlyFound) {
                            Text(
                                "NEU",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        if (ok) {
                            GlyphIcon(Glyph.CHECK, color = Palette.success)
                        } else {
                            Text("?", color = Palette.textDim, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val count = solutions.count { it.id in found }
                Text(
                    if (count < solutions.size) {
                        "$count von ${solutions.size} Lösungswegen gefunden. Es gibt noch andere Wege …"
                    } else {
                        "Alle ${solutions.size} Lösungswege gefunden."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.textDim,
                )
            }
        },
        confirmButton = {
            Button(onClick = onNext) {
                Text(if (completion.nextLevelId != null) "Nächstes Level" else "Zur Weltkarte")
            }
        },
        dismissButton = {
            TextButton(onClick = onStay) { Text("Weiter experimentieren") }
        },
    )
}
