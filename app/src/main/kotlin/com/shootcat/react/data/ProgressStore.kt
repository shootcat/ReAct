package com.shootcat.react.data

import android.content.Context
import com.shootcat.react.level.Medal
import com.shootcat.react.level.Result

/** The best a level was solved with: the better medal, and at the same medal the shorter time. */
data class LevelBest(val medal: Medal, val seconds: Float, val actions: Int) {
    fun beats(other: LevelBest?): Boolean =
        other == null || medal.ordinal < other.medal.ordinal || (medal == other.medal && seconds < other.seconds)
}

data class Progress(
    val completed: Set<String> = emptySet(),
    /** Optional tasks the player has managed, as "levelId/goalIndex". */
    val extras: Set<String> = emptySet(),
    /** Ids of rules the player has seen in action. */
    val discoveries: Set<String> = emptySet(),
    /** The best result of each solved level, by level id. */
    val best: Map<String, LevelBest> = emptyMap(),
) {
    /** The level [levelId] solved with [result]: kept if it is the best so far. */
    fun solved(levelId: String, result: Result): Progress {
        val now = LevelBest(result.medal, result.seconds, result.actions)
        return copy(completed = completed + levelId, best = if (now.beats(best[levelId])) best + (levelId to now) else best)
    }

    /** Indices of the optional goals of [levelId] that were achieved at least once. */
    fun extrasFor(levelId: String): Set<Int> =
        extras.filter { it.startsWith("$levelId/") }.mapNotNull { it.substringAfter('/').toIntOrNull() }.toSet()

    /** The progress without levels that no longer exist (an earlier level set); discoveries stay. */
    fun onlyLevels(levelIds: Set<String>): Progress =
        copy(
            completed = completed.filter { it in levelIds }.toSet(),
            extras = extras.filter { it.substringBefore('/') in levelIds }.toSet(),
            best = best.filterKeys { it in levelIds },
        )
}

class ProgressStore(context: Context) {
    // A new file: progress of the earlier level sets (and their discoveries) does not fit the test world.
    private val prefs = context.getSharedPreferences("react_progress_v3", Context.MODE_PRIVATE)

    init {
        for (old in OLD_FILES) context.getSharedPreferences(old, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun load(): Progress = Progress(
        completed = read(KEY_COMPLETED),
        extras = read(KEY_EXTRAS),
        discoveries = read(KEY_DISCOVERIES),
        best = read(KEY_BEST).mapNotNull(::parseBest).toMap(),
    )

    fun save(progress: Progress) {
        prefs.edit()
            .putStringSet(KEY_COMPLETED, progress.completed)
            .putStringSet(KEY_EXTRAS, progress.extras)
            .putStringSet(KEY_DISCOVERIES, progress.discoveries)
            .putStringSet(KEY_BEST, progress.best.map { (id, b) -> "$id|${b.medal.name}|${b.seconds}|${b.actions}" }.toSet())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun read(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    /** "id|MEDAL|seconds|actions"; anything else (an older or broken entry) is left out. */
    private fun parseBest(entry: String): Pair<String, LevelBest>? {
        val p = entry.split('|')
        if (p.size != 4) return null
        val medal = Medal.entries.firstOrNull { it.name == p[1] } ?: return null
        val seconds = p[2].toFloatOrNull() ?: return null
        val actions = p[3].toIntOrNull() ?: return null
        return p[0] to LevelBest(medal, seconds, actions)
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
        const val KEY_EXTRAS = "extras"
        const val KEY_DISCOVERIES = "discoveries"
        const val KEY_BEST = "best"

        /** Where earlier versions kept their progress; it is thrown away. */
        val OLD_FILES = listOf("react_progress", "react_progress_v2")
    }
}
