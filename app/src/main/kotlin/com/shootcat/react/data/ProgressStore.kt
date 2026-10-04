package com.shootcat.react.data

import android.content.Context

data class Progress(
    val completed: Set<String> = emptySet(),
    /** Optional tasks the player has managed, as "levelId/goalIndex". */
    val extras: Set<String> = emptySet(),
    /** Ids of rules the player has seen in action. */
    val discoveries: Set<String> = emptySet(),
) {
    /** Indices of the optional goals of [levelId] that were achieved at least once. */
    fun extrasFor(levelId: String): Set<Int> =
        extras.filter { it.startsWith("$levelId/") }.mapNotNull { it.substringAfter('/').toIntOrNull() }.toSet()

    /** The progress without levels that no longer exist (an earlier level set); discoveries stay. */
    fun onlyLevels(levelIds: Set<String>): Progress =
        copy(completed = completed.filter { it in levelIds }.toSet(), extras = extras.filter { it.substringBefore('/') in levelIds }.toSet())
}

class ProgressStore(context: Context) {
    // A new file: progress of the earlier level sets does not fit the new worlds.
    private val prefs = context.getSharedPreferences("react_progress_v2", Context.MODE_PRIVATE)

    fun load(): Progress = Progress(
        completed = read(KEY_COMPLETED),
        extras = read(KEY_EXTRAS),
        discoveries = read(KEY_DISCOVERIES),
    )

    fun save(progress: Progress) {
        prefs.edit()
            .putStringSet(KEY_COMPLETED, progress.completed)
            .putStringSet(KEY_EXTRAS, progress.extras)
            .putStringSet(KEY_DISCOVERIES, progress.discoveries)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun read(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    private companion object {
        const val KEY_COMPLETED = "completed"
        const val KEY_EXTRAS = "extras"
        const val KEY_DISCOVERIES = "discoveries"
    }
}
