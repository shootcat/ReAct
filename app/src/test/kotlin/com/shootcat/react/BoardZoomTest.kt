package com.shootcat.react

import androidx.compose.ui.geometry.Offset
import com.shootcat.react.ui.level.BoardLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Two-finger zoom on the board: what stays under the fingers, how far it goes, and that no empty edge opens. */
class BoardZoomTest {

    private val width = 1000f
    private val height = 1400f
    private val columns = 15
    private val rows = 17
    private val whole = BoardLayout.of(width, height, columns, rows)

    private fun BoardLayout.gridAt(p: Offset) = (p - origin) / cell

    @Test
    fun `the point between the fingers stays under them`() {
        val focus = Offset(500f, 700f)
        val zoomed = whole.transformed(2f, focus, Offset.Zero, width, height, columns, rows)
        assertEquals(whole.cell * 2f, zoomed.cell, 0.01f)
        assertEquals(whole.gridAt(focus).x, zoomed.gridAt(focus).x, 0.01f)
        assertEquals(whole.gridAt(focus).y, zoomed.gridAt(focus).y, 0.01f)
    }

    @Test
    fun `zoom stays between the whole grid and the maximum`() {
        val focus = Offset(500f, 700f)
        assertEquals(whole, whole.transformed(0.5f, focus, Offset.Zero, width, height, columns, rows))
        val far = whole.transformed(10f, focus, Offset.Zero, width, height, columns, rows)
        assertEquals(whole.cell * BoardLayout.MAX_ZOOM, far.cell, 0.01f)
        // Pinching back out returns to exactly the whole grid.
        assertEquals(whole, far.transformed(0.1f, focus, Offset.Zero, width, height, columns, rows))
    }

    @Test
    fun `panning never opens an empty edge`() {
        val zoomed = whole.transformed(2f, Offset(500f, 700f), Offset.Zero, width, height, columns, rows)
        val left = zoomed.transformed(1f, Offset(500f, 700f), Offset(5000f, 0f), width, height, columns, rows)
        assertEquals(0f, left.origin.x, 0.01f)
        val right = zoomed.transformed(1f, Offset(500f, 700f), Offset(-5000f, 0f), width, height, columns, rows)
        assertEquals(width - right.cell * columns, right.origin.x, 0.01f)
    }

    @Test
    fun `a zoomed view follows a smaller canvas`() {
        val zoomed = whole.transformed(3f, Offset(1000f, 1400f), Offset.Zero, width, height, columns, rows)
        val refit = zoomed.transformed(1f, Offset.Zero, Offset.Zero, 600f, 800f, columns, rows)
        val smallWhole = BoardLayout.of(600f, 800f, columns, rows)
        assert(refit.cell <= smallWhole.cell * BoardLayout.MAX_ZOOM + 0.01f)
        assert(refit.origin.x >= 600f - refit.cell * columns - 0.01f && refit.origin.x <= 0.01f)
    }
}
