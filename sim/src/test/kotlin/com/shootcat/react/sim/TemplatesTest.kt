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
                Template.CAVES -> assertTrue(world.has("gas_vent") > 0 && world.has("firedamp") > 20 && world.has("firefly") > 0 && world.has("water") > 100 && world.has("oil") > 20 && world.has("oil_spring") > 0, what)
                Template.FOREST -> assertTrue(world.has("water") > world.size / 30 && world.has("trunk") > 30 && world.has("stone") > 100 && world.has("deadwood") > 0 && world.has("wood") > 0, what)
                Template.LAKE -> assertTrue(world.has("water") > world.size / 30 && world.has("stone") > 50 && world.has("deadwood") > 40 && world.has("wood") > 40 && world.has("cloud") > 200, what)
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

    @Test
    fun `on the wooded slope the boulders lie against the trees, a burnt tree lets its boulder roll into the lake`() {
        val size = WorldSize.MEDIUM
        val world = World(size.width, size.height, materials, Terrain.SEED)
        Template.FOREST.build(world)
        // the scenery behind, made once as the app does (made later, a raindrop would count as ground)
        val backdrop = Backdrop.of(world)
        world.run(600)
        val stone = materials.id("stone")
        val boulders = world.bodyStates().filter { it.material == stone && it.cells > 20 }
        Pictures.write(world, backdrop, "template_forest_still", scale = 2)
        assertTrue(boulders.size >= 3, "boulders on the slope: ${boulders.size}")
        // they lie still (one wedged between slope and trunk may wake now and then when the leaves change, it does not move)
        world.run(300)
        val later = world.bodyStates().filter { it.material == stone && it.cells > 20 }
        assertEquals(boulders.map { it.x.toInt() to it.y.toInt() }, later.map { it.x.toInt() to it.y.toInt() }, "they lie still")
        assertTrue(world.bodyStates().any { it.material == materials.id("wood") && it.asleep }, "the raft floats still")
        // fire to the trunk below the lowest boulder that leans on a tree
        val b = boulders.filter { it.x > size.width * 0.15f }.maxBy { it.x }
        val trunk = materials.id("trunk")
        var tx = -1
        var ty = -1
        for (x in b.x.toInt() + 1..b.x.toInt() + 10) {
            for (y in 0 until size.height) if (world.material(x, y) == trunk) { tx = x; ty = y }
            if (tx >= 0) break
        }
        assertTrue(tx >= 0, "a tree below the boulder at ${b.x}")
        // the whole tree set alight
        for (y in ty - 30..ty) for (x in tx - 6..tx + 6) {
            val m = world.material(x, y)
            if (m == trunk || m == materials.id("leaves")) world.setTemperature(x, y, 500f)
        }
        val startX = b.x
        var furthest = startX
        repeat(1800) {
            world.step()
            world.bodyStates().filter { it.material == stone && it.cells > 20 }.forEach { if (it.x < startX + 60) furthest = maxOf(furthest, it.x) }
        }
        Pictures.write(world, backdrop, "template_forest_rolled", scale = 2)
        assertTrue(furthest > startX + 15, "the boulder rolled on down: from ${startX} to $furthest")
    }

    @Test
    fun `by the lake the raft and the boat float, logs and boulders rest on the slope, the clouds drift in the breeze`() {
        val size = WorldSize.MEDIUM
        val world = World(size.width, size.height, materials, Terrain.SEED)
        Template.LAKE.build(world)
        val backdrop = Backdrop.of(world)
        val cloud = materials.id("cloud")
        fun cloudX(): Double {
            var sum = 0.0
            var n = 0
            for (i in 0 until world.size) if (world.mat[i].toInt() == cloud) { sum += i % world.width; n++ }
            return sum / n
        }
        val x0 = cloudX()
        world.run(900)
        Pictures.write(world, backdrop, "template_lake_after", scale = 2)
        val bodies = world.bodyStates()
        val wood = bodies.filter { it.material == materials.id("wood") }
        val water = materials.id("water")
        println("lake: " + bodies.joinToString { "${materials.spec(it.material).id}${it.cells}@%.0f,%.0f a%.0f%s".format(it.x, it.y, it.angle, if (it.asleep) "z" else "") })
        assertEquals(2, wood.size, "a raft and a boat")
        for (b in wood) {
            val below = world.material(b.x.toInt(), b.y.toInt() + 3)
            assertEquals(water, below, "floating: water under the ${b.cells} cells at ${b.x},${b.y}")
        }
        // (pebbles of stone in the sand of the lake bed come loose too)
        val onSlope = bodies.filter { it.material != materials.id("wood") && it.cells > 5 }
        assertTrue(onSlope.size >= 8, "logs and boulders: ${onSlope.size}")
        assertTrue(onSlope.all { it.x < size.width * 0.45f }, "still on the slope: ${onSlope.map { it.x.toInt() }}")
        assertTrue(cloudX() > x0 + 5, "the clouds drift with the breeze: from %.0f to %.0f".format(x0, cloudX()))
    }
}
