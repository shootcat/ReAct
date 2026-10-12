package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Procuring (Leitfaden V3, 4.3): bucket, hand, store, building from what was procured. */
class SupplyTest {

    private val materials = MaterialTable.load()

    /** A session on a 64 × 64 lab with a frame of rock, built by [build]. */
    private fun session(build: (World, MaterialTable) -> Unit): Session = Session(materials, 64, 64, 9L) { w ->
        val rock = materials.id("rock")
        for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
        for (y in 0..61) for (x in listOf(0, 1, 62, 63)) w.set(x, y, rock)
        build(w, materials)
    }

    private fun Session.count(name: String) = world.count(materials.id(name))
    private fun Session.fill(name: String, x0: Int, y0: Int, x1: Int, y1: Int, t: Float = Float.NaN) {
        val m = materials.id(name)
        for (y in y0..y1) for (x in x0..x1) world.set(x, y, m, t)
    }
    private fun Session.run(seconds: Float) = repeat((seconds * 60).toInt()) { tick() }
    private fun Session.tap(x: Int, y: Int, tool: Int, radius: Int = 0) {
        press(x, y, tool, radius)
        release()
    }

    @Test
    fun `the bucket takes one kind of loose or liquid material, as much as fits, and pours it out elsewhere`() {
        val s = session { _, _ -> }
        s.fill("sand", 2, 50, 30, 61)
        s.fill("water", 34, 50, 61, 61)
        s.run(1f)
        val sand = s.count("sand")
        s.bucketSize = 120
        s.press(15, 52, Session.SCOOP, 6)
        s.drag(20, 52)
        s.drag(40, 52) // over the water: the bucket holds sand, it takes no water
        s.release()
        assertEquals(materials.id("sand"), s.bucketMaterial)
        assertEquals(120, s.bucketAmount, "a full bucket")
        assertEquals(sand - 120, s.count("sand"))
        s.press(45, 20, Session.POUR, 4)
        s.run(3f)
        s.release()
        assertEquals(0, s.bucketAmount, "poured out")
        assertEquals(sand, s.count("sand"), "nothing lost")
    }

    @Test
    fun `the bucket takes no fire, lava, molten metal, gas, rock or structure`() {
        for (name in listOf("lava", "molten_metal", "steam", "smoke", "fire", "ember", "wood", "stone", "rock")) {
            val s = session { _, _ -> }
            s.fill(name, 20, 54, 40, 61)
            s.tap(30, 58, Session.SCOOP, 4)
            assertEquals(0, s.bucketAmount, "$name in the bucket")
        }
    }

    @Test
    fun `the bucket keeps the heat of what is in it and cools slowly, pouring mixes by the normal rules`() {
        val s = session { _, _ -> }
        s.fill("sand", 2, 56, 20, 61, 450f)
        s.tap(10, 58, Session.SCOOP, 3)
        assertTrue(s.bucketAmount > 10)
        assertTrue(s.bucketTemp > 400f, "hot sand in the bucket: ${s.bucketTemp}")
        s.run(5f)
        assertTrue(s.bucketTemp > 300f, "after 5 seconds still hot: ${s.bucketTemp}")
        s.press(44, 40, Session.POUR, 3)
        s.run(1f)
        s.release()
        val sand = materials.id("sand")
        var hottest = 0f
        for (i in 0 until s.world.size) if (s.world.mat[i].toInt() == sand && i % 64 > 30) hottest = maxOf(hottest, s.world.temp[i])
        assertTrue(hottest > 280f, "the sand comes out hot: $hottest")

        // water from the bucket onto lava: it boils, the lava sets to basalt
        val pan = session { _, _ -> }
        pan.fill("water", 2, 56, 20, 61)
        pan.fill("lava", 34, 58, 61, 61)
        pan.tap(10, 58, Session.SCOOP, 4)
        assertEquals(materials.id("water"), pan.bucketMaterial)
        pan.press(47, 50, Session.POUR, 4)
        pan.run(2f)
        pan.release()
        pan.run(3f)
        assertTrue(pan.world.events[Event.BOIL] > 0 && pan.count("basalt") > 0, "boiled and set: ${pan.count("basalt")} basalt")
    }

    @Test
    fun `the hand picks up a loose object and puts it down elsewhere, where it falls`() {
        val s = session { _, _ -> }
        s.fill("trunk", 10, 59, 25, 61)
        val trunk = s.count("trunk")
        s.tap(17, 60, Session.HAND)
        val h = assertNotNull(s.held, s.notice)
        assertEquals(trunk, h.size)
        assertEquals(0, s.count("trunk"))
        s.tap(45, 20, Session.HAND)
        assertNull(s.held, s.notice)
        assertEquals(trunk, s.count("trunk"))
        s.run(3f)
        assertEquals("trunk", materials.spec(s.world.material(45, 60)).id, "it fell to the floor")
    }

    @Test
    fun `the hand leaves what is anchored, too heavy or too hot`() {
        val anchored = session { _, _ -> }
        anchored.fill("stone", 2, 30, 12, 32)
        anchored.tap(6, 31, Session.HAND)
        assertNull(anchored.held, "stone on the rock wall")
        assertTrue("verankert" in anchored.notice, anchored.notice)

        val heavy = session { _, _ -> }
        heavy.fill("metal", 20, 54, 29, 61)
        heavy.tap(25, 58, Session.HAND)
        assertNull(heavy.held, "80 cells of metal")
        assertEquals("Zu schwer", heavy.notice)

        val hot = session { _, _ -> }
        hot.fill("metal", 20, 59, 22, 61, 300f)
        hot.tap(21, 60, Session.HAND)
        assertNull(hot.held, "hot metal")
        assertTrue("heiß" in hot.notice, hot.notice)

        val bar = session { _, _ -> }
        bar.fill("metal", 20, 60, 25, 61)
        bar.tap(22, 61, Session.HAND)
        assertNotNull(bar.held, "a small bar of metal: ${bar.notice}")
    }

    @Test
    fun `the bucket pours and the hand puts down into smoke and steam, not into fire`() {
        val s = session { _, _ -> }
        s.fill("water", 2, 56, 20, 61)
        s.fill("trunk", 24, 59, 29, 61)
        s.fill("rock", 34, 30, 61, 31)
        // a cave roof with smoke gathered under it
        s.fill("smoke", 34, 32, 61, 45)
        s.tap(10, 58, Session.SCOOP, 3)
        val water = s.bucketAmount
        assertTrue(water > 10)
        s.tap(45, 36, Session.POUR, 3)
        assertTrue(s.bucketAmount < water, "poured into the smoke: ${s.bucketAmount} of $water left")
        s.tap(26, 60, Session.HAND)
        assertNotNull(s.held, s.notice)
        s.tap(55, 34, Session.HAND)
        assertNull(s.held, "put down in the smoke: ${s.notice}")

        val hot = session { _, _ -> }
        hot.fill("trunk", 24, 59, 29, 61)
        hot.fill("fire", 34, 40, 61, 61)
        hot.tap(26, 60, Session.HAND)
        hot.tap(48, 50, Session.HAND)
        assertNotNull(hot.held, "not into the flames")
        assertEquals("Kein Platz zum Ablegen", hot.notice)
    }

    @Test
    fun `a log in the hand gives planks, and in procuring the store is what can be built`() {
        val s = session { _, _ -> }
        s.fill("trunk", 10, 60, 19, 61)
        s.setSupply(Session.Supply.PROCURE)
        // nothing in store: no building, no sand from nowhere
        s.press(40, 30, materials.id("wood"), 3)
        s.release()
        s.press(40, 30, materials.id("sand"), 3)
        s.release()
        assertEquals(0, s.count("wood") + s.count("sand"))
        s.tap(15, 60, Session.HAND)
        s.store()
        val wood = materials.id("wood")
        assertEquals(20 * 3, s.stock[wood], "20 cells of log give 60 planks")
        s.shape(Session.ShapeKind.LINE, 10, 30, 60, 30, wood, 1)
        assertEquals(60, s.count("wood"), "the line uses up the store")
        assertEquals(0, s.stock[wood])
        s.undo()
        assertEquals(60, s.stock[wood], "undo gives the planks back")
        s.undo()
        assertNotNull(s.held, "and the log is back in the hand")
    }

    @Test
    fun `procuring plays back the same`() {
        fun build(w: World) {
            val rock = materials.id("rock")
            for (y in 62..63) for (x in 0..63) w.set(x, y, rock)
            w.paintRect(materials.id("sand"), 4, 50, 30, 61, false, 1)
            w.paintRect(materials.id("trunk"), 40, 59, 50, 61, false, 1)
        }
        val a = Session(materials, 64, 64, 3L) { build(it) }
        a.setSupply(Session.Supply.PROCURE)
        repeat(10) { a.tick() }
        a.press(10, 52, Session.SCOOP, 4)
        repeat(5) { a.tick() }
        a.release()
        a.press(20, 20, Session.POUR, 2)
        repeat(30) { a.tick() }
        a.release()
        a.tap(45, 60, Session.HAND)
        a.store()
        a.shape(Session.ShapeKind.RECT, 30, 20, 40, 24, materials.id("wood"), 1)
        repeat(60) { a.tick() }
        val b = Session.replay(materials, 64, 64, 3L, { build(it) }, a.log, a.world.step)
        assertEquals(a.world.hash(), b.world.hash())
        assertEquals(a.stock.toList(), b.stock.toList())
        assertEquals(a.bucketAmount, b.bucketAmount)
    }
}
