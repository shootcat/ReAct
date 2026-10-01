package com.shootcat.react.data

import android.content.Context

data class Progress(
    val completed: Set<String> = emptySet(),
    /** Found solution classes as "levelId/solutionId". */
    val solutions: Set<String> = emptySet(),
    /** Ids of rules the player has seen in action. */
    val discoveries: Set<String> = emptySet(),
) {
    fun solutionsFor(levelId: String): Set<String> =
        solutions.filter { it.startsWith("$levelId/") }.map { it.substringAfter('/') }.toSet()
}

class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("react_progress", Context.MODE_PRIVATE)

    fun load(): Progress = Progress(
        completed = read(KEY_COMPLETED),
        solutions = read(KEY_SOLUTIONS),
        discoveries = read(KEY_DISCOVERIES),
    )

    fun save(progress: Progress) {
        prefs.edit()
            .putStringSet(KEY_COMPLETED, progress.completed)
            .putStringSet(KEY_SOLUTIONS, progress.solutions)
            .putStringSet(KEY_DISCOVERIES, progress.discoveries)
            .apply()
    }

    private fun read(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    private companion object {
        const val KEY_COMPLETED = "completed"
        const val KEY_SOLUTIONS = "solutions"
        const val KEY_DISCOVERIES = "discoveries"
    }
}
