package com.shootcat.react.data

import android.content.Context

enum class Speed(val label: String, val stepMillis: Long) {
    SLOW("Langsam", 480L),
    NORMAL("Normal", 300L),
    FAST("Schnell", 170L),
}

data class Settings(
    val speed: Speed = Speed.NORMAL,
    /** While dragging, the target shows what would happen: merge, reaction or bounce. */
    val reactionPreview: Boolean = true,
    /** A light frame around everything the player may move. */
    val markers: Boolean = true,
    /** Short level descriptions (off by default for immersion). */
    val levelTexts: Boolean = false,
    val haptics: Boolean = true,
    /** Sound effects of the world. */
    val sound: Boolean = true,
    /** The quiet melody in the background. */
    val music: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("react_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            speed = Speed.entries.firstOrNull { it.name == prefs.getString(KEY_SPEED, null) } ?: defaults.speed,
            reactionPreview = prefs.getBoolean(KEY_PREVIEW, defaults.reactionPreview),
            markers = prefs.getBoolean(KEY_MARKERS, defaults.markers),
            levelTexts = prefs.getBoolean(KEY_LEVEL_TEXTS, defaults.levelTexts),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            sound = prefs.getBoolean(KEY_SOUND, defaults.sound),
            music = prefs.getBoolean(KEY_MUSIC, defaults.music),
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_SPEED, settings.speed.name)
            .putBoolean(KEY_PREVIEW, settings.reactionPreview)
            .putBoolean(KEY_MARKERS, settings.markers)
            .putBoolean(KEY_LEVEL_TEXTS, settings.levelTexts)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_SOUND, settings.sound)
            .putBoolean(KEY_MUSIC, settings.music)
            .apply()
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_PREVIEW = "reaction_preview"
        const val KEY_MARKERS = "markers"
        const val KEY_LEVEL_TEXTS = "level_texts"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
    }
}
