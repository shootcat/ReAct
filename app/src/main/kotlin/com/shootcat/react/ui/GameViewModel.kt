package com.shootcat.react.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.GameRepository
import com.shootcat.react.data.Progress
import com.shootcat.react.data.ProgressStore
import com.shootcat.react.data.Settings
import com.shootcat.react.data.SettingsStore
import com.shootcat.react.engine.Reaction
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.RuleEngine
import com.shootcat.react.engine.SimulationResult
import com.shootcat.react.engine.Simulator
import com.shootcat.react.engine.SolutionClassifier
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { TITLE, MAP, LEVEL, DISCOVERIES, SETTINGS }

enum class Mode {
    /** The player arranges movable objects. */
    SETUP,
    /** The simulation ran; the timeline can be played and scrubbed. */
    SIMULATION,
}

data class LevelSession(
    val level: LevelData,
    val setup: GameState,
    val mode: Mode = Mode.SETUP,
    val simulation: SimulationResult? = null,
    val frameIndex: Int = 0,
    val playing: Boolean = false,
    /** Highest frame the player has seen; discoveries are only made once a frame was watched. */
    val seenFrame: Int = 0,
    val finished: Boolean = false,
) {
    val shownState: GameState
        get() = simulation?.takeIf { mode == Mode.SIMULATION }?.frames?.getOrNull(frameIndex)?.state ?: setup

    val movedCount: Int get() = SolutionClassifier.movedObjects(level, setup).size
}

data class Completion(
    val level: LevelData,
    val newlyFound: Set<String>,
    val nextLevelId: String?,
)

data class Toast(val id: Long, val reactions: List<Reaction>)

data class GameUiState(
    val content: GameContent? = null,
    val loadError: String? = null,
    val screen: Screen = Screen.TITLE,
    /** Where Discoveries/Settings return to. */
    val returnScreen: Screen = Screen.TITLE,
    val progress: Progress = Progress(),
    val settings: Settings = Settings(),
    val session: LevelSession? = null,
    val completion: Completion? = null,
    val toast: Toast? = null,
) {
    fun isUnlocked(levelId: String): Boolean {
        val levels = content?.levels ?: return false
        val index = levels.indexOfFirst { it.id == levelId }
        return index == 0 || (index > 0 && levels[index - 1].id in progress.completed)
    }
}

sealed interface GameEvent {
    data class OpenLevel(val levelId: String) : GameEvent
    data object OpenTitle : GameEvent
    data object OpenMap : GameEvent
    data object OpenDiscoveries : GameEvent
    data object OpenSettings : GameEvent
    data object CloseOverlay : GameEvent
    data class UpdateSettings(val settings: Settings) : GameEvent
    data object ResetProgress : GameEvent
    data class Move(val objectId: String, val to: Position) : GameEvent
    data object Start : GameEvent
    data object Edit : GameEvent
    data object Reset : GameEvent
    data object TogglePlay : GameEvent
    data class Seek(val frame: Int) : GameEvent
    data object StepForward : GameEvent
    data object StepBack : GameEvent
    data object DismissCompletion : GameEvent
    data object NextLevel : GameEvent
    data object DismissToast : GameEvent
    data object Back : GameEvent
}

/** Single source of truth for the UI (unidirectional data flow: events in, state out). */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProgressStore(app)
    private val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(load(app))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var toastJob: Job? = null
    private var toastCounter = 0L

    private fun load(app: Application): GameUiState =
        try {
            GameUiState(content = GameRepository.load(app), progress = store.load(), settings = settingsStore.load())
        } catch (e: Exception) {
            GameUiState(loadError = e.message ?: e.toString())
        }

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.OpenLevel -> openLevel(event.levelId)
            GameEvent.OpenTitle -> {
                stopPlayback()
                _state.update { it.copy(screen = Screen.TITLE, session = null, completion = null) }
            }
            GameEvent.OpenMap -> {
                stopPlayback()
                _state.update { it.copy(screen = Screen.MAP, session = null, completion = null) }
            }
            GameEvent.OpenDiscoveries -> openOverlay(Screen.DISCOVERIES)
            GameEvent.OpenSettings -> openOverlay(Screen.SETTINGS)
            GameEvent.CloseOverlay -> _state.update { it.copy(screen = it.returnScreen) }
            is GameEvent.UpdateSettings -> {
                settingsStore.save(event.settings)
                _state.update { it.copy(settings = event.settings) }
            }
            GameEvent.ResetProgress -> {
                store.clear()
                _state.update { it.copy(progress = Progress()) }
            }
            is GameEvent.Move -> updateSession { s ->
                if (s.mode != Mode.SETUP) s else s.setup.withObjectMoved(event.objectId, event.to)?.let { s.copy(setup = it) } ?: s
            }
            GameEvent.Start -> start()
            GameEvent.Edit -> {
                stopPlayback()
                updateSession { LevelSession(it.level, it.setup) }
            }
            GameEvent.Reset -> {
                stopPlayback()
                updateSession { LevelSession(it.level, it.level.initialState()) }
            }
            GameEvent.TogglePlay -> togglePlay()
            is GameEvent.Seek -> {
                pause()
                goTo(event.frame)
            }
            GameEvent.StepForward -> {
                pause()
                session()?.let { goTo(it.frameIndex + 1) }
            }
            GameEvent.StepBack -> {
                pause()
                session()?.let { goTo(it.frameIndex - 1) }
            }
            GameEvent.DismissCompletion -> _state.update { it.copy(completion = null) }
            GameEvent.NextLevel -> {
                val next = _state.value.completion?.nextLevelId
                _state.update { it.copy(completion = null) }
                if (next != null) openLevel(next) else onEvent(GameEvent.OpenMap)
            }
            GameEvent.DismissToast -> _state.update { it.copy(toast = null) }
            GameEvent.Back -> back()
        }
    }

    private fun back() {
        val st = _state.value
        when {
            st.completion != null -> onEvent(GameEvent.DismissCompletion)
            st.screen == Screen.DISCOVERIES || st.screen == Screen.SETTINGS -> onEvent(GameEvent.CloseOverlay)
            st.screen == Screen.LEVEL && st.session?.mode == Mode.SIMULATION -> onEvent(GameEvent.Edit)
            st.screen == Screen.LEVEL -> onEvent(GameEvent.OpenMap)
            st.screen == Screen.MAP -> onEvent(GameEvent.OpenTitle)
            else -> Unit
        }
    }

    private fun openOverlay(screen: Screen) {
        pause()
        _state.update {
            val from = if (it.screen == Screen.DISCOVERIES || it.screen == Screen.SETTINGS) it.returnScreen else it.screen
            it.copy(screen = screen, returnScreen = from)
        }
    }

    private fun openLevel(levelId: String) {
        val st = _state.value
        val level = st.content?.level(levelId) ?: return
        if (!st.isUnlocked(levelId)) return
        stopPlayback()
        _state.update {
            it.copy(screen = Screen.LEVEL, session = LevelSession(level, level.initialState()), completion = null)
        }
    }

    private fun start() {
        val s = session() ?: return
        val content = _state.value.content ?: return
        if (s.mode != Mode.SETUP) return
        val engine = RuleEngine(content.world.types, s.level.rules)
        val result = Simulator(s.level, engine).run(s.setup)
        _state.update {
            it.copy(session = s.copy(mode = Mode.SIMULATION, simulation = result, frameIndex = 0, playing = true))
        }
        if (result.lastIndex == 0) finish() else startPlayback()
    }

    private fun togglePlay() {
        val s = session() ?: return
        val sim = s.simulation ?: return
        if (s.playing) {
            pause()
            return
        }
        val from = if (s.frameIndex >= sim.lastIndex) 0 else s.frameIndex
        _state.update { it.copy(session = s.copy(playing = true, frameIndex = from)) }
        startPlayback()
    }

    private fun startPlayback() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                delay(_state.value.settings.speed.stepMillis)
                val s = session() ?: break
                val sim = s.simulation ?: break
                if (!s.playing || s.frameIndex >= sim.lastIndex) break
                goTo(s.frameIndex + 1)
            }
        }
    }

    private fun pause() {
        stopPlayback()
        updateSession { it.copy(playing = false) }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
    }

    private fun goTo(frame: Int) {
        val s = session() ?: return
        val sim = s.simulation ?: return
        val target = frame.coerceIn(0, sim.lastIndex)

        var progress = _state.value.progress
        val discovered = mutableListOf<String>()
        for (i in s.seenFrame + 1..target) {
            for (event in sim.frames[i].events) {
                if (event.positive && event.ruleId !in progress.discoveries) {
                    progress = progress.copy(discoveries = progress.discoveries + event.ruleId)
                    discovered += event.ruleId
                }
            }
        }
        val reachedEnd = target == sim.lastIndex
        _state.update {
            it.copy(
                progress = progress,
                session = s.copy(
                    frameIndex = target,
                    seenFrame = maxOf(s.seenFrame, target),
                    playing = s.playing && !reachedEnd,
                ),
            )
        }
        if (discovered.isNotEmpty()) {
            store.save(progress)
            showDiscoveries(discovered)
        }
        if (reachedEnd) finish()
    }

    /** Called once the end of the timeline was reached for the first time. */
    private fun finish() {
        val s = session() ?: return
        if (s.finished) return
        val sim = s.simulation ?: return
        val content = _state.value.content ?: return
        _state.update { it.copy(session = s.copy(finished = true, playing = false)) }
        stopPlayback()
        if (sim.outcome != Outcome.SUCCESS) return

        val found = SolutionClassifier.classify(s.level, s.setup, sim).map { it.id }.toSet()
        val before = _state.value.progress
        val known = before.solutionsFor(s.level.id)
        val progress = before.copy(
            completed = before.completed + s.level.id,
            solutions = before.solutions + found.map { "${s.level.id}/$it" },
        )
        store.save(progress)
        val next = content.levels.getOrNull(content.indexOf(s.level.id) + 1)?.id
        val completion = Completion(s.level, found - known, next)
        _state.update { it.copy(progress = progress) }
        // Give the player a moment to see the door open before the dialog appears.
        viewModelScope.launch {
            delay(COMPLETION_DELAY_MILLIS)
            _state.update { st ->
                val current = st.session
                if (current != null && current.level.id == s.level.id && current.mode == Mode.SIMULATION) {
                    st.copy(completion = completion)
                } else {
                    st
                }
            }
        }
    }

    private fun showDiscoveries(ruleIds: List<String>) {
        val content = _state.value.content ?: return
        val reactions = ruleIds.mapNotNull { id -> content.allRules.firstOrNull { it.id == id } }
            .map { Reactions.describe(it, content.world.types) }
        val toast = Toast(++toastCounter, reactions)
        _state.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            _state.update { if (it.toast?.id == toast.id) it.copy(toast = null) else it }
        }
    }

    private fun session(): LevelSession? = _state.value.session

    private fun updateSession(transform: (LevelSession) -> LevelSession) {
        _state.update { st -> st.session?.let { st.copy(session = transform(it)) } ?: st }
    }

    private companion object {
        const val TOAST_MILLIS = 3500L
        const val COMPLETION_DELAY_MILLIS = 900L
    }
}
