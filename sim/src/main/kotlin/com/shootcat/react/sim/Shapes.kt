package com.shootcat.react.sim

import kotlin.math.abs

/** The cells of the building shapes (Leitfaden V3, 4.1): the same for drawing the preview and for building. */
object Shapes {

    /** Every cell on the straight line from (x0, y0) to (x1, y1), both ends included (Bresenham). */
    inline fun line(x0: Int, y0: Int, x1: Int, y1: Int, cell: (Int, Int) -> Unit) {
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var x = x0
        var y = y0
        while (true) {
            cell(x, y)
            if (x == x1 && y == y1) return
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    /** Every cell of the rectangle between two corners, or only its frame [thickness] cells wide. */
    inline fun rect(x0: Int, y0: Int, x1: Int, y1: Int, frame: Boolean, thickness: Int, cell: (Int, Int) -> Unit) {
        val left = minOf(x0, x1)
        val right = maxOf(x0, x1)
        val top = minOf(y0, y1)
        val bottom = maxOf(y0, y1)
        val t = thickness.coerceAtLeast(1)
        for (y in top..bottom) for (x in left..right) {
            if (!frame || x < left + t || x > right - t || y < top + t || y > bottom - t) cell(x, y)
        }
    }
}
