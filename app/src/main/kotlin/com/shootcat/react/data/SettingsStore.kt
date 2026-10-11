package com.shootcat.react.data

import android.content.Context

data class Settings(
    val haptics: Boolean = true,
    /** Sound effects of the world. */
    val sound: Boolean = true,
    /** The quiet melody in the background. */
    val music: Boolean = true,
    /** Steps per second, timings and awake chunks over the world. */
    val performance: Boolean = false,
    /** Thickness of the brush in cells. */
    val brush: Int = 3,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("react_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            sound = prefs.getBoolean(KEY_SOUND, defaults.sound),
            music = prefs.getBoolean(KEY_MUSIC, defaults.music),
            performance = prefs.getBoolean(KEY_PERFORMANCE, defaults.performance),
            brush = prefs.getInt(KEY_BRUSH, defaults.brush),
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_SOUND, settings.sound)
            .putBoolean(KEY_MUSIC, settings.music)
            .putBoolean(KEY_PERFORMANCE, settings.performance)
            .putInt(KEY_BRUSH, settings.brush)
            .apply()
    }

    private companion object {
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
        const val KEY_PERFORMANCE = "performance"
        const val KEY_BRUSH = "brush"
    }
}
