package com.shootcat.react.sim

/**
 * How bright it is everywhere in the world (Leitfaden V3, 4.2), in blocks of 2 × 2 cells, from 0 (dark) to
 * 1 (daylight).
 *
 * Sky light falls straight down from the top of the world. Air passes it whole; water, glass, leaves and
 * smoke dim it a little per cell (`light_pass`); solid ground, powders and rock stop it. From there it
 * scatters into every direction and fades slowly, the way daylight fills the shade under a bridge or a
 * tree and reaches some way into a cave mouth. Fire, embers, lava, molten and glowing hot material and
 * fireflies give off light of their own, which fades faster. A cave without an opening and without a light
 * source is really dark.
 *
 * Only additions, multiplications and comparisons: the same world gives the same light on every device.
 * The simulation keeps one field for the plants and animals; the renderer computes its own for drawing.
 */
class Light(private val world: World) {

    val width = (world.width + 1) / 2
    val height = (world.height + 1) / 2

    /** Light arriving in each block, 0..1. */
    val level = FloatArray(width * height)

    /** Light each block gives off itself. */
    val glow = FloatArray(width * height)

    /** Share of light that crosses each block. */
    private val pass = FloatArray(width * height)

    /** Light leaving each block: what passes through it, or its own glow. */
    private val out = FloatArray(width * height)

    /** Light of the light sources alone, spread separately because it fades faster than daylight. */
    private val lamp = FloatArray(width * height)
    private val lampOut = FloatArray(width * height)

    private val mt = world.materials

    /** The light at cell (x, y). */
    fun at(x: Int, y: Int): Float = level[(y shr 1) * width + (x shr 1)]

    /** How bright block [b] looks: the light falling on it, or its own glow. */
    fun shown(b: Int): Float = if (glow[b] > level[b]) glow[b] else level[b]

    fun compute() {
        val glowing = gather()
        sky()
        repeat(ROUNDS) {
            forward(level, out, SKY_DECAY, SKY_DECAY_DIAGONAL)
            backward(level, out, SKY_DECAY, SKY_DECAY_DIAGONAL)
        }
        if (!glowing) return
        lamp.fill(0f)
        glow.copyInto(lampOut)
        repeat(ROUNDS) {
            forward(lamp, lampOut, DECAY, DECAY_DIAGONAL)
            backward(lamp, lampOut, DECAY, DECAY_DIAGONAL)
        }
        for (b in level.indices) if (lamp[b] > level[b]) level[b] = lamp[b]
    }

    /**
     * What each block lets through and how much it glows by itself; returns whether anything glows. Light
     * crosses a block either sideways (through both cells of a row) or up and down (through both cells of
     * a column); the block passes the smaller of the two, so a wall of solid cells stops light wherever it
     * lies in the grid.
     */
    private fun gather(): Boolean {
        val w = world.width
        val h = world.height
        val mat = world.mat
        val lp = mt.lightPass
        var glowing = false
        for (by in 0 until height) {
            val y0 = by * 2
            val y1 = if (y0 + 1 < h) y0 + 1 else y0
            var k = by * width
            for (bx in 0 until width) {
                val x0 = bx * 2
                val x1 = if (x0 + 1 < w) x0 + 1 else x0
                val a = y0 * w + x0
                val b = y0 * w + x1
                val c = y1 * w + x0
                val d = y1 * w + x1
                val pa = lp[mat[a].toInt()]
                val pb = lp[mat[b].toInt()]
                val pc = lp[mat[c].toInt()]
                val pd = lp[mat[d].toInt()]
                val across = (pa * pb + pc * pd) * 0.5f
                val down = (pa * pc + pb * pd) * 0.5f
                pass[k] = if (across < down) across else down
                var e = cellGlow(a)
                var f = cellGlow(b)
                if (f > e) e = f
                f = cellGlow(c)
                if (f > e) e = f
                f = cellGlow(d)
                if (f > e) e = f
                glow[k] = e
                if (e > 0f) glowing = true
                k++
            }
        }
        return glowing
    }

    /** Light a cell gives off: its own, as it burns, or as it glows with heat (hot metal, sand, rock). */
    private fun cellGlow(i: Int): Float {
        val m = world.mat[i].toInt()
        var e = mt.glow[m]
        val t = world.temp[i]
        val burning = world.flags[i].toInt() and Flag.BURNING != 0
        if (!burning && t <= HOT) return e
        if (burning) {
            val b = mt.emitsLight[m]
            if (b > e) e = b
            if (BURNING > e) e = BURNING
        }
        if (t > HOT && mt.kind[m] != Kind.GAS) {
            var hot = (t - HOT) * HOT_SCALE
            if (hot > HOT_MAX) hot = HOT_MAX
            if (hot > e) e = hot
        }
        return e
    }

    /** Daylight from the top, straight down until something stops it. */
    private fun sky() {
        for (bx in 0 until width) {
            var s = 1f
            var b = bx
            for (by in 0 until height) {
                level[b] = s
                s *= pass[b]
                out[b] = s
                b += width
            }
        }
    }

    /** Spreads light ([into], leaving blocks as [out]) rightwards and downwards, fading by [d] per block. */
    private fun forward(into: FloatArray, out: FloatArray, d: Float, dd: Float) {
        val w = width
        for (by in 0 until height) {
            var b = by * w
            for (bx in 0 until w) {
                var c = if (bx > 0) out[b - 1] * d else 0f
                if (by > 0) {
                    var v = out[b - w] * d
                    if (v > c) c = v
                    if (bx > 0) {
                        v = out[b - w - 1] * dd
                        if (v > c) c = v
                    }
                    if (bx < w - 1) {
                        v = out[b - w + 1] * dd
                        if (v > c) c = v
                    }
                }
                if (c > into[b]) take(into, out, b, c)
                b++
            }
        }
    }

    /** Spreads light leftwards and upwards. */
    private fun backward(into: FloatArray, out: FloatArray, d: Float, dd: Float) {
        val w = width
        for (by in height - 1 downTo 0) {
            var b = by * w + w - 1
            for (bx in w - 1 downTo 0) {
                var c = if (bx < w - 1) out[b + 1] * d else 0f
                if (by < height - 1) {
                    var v = out[b + w] * d
                    if (v > c) c = v
                    if (bx > 0) {
                        v = out[b + w - 1] * dd
                        if (v > c) c = v
                    }
                    if (bx < w - 1) {
                        v = out[b + w + 1] * dd
                        if (v > c) c = v
                    }
                }
                if (c > into[b]) take(into, out, b, c)
                b--
            }
        }
    }

    /** Light [c] arrives in block [b]: it is lit that much, and passes on what gets through. */
    private fun take(into: FloatArray, out: FloatArray, b: Int, c: Float) {
        into[b] = c
        val o = c * pass[b]
        if (o > out[b]) out[b] = o
    }

    companion object {
        /** Light of a source left after one block (two cells) of open air. */
        const val DECAY = 0.86f
        const val DECAY_DIAGONAL = 0.81f

        /** Daylight scatters further: what is left of it after one block of air off its straight way down. */
        const val SKY_DECAY = 0.95f
        const val SKY_DECAY_DIAGONAL = 0.93f

        /** Forward and backward sweeps: two rounds carry light round a corner or two. */
        private const val ROUNDS = 2

        /** Light of burning material. */
        private const val BURNING = 0.7f

        /** Glowing heat: from this temperature on, brighter the hotter. */
        private const val HOT = 480f
        private const val HOT_SCALE = 1f / 700f
        private const val HOT_MAX = 0.9f
    }
}
