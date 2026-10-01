package com.shootcat.react

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Starts the real app on the JVM, plays level 0 like a player would and stores screenshots
 * of every screen in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun playLevelZeroThroughTheUi() {
        compose.onNodeWithText("REACT").assertExists()
        compose.onNodeWithText("Schmelzpunkt").assertExists()
        shot("1_weltkarte")

        compose.onNodeWithTag("level_level_00").performClick()
        compose.onNodeWithText("Ziel: Tür offen").assertExists()
        shot("2_level0_aufbau")

        // Tap the fire, then the free cell left of the ice block (level 0 is 8 cells wide).
        compose.onNodeWithTag("board").performTouchInput {
            val cell = width / 8f
            click(Offset(cell * 2.5f, cell * 4.5f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("board").performTouchInput {
            val cell = width / 8f
            click(Offset(cell * 5.5f, cell * 4.5f))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Verschoben: 1 Objekt").assertExists()
        shot("3_level0_feuer_verschoben")

        compose.onNodeWithText("Start").performClick()
        advance(millis = 500)
        shot("4_level0_simulation")

        advance(millis = 4000)
        compose.onNodeWithText("Level geschafft!").assertExists()
        compose.onNodeWithText("Standard-Weg").assertExists()
        shot("5_level_geschafft", compose.onAllNodes(isRoot()).onLast())

        compose.onNodeWithText("Weiter experimentieren").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Schritt 2 / 2").assertExists()
        shot("6_level0_zeitleiste")

        compose.onNodeWithTag("discoveries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Feuer + Eis → Wasser").assertExists()
        shot("7_entdeckungen")
    }

    /** Lets coroutine delays (playback, toasts) and animations run for [millis] of virtual time. */
    private fun advance(millis: Long) {
        var left = millis
        while (left > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            left -= 100
        }
    }

    private fun shot(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        compose.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
