package com.shootcat.react.data

import com.shootcat.react.level.Medal
import com.shootcat.react.level.Result
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
            best = mapOf("w1_01" to LevelBest(Medal.SILVER, 47.25f, 6)),
        )
        ProgressStore(app).save(progress)
        assertEquals(progress, ProgressStore(app).load())
        assertEquals(setOf(1), ProgressStore(app).load().extrasFor("w1_01"))
    }

    @Test
    fun aLevelKeepsItsBestResult() {
        val p = Progress()
            .solved("signalfeuer", Result(150f, 12, Medal.SILVER))
            .solved("signalfeuer", Result(200f, 30, Medal.BRONZE))
            .solved("signalfeuer", Result(120f, 10, Medal.SILVER))
        assertEquals(setOf("signalfeuer"), p.completed)
        assertEquals(LevelBest(Medal.SILVER, 120f, 10), p.best["signalfeuer"])
        val gold = p.solved("signalfeuer", Result(80f, 8, Medal.GOLD))
        assertEquals(Medal.GOLD, gold.best["signalfeuer"]?.medal)
        ProgressStore(app).save(gold)
        assertEquals(gold, ProgressStore(app).load())
    }

    @Test
    fun clearForgetsEverything() {
        ProgressStore(app).save(Progress(completed = setOf("w1_01")))
        ProgressStore(app).clear()
        assertEquals(Progress(), ProgressStore(app).load())
    }

    @Test
    fun settingsSurviveARestart() {
        val settings = Settings(haptics = false, sound = false, music = false, performance = true, brush = 10)
        SettingsStore(app).save(settings)
        assertEquals(settings, SettingsStore(app).load())
    }
}
