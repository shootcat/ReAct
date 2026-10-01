package com.shootcat.react.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.ui.discovery.DiscoveryScreen
import com.shootcat.react.ui.level.CompletionDialog
import com.shootcat.react.ui.level.LevelScreen
import com.shootcat.react.ui.map.WorldMapScreen
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.ReactTheme

@Composable
fun ReactApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val onEvent: (GameEvent) -> Unit = viewModel::onEvent

    BackHandler(enabled = state.screen != Screen.MAP || state.completion != null) {
        onEvent(GameEvent.Back)
    }

    ReactTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val content = state.content
                val session = state.session
                when {
                    content == null -> LoadError(state.loadError)
                    state.screen == Screen.DISCOVERIES -> DiscoveryScreen(
                        content = content,
                        progress = state.progress,
                        isUnlocked = state::isUnlocked,
                        onBack = { onEvent(GameEvent.CloseDiscoveries) },
                    )
                    state.screen == Screen.LEVEL && session != null -> LevelScreen(
                        session = session,
                        content = content,
                        progress = state.progress,
                        onEvent = onEvent,
                    )
                    else -> WorldMapScreen(
                        content = content,
                        progress = state.progress,
                        isUnlocked = state::isUnlocked,
                        onOpenLevel = { onEvent(GameEvent.OpenLevel(it)) },
                        onOpenLog = { onEvent(GameEvent.OpenDiscoveries) },
                    )
                }

                val toast = state.toast
                if (toast != null) {
                    DiscoveryToast(
                        toast = toast,
                        onClick = { onEvent(GameEvent.DismissToast) },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }

            val completion = state.completion
            if (completion != null) {
                CompletionDialog(
                    completion = completion,
                    found = state.progress.solutionsFor(completion.level.id),
                    onNext = { onEvent(GameEvent.NextLevel) },
                    onStay = { onEvent(GameEvent.DismissCompletion) },
                )
            }
        }
    }
}

@Composable
private fun DiscoveryToast(toast: Toast, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surfaceHigh)
            .border(1.dp, Palette.accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("✦ ${toast.title}", style = MaterialTheme.typography.labelLarge, color = Palette.accent, fontWeight = FontWeight.Bold)
        for (line in toast.lines) {
            Text(line, style = MaterialTheme.typography.titleSmall, color = Palette.text)
        }
    }
}

@Composable
private fun LoadError(message: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Level-Daten konnten nicht geladen werden.", color = Palette.danger, fontWeight = FontWeight.Bold)
        Text(message ?: "Unbekannter Fehler", color = Palette.textDim)
    }
}
