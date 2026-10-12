package com.shootcat.react.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.LevelBest
import com.shootcat.react.data.Progress
import com.shootcat.react.data.ProgressStore
import com.shootcat.react.data.Settings
import com.shootcat.react.data.SettingsStore
import com.shootcat.react.level.Level
import com.shootcat.react.level.Levels
import com.shootcat.react.level.Play
import com.shootcat.react.level.Result
import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.Discoveries
import com.shootcat.react.sim.DiscoveryWatch
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.Template
import com.shootcat.react.sim.Terrain
import com.shootcat.react.sim.WorldSize
import com.shootcat.react.ui.sandbox.BuildMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Screen { TITLE, SANDBOX, SETTINGS, BOOK, LEVELS, LEVEL }

data class AppUiState(
    val screen: Screen = Screen.TITLE,
    /** Where the settings return to. */
    val returnTo: Screen = Screen.TITLE,
    val settings: Settings = Settings(),
    /** Material number the brush paints with, or [Session.ERASER]. */
    val tool: Int = -2,
    /** The tool in the hand while a level is played: the bucket or the hand, or a material from the store. */
    val levelTool: Int = Session.SCOOP,
    /** How the finger builds: paint, line, rectangle, frame. */
    val mode: BuildMode = BuildMode.BRUSH,
    /** Ids of the discovery book entries found so far (Leitfaden V3, 4.3). */
    val discoveries: Set<String> = emptySet(),
    /** The best result of each solved level, by level id. */
    val best: Map<String, LevelBest> = emptyMap(),
    val loadError: String? = null,
)

/** A level being played (Leitfaden V3, Phase 3): its rules and goals, and the thread that steps its world. */
class LevelRun(val index: Int, val play: Play, val runner: SimRunner)

/**
 * The app frame (Leitfaden V3, 3: menus, settings, sound) around the sandbox and the levels. The simulation lives here,
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
            best = progress.best,
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

    private val _world = MutableStateFlow<SimRunner?>(null)

    /** The sandbox simulation as it is now; a new world replaces it. */
    val world: StateFlow<SimRunner?> = _world.asStateFlow()

    /** The levels that come with the game, in order (Leitfaden V3, 5.4). */
    val levels: List<Level> by lazy { runCatching { Levels.all() }.getOrDefault(emptyList()) }

    private val _level = MutableStateFlow<LevelRun?>(null)

    /** The level being played, if one is. */
    val level: StateFlow<LevelRun?> = _level.asStateFlow()

    /** Starts level [index] from the beginning: only its tools, its store, its goals (Leitfaden V3, 5.1). */
    fun startLevel(index: Int) {
        val mt = materials ?: return
        val level = levels.getOrNull(index) ?: return
        _level.value?.runner?.stop()
        val play = Play(level, mt)
        val runner = SimRunner(play.session, Backdrop.of(play.session.world))
        book?.let { b -> runner.watch = DiscoveryWatch(b, progress.discoveries) }
        _level.value = LevelRun(index, play, runner)
        val tools = play.tools
        _state.update { it.copy(screen = Screen.LEVEL, levelTool = if (tools.bucket || !tools.hand) Session.SCOOP else Session.HAND) }
        updateThread()
    }

    /** A level solved: done for good, and its best medal kept. */
    fun solved(run: LevelRun, result: Result) {
        progress = progress.solved(run.play.level.spec.id, result)
        progressStore.save(progress)
        _state.update { it.copy(best = progress.best) }
    }

    /** The sandbox simulation, built on first use from the landscape and size in the settings (4a.5). */
    fun sandbox(): SimRunner {
        runner?.let { return it }
        val s = _state.value.settings
        return build(templateOf(s), sizeOf(s))
    }

    /** Starts a new sandbox world from [template] in [size]; the old one is gone (4a.5). */
    fun newWorld(template: Template, size: WorldSize) {
        updateSettings(_state.value.settings.copy(template = template.name, worldSize = size.name))
        runner?.stop()
        runner = null
        build(template, size)
    }

    private fun build(template: Template, size: WorldSize): SimRunner {
        val mt = materials ?: error("no materials")
        val session = Session(mt, size.width, size.height, Terrain.SEED) { template.build(it) }
        return SimRunner(session, Backdrop.of(session.world)).also {
            book?.let { b -> it.watch = DiscoveryWatch(b, progress.discoveries) }
            runner = it
            _world.value = it
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

    /**
     * Back: from the settings or the book to where they were opened, from a level to the list of levels (the
     * level is left), from the list and the sandbox to the title.
     */
    fun back() {
        val from = _state.value.screen
        _state.update {
            when (it.screen) {
                Screen.SETTINGS, Screen.BOOK -> it.copy(screen = it.returnTo)
                Screen.LEVEL -> it.copy(screen = Screen.LEVELS)
                else -> it.copy(screen = Screen.TITLE)
            }
        }
        if (from == Screen.LEVEL) {
            _level.value?.runner?.stop()
            _level.value = null
        }
        updateThread()
    }

    fun selectTool(tool: Int) = _state.update { if (it.screen == Screen.LEVEL) it.copy(levelTool = tool) else it.copy(tool = tool) }

    private fun templateOf(s: Settings) = Template.entries.firstOrNull { it.name == s.template } ?: Template.MEADOW
    private fun sizeOf(s: Settings) = WorldSize.entries.firstOrNull { it.name == s.worldSize } ?: WorldSize.MEDIUM

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

    /** Only the world on screen runs, and only while the app is in the foreground. */
    private fun updateThread() {
        val screen = _state.value.screen
        runner?.let { if (foreground && screen == Screen.SANDBOX) it.start() else it.stop() }
        _level.value?.runner?.let { if (foreground && screen == Screen.LEVEL) it.start() else it.stop() }
    }

    override fun onCleared() {
        runner?.stop()
        _level.value?.runner?.stop()
        sound.release()
    }
}
