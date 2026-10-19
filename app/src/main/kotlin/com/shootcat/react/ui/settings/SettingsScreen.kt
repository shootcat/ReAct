package com.shootcat.react.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar(title = "Einstellungen", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Klang")
            Card {
                ToggleRow("Geräusche", settings.sound) { onChange(settings.copy(sound = it)) }
                ToggleRow("Musik", settings.music) { onChange(settings.copy(music = it)) }
            }

            Section("Gerät")
            Card {
                ToggleRow("Vibration", settings.haptics) { onChange(settings.copy(haptics = it)) }
                ToggleRow("Leistungsanzeige", settings.performance) { onChange(settings.copy(performance = it)) }
                Text(
                    "Zeigt über der Welt Schritte pro Sekunde, Rechenzeit und wache Chunks. " +
                        "Im Spielplatz misst „Leistung messen“ im Menü die Werte für den Bericht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.textDim,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                ToggleRow("Wolkenansicht für Entwickler", settings.cloudDebug) { onChange(settings.copy(cloudDebug = it)) }
                Text(
                    "Zeigt unter jeder Wolke, wie dicht sie in dieser Spalte ist, die Schwelle, ab der es regnet, " +
                        "ihre Temperatur (Schnee bei 0 °C und darunter) und wie viele Tropfen in der Sekunde fallen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.textDim,
                    modifier = Modifier.padding(bottom = 6.dp),
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
