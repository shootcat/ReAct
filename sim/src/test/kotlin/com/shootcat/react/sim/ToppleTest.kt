package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4c, bodies tip over in the game as they should: played with the session's own input, as the finger does
 * it on the device. A stroke the finger still rests on is not a body yet (made anew from the grid at every step it
 * stood square and still, and shook on the spot); a body leaning on something stays as it leans.
 */
class ToppleTest {

    private val mt = Lab.materials

    private fun session(w: Int = 80, h: Int = 64, build: (World) -> Unit = { world ->
        val rock = mt.id("rock")
        for (y in h - 2 until h) for (x in 0 until w) world.set(x, y, rock)
    }) = Session(mt, w, h, 7L, build).also { it.begin(Session.Supply.UNLIMITED) }

    /** A finger drawn slowly from (x0, y0) to (x1, y1), a move every second step, then resting [rest] steps. */
    private fun Session.slowStroke(x0: Int, y0: Int, x1: Int, y1: Int, tool: Int, radius: Int, rest: Int) {
        press(x0, y0, tool, radius)
        val n = maxOf(abs(x1 - x0), abs(y1 - y0))
        for (k in 1..n) {
            drag(x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n)
            tick()
            tick()
        }
        repeat(rest) { tick() }
        release()
    }

    @Test
    fun `a slanting stick falls flat within three seconds, however long the finger rested on it`() {
        for (rest in listOf(0, 10, 30, 60, 120)) {
            val s = session()
            s.slowStroke(30, 59, 42, 47, mt.id("wood"), 1, rest)
            assertEquals(0, s.world.bodyCount, "while the finger is on it, the stroke stays where it is drawn")
            repeat(180) { s.tick() }
            val b = s.world.bodyStates().single()
            assertTrue(abs(b.angle) > 35f && b.asleep, "finger resting $rest steps: it lies flat, turned ${b.angle}°, asleep ${b.asleep}")
        }
    }

    @Test
    fun `an upright stick stands, and falls when a second one falls against it`() {
        val s = session()
        val wood = mt.id("wood")
        s.slowStroke(40, 59, 40, 43, wood, 1, 10)
        repeat(60) { s.tick() }
        val upright = s.world.bodyStates().single()
        assertTrue(abs(upright.angle) < 1f && upright.asleep, "it stands: ${upright.angle}°")
        s.slowStroke(25, 59, 34, 45, wood, 1, 0)
        repeat(180) { s.tick() }
        val after = s.world.bodyStates().first { it.id == upright.id }
        assertTrue(abs(after.angle) > 60f, "pushed, it fell over: ${after.angle}°")
    }

    @Test
    fun `a stick leaning against a block rests as it leans and does not shake`() {
        // (against the rock of the world it would be built into it, as a beam in a cliff)
        val s = session()
        s.shape(Session.ShapeKind.RECT, 50, 30, 57, 61, mt.id("stone"), 1)
        repeat(30) { s.tick() }
        val block = s.world.bodyStates().single()
        // drawn leaning a little towards the block, not touching it
        s.slowStroke(44, 59, 47, 42, mt.id("wood"), 1, 0)
        repeat(120) { s.tick() }
        val b = s.world.bodyStates().first { it.id != block.id }
        assertTrue(b.asleep && abs(b.angle) > 3f && abs(b.angle) < 45f, "it fell against the block and rests there: ${b.angle}°")
        val angle = b.angle
        // grains of sand fall onto it one by one: each time it wakes, and lies down again where it was
        repeat(10) { k ->
            s.press(42 + k % 4, 20, mt.id("sand"), 0)
            s.release()
            repeat(40) { s.tick() }
        }
        val later = s.world.bodyStates().first { it.id == b.id }
        assertTrue(abs(later.angle - angle) < 1f, "it stays as it leans: ${angle}° then ${later.angle}°")
    }

    @Test
    fun `a log laid over a ramp tips onto it and lies along it`() {
        val s = session()
        // a wedge of stone in one stroke, lying flat on the floor
        val stone = mt.id("stone")
        s.press(13, 58, stone, 3)
        for ((a, b) in listOf((13 to 58) to (37 to 58), (37 to 58) to (37 to 44), (37 to 44) to (13 to 58), (13 to 58) to (37 to 51))) {
            val n = maxOf(abs(b.first - a.first), abs(b.second - a.second))
            for (k in 1..n) {
                s.drag(a.first + (b.first - a.first) * k / n, a.second + (b.second - a.second) * k / n)
                s.tick()
            }
        }
        s.release()
        repeat(60) { s.tick() }
        val wedge = s.world.bodyStates().single()
        assertTrue(wedge.asleep && abs(wedge.angle) < 1f, "the ramp lies still: ${wedge.angle}°")
        s.slowStroke(19, 44, 33, 44, mt.id("wood"), 1, 10)
        repeat(180) { s.tick() }
        val log = s.world.bodyStates().first { it.id != wedge.id }
        assertTrue(abs(log.angle) > 15f && log.asleep, "the log lies along the ramp: ${log.angle}°")
    }
}
