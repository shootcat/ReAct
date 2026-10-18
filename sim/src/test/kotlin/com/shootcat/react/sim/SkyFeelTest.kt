package com.shootcat.react.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4b, the sky: clouds hang in their band, spread, drift and gather into billows, never pile up under the
 * top of the world nor into cones, and are drawn soft (1); light and shadow: no dark columns through the air,
 * shadows on surfaces, soft and fainter with the height, caves dark (2).
 */
class SkyFeelTest {

    private fun cloudRows(lab: Lab): IntRange {
        val cloud = lab.id("cloud")
        var top = Int.MAX_VALUE
        var bottom = -1
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == cloud) {
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        return top..bottom
    }

    private fun cloudX(lab: Lab): Double {
        val cloud = lab.id("cloud")
        var sum = 0.0
        var n = 0
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == cloud) {
            sum += x
            n++
        }
        return sum / n
    }

    @Test
    fun `clouds painted high and low go into their band and hang there, never at the top of the world`() {
        val lab = Lab(160, 80)
        lab.world.setWeather(cloudBase = 30)
        val cloud = lab.id("cloud")
        lab.world.paint(cloud, 40, 4, 6)
        lab.world.paint(cloud, 110, 50, 6)
        lab.seconds(30f)
        val rows = cloudRows(lab)
        var outside = 0
        for (y in 0 until lab.height) for (x in 0 until lab.width) if (lab.world.material(x, y) == cloud && y !in 30 - World.CLOUD_BAND..30) outside++
        println("cloud rows ${rows.first}..${rows.last}, band ${30 - World.CLOUD_BAND}..30, $outside cells outside")
        // a wisp or two may still be on its way round the cloud in front of it
        assertTrue(rows.first >= 30 - World.CLOUD_BAND - 1 && rows.last <= 31 && outside <= 3, "in the band: rows $rows, $outside outside")
        lab.picture("sky_band")
    }

    @Test
    fun `a band reaching to the top of the world keeps clear of it`() {
        val lab = Lab(120, 60)
        lab.world.setWeather(cloudBase = 10)
        lab.world.paint(lab.id("cloud"), 60, 2, 8)
        lab.seconds(30f)
        assertTrue(cloudRows(lab).first >= World.CLOUD_CEILING, "${cloudRows(lab)}")
    }

    @Test
    fun `a cloud has a flat underside and steep, rounded sides, not the slope of a heap`() {
        val lab = Lab(120, 60)
        lab.world.setWeather(cloudBase = 30)
        lab.world.paint(lab.id("cloud"), 60, 22, 7)
        lab.seconds(20f)
        val cloud = lab.id("cloud")
        val columns = (0 until 120).map { x -> (0 until 60).count { y -> lab.world.material(x, y) == cloud } }.filter { it > 0 }
        val underside = (0 until 120).filter { x -> lab.world.material(x, 30) == cloud }.size
        println("cloud columns $columns, ${underside} cells on the base row")
        // a heap of sand would rise by a row per column; a cloud's sides climb steeply
        val tallest = columns.max()
        assertTrue(tallest >= 6, "it billows up: $columns")
        assertTrue(columns.size <= tallest * 3, "not a flat cone: $columns")
        assertTrue(underside >= columns.size / 2, "its underside on the base: $underside of ${columns.size}")
    }

    @Test
    fun `a tall narrow cloud spreads out sideways`() {
        val lab = Lab(120, 60)
        lab.world.setWeather(cloudBase = 30)
        lab.rect("cloud", 58, 17, 61, 30)
        val cloud = lab.id("cloud")
        fun width(): Int {
            var x0 = Int.MAX_VALUE
            var x1 = -1
            for (y in 0 until 60) for (x in 0 until 120) if (lab.world.material(x, y) == cloud) {
                x0 = minOf(x0, x)
                x1 = maxOf(x1, x)
            }
            return x1 - x0 + 1
        }
        lab.seconds(20f)
        println("narrow cloud after 20 s: ${width()} wide")
        assertTrue(width() >= 8, "it spreads: ${width()} wide")
    }

    @Test
    fun `scattered wisps gather into a billow`() {
        val lab = Lab(120, 60)
        lab.world.setWeather(cloudBase = 30)
        val cloud = lab.id("cloud")
        for (i in 0 until 10) for (j in 0 until 4) lab.world.set(40 + 3 * i, 18 + 3 * j, cloud)
        fun lone(): Int {
            var n = 0
            for (y in 1 until 59) for (x in 1 until 119) {
                if (lab.world.material(x, y) != cloud) continue
                var near = 0
                for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && lab.world.material(x + dx, y + dy) == cloud) near++
                if (near == 0) n++
            }
            return n
        }
        val before = lone()
        lab.seconds(20f)
        println("lone wisps: $before, after 20 s ${lone()}")
        assertTrue(lone() < before / 2, "they gather: ${lone()} of $before alone")
    }

    @Test
    fun `the wind takes the clouds along`() {
        val lab = Lab(200, 60)
        lab.world.setWeather(cloudBase = 30, wind = 0.5f)
        lab.world.paint(lab.id("cloud"), 60, 24, 6)
        val x0 = cloudX(lab)
        lab.seconds(10f)
        println("cloud drifted from %.1f to %.1f".format(x0, cloudX(lab)))
        assertTrue(cloudX(lab) > x0 + 10, "downwind")
    }

    /** The cloud layer's (alpha, brightness) at block (bx, by). */
    private fun cloudPixel(layer: IntArray, cw: Int, bx: Int, by: Int): Pair<Int, Int> {
        val c = layer[by * cw + bx]
        return (c ushr 24) to ((((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3)
    }

    @Test
    fun `clouds are drawn soft, bright on top, greyer below, a thundercloud dark`() {
        fun layer(rows: Int): Triple<Renderer, IntArray, Lab> {
            val lab = Lab(120, 60)
            lab.rect("cloud", 30, 10, 89, 10 + rows - 1)
            val r = Renderer(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() })
            val out = IntArray(r.cloudWidth * r.cloudHeight)
            r.renderClouds(out, 0)
            return Triple(r, out, lab)
        }
        val (r, thin, _) = layer(6)
        val cw = r.cloudWidth
        val bx = 30
        // the rim fades: somewhere between outside and inside the alpha lies between
        val alphas = (0 until r.cloudHeight).map { cloudPixel(thin, cw, bx, it).first }
        assertTrue(alphas.any { it in 20..200 }, "a soft rim: $alphas")
        val inside = (0 until r.cloudHeight).filter { cloudPixel(thin, cw, bx, it).first > 200 }
        val top = cloudPixel(thin, cw, bx, inside.first()).second
        val bottom = cloudPixel(thin, cw, bx, inside.last()).second
        println("ordinary cloud: top $top, underside $bottom")
        assertTrue(top > bottom, "bright on top, greyer below: $top, $bottom")
        assertTrue(bottom > 170, "an ordinary cloud is not dark: $bottom")
        val (r2, deep, _) = layer(20)
        val inside2 = (0 until r2.cloudHeight).filter { cloudPixel(deep, cw, bx, it).first > 200 }
        val belly = cloudPixel(deep, cw, bx, inside2.last()).second
        println("thundercloud underside $belly")
        assertTrue(belly < 130, "a thundercloud has a dark belly: $belly")
    }

    // ------------------------------------------------------------------ 2. light and shadow

    @Test
    fun `no dark column in the air under a drop or a stone in the sky, the cave stays dark`() {
        val lab = Lab(120, 80)
        lab.rect("rock", 2, 60, 117, 77)
        // a closed cave in the rock
        lab.rect("air", 70, 64, 100, 72)
        lab.world.set(30, 20, lab.id("water"))
        lab.rect("stone", 50, 20, 53, 22)
        val sky = 0xFF9CC3E0.toInt()
        val r = Renderer(lab.world, IntArray(lab.world.size) { sky })
        val px = IntArray(lab.world.size)
        r.render(px, 0)
        for (y in 24..58) {
            assertEquals(sky, px[lab.world.index(30, y)], "air under the drop at row $y")
            assertEquals(sky, px[lab.world.index(51, y)], "air under the stone at row $y")
        }
        val cave = px[lab.world.index(85, 68)]
        assertTrue(((cave shr 16) and 0xFF) < ((sky shr 16) and 0xFF) * 0.8, "the cave is dark: ${Integer.toHexString(cave)}")
    }

    @Test
    fun `a board in the air shades the ground under it softly, less the higher it is`() {
        fun shade(height: Int): Int {
            val lab = Lab(120, 80)
            lab.rect("rock", 2, 60, 117, 77)
            lab.rect("stone", 50, 59 - height, 69, 59 - height)
            val r = Renderer(lab.world, IntArray(lab.world.size) { 0xFF9CC3E0.toInt() })
            val px = IntArray(lab.world.size)
            r.render(px, 0)
            val lit = px[lab.world.index(20, 60)]
            val under = px[lab.world.index(60, 60)]
            return ((lit shr 16) and 0xFF) - ((under shr 16) and 0xFF)
        }
        val low = shade(4)
        val high = shade(30)
        println("ground darker under a board 4 cells up by $low, 30 cells up by $high")
        assertTrue(low > high, "fainter the higher: $low, $high")
        assertTrue(high >= 0, "and never brighter")
    }
}
