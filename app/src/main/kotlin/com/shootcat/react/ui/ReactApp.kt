package com.shootcat.react.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.ui.book.BookScreen
import com.shootcat.react.ui.sandbox.SandboxScreen
import com.shootcat.react.ui.settings.SettingsScreen
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.ReactTheme
import com.shootcat.react.ui.title.TitleScreen

@Composable
fun ReactApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val world by viewModel.world.collectAsState()

    BackHandler(enabled = state.screen != Screen.TITLE) { viewModel.back() }

    ReactTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val materials = viewModel.materials
                when {
                    materials == null -> LoadError(state.loadError)
                    state.screen == Screen.TITLE -> TitleScreen(
                        materials = materials,
                        onPlay = { viewModel.open(Screen.SANDBOX) },
                        onSettings = { viewModel.open(Screen.SETTINGS) },
                    )
                    state.screen == Screen.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        onChange = viewModel::updateSettings,
                        onBack = viewModel::back,
                    )
                    state.screen == Screen.BOOK && viewModel.book != null -> BookScreen(
                        book = viewModel.book,
                        found = state.discoveries,
                        onBack = viewModel::back,
                    )
                    else -> SandboxScreen(
                        runner = world ?: viewModel.sandbox(),
                        materials = materials,
                        settings = state.settings,
                        tool = state.tool,
                        mode = state.mode,
                        sound = viewModel.sound,
                        onTool = viewModel::selectTool,
                        onMode = viewModel::selectMode,
                        onSettings = { viewModel.updateSettings(it) },
                        onOpenSettings = { viewModel.open(Screen.SETTINGS) },
                        onOpenBook = { viewModel.open(Screen.BOOK) },
                        onDiscovered = viewModel::discovered,
                        onNewWorld = viewModel::newWorld,
                        onBack = viewModel::back,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadError(message: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Die Materialdaten konnten nicht geladen werden.", color = Palette.danger, fontWeight = FontWeight.Bold)
        Text(message ?: "Unbekannter Fehler", color = Palette.textDim)
    }
}
