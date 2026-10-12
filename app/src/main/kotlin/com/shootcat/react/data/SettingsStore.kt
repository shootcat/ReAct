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
    /** The landscape and size a new sandbox world starts from (Leitfaden V3, 4a.5): names of Template and WorldSize. */
    val template: String = "MEADOW",
    val worldSize: String = "MEDIUM",
    /** The bar of materials and tools is folded away. */
    val barFolded: Boolean = false,
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
            template = prefs.getString(KEY_TEMPLATE, defaults.template) ?: defaults.template,
            worldSize = prefs.getString(KEY_SIZE, defaults.worldSize) ?: defaults.worldSize,
            barFolded = prefs.getBoolean(KEY_FOLDED, defaults.barFolded),
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_SOUND, settings.sound)
            .putBoolean(KEY_MUSIC, settings.music)
            .putBoolean(KEY_PERFORMANCE, settings.performance)
            .putInt(KEY_BRUSH, settings.brush)
            .putString(KEY_TEMPLATE, settings.template)
            .putString(KEY_SIZE, settings.worldSize)
            .putBoolean(KEY_FOLDED, settings.barFolded)
            .apply()
    }

    private companion object {
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
        const val KEY_PERFORMANCE = "performance"
        const val KEY_BRUSH = "brush"
        const val KEY_TEMPLATE = "template"
        const val KEY_SIZE = "world_size"
        const val KEY_FOLDED = "bar_folded"
    }
}
