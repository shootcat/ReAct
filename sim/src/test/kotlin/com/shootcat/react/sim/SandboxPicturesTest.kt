package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertTrue

/** Pictures of the sandbox start and of a little play, for the report (build/pictures). */
class SandboxPicturesTest {

    @Test
    fun `sandbox pictures`() {
        val materials = MaterialTable.load()
        val s = Session(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED) { Terrain.sandbox(it) }
        val backdrop = Backdrop.of(s.world)
        repeat(120) { s.tick() }
        Pictures.write(s.world, backdrop, "sandbox_start")
        // fire on the logs and the bridge, lava into the pond, sand on the dune, water on the hill
        s.press(36, 112, materials.id("fire"), 4); repeat(12) { s.tick() }; s.release()
        s.press(100, 150, materials.id("fire"), 4); repeat(12) { s.tick() }; s.release()
        repeat(240) { s.tick() }
        Pictures.write(s.world, backdrop, "sandbox_fire", frame = 1)
        s.press(115, 120, materials.id("lava"), 5); repeat(40) { s.tick() }; s.release()
        s.press(20, 60, materials.id("water"), 6); repeat(60) { s.tick() }; s.release()
        repeat(120) { s.tick() }
        Pictures.write(s.world, backdrop, "sandbox_lava", frame = 2)
        repeat(600) { s.tick() }
        Pictures.write(s.world, backdrop, "sandbox_later", frame = 3)
        assertTrue(s.world.count(materials.id("stone")) > 0)
    }
}
