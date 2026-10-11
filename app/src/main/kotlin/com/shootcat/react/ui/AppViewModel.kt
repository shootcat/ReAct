package com.shootcat.react.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.Progress
import com.shootcat.react.data.ProgressStore
import com.shootcat.react.data.Settings
import com.shootcat.react.data.SettingsStore
import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.Discoveries
import com.shootcat.react.sim.DiscoveryWatch
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.Terrain
import com.shootcat.react.ui.sandbox.BuildMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Screen { TITLE, SANDBOX, SETTINGS, BOOK }

data class AppUiState(
    val screen: Screen = Screen.TITLE,
    /** Where the settings return to. */
    val returnTo: Screen = Screen.TITLE,
    val settings: Settings = Settings(),
    /** Material number the brush paints with, or [Session.ERASER]. */
    val tool: Int = -2,
    /** How the finger builds: paint, line, rectangle, frame. */
    val mode: BuildMode = BuildMode.BRUSH,
    /** Ids of the discovery book entries found so far (Leitfaden V3, 4.3). */
    val discoveries: Set<String> = emptySet(),
    val loadError: String? = null,
)

/**
 * The app frame (Leitfaden V3, 3: menus, settings, sound) around the sandbox. The simulation lives here,
 * outside the screens, so it survives leaving the sandbox and rotating; its thread only runs while the
 * sandbox is on screen and the app is in the foreground.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val progressStore = ProgressStore(app)
    private var progress: Progress = progressStore.load()
    val sound = SoundManager(app)

    val materials: MaterialTable? = runCatching { MaterialTable.load() }.getOrNull()

    /** The discovery book: entries and their little scenes. */
    val book: Discoveries? = materials?.let { runCatching { Discoveries.load(it) }.getOrNull() }

    private val _state = MutableStateFlow(
        AppUiState(
            settings = settingsStore.load(),
            tool = materials?.idOrNull("sand") ?: 1,
            discoveries = progress.discoveries,
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
            book?.let { b -> it.watch = DiscoveryWatch(b, progress.discoveries) }
            runner = it
            updateThread()
        }
    }

    /** New entries for the discovery book: kept for good. */
    fun discovered(entries: List<Discoveries.Entry>) {
        if (entries.isEmpty()) return
        progress = progress.copy(discoveries = progress.discoveries + entries.map { it.id })
        progressStore.save(progress)
        _state.update { it.copy(discoveries = progress.discoveries) }
    }

    fun open(screen: Screen) {
        _state.update {
            if (screen == Screen.SETTINGS || screen == Screen.BOOK) it.copy(screen = screen, returnTo = it.screen) else it.copy(screen = screen)
        }
        updateThread()
    }

    /** Back: from the settings or the book to where they were opened, from the sandbox to the title. */
    fun back() {
        _state.update {
            when (it.screen) {
                Screen.SETTINGS, Screen.BOOK -> it.copy(screen = it.returnTo)
                else -> it.copy(screen = Screen.TITLE)
            }
        }
        updateThread()
    }

    fun selectTool(tool: Int) = _state.update { it.copy(tool = tool) }

    fun selectMode(mode: BuildMode) = _state.update { it.copy(mode = mode) }

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
