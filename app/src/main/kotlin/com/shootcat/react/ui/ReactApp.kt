package com.shootcat.react.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.ReactionSymbols
import com.shootcat.react.ui.discovery.DiscoveryScreen
import com.shootcat.react.ui.level.CompletionOverlay
import com.shootcat.react.ui.level.LevelScreen
import com.shootcat.react.ui.map.WorldMapScreen
import com.shootcat.react.ui.settings.SettingsScreen
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.ReactTheme
import com.shootcat.react.ui.title.TitleScreen

@Composable
fun ReactApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val onEvent: (GameEvent) -> Unit = viewModel::onEvent
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = state.screen != Screen.TITLE || state.completion != null) {
        onEvent(GameEvent.Back)
    }
    LaunchedEffect(state.toast?.id, state.completion != null) {
        if (state.settings.haptics && (state.toast != null || state.completion != null)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    ReactTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val content = state.content
                val session = state.session
                when {
                    content == null -> LoadError(state.loadError)
                    state.screen == Screen.TITLE -> TitleScreen(
                        types = content.world.types,
                        onPlay = { onEvent(GameEvent.OpenMap) },
                        onDiscoveries = { onEvent(GameEvent.OpenDiscoveries) },
                        onSettings = { onEvent(GameEvent.OpenSettings) },
                    )
                    state.screen == Screen.DISCOVERIES -> DiscoveryScreen(
                        content = content,
                        progress = state.progress,
                        isUnlocked = state::isUnlocked,
                        onBack = { onEvent(GameEvent.CloseOverlay) },
                    )
                    state.screen == Screen.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        onChange = { onEvent(GameEvent.UpdateSettings(it)) },
                        onResetProgress = { onEvent(GameEvent.ResetProgress) },
                        onBack = { onEvent(GameEvent.CloseOverlay) },
                    )
                    state.screen == Screen.LEVEL && session != null -> LevelScreen(
                        session = session,
                        content = content,
                        progress = state.progress,
                        settings = state.settings,
                        onEvent = onEvent,
                    )
                    else -> WorldMapScreen(
                        content = content,
                        progress = state.progress,
                        isUnlocked = state::isUnlocked,
                        onOpenLevel = { onEvent(GameEvent.OpenLevel(it)) },
                        onOpenLog = { onEvent(GameEvent.OpenDiscoveries) },
                        onOpenSettings = { onEvent(GameEvent.OpenSettings) },
                        onBack = { onEvent(GameEvent.OpenTitle) },
                    )
                }

                val completion = state.completion
                if (completion != null && content != null) {
                    CompletionOverlay(
                        completion = completion,
                        found = state.progress.solutionsFor(completion.level.id),
                        types = content.world.types,
                        onNext = { onEvent(GameEvent.NextLevel) },
                        onReplay = { onEvent(GameEvent.Replay) },
                    )
                }

                val toast = state.toast
                if (toast != null && content != null && state.screen == Screen.LEVEL && completion == null) {
                    DiscoveryToast(
                        toast = toast,
                        types = content.world.types,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        }
    }
}

/** New discoveries, shown as symbols only. Touches pass through to the board – the world keeps running. */
@Composable
private fun DiscoveryToast(toast: Toast, types: TypeCatalog, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.surfaceHigh)
            .border(1.dp, Palette.accent, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (reaction in toast.reactions) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlyphIcon(Glyph.SPARK, color = Palette.accent, size = 18.dp)
                ReactionSymbols(reaction, types, iconSize = 32.dp)
            }
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
