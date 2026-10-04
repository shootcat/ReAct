package com.shootcat.react.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Progress and settings must survive a restart of the app (a new store reading the same preferences). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressStoreTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun progressSurvivesARestart() {
        val progress = Progress(
            completed = setOf("w1_01", "w1_02"),
            extras = setOf("w1_01/1", "w1_02/2"),
            discoveries = setOf("heat_melts_ice", "water_douses_fire"),
        )
        ProgressStore(app).save(progress)
        assertEquals(progress, ProgressStore(app).load())
        assertEquals(setOf(1), ProgressStore(app).load().extrasFor("w1_01"))
    }

    @Test
    fun clearForgetsEverything() {
        ProgressStore(app).save(Progress(completed = setOf("w1_01")))
        ProgressStore(app).clear()
        assertEquals(Progress(), ProgressStore(app).load())
    }

    @Test
    fun settingsSurviveARestart() {
        val settings = Settings(speed = Speed.FAST, markers = false, levelTexts = true, haptics = false, sound = false, music = false)
        SettingsStore(app).save(settings)
        assertEquals(settings, SettingsStore(app).load())
    }
}
