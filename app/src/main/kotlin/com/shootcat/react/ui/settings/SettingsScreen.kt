package com.shootcat.react.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.BuildConfig
import com.shootcat.react.data.Settings
import com.shootcat.react.data.Speed
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(title = "Einstellungen", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Simulation")
            Card {
                Text("Tempo", style = MaterialTheme.typography.titleSmall, color = Palette.text)
                Spacer(Modifier.height(10.dp))
                SpeedSelector(settings.speed) { onChange(settings.copy(speed = it)) }
            }

            Section("Hilfen")
            Card {
                ToggleRow("Reaktions-Vorschau", settings.reactionPreview) { onChange(settings.copy(reactionPreview = it)) }
                ToggleRow("Markierungen", settings.markers) { onChange(settings.copy(markers = it)) }
                ToggleRow("Level-Texte", settings.levelTexts) { onChange(settings.copy(levelTexts = it)) }
            }

            Section("Gerät")
            Card {
                ToggleRow("Vibration", settings.haptics) { onChange(settings.copy(haptics = it)) }
            }

            Section("Spielstand")
            Card {
                Text(
                    "Fortschritt zurücksetzen",
                    color = Palette.danger,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmReset = true }
                        .padding(vertical = 10.dp),
                )
            }

            Text(
                "REACT ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Palette.surface,
            title = { Text("Alles zurücksetzen?", color = Palette.text) },
            text = { Text("Level, Lösungen und Entdeckungen gehen verloren.", color = Palette.textDim) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetProgress()
                }) { Text("Zurücksetzen", color = Palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 2.sp,
        color = Palette.textDim,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp)
            .testTag("setting_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.background,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textDim,
                uncheckedTrackColor = Palette.surfaceHigh,
                uncheckedBorderColor = Palette.outline,
            ),
        )
    }
}

@Composable
private fun SpeedSelector(current: Speed, onSelect: (Speed) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (speed in Speed.entries) {
            val selected = speed == current
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Palette.accent else Palette.surfaceHigh)
                    .clickable { onSelect(speed) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    speed.label,
                    color = if (selected) Palette.background else Palette.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
