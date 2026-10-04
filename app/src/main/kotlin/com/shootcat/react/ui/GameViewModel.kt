package com.shootcat.react.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.GameContent
import com.shootcat.react.data.GameRepository
import com.shootcat.react.data.Progress
import com.shootcat.react.data.ProgressStore
import com.shootcat.react.data.Settings
import com.shootcat.react.data.SettingsStore
import com.shootcat.react.engine.Drop
import com.shootcat.react.engine.LiveSimulation
import com.shootcat.react.engine.Outcome
import com.shootcat.react.engine.Reaction
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.RuleEngine
import com.shootcat.react.engine.Run
import com.shootcat.react.engine.Tick
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
    /** Counts jumps through history (undo, redo, reset): the board then shows the new world at once. */
    val snaps: Int = 0,
) {
    val state: GameState get() = run.state
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    /** Once the task is done (or the cascade protection fired) only undo, redo and reset are left. */
    val canMove: Boolean get() = run.outcome == null

    /** For every goal of the level: does it hold right now (or has it happened, for rain)? */
    val goalsMet: List<Boolean>
        get() = level.goals.mapIndexed { i, goal -> i in run.latched || goal.isMet(run.state, run.moves.size) }
    val atStart: Boolean get() = run.moves.isEmpty() && !run.active
}

data class Completion(
    val level: LevelData,
    /** Optional goals (indices into the level's goals) met this time. */
    val achieved: Set<Int>,
    /** Optional goals met for the first time. */
    val newlyAchieved: Set<Int>,
    /** Optional goals met in any run so far, this one included. */
    val everAchieved: Set<Int>,
    val moves: Int,
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
    /** A drop that is not allowed: the object bounced back. */
    data class Bounce(val objectId: String) : GameEvent
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
    private val sound = SoundManager(app).apply {
        val settings = _state.value.settings
        configure(effects = settings.sound, music = settings.music)
    }

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
                    sound.setTrack(event.number)
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
                sound.configure(effects = event.settings.sound, music = event.settings.music)
            }
            GameEvent.ResetProgress -> {
                store.clear()
                _state.update { it.copy(progress = Progress()) }
            }
            is GameEvent.Move -> move(event.objectId, event.to)
            is GameEvent.Bounce -> sound.play("bounce")
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

    /** What dropping [objectId] on [to] would do right now; null means it would bounce off. */
    fun previewDrop(objectId: String, to: Position): Drop? {
        val s = session() ?: return null
        return live?.drop(s.run, objectId, to)
    }

    /** The app came to the front or went to the background: the melody follows. */
    fun setForeground(foreground: Boolean) {
        sound.setForeground(foreground)
    }

    override fun onCleared() {
        sound.release()
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
        val sim = LiveSimulation(level, RuleEngine(content.types, level.rules, wind = level.wind))
        live = sim
        val world = content.worldOf(levelId)?.world ?: st.worldNumber
        sound.setTrack(world)
        _state.update {
            it.copy(screen = Screen.LEVEL, session = LevelSession(level, sim.start()), completion = null, worldNumber = world)
        }
    }

    // ------------------------------------------------------------------ player actions

    private fun move(objectId: String, to: Position) {
        val s = session() ?: return
        val sim = live ?: return
        val drop = sim.drop(s.run, objectId, to)
        if (drop == null) {
            sound.play("bounce")
            return
        }
        val after = sim.apply(s.run, objectId, to, drop)
        if (drop is Drop.Merged) discover(listOf(drop.rule.id))
        sound.play(
            when (drop) {
                is Drop.Merged -> drop.rule.sound ?: "merge"
                is Drop.NextTo -> "place"
                is Drop.Placed -> if (s.state.objectAt(to)?.isLiquid == true) "splash" else "place"
            },
        )
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
        _state.update { it.copy(session = session.copy(previous = null, tick = session.tick + 1, snaps = session.snaps + 1), completion = null) }
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
                playStep(s, tick)
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

    /**
     * The sounds of one step: what the rules and the weather did, things that landed, and a soft chime
     * when a task is met.
     */
    private fun playStep(before: LevelSession, tick: Tick) {
        val old = before.run.state
        val new = tick.run.state
        for (cue in tick.cues.map { it.sound }.distinct()) sound.play(cue)
        var thud = false
        var splash = false
        for (o in new.objects) {
            val was = old.objectById(o.id) ?: continue
            if (was.position.x != o.position.x || was.position.y >= o.position.y) continue
            val below = o.position.down()
            val landed = new.isWall(below) || new.objectAt(below)?.let { !it.isAiry } == true
            if (!landed) continue
            if (o.isLiquid || new.objectAt(below)?.isLiquid == true) splash = true else if (!o.isAiry) thud = true
        }
        // Falling water that has run into a pool.
        for (o in old.objects) {
            if (!o.isLiquid || new.objectById(o.id) != null) continue
            if (old.isFree(o.position.down()) && new.objectAt(o.position.down())?.isLiquid == true) splash = true
        }
        if (thud) sound.play("thud")
        if (splash) sound.play("splash", 0.6f)
        val wasMet = before.goalsMet
        val nowMet = before.copy(run = tick.run).goalsMet
        val newlyMet = before.level.goals.indices.any { !before.level.goals[it].optional && nowMet[it] && !wasMet[it] }
        if (newlyMet && tick.run.outcome != Outcome.SUCCESS) sound.play("goal")
    }

    /** The task is done: record the optional goals met and show the result after a short pause. */
    private fun complete(run: Run) {
        val s = session() ?: return
        val before = _state.value.progress
        val known = before.extrasFor(s.level.id)
        val progress = before.copy(
            completed = before.completed + s.level.id,
            extras = before.extras + run.achieved.map { "${s.level.id}/$it" },
        )
        store.save(progress)
        _state.update { it.copy(progress = progress) }
        sound.play("success")
        val completion = Completion(
            level = s.level,
            achieved = run.achieved,
            newlyAchieved = run.achieved - known,
            everAchieved = known + run.achieved,
            moves = run.moves.size,
            nextLevelId = nextLevel(s.level.id),
        )
        completionJob?.cancel()
        // Give the player a moment to see the world settle before the card appears.
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
        val reactions = ruleIds.mapNotNull { id ->
            content.allRules.firstOrNull { it.id == id }?.let { Reactions.describe(it, content.types) }
                ?: content.merges.firstOrNull { it.id == id }?.let { Reactions.describeMerge(it, content.types) }
        }
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
