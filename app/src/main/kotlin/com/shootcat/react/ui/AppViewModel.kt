package com.shootcat.react.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.Settings
import com.shootcat.react.data.SettingsStore
import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.Terrain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Screen { TITLE, SANDBOX, SETTINGS }

data class AppUiState(
    val screen: Screen = Screen.TITLE,
    /** Where the settings return to. */
    val returnTo: Screen = Screen.TITLE,
    val settings: Settings = Settings(),
    /** Material number the brush paints with, or [Session.ERASER]. */
    val tool: Int = -2,
    val loadError: String? = null,
)

/**
 * The app frame (Leitfaden V3, 3: menus, settings, sound) around the sandbox. The simulation lives here,
 * outside the screens, so it survives leaving the sandbox and rotating; its thread only runs while the
 * sandbox is on screen and the app is in the foreground.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    val sound = SoundManager(app)

    val materials: MaterialTable? = runCatching { MaterialTable.load() }.getOrNull()

    private val _state = MutableStateFlow(
        AppUiState(
            settings = settingsStore.load(),
            tool = materials?.idOrNull("sand") ?: 1,
            loadError = if (materials == null) "materials.json konnte nicht gelesen werden" else null,
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var runner: SimRunner? = null
    private var foreground = false

    init {
        applySound()
        sound.setTrack(1)
    }

    /** The sandbox simulation, built on first use: the start landscape at 200 × 350 cells. */
    fun sandbox(): SimRunner {
        runner?.let { return it }
        val mt = materials ?: error("no materials")
        val session = Session(mt, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED) { Terrain.sandbox(it) }
        return SimRunner(session, Backdrop.of(session.world)).also {
            runner = it
            updateThread()
        }
    }

    fun open(screen: Screen) {
        _state.update {
            if (screen == Screen.SETTINGS) it.copy(screen = screen, returnTo = it.screen) else it.copy(screen = screen)
        }
        updateThread()
    }

    /** Back: from the settings to where they were opened, from the sandbox to the title. */
    fun back() {
        _state.update {
            when (it.screen) {
                Screen.SETTINGS -> it.copy(screen = it.returnTo)
                else -> it.copy(screen = Screen.TITLE)
            }
        }
        updateThread()
    }

    fun selectTool(tool: Int) = _state.update { it.copy(tool = tool) }

    fun updateSettings(settings: Settings) {
        _state.update { it.copy(settings = settings) }
        settingsStore.save(settings)
        applySound()
    }

    fun setForeground(on: Boolean) {
        foreground = on
        sound.setForeground(on)
        updateThread()
    }

    private fun applySound() {
        val s = _state.value.settings
        sound.configure(effects = s.sound, music = s.music)
    }

    private fun updateThread() {
        val r = runner ?: return
        if (foreground && _state.value.screen == Screen.SANDBOX) r.start() else r.stop()
    }

    override fun onCleared() {
        runner?.stop()
        sound.release()
    }
}
