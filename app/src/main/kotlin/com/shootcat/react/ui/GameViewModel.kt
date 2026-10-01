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
import com.shootcat.react.engine.LiveSimulation
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.Reaction
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.RuleEngine
import com.shootcat.react.engine.Run
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

enum class Screen { TITLE, WORLDS, MAP, LEVEL, DISCOVERIES, SETTINGS }

/** One undoable player action: the world right before it and right after it. */
data class HistoryEntry(val before: Run, val after: Run)

data class LevelSession(
    val level: LevelData,
    val run: Run,
    /** The world before the last simulation step, to animate it; null when the board should snap. */
    val previous: GameState? = null,
    /** Increases with every shown change so the board knows when to (re)start an animation. */
    val tick: Int = 0,
    val undo: List<HistoryEntry> = emptyList(),
    val redo: List<HistoryEntry> = emptyList(),
) {
    val state: GameState get() = run.state
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    /** Once the door is open (or the cascade protection fired) only undo, redo and reset are left. */
    val canMove: Boolean get() = run.outcome == null
    val atStart: Boolean get() = run.moves.isEmpty() && !run.active
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
    /** The world whose map is shown. */
    val worldNumber: Int = 1,
) {
    /** A world opens once the last main level of the world before it is solved. */
    fun isWorldUnlocked(number: Int): Boolean {
        val worlds = content?.worlds ?: return false
        val index = worlds.indexOfFirst { it.world == number }
        if (index <= 0) return index == 0
        return worlds[index - 1].levelIds.lastOrNull() in progress.completed
    }

    /** Main levels open one after another; the bonus level opens when every main level is solved. */
    fun isUnlocked(levelId: String): Boolean {
        val world = content?.worldOf(levelId) ?: return false
        if (!isWorldUnlocked(world.world)) return false
        if (levelId == world.bonusLevelId) return world.levelIds.all { it in progress.completed }
        val index = world.levelIds.indexOf(levelId)
        return index == 0 || (index > 0 && world.levelIds[index - 1] in progress.completed)
    }

    /** Solved main levels of a world, for the world selection. */
    fun solvedIn(number: Int): Int = content?.world(number)?.levelIds?.count { it in progress.completed } ?: 0
}

sealed interface GameEvent {
    data class OpenLevel(val levelId: String) : GameEvent
    data object OpenTitle : GameEvent
    data object OpenWorlds : GameEvent
    data class OpenWorld(val number: Int) : GameEvent
    data object OpenMap : GameEvent
    data object OpenDiscoveries : GameEvent
    data object OpenSettings : GameEvent
    data object CloseOverlay : GameEvent
    data class UpdateSettings(val settings: Settings) : GameEvent
    data object ResetProgress : GameEvent
    data class Move(val objectId: String, val to: Position) : GameEvent
    data object Undo : GameEvent
    data object Redo : GameEvent
    data object Reset : GameEvent
    data object Replay : GameEvent
    data object DismissCompletion : GameEvent
    data object NextLevel : GameEvent
    data object Back : GameEvent
}

/**
 * Single source of truth for the UI (unidirectional data flow: events in, state out).
 *
 * Live mode: every move immediately sets the world in motion; it then advances one simulation step per
 * tick until it is at rest again. Each player action is kept on an undo stack as a pair of immutable
 * worlds (before/after), so undo and redo are exact.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProgressStore(app)
    private val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(load(app))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var live: LiveSimulation? = null
    private var simJob: Job? = null
    private var completionJob: Job? = null
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
            GameEvent.OpenTitle -> leaveLevel(Screen.TITLE)
            GameEvent.OpenWorlds -> leaveLevel(Screen.WORLDS)
            is GameEvent.OpenWorld -> {
                if (_state.value.isWorldUnlocked(event.number)) {
                    leaveLevel(Screen.MAP)
                    _state.update { it.copy(worldNumber = event.number) }
                }
            }
            GameEvent.OpenMap -> leaveLevel(Screen.MAP)
            GameEvent.OpenDiscoveries -> openOverlay(Screen.DISCOVERIES)
            GameEvent.OpenSettings -> openOverlay(Screen.SETTINGS)
            GameEvent.CloseOverlay -> {
                _state.update { it.copy(screen = it.returnScreen) }
                if (_state.value.screen == Screen.LEVEL) simulate(initialDelay = stepMillis())
            }
            is GameEvent.UpdateSettings -> {
                settingsStore.save(event.settings)
                _state.update { it.copy(settings = event.settings) }
            }
            GameEvent.ResetProgress -> {
                store.clear()
                _state.update { it.copy(progress = Progress()) }
            }
            is GameEvent.Move -> move(event.objectId, event.to)
            GameEvent.Undo -> undo()
            GameEvent.Redo -> redo()
            GameEvent.Reset -> reset()
            GameEvent.Replay -> {
                _state.update { it.copy(completion = null) }
                reset()
            }
            GameEvent.DismissCompletion -> _state.update { it.copy(completion = null) }
            GameEvent.NextLevel -> {
                val next = _state.value.completion?.nextLevelId
                _state.update { it.copy(completion = null) }
                if (next != null) openLevel(next) else onEvent(GameEvent.OpenMap)
            }
            GameEvent.Back -> back()
        }
    }

    private fun back() {
        val st = _state.value
        when {
            st.completion != null -> onEvent(GameEvent.DismissCompletion)
            st.screen == Screen.DISCOVERIES || st.screen == Screen.SETTINGS -> onEvent(GameEvent.CloseOverlay)
            st.screen == Screen.LEVEL -> onEvent(GameEvent.OpenMap)
            st.screen == Screen.MAP -> onEvent(GameEvent.OpenWorlds)
            st.screen == Screen.WORLDS -> onEvent(GameEvent.OpenTitle)
            else -> Unit
        }
    }

    private fun leaveLevel(screen: Screen) {
        stopSimulation()
        completionJob?.cancel()
        live = null
        _state.update { it.copy(screen = screen, session = null, completion = null, toast = null) }
    }

    private fun openOverlay(screen: Screen) {
        // The world holds still while the player looks at something else.
        stopSimulation()
        _state.update {
            val from = if (it.screen == Screen.DISCOVERIES || it.screen == Screen.SETTINGS) it.returnScreen else it.screen
            it.copy(screen = screen, returnScreen = from, toast = null)
        }
    }

    private fun openLevel(levelId: String) {
        val st = _state.value
        val content = st.content ?: return
        val level = content.level(levelId) ?: return
        if (!st.isUnlocked(levelId)) return
        stopSimulation()
        completionJob?.cancel()
        val sim = LiveSimulation(level, RuleEngine(content.types, level.rules))
        live = sim
        val world = content.worldOf(levelId)?.world ?: st.worldNumber
        _state.update {
            it.copy(screen = Screen.LEVEL, session = LevelSession(level, sim.start()), completion = null, worldNumber = world)
        }
    }

    // ------------------------------------------------------------------ player actions

    private fun move(objectId: String, to: Position) {
        val s = session() ?: return
        val sim = live ?: return
        val after = sim.move(s.run, objectId, to) ?: return
        _state.update {
            it.copy(
                session = s.copy(
                    run = after,
                    previous = null,
                    tick = s.tick + 1,
                    undo = (s.undo + HistoryEntry(s.run, after)).takeLast(MAX_UNDO),
                    redo = emptyList(),
                ),
            )
        }
        // React right away: the first step follows the drop almost immediately.
        simulate(initialDelay = FIRST_STEP_MILLIS)
    }

    private fun undo() {
        val s = session() ?: return
        val entry = s.undo.lastOrNull() ?: return
        show(s.copy(run = entry.before, undo = s.undo.dropLast(1), redo = s.redo + entry))
    }

    private fun redo() {
        val s = session() ?: return
        val entry = s.redo.lastOrNull() ?: return
        show(s.copy(run = entry.after, undo = s.undo + entry, redo = s.redo.dropLast(1)))
    }

    /** Back to the level's start. Undoable like any other action. */
    private fun reset() {
        val s = session() ?: return
        val sim = live ?: return
        if (s.atStart) return
        val start = sim.start()
        show(s.copy(run = start, undo = (s.undo + HistoryEntry(s.run, start)).takeLast(MAX_UNDO), redo = emptyList()))
    }

    /** Jumps to another point in history: the board snaps, and the world carries on if it was still moving. */
    private fun show(session: LevelSession) {
        stopSimulation()
        completionJob?.cancel()
        _state.update { it.copy(session = session.copy(previous = null, tick = session.tick + 1), completion = null) }
        simulate(initialDelay = stepMillis())
    }

    // ------------------------------------------------------------------ the running world

    private fun stepMillis(): Long = _state.value.settings.speed.stepMillis

    /** Advances the world step by step while it is reacting. Safe to call while it is already running. */
    private fun simulate(initialDelay: Long) {
        if (simJob?.isActive == true) return
        if (session()?.run?.active != true) return
        simJob = viewModelScope.launch {
            delay(initialDelay)
            while (true) {
                val s = session() ?: break
                val sim = live ?: break
                if (!s.run.active || _state.value.screen != Screen.LEVEL) break
                val tick = sim.step(s.run)
                val next = tick.run
                val changed = next.state != s.run.state
                _state.update { st ->
                    st.copy(
                        session = s.copy(
                            run = next,
                            previous = if (changed) s.run.state else s.previous,
                            tick = if (changed) s.tick + 1 else s.tick,
                        ),
                    )
                }
                discover(tick.events.filter { it.positive }.map { it.ruleId })
                if (next.outcome == Outcome.SUCCESS) complete(next)
                if (!next.active) break
                delay(stepMillis())
            }
        }
    }

    private fun stopSimulation() {
        simJob?.cancel()
        simJob = null
    }

    private fun discover(ruleIds: List<String>) {
        var progress = _state.value.progress
        val found = ruleIds.distinct().filter { it !in progress.discoveries }
        if (found.isEmpty()) return
        progress = progress.copy(discoveries = progress.discoveries + found)
        store.save(progress)
        _state.update { it.copy(progress = progress) }
        showDiscoveries(found)
    }

    /** The goals were reached: record the solution classes and show the result after a short pause. */
    private fun complete(run: Run) {
        val s = session() ?: return
        val content = _state.value.content ?: return
        val found = SolutionClassifier.classify(s.level, run).map { it.id }.toSet()
        val before = _state.value.progress
        val known = before.solutionsFor(s.level.id)
        val progress = before.copy(
            completed = before.completed + s.level.id,
            solutions = before.solutions + found.map { "${s.level.id}/$it" },
        )
        store.save(progress)
        _state.update { it.copy(progress = progress) }
        val completion = Completion(s.level, found - known, nextLevel(s.level.id))
        completionJob?.cancel()
        // Give the player a moment to see the door open before the dialog appears.
        completionJob = viewModelScope.launch {
            delay(COMPLETION_DELAY_MILLIS)
            _state.update { st ->
                val current = st.session
                if (current != null && current.level.id == s.level.id && current.run.outcome == Outcome.SUCCESS) {
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
            .map { Reactions.describe(it, content.types) }
        val toast = Toast(++toastCounter, reactions)
        _state.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            _state.update { if (it.toast?.id == toast.id) it.copy(toast = null) else it }
        }
    }

    /**
     * Where "Weiter" leads after a solved level: the next main level, then the bonus level once it is
     * open, then the first level of the next world. Null means back to the map.
     */
    private fun nextLevel(levelId: String): String? {
        val st = _state.value
        val content = st.content ?: return null
        val world = content.worldOf(levelId) ?: return null
        content.nextInWorld(levelId)?.let { return it }
        val bonus = world.bonusLevelId
        if (bonus != null && bonus != levelId && bonus !in st.progress.completed && st.isUnlocked(bonus)) return bonus
        val nextWorld = content.worlds.getOrNull(content.worlds.indexOf(world) + 1) ?: return null
        return nextWorld.levelIds.firstOrNull()?.takeIf { st.isUnlocked(it) && it !in st.progress.completed }
    }

    private fun session(): LevelSession? = _state.value.session

    private companion object {
        const val TOAST_MILLIS = 3500L
        const val COMPLETION_DELAY_MILLIS = 900L
        const val FIRST_STEP_MILLIS = 120L
        const val MAX_UNDO = 200
    }
}
