package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4b, how bodies feel: what is built in separate strokes stays apart (5), and a body whose weight is not
 * over what it stands on goes over, slowly at first, then faster, and comes to lie flat; round logs roll (4).
 */
class BodyFeelTest {

    private val mt = Lab.materials

    private fun session(w: Int, h: Int, build: (World) -> Unit = { world ->
        val rock = mt.id("rock")
        for (y in h - 2 until h) for (x in 0 until w) world.set(x, y, rock)
    }) = Session(mt, w, h, 7L, build).also { it.begin(Session.Supply.UNLIMITED) }

    // ------------------------------------------------------------------ 5. no merging

    @Test
    fun `blocks built in separate strokes stay separate bodies, side by side and stacked`() {
        val s = session(64, 64)
        val wood = mt.id("wood")
        s.shape(Session.ShapeKind.RECT, 20, 50, 30, 61, wood, 1)
        s.shape(Session.ShapeKind.RECT, 31, 55, 36, 61, wood, 1)
        s.shape(Session.ShapeKind.RECT, 22, 40, 28, 49, wood, 1)
        repeat(120) { s.tick() }
        val bodies = s.world.bodyStates()
        assertEquals(listOf(42, 70, 132), bodies.map { it.cells }.sorted(), "three bodies, as they were built")
        assertTrue(bodies.all { it.asleep }, "lying still")
        // the hand takes the top one alone
        s.press(25, 45, Session.HAND, 0)
        assertEquals(70, s.held?.mat?.size ?: 0, "only the top block is in the hand: ${s.notice}")
    }

    @Test
    fun `what one stroke paints is one body, even painted over several steps`() {
        val s = session(64, 64)
        val wood = mt.id("wood")
        s.press(20, 20, wood, 2)
        s.tick()
        s.drag(30, 20)
        s.tick()
        s.drag(40, 20)
        s.release()
        repeat(120) { s.tick() }
        assertEquals(1, s.world.bodyCount, "one stroke, one body")
    }

    @Test
    fun `a stroke built into the rock stands, a stroke laid on it stays loose`() {
        val s = session(64, 64) { w ->
            val rock = mt.id("rock")
            for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
            for (y in 30..61) for (x in 0..9) w.set(x, y, rock)
            for (y in 30..61) for (x in 54..63) w.set(x, y, rock)
        }
        val wood = mt.id("wood")
        // a bridge from wall to wall, a plank on it
        s.shape(Session.ShapeKind.RECT, 10, 40, 53, 41, wood, 1)
        s.shape(Session.ShapeKind.RECT, 25, 38, 34, 39, wood, 1)
        repeat(90) { s.tick() }
        assertEquals(1, s.world.bodyCount, "the bridge is built into the rock, the plank is a body")
        assertEquals(20, s.world.bodyStates()[0].cells)
        s.press(30, 38, Session.HAND, 0)
        assertEquals(20, s.held?.mat?.size ?: 0, "the hand lifts the plank off the bridge: ${s.notice}")
    }

    // ------------------------------------------------------------------ 4. tipping over

    @Test
    fun `a square on its corner does not balance there, it falls onto a side`() {
        val lab = Lab()
        val stone = lab.id("stone")
        for (y in -6..6) for (x in -6..6) if (abs(x) + abs(y) <= 6) lab.world.set(32 + x, 55 + y, stone)
        lab.seconds(5f)
        val b = lab.world.bodyStates().single()
        assertTrue(abs(abs(b.angle) - 45f) < 3f, "it lies on a side (turned by 45°): ${b.angle}°")
        assertTrue(b.asleep)
        lab.picture("feel_diamond")
    }

    @Test
    fun `a post stands, pushed it goes over slowly at first, then faster, and lies flat`() {
        val lab = Lab(96, 64)
        lab.rect("deadwood", 40, 30, 41, 61)
        lab.seconds(1f)
        assertEquals(0f, lab.world.bodyStates().single().angle, "upright, it stands")
        // a pebble thrown against its top
        val cells = IntArray(4)
        var k = 0
        for (y in 31..32) for (x in 36..37) {
            lab.world.set(x, y, lab.id("stone"))
            cells[k++] = lab.world.index(x, y)
        }
        lab.world.bodies.create(cells, 4, 1.5f, 0f)
        val post = lab.world.bodyStates().first { it.material == lab.id("deadwood") }.id
        var t10 = -1
        var t45 = -1
        var t80 = -1
        repeat(240) { t ->
            lab.world.step()
            val a = abs(lab.world.bodyStates().firstOrNull { it.id == post }?.angle ?: 90f)
            if (t10 < 0 && a >= 10f) t10 = t
            if (t45 < 0 && a >= 45f) t45 = t
            if (t80 < 0 && a >= 80f) t80 = t
        }
        lab.picture("feel_post_pushed")
        println("pushed post: 10° after $t10, 45° after $t45, 80° after $t80 steps")
        assertTrue(t10 > 0 && t45 > t10 && t80 > t45, "it went over: $t10, $t45, $t80")
        assertTrue(t45 - t10 > t80 - t45, "slowly at first, then faster: ${t45 - t10} steps from 10° to 45°, ${t80 - t45} from 45° to 80°")
        val wood = (0 until lab.world.size).filter { lab.world.mat[it].toInt() == lab.id("deadwood") }.map { it / lab.width }
        assertTrue(wood.max() - wood.min() <= 2, "it lies flat: rows ${wood.min()}..${wood.max()}")
    }

    @Test
    fun `a round log rolls down the slope and rolls out on the flat`() {
        val lab = Lab(320, 80)
        // a slope of rock down to x 60, flat after it
        for (x in 2 until 318) {
            val y = if (x < 60) 20 + (x - 2) / 2 else 49
            lab.rect("rock", x, y, x, 77)
        }
        val log = lab.id("deadwood")
        val r = 4
        val cx = 10
        val cy = 20 + (cx - 2) / 2 - r - 2
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r + r) lab.world.set(x, y, log)
        var turned = 0f
        repeat(900) {
            lab.world.step()
            lab.world.bodyStates().forEach { turned = maxOf(turned, abs(it.angle)) }
        }
        val b = lab.world.bodyStates().single()
        lab.picture("feel_log_rolled")
        println("rolled log: at x ${b.x}, turned $turned°")
        assertTrue(b.x > 90f, "it rolled on well past the foot of the slope: x ${b.x}")
        assertTrue(turned > 720f, "rolling, not sliding: turned $turned°")
        assertTrue(b.asleep, "and came to rest")
    }

    @Test
    fun `a post stood in a slot one cell wide stays upright`() {
        val lab = Lab()
        // two blocks of rock with a slot between them, three deep
        lab.rect("rock", 20, 59, 29, 61)
        lab.rect("rock", 31, 59, 40, 61)
        lab.rect("metal", 30, 50, 30, 58)
        lab.seconds(4f)
        // wedged in the rock it is part of the world again, standing upright
        val metal = (0 until lab.world.size).filter { lab.world.mat[it].toInt() == lab.id("metal") }
        assertEquals((53..61).map { lab.world.index(30, it) }, metal.sorted(), "upright in the slot")
    }
}
