package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The discovery book (Leitfaden V3, 4.3): properties, never recipes, each shown by its own little scene. */
class DiscoveryTest {

    private val materials = MaterialTable.load()
    private val book = Discoveries.load(materials)

    @Test
    fun `entries name properties, never recipes`() {
        assertTrue(book.all.size >= 40, "${book.all.size} entries")
        for (e in book.all) {
            assertTrue(e.spec.text.none { it == '+' || it == '→' || it == '=' }, "${e.id} reads like a recipe: ${e.spec.text}")
            assertTrue(e.spec.scene.isNotEmpty(), "${e.id} has no scene")
        }
        assertEquals("Sand behält Wärme", book.byId("sand_heat")!!.sentence)
    }

    @Test
    fun `every scene shows the property of its entry within its time`() {
        val missing = ArrayList<String>()
        for (e in book.all) {
            val world = book.scene(e)
            val watch = DiscoveryWatch(book, emptySet())
            var found = false
            var step = 0
            while (step < Discoveries.SCENE_STEPS && !found) {
                world.step()
                step++
                if (step % 30 == 0) found = watch.check(world).any { it.id == e.id }
            }
            if (!found) missing += e.id
        }
        assertTrue(missing.isEmpty(), "scenes that do not show their property: $missing")
    }

    @Test
    fun `the watch reports each entry once and keeps what is known`() {
        val e = book.byId("ice_melt")!!
        val known = DiscoveryWatch(book, setOf("ice_melt"))
        val fresh = DiscoveryWatch(book, emptySet())
        val a = book.scene(e)
        val first = ArrayList<String>()
        repeat(600) {
            a.step()
            if (it % 30 == 29) {
                first += fresh.check(a).map { f -> f.id }
                assertTrue(known.check(a).none { f -> f.id == "ice_melt" }, "already known")
            }
        }
        assertEquals(1, first.count { it == "ice_melt" }, "found once: $first")
    }

    @Test
    fun `watching does not change the world`() {
        val e = book.byId("water_boil")!!
        val a = book.scene(e)
        val b = book.scene(e)
        val watch = DiscoveryWatch(book, emptySet())
        repeat(300) {
            a.step()
            b.step()
            watch.check(a)
        }
        assertEquals(a.hash(), b.hash())
    }

    @Test
    fun `scene pictures for the report`() {
        // as the book shows them: dark scenes lifted a little
        for ((id, steps) in listOf("sand_heat" to 900, "salt_ice" to 300, "wood_wet" to 40, "trunk_fall" to 700, "moss_grow" to 1200, "bird_flee" to 120)) {
            val e = book.byId(id)!!
            val world = book.scene(e)
            world.run(steps)
            Pictures.write(world, IntArray(world.size) { 0xFF9CC3E0.toInt() }, "book_$id", scale = 5, ambient = 0.3f)
        }
    }
}
