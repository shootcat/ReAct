package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4b, how things look: bodies drawn as turned, smoothed pictures of their own, moved on between two pictures,
 * while the grid stays for the simulation (6); wood with a grain along the stroke, darker bark at its edges and
 * less of a staircase on slanting edges (8).
 */
class LookTest {
    private val mt = Lab.materials
    private val sky = 0xFF9CC3E0.toInt()

    private fun brightness(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)

    @Test
    fun `a body is drawn as a turned picture of its own, smoothed at its rim, and not in the grid`() {
        val lab = Lab(80, 60)
        lab.rect("rock", 2, 50, 77, 57)
        // a plank falling over a step, so that it turns
        lab.rect("rock", 2, 40, 30, 49)
        lab.rect("wood", 20, 30, 45, 32)
        lab.seconds(0.6f)
        val body = lab.world.bodyStates().single()
        println("plank at ${body.x},${body.y} turned ${body.angle}°")
        val r = Renderer(lab.world, IntArray(lab.world.size) { sky }).also { it.bodySprites = true }
        val px = IntArray(lab.world.size)
        r.render(px, 0)
        val sprites = ArrayList<BodySprite>()
        assertEquals(1, r.renderBodies(sprites))
        val sp = sprites[0]
        assertEquals(26 * Renderer.SPRITE_SCALE, sp.w, "26 cells wide, ${Renderer.SPRITE_SCALE} pixels each")
        assertEquals(body.x, sp.x)
        assertTrue(abs(Math.toDegrees(sp.angle.toDouble()) - body.angle) < 0.01, "turned as the body: ${Math.toDegrees(sp.angle.toDouble())}")
        // the rim is smoothed: some pixels part covered
        val alphas = sp.pixels.map { it ushr 24 }
        assertTrue(alphas.any { it in 1..254 }, "a soft rim")
        assertTrue(alphas.count { it == 255 } > sp.w * sp.h / 2, "solid inside")
        // the grid under it shows what is around, not wood
        val wood = mt.id("wood")
        val woodColour = mt.color[wood]
        for (i in 0 until lab.world.size) if (lab.world.mat[i].toInt() == wood) {
            assertTrue(abs(brightness(px[i]) - brightness(woodColour)) > 60, "the grid cell is not drawn as wood")
        }
    }

    @Test
    fun `between two pictures a falling body is moved on from where it was`() {
        val lab = Lab(60, 60)
        lab.rect("stone", 20, 10, 25, 14)
        lab.seconds(0.3f)
        val r = Renderer(lab.world, IntArray(lab.world.size) { sky }).also { it.bodySprites = true }
        val px = IntArray(lab.world.size)
        val sprites = ArrayList<BodySprite>()
        r.render(px, 0)
        r.renderBodies(sprites)
        val first = sprites[0].y
        lab.world.run(3)
        r.render(px, 1)
        r.renderBodies(sprites)
        assertEquals(first, sprites[0].lastY, "where it was in the picture before")
        assertTrue(sprites[0].y > first, "and where it has fallen to: ${sprites[0].y}")
    }

    @Test
    fun `wood takes the direction of the stroke as its grain`() {
        val s = Session(mt, 80, 60, 7L) { w -> for (y in 56..59) for (x in 0..79) w.set(x, y, mt.id("rock")) }.also { it.begin(Session.Supply.UNLIMITED) }
        val wood = mt.id("wood")
        s.press(10, 50, wood, 3); s.drag(30, 50); s.release()
        s.press(50, 25, wood, 3); s.drag(50, 50); s.release()
        val w = s.world
        fun grain(x: Int, y: Int) = (w.variant[w.index(x, y)].toInt() shr 4) and 3
        assertEquals(World.GRAIN_ALONG, grain(20, 50), "a stroke left to right")
        assertEquals(World.GRAIN_ACROSS, grain(50, 40), "a stroke down")
        // drawn: lines along the stroke, so along a row of the horizontal one the colour stays more alike than down a column
        val r = Renderer(w, IntArray(w.size) { sky })
        r.lit = false
        val px = IntArray(w.size)
        r.render(px, 0)
        fun spread(cells: List<Int>) = cells.map { brightness(px[it]) }.let { it.max() - it.min() }
        val alongRow = (14..26).map { spread(listOf(w.index(it, 50))) }
        val row = spread((14..26).map { w.index(it, 50) })
        val column = spread((48..52).map { w.index(20, it) })
        println("horizontal stroke: spread along a row $row, down a column $column")
        assertTrue(row < column, "the grain runs along the stroke")
        assertTrue(alongRow.isNotEmpty())
    }

    @Test
    fun `wood has a darker bark at its edges, and a slant less of a staircase`() {
        val lab = Lab(80, 60)
        lab.rect("rock", 2, 30, 9, 57)
        lab.rect("wood", 10, 40, 30, 47)
        val w = lab.world
        val r = Renderer(w, IntArray(w.size) { sky })
        r.lit = false
        val px = IntArray(w.size)
        r.render(px, 0)
        val inside = (15..25).map { brightness(px[w.index(it, 43)]) }.average()
        val edge = (15..25).map { brightness(px[w.index(it, 40)]) }.average()
        println("wood inside $inside, at its top edge $edge")
        assertTrue(edge < inside * 0.85, "bark darker")
        // a slanting beam built into the rock: the notches of its steps take on some of the wood
        val slant = Lab(80, 60)
        slant.rect("rock", 2, 20, 9, 57)
        for (k in 0 until 20) slant.rect("wood", 10 + k, 20 + k, 10 + k, 21 + k)
        val p2 = IntArray(slant.world.size)
        Renderer(slant.world, IntArray(slant.world.size) { sky }).also { it.lit = false }.render(p2, 0)
        val notch = p2[slant.world.index(15, 21 + 5 - 1 - 1)]
        println("notch beside the step: ${Integer.toHexString(notch)}")
        var notches = 0
        for (k in 1 until 19) {
            val i = slant.world.index(10 + k + 1, 20 + k)
            if (slant.world.mat[i].toInt() == 0 && p2[i] != sky) notches++
        }
        assertTrue(notches >= 15, "the notches of the steps are softened: $notches of 18")
    }
}
