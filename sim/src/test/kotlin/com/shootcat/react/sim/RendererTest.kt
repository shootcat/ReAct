package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals

/** Drawing only the part of the world on screen (4a.5) gives the same picture there as drawing all of it. */
class RendererTest {

    @Test
    fun `a part drawn alone looks exactly as in the whole picture`() {
        val materials = Lab.materials
        val size = WorldSize.MEDIUM
        val world = World(size.width, size.height, materials, Terrain.SEED)
        Template.MEADOW.build(world)
        world.run(120)
        val backdrop = Backdrop.of(world)
        val whole = IntArray(world.size)
        Renderer(world, backdrop).render(whole, 7, running = true)
        // a window through the pond, the bridge and the deep rock, and one at the edge
        for ((x0, y0, x1, y1) in listOf(listOf(150, 60, 330, 280), listOf(0, 0, 90, 299), listOf(400, 120, 479, 299))) {
            val part = IntArray(world.size)
            Renderer(world, backdrop).render(part, 7, true, x0, y0, x1, y1)
            var differ = 0
            for (y in y0..y1) for (x in x0..x1) if (part[y * world.width + x] != whole[y * world.width + x]) differ++
            assertEquals(0, differ, "cells drawn differently in the window $x0,$y0 – $x1,$y1")
        }
    }
}
