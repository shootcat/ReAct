package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** materials.json (Leitfaden V3, 4.3): every field the guide asks for, sensible values, no loose ends. */
class MaterialsTest {

    private val materials = MaterialTable.load()

    @Test
    fun `the materials of phase 1, 2a and 2b are all there`() {
        for (id in listOf(
            "rock", "sand", "earth", "water", "wood", "fire", "smoke", "steam", "stone", "lava",
            "gravel", "clay", "ore", "ore_rubble", "salt", "saltwater", "ice", "snow", "glass", "metal", "molten_metal",
            "charcoal", "ember", "ash", "brick", "rubble",
            "grass", "moss", "vine", "seed", "trunk", "deadwood", "leaves", "dead_plant",
            "fish", "dead_fish", "firefly", "worm", "bird",
        )) {
            assertTrue(materials.idOrNull(id) != null, "$id is missing")
        }
        assertEquals("air", materials.spec(0).id)
    }

    @Test
    fun `the file names the fields of the guide`() {
        val text = MaterialTable::class.java.getResourceAsStream("/materials.json")!!.bufferedReader().readText()
        for (field in listOf(
            "kind", "density", "anchored", "flammable", "ignite_temp", "burn_time", "burn_product", "quench_product",
            "wet_blocks_burning", "absorbs", "melt_temp", "melt_product", "boil_temp", "boil_product", "freeze_temp",
            "freeze_product", "conductivity", "heat_capacity", "hardness", "erodes_in_water", "dissolves_in",
            "dissolved_product", "bucketable", "grabbable", "emits_light", "blocks_light", "color", "color_variance",
        )) assertTrue("\"$field\"" in text, "no material uses $field")
        // every material states the fields that apply to all of them (Leitfaden V3, 6.3)
        val blocks = text.substringAfter("\"materials\"").split("\"id\":").drop(1)
        for (b in blocks) for (field in listOf(
            "kind", "density", "anchored", "flammable", "wet_blocks_burning", "absorbs", "conductivity", "heat_capacity",
            "hardness", "erodes_in_water", "dissolves_in", "dissolved_product", "bucketable", "grabbable", "emits_light",
            "blocks_light", "color", "color_variance",
        )) assertTrue("\"$field\"" in b, "${b.substringBefore(",")} has no $field")
    }

    @Test
    fun `phase changes never undo themselves at once`() {
        for (m in 0 until materials.count) {
            val melt = materials.meltProduct[m]
            if (melt != m) assertTrue(materials.freezeTemp[melt] < materials.meltTemp[m], "${materials.spec(m).id} melts into something that sets again at once")
            val boil = materials.boilProduct[m]
            if (boil != m) assertTrue(materials.freezeTemp[boil] < materials.boilTemp[m], "${materials.spec(m).id} boils into something that condenses again at once")
            val freeze = materials.freezeProduct[m]
            if (freeze != m) assertTrue(materials.meltTemp[freeze] > materials.freezeTemp[m], "${materials.spec(m).id} sets into something that melts again at once")
        }
    }

    @Test
    fun `rock cannot be destroyed`() {
        val rock = materials.id("rock")
        assertEquals(1f, materials.hardness[rock])
        assertTrue(materials.inert[rock], "nothing ever happens to rock")
    }

    @Test
    fun `what floats and what sinks follows from density`() {
        fun d(id: String) = materials.density[materials.id(id)]
        assertTrue(d("sand") > d("water") && d("lava") > d("water"), "sand and lava sink in water")
        assertTrue(d("ash") < d("water") && d("charcoal") < d("water") && d("wood") < d("water"), "ash, charcoal and wood float")
        assertTrue(d("steam") < d("air") && d("smoke") < d("air") && d("fire") < d("air"), "steam, smoke and flames rise")
    }
}
