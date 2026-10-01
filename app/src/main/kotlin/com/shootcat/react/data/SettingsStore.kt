package com.shootcat.react.data

import android.content.Context

enum class Speed(val label: String, val stepMillis: Long) {
    SLOW("Langsam", 650L),
    NORMAL("Normal", 420L),
    FAST("Schnell", 220L),
}

data class Settings(
    val speed: Speed = Speed.NORMAL,
    /** Subtle glow on objects that could react with the one being dragged. */
    val reactionPreview: Boolean = true,
    /** Dashed markers on objects the player may move. */
    val markers: Boolean = true,
    /** Symbol row under the board that shows what happened in the current step. */
    val stepDetails: Boolean = true,
    /** Short level descriptions (off by default for immersion). */
    val levelTexts: Boolean = false,
    val haptics: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("react_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            speed = Speed.entries.firstOrNull { it.name == prefs.getString(KEY_SPEED, null) } ?: defaults.speed,
            reactionPreview = prefs.getBoolean(KEY_PREVIEW, defaults.reactionPreview),
            markers = prefs.getBoolean(KEY_MARKERS, defaults.markers),
            stepDetails = prefs.getBoolean(KEY_STEP_DETAILS, defaults.stepDetails),
            levelTexts = prefs.getBoolean(KEY_LEVEL_TEXTS, defaults.levelTexts),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_SPEED, settings.speed.name)
            .putBoolean(KEY_PREVIEW, settings.reactionPreview)
            .putBoolean(KEY_MARKERS, settings.markers)
            .putBoolean(KEY_STEP_DETAILS, settings.stepDetails)
            .putBoolean(KEY_LEVEL_TEXTS, settings.levelTexts)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .apply()
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_PREVIEW = "reaction_preview"
        const val KEY_MARKERS = "markers"
        const val KEY_STEP_DETAILS = "step_details"
        const val KEY_LEVEL_TEXTS = "level_texts"
        const val KEY_HAPTICS = "haptics"
    }
}
