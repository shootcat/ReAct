package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Leitfaden V3, 4.1.6: the same seed and the same actions give exactly the same world, every time. This is
 * what undo, tests and best scores rely on.
 */
class DeterminismTest {

    private val materials = MaterialTable.load()
    private val build: (World) -> Unit = { Terrain.sandbox(it) }

    private fun session() = Session(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED, build)

    /** A few seconds of play: fire on the bridge, water poured and dragged, sand, lava into the pond. */
    private fun play(s: Session) {
        val fire = materials.id("fire")
        val water = materials.id("water")
        val sand = materials.id("sand")
        val lava = materials.id("lava")
        repeat(30) { s.tick() }
        s.press(100, 150, fire, 3)
        repeat(8) { s.tick() }
        s.release()
        repeat(60) { s.tick() }
        s.press(40, 40, water, 6)
        for (k in 0 until 20) {
            s.drag(40 + k * 2, 40 + k)
            s.tick()
        }
        s.release()
        s.press(160, 30, sand, 5)
        repeat(30) { s.tick() }
        s.release()
        s.press(110, 120, lava, 4)
        s.tick()
        s.release()
        repeat(200) { s.tick() }
    }

    @Test
    fun `the same actions give the same world`() {
        val a = session().also(::play)
        val b = session().also(::play)
        assertEquals(a.world.step, b.world.step)
        assertEquals(a.world.hash(), b.world.hash())
        assertNotEquals(session().world.hash(), a.world.hash(), "and something did happen")
    }

    @Test
    fun `playing back the log gives the same world`() {
        val a = session().also(::play)
        val b = Session.replay(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED, build, a.log, a.world.step)
        assertEquals(a.world.hash(), b.world.hash())
    }

    @Test
    fun `a different seed gives a different world`() {
        val a = session().also(::play)
        val b = Session(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED + 1, build).also(::play)
        assertNotEquals(a.world.hash(), b.world.hash())
    }

    @Test
    fun `undo returns exactly to the state before the action`() {
        val s = session()
        repeat(50) { s.tick() }
        val before = s.world.hash()
        s.press(100, 150, materials.id("fire"), 4)
        repeat(120) { s.tick() }
        s.release()
        assertNotEquals(before, s.world.hash())
        assertTrue(s.undo())
        assertEquals(before, s.world.hash())
    }

    @Test
    fun `after an undo the world goes on as if the action had never happened`() {
        val water = materials.id("water")
        val sand = materials.id("sand")
        // with an action that is taken back ...
        val a = session()
        repeat(40) { a.tick() }
        a.press(60, 60, water, 5)
        repeat(30) { a.tick() }
        a.release()
        a.undo()
        a.press(150, 40, sand, 4)
        repeat(100) { a.tick() }
        a.release()
        repeat(100) { a.tick() }
        // ... and without it
        val b = session()
        repeat(40) { b.tick() }
        b.press(150, 40, sand, 4)
        repeat(100) { b.tick() }
        b.release()
        repeat(100) { b.tick() }
        assertEquals(b.world.hash(), a.world.hash())
        // and the log of the first is the straight history of its world
        val c = Session.replay(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED, build, a.log, a.world.step)
        assertEquals(a.world.hash(), c.world.hash())
    }

    @Test
    fun `reset goes back to the start, and can be undone`() {
        val s = session()
        val start = s.world.hash()
        play(s)
        val played = s.world.hash()
        s.reset()
        assertEquals(start, s.world.hash())
        s.undo()
        assertEquals(played, s.world.hash())
    }

    @Test
    fun `drawing the picture does not change the world`() {
        val a = session().also(::play)
        val b = session()
        val renderer = Renderer(b.world, Backdrop.of(b.world))
        val pixels = IntArray(b.world.size)
        val haze = IntArray(renderer.layerWidth * renderer.layerHeight)
        val light = IntArray(renderer.layerWidth * renderer.layerHeight)
        // as if drawn at a high frame rate, after every step
        val drawn = object {
            fun tick() {
                b.tick()
                renderer.render(pixels, b.world.step)
                renderer.renderLayers(haze, light)
            }
        }
        // the same play, with a picture after every step
        val fire = materials.id("fire")
        val water = materials.id("water")
        val sand = materials.id("sand")
        val lava = materials.id("lava")
        repeat(30) { drawn.tick() }
        b.press(100, 150, fire, 3)
        repeat(8) { drawn.tick() }
        b.release()
        repeat(60) { drawn.tick() }
        b.press(40, 40, water, 6)
        for (k in 0 until 20) {
            b.drag(40 + k * 2, 40 + k)
            drawn.tick()
        }
        b.release()
        b.press(160, 30, sand, 5)
        repeat(30) { drawn.tick() }
        b.release()
        b.press(110, 120, lava, 4)
        drawn.tick()
        b.release()
        repeat(200) { drawn.tick() }
        assertEquals(a.world.hash(), b.world.hash())
    }

    @Test
    fun `the simulation thread gives the same world as stepping by hand`() {
        val a = session()
        repeat(240) { a.tick() }
        val b = session()
        val runner = SimRunner(b, Backdrop.of(b.world))
        runner.speed = SimRunner.Speed.FAST
        runner.start()
        // wait until the thread has run past 240 steps, then stop it exactly there by pausing
        while (runner.runBlocking { it.world.step } < 200) Thread.sleep(5)
        runner.speed = SimRunner.Speed.PAUSE
        val reached = runner.runBlocking { it.world.step }
        runner.runBlocking { s -> while (s.world.step < maxOf(240, reached)) s.tick() }
        runner.stop()
        val c = session()
        repeat(b.world.step) { c.tick() }
        assertEquals(c.world.hash(), b.world.hash())
        if (b.world.step == 240) assertEquals(a.world.hash(), b.world.hash())
    }
}
