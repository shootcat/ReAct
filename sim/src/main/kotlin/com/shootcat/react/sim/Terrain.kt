package com.shootcat.react.sim

/**
 * Smooth value noise from integer hashing only, so a terrain built from a seed is the same on every device.
 */
class Noise(seed: Long) {
    private val s = (seed xor (seed ushr 29)).toInt()

    fun hash(x: Int, y: Int): Float {
        var h = s xor (x * 0x27d4eb2d) xor (y * 0x165667b1)
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return (h ushr 8) / 16777216f
    }

    /** Value noise in 0..1, smooth over a lattice of size 1. */
    fun value(x: Float, y: Float): Float {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash(ix, iy)
        val b = hash(ix + 1, iy)
        val c = hash(ix, iy + 1)
        val d = hash(ix + 1, iy + 1)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
    }

    /** Several octaves of [value], in 0..1. */
    fun fbm(x: Float, y: Float, octaves: Int = 4): Float {
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        var fx = x
        var fy = y
        repeat(octaves) {
            sum += value(fx, fy) * amp
            norm += amp
            amp *= 0.5f
            fx *= 2f
            fy *= 2f
        }
        return sum / norm
    }

    private fun floor(v: Float): Int {
        val i = v.toInt()
        return if (v < i) i - 1 else i
    }
}

/** The start of the free sandbox (Phase 1): a hillside with a pond, a wooden bridge, sand, and a lava cave. */
object Terrain {

    const val WIDTH = 200
    const val HEIGHT = 350
    const val SEED = 20261011L

    fun sandbox(world: World, seed: Long = SEED) {
        val mt = world.materials
        val rock = mt.id("rock")
        val stone = mt.id("stone")
        val sand = mt.id("sand")
        val earth = mt.id("earth")
        val water = mt.id("water")
        val wood = mt.id("wood")
        val lava = mt.id("lava")
        val w = world.width
        val h = world.height
        val n = Noise(seed)
        val sx = w / 200f
        val sy = h / 350f

        // the surface line: a hill on the left, a hollow with the pond in the middle, a gentle rise on the right
        val surface = IntArray(w) { x ->
            val u = x / sx
            val rough = (n.fbm(u / 26f, 0.5f, 4) - 0.5f) * 18f
            ((profile(u) + rough) * sy).toInt().coerceIn(h / 4, h - 20)
        }
        val deepest = (90 * sx).toInt() + (0 until (30 * sx).toInt()).maxByOrNull { surface[(90 * sx).toInt() + it] }!!
        val bankLeft = ((60 * sx).toInt()..deepest).minOf { surface[it] }
        val bankRight = (deepest..(150 * sx).toInt()).minOf { surface[it] }
        val waterLine = maxOf(bankLeft, bankRight) + 2
        var pondLeft = deepest
        while (pondLeft > 0 && surface[pondLeft - 1] > waterLine) pondLeft--
        var pondRight = deepest
        while (pondRight < w - 1 && surface[pondRight + 1] > waterLine) pondRight++

        for (x in 0 until w) {
            val u = x / sx
            val top = surface[x]
            val soil = ((10f + 8f * n.fbm(u / 20f, 3.3f)) * sy).toInt()
            for (y in top until h) {
                val d = y - top
                val v = y / sy
                val m = when {
                    // loose ground on top: sand towards the right and at the pond, earth on the hill
                    d < soil && (u > 140f + 10f * n.value(v / 9f, 1.7f) || x in pondLeft..pondRight) -> sand
                    d < soil -> earth
                    // a few boulders of stone in the rock near the surface
                    d < soil + 10 && n.fbm(u / 9f, v / 9f, 2) > 0.72f -> stone
                    else -> rock
                }
                world.set(x, y, m)
            }
        }

        // the pond: fill the hollow with water up to the water line
        for (x in pondLeft..pondRight) for (y in waterLine + 1 until h) {
            if (world.material(x, y) != MaterialTable.AIR) break
            world.set(x, y, water)
        }

        // a cave in the deep rock on the right, with lava gathered at its bottom behind a crust of stone
        val cx = 150f * sx
        val cy = 300f * sy
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / (34f * sx)
            val dy = (y - cy) / (17f * sy)
            val wobble = (n.fbm(x / (11f * sx), y / (11f * sy), 3) - 0.5f) * 0.7f
            val r = dx * dx + dy * dy + wobble
            if (r < 1f) {
                world.set(x, y, if (y > cy + 5f * sy) lava else MaterialTable.AIR)
            } else if (r < 1.25f && y > cy + 2f * sy && world.material(x, y) == rock) {
                world.set(x, y, stone, 1000f)
            } else if (r < 2.2f && world.material(x, y) == rock) {
                // the rock around the lava is hot as well, so the lava stays liquid for a while
                world.setTemperature(x, y, 1050f - (r - 1f) * 700f)
            }
        }

        // a wooden bridge over the pond, on two posts
        val deckY = waterLine - (9 * sy).toInt()
        for (x in pondLeft - 6..pondRight + 6) for (y in deckY until deckY + 3) {
            if (world.inside(x, y) && world.material(x, y) == MaterialTable.AIR) world.set(x, y, wood)
        }
        for (px in listOf(pondLeft + 8, pondRight - 8)) for (x in px until px + 3) {
            var y = deckY + 3
            while (y < h && world.material(x, y).let { it == MaterialTable.AIR || it == water }) {
                world.set(x, y, wood)
                y++
            }
        }

        // a stack of logs on the hill
        val logX = (24 * sx).toInt()
        for (k in 0 until 3) {
            val y0 = surface[logX + 10] - (k + 1) * (5 * sy).toInt()
            for (x in logX + k * 3 until logX + 26 - k * 3) for (y in y0 until y0 + 4) {
                if (world.inside(x, y) && world.material(x, y) == MaterialTable.AIR) world.set(x, y, wood)
            }
        }
        world.wakeAll()
    }

    /** Height of the ground (y grows downwards) at u = 0..200, between hand-set points. */
    private val POINTS = floatArrayOf(0f, 108f, 30f, 114f, 60f, 140f, 85f, 172f, 104f, 204f, 122f, 182f, 145f, 160f, 175f, 152f, 200f, 158f)

    private fun profile(u: Float): Float {
        var k = 0
        while (k + 3 < POINTS.size && POINTS[k + 2] < u) k += 2
        val u0 = POINTS[k]
        val u1 = POINTS[k + 2]
        val t = ((u - u0) / (u1 - u0)).coerceIn(0f, 1f)
        val e = t * t * (3f - 2f * t)
        return POINTS[k + 1] + (POINTS[k + 3] - POINTS[k + 1]) * e
    }
}
