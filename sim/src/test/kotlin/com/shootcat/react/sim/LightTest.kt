package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertTrue

/** Light (Leitfaden V3, 4.2): sources, what lets it through, and that caves without a source are dark. */
class LightTest {

    /** A cave under a roof of rock from x0 to x1, closed all round. */
    private fun Lab.cave(x0: Int = 2, x1: Int = width - 3) {
        rect("rock", x0, 18, x1, 21)
    }

    /** One step works out the light of the world as it is now. */
    private fun Lab.lightAfterAStep(x: Int, y: Int): Float {
        world.step()
        return world.lightAt(x, y)
    }

    @Test
    fun `daylight falls in from above, a closed cave is dark, an opening lets some in`() {
        val lab = Lab()
        lab.cave()
        assertTrue(lab.lightAfterAStep(30, 10) > 0.95f, "open sky: ${lab.world.lightAt(30, 10)}")
        assertTrue(lab.world.lightAt(30, 45) < 0.01f, "closed cave: ${lab.world.lightAt(30, 45)}")

        val open = Lab()
        open.cave()
        for (y in 18..21) for (x in 50..53) open.world.set(x, y, MaterialTable.AIR)
        val below = open.lightAfterAStep(51, 40)
        val far = open.world.lightAt(8, 40)
        assertTrue(below > 0.8f, "under the opening: $below")
        assertTrue(far < below * 0.6f && far > 0f, "far inside it is dimmer: $far")
    }

    @Test
    fun `light passes water and glass and is stopped by stone`() {
        val lab = Lab()
        // three shafts with walls of rock: water at the bottom of the first, a glass roof and a stone roof
        lab.rect("rock", 20, 10, 21, 61)
        lab.rect("rock", 41, 10, 42, 61)
        lab.rect("water", 2, 36, 19, 61)
        lab.rect("glass", 22, 26, 40, 29)
        lab.rect("stone", 43, 26, 61, 29)
        val underWater = lab.lightAfterAStep(10, 58)
        val underGlass = lab.world.lightAt(30, 50)
        val underStone = lab.world.lightAt(52, 50)
        assertTrue(underWater > 0.4f, "at the bottom of 22 cells of water: $underWater")
        assertTrue(underGlass > 0.8f, "under glass: $underGlass")
        assertTrue(underStone < 0.02f, "under stone: $underStone")
    }

    @Test
    fun `fire, embers, lava, hot metal and fireflies light up a dark cave`() {
        fun litBy(name: String, t: Float = Float.NaN): Float {
            val lab = Lab()
            lab.cave()
            lab.rect(name, 30, 56, 33, 61, t)
            return lab.lightAfterAStep(31, 46)
        }
        val lava = litBy("lava")
        val metal = litBy("metal", 900f)
        val cold = litBy("metal")
        val ember = litBy("ember")
        assertTrue(lava > 0.3f, "lava: $lava")
        assertTrue(metal > 0.2f, "glowing metal: $metal")
        assertTrue(cold < 0.01f, "cold metal gives no light: $cold")
        assertTrue(ember > 0.2f, "embers: $ember")

        val lab = Lab()
        lab.cave()
        lab.world.set(31, 40, lab.id("firefly"))
        val firefly = lab.lightAfterAStep(31, 40)
        assertTrue(firefly > 0.15f && firefly < lava, "a firefly gives faint light: $firefly")
        assertTrue(lab.world.lightAt(5, 58) < firefly * 0.5f, "only near it")

        val fire = Lab()
        fire.cave()
        fire.rect("wood", 28, 58, 35, 61)
        fire.world.paint(fire.id("fire"), 31, 55, 2)
        val flames = fire.lightAfterAStep(31, 46)
        assertTrue(flames > 0.3f, "fire: $flames")
    }
}
