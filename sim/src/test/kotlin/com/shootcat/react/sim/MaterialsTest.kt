package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** materials.json (Leitfaden V3, 4.3): every field the guide asks for, sensible values, no loose ends. */
class MaterialsTest {

    private val materials = MaterialTable.load()

    @Test
    fun `phase 1 materials are all there`() {
        for (id in listOf("rock", "sand", "earth", "water", "wood", "fire", "smoke", "steam", "stone", "lava")) {
            assertTrue(materials.idOrNull(id) != null, "$id is missing")
        }
        assertEquals("air", materials.spec(0).id)
    }

    @Test
    fun `the file names the fields of the guide`() {
        val text = MaterialTable::class.java.getResourceAsStream("/materials.json")!!.bufferedReader().readText()
        for (field in listOf(
            "kind", "density", "flammable", "ignite_temp", "burn_time", "burn_product", "quench_product", "melt_temp",
            "melt_product", "boil_temp", "boil_product", "freeze_temp", "freeze_product", "conductivity", "heat_capacity",
            "hardness", "erodes_in_water", "absorbs", "emits_light", "color", "color_variance", "cost",
        )) assertTrue("\"$field\"" in text, "no material uses $field")
        // dissolves_in / dissolved_product are read as well; salt (phase 2) is the first to need them
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
