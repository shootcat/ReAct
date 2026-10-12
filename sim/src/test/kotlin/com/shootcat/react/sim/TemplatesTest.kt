package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The starting landscapes of the sandbox and its sizes (Leitfaden V3, 4a.5). */
class TemplatesTest {

    private val materials = Lab.materials

    private fun World.has(name: String) = count(materials.id(name))

    @Test
    fun `every landscape builds in every size, with what makes it`() {
        for (size in WorldSize.entries) for (t in Template.entries) {
            val world = World(size.width, size.height, materials, Terrain.SEED)
            t.build(world)
            val what = "${t.label}, ${size.label}"
            val air = world.has("air")
            assertTrue(air > world.size / 5 && air < world.size * 4 / 5, "$what: air $air of ${world.size}")
            when (t) {
                Template.MEADOW -> assertTrue(world.has("water") > 100 && world.has("trunk") > 20 && world.has("lava") > 50, what)
                Template.EMPTY -> assertTrue(world.has("grass") >= size.width - 4 && world.has("water") == 0, what)
                Template.ISLAND -> assertTrue(world.has("saltwater") > world.size / 10 && world.has("trunk") > 10, what)
                Template.COAST -> assertTrue(world.has("saltwater") > world.size / 20 && world.has("sand") > 200 && world.has("trunk") > 10, what)
                Template.MOUNTAIN -> assertTrue(world.has("snow") > 200 && world.has("ice") > 20, what)
                Template.VOLCANO -> assertTrue(world.has("lava") > 300 && world.has("basalt") > 300 && world.has("saltwater") > 500, what)
                Template.CAVES -> assertTrue(world.has("gas_vent") > 0 && world.has("firedamp") > 20 && world.has("firefly") > 0 && world.has("water") > 100, what)
            }
        }
    }

    @Test
    fun `the landscapes lie still and do not blow up at the start`() {
        val pictures = HashMap<Template, World>()
        for (t in Template.entries) {
            val size = WorldSize.MEDIUM
            val world = World(size.width, size.height, materials, Terrain.SEED)
            t.build(world)
            val snow = world.has("snow")
            world.run(600)
            assertEquals(0, world.events[Event.EXPLODE], "${t.label}: nothing explodes by itself")
            if (t == Template.MOUNTAIN) assertTrue(world.has("snow") > snow * 9 / 10, "the snow cap stays in the cold: ${world.has("snow")} of $snow")
            pictures[t] = world
        }
        for ((t, world) in pictures) Pictures.write(world, Backdrop.of(world), "template_${t.name.lowercase()}", scale = 2)
    }

    @Test
    fun `the meadow in a wide world is the start landscape, unstretched, with more around it`() {
        val size = WorldSize.MEDIUM
        val world = World(size.width, size.height, materials, Terrain.SEED)
        Template.MEADOW.build(world)
        // the pond with its bridge lies in the middle
        val mid = size.width / 2
        var water = 0
        for (x in mid - 40..mid + 40) for (y in 0 until size.height) if (world.material(x, y) == materials.id("water")) water++
        assertTrue(water > 200, "the pond in the middle: $water")
        assertTrue(world.has("trunk") > 60, "trees on the hills to the left as well: ${world.has("trunk")}")
    }
}
