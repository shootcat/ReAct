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

/** All materials side by side, as the renderer draws them (for the report). */
class MaterialPicturesTest {
    @Test
    fun `every material at a glance`() {
        val materials = MaterialTable.load()
        val names = listOf(
            "rock", "ore", "stone", "rubble", "ore_rubble", "gravel",
            "sand", "earth", "clay", "salt", "snow", "ice",
            "water", "saltwater", "lava", "molten_metal", "wood", "brick",
            "glass", "metal", "charcoal", "ember", "ash", "fire",
            "grass", "moss", "vine", "seed", "trunk", "leaves",
            "deadwood", "dead_plant", "fish", "firefly", "worm", "bird",
            // basalt beside stone and brick: set lava must not look like either (4a.1.3)
            "basalt", "stone", "brick", "dead_fish", "steam", "smoke",
            "glass_shards", "glass", "gravel", "snow", "ice", "clay",
        )
        val cols = 6
        val cell = 14
        val world = World(cols * cell, (names.size / cols) * cell, materials, 3L)
        for ((k, name) in names.withIndex()) {
            val m = materials.id(name)
            val x0 = (k % cols) * cell
            val y0 = (k / cols) * cell
            for (y in y0 + 1 until y0 + cell - 1) for (x in x0 + 1 until x0 + cell - 1) world.set(x, y, m)
        }
        // no stepping: everything stays where it was put, only drawn
        Pictures.write(world, IntArray(world.size) { 0xFF1A2230.toInt() }, "materials", scale = 6, lit = false)
    }
}

/** Plants, animals and light in the sandbox (2b), for the report. */
class LifePicturesTest {
    @Test
    fun `life in the sandbox`() {
        val materials = MaterialTable.load()
        val s = Session(materials, Terrain.WIDTH, Terrain.HEIGHT, Terrain.SEED) { Terrain.sandbox(it) }
        val backdrop = Backdrop.of(s.world)
        repeat(60) { s.tick() }
        Pictures.write(s.world, backdrop, "life_grotto", scale = 6, area = intArrayOf(10, 192, 80, 232))
        Pictures.write(s.world, backdrop, "life_hill", scale = 5, area = intArrayOf(0, 90, 110, 170))
        // a minute later: grass on the damp ground, the vine at the pond, seeds sprouting
        s.press(40, 100, materials.id("seed"), 3); repeat(30) { s.tick() }; s.release()
        repeat(3600) { s.tick() }
        Pictures.write(s.world, backdrop, "life_minute", scale = 5, area = intArrayOf(0, 90, 110, 170))
        val birds = s.world.count(materials.id("bird"))
        // fire in the tree on the slope: the birds fly off, the trunk burns through, the top falls
        s.press(58, 130, materials.id("fire"), 3); repeat(20) { s.tick() }; s.release()
        repeat(150) { s.tick() }
        Pictures.write(s.world, backdrop, "life_fire", scale = 5, area = intArrayOf(0, 90, 110, 170), frame = 1)
        repeat(900) { s.tick() }
        Pictures.write(s.world, backdrop, "life_after_fire", scale = 5, area = intArrayOf(0, 90, 110, 170), frame = 2)
        assertTrue(s.world.count(materials.id("bird")) < birds, "birds flew off")
    }
}
