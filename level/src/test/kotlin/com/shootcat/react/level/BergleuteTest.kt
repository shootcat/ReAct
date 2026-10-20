package com.shootcat.react.level

import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.Flag
import com.shootcat.react.sim.Folk
import com.shootcat.react.sim.MaterialTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 6, "Bergleute" (Phase 5c): four miners shut in a gallery deep in the mountain. The adit that leads out is
 * buried by a fall of rubble; behind it firedamp has gathered, and more seeps from cracks in the gallery's roof. In
 * the mouth of the adit, right in front of the rubble, the shepherds' fire burns in a pit of tar; in the village
 * below a well and a heap of sand. Only the bucket.
 *
 * Firedamp let out through the rubble meets the fire at once (2d): the flash runs back through the whole cloud, and
 * miners in it faint and are brought back to where they were shut in (5c: a rescue level never brings a fainted one
 * into the village). What seeps in afterwards burns on at the mouth, a flame of gas; water poured into it only turns
 * to steam. Put out first (water or sand from the village, or rubble from the fall itself), the fire lets the gas go.
 */
class BergleuteTest {
    private val mt = MaterialTable.load()

    private fun play() = Play(Levels.load("bergleute"), mt)

    /** What happened in a run: the result, how often the gas went off, how often miners fainted. */
    private class Run(val play: Play, val result: Result?, val blasts: Int, val faints: Int)

    /** Plays [script] to [limit] seconds, watching for the gas going off; keeps a picture of the end. */
    private fun solve(name: String, script: String, limit: Int = 120): Run {
        val play = play()
        val w = play.session.world
        val backdrop = Backdrop.of(w)
        val fire = mt.id("fire")
        var blasts = 0
        var flames = 0
        val check = play.session.onStep
        play.session.onStep = { s ->
            check?.invoke(s)
            // a flash: suddenly many cells of fire inside the adit
            if (w.step % 10 == 0) {
                var n = 0
                for (y in 30..100) for (x in 40..110) if (w.mat[y * w.width + x].toInt() == fire) n++
                if (n > 20 && flames <= 20) blasts++
                flames = n
            }
        }
        val result = play.perform(Action.parse(script), limitSeconds = limit)
        Pictures.write(w, backdrop, "level6_$name", scale = 3)
        val faints = w.folk.figures().sumOf { it.faints }
        println("$name: ${play.state}, ${result ?: "${w.step / 60f} s"}, ${play.actions} actions, $blasts blasts, $faints faints, " +
            "home ${(0 until w.folk.count).count { w.folk.home(it) }}")
        return Run(play, result, blasts, faints)
    }

    private fun burning(play: Play): Int {
        val w = play.session.world
        var n = 0
        for (y in 58..68) for (x in 56..63) if (w.flags[y * w.width + x].toInt() and Flag.BURNING != 0) n++
        return n
    }

    @Test
    fun `the level is as painted, and nobody gets out by himself`() {
        val play = play()
        val w = play.session.world
        Pictures.write(w, Backdrop.of(w), "level6_start", scale = 3)
        assertEquals(4, w.folk.count, "four miners")
        assertTrue(w.folk.backToStart, "a fainted miner is brought back to where he was shut in")
        assertTrue(w.count(mt.id("rubble")) >= 55, "the fall of rubble")
        val gas = w.count(mt.id("firedamp"))
        assertTrue(gas > 100, "the firedamp behind it: $gas")
        repeat(30) { play.session.tick() }
        assertTrue(burning(play) > 0, "the fire on the cliff glows")
        val run = solve("ohne", "", limit = 120)
        assertNull(run.result)
        assertEquals(0, (0 until w.folk.count).count { w.folk.home(it) }, "nobody in the village")
        assertTrue(w.count(mt.id("firedamp")) > gas, "more firedamp has seeped in: ${w.count(mt.id("firedamp"))}")
        assertTrue(burning(run.play) > 0, "the fire still glows after two minutes")
    }

    /** Water from the well onto the fire: one bucket. */
    private val douse = "scoop 16 72 3 20\npour 59 62 2 20\n"

    /** Sand from the heap in the village onto the fire: one bucket. */
    private val smother = "scoop 6 68 3 20\npour 59 62 2 20\n"

    /** The rubble out of the adit, three buckets tipped out over the valley. */
    private val dig = "scoop 64 63 3 20\npour 30 20 3 20\nscoop 67 63 3 20\npour 30 20 3 20\nscoop 71 63 3 20\npour 30 20 3 20\n"

    private fun home(run: Run) = (0 until run.play.session.world.folk.count).count { run.play.session.world.folk.home(it) }

    @Test
    fun `the front of the fall tipped onto the fire, then the rest dug out, gold`() {
        val run = solve("schutt_aufs_feuer", "scoop 64 63 3 20\npour 59 62 2 20\nscoop 67 63 3 20\npour 30 20 3 20\nscoop 71 63 3 20\npour 30 20 3 20\n")
        assertNotNull(run.result)
        assertEquals(0, run.blasts, "the gas went out harmlessly")
        assertEquals(0, run.faints)
        assertEquals(Medal.GOLD, run.result!!.medal)
    }

    @Test
    fun `the fire put out with water from the well, then the rubble dug out`() {
        val run = solve("loeschen_graben", douse + dig)
        assertNotNull(run.result)
        assertEquals(0, run.blasts, "the gas went out harmlessly")
        assertEquals(0, run.faints)
        assertEquals(Medal.SILVER, run.result!!.medal)
    }

    @Test
    fun `the fire smothered with sand from the heap in the village, then the rubble dug out`() {
        val run = solve("ersticken_graben", smother + dig)
        assertNotNull(run.result)
        assertEquals(0, run.blasts)
        assertEquals(0, run.faints)
        assertEquals(Medal.SILVER, run.result!!.medal)
    }

    @Test
    fun `dug out with the fire burning, the gas goes off, then sand smothers the fire and the flame of gas, the miners come out`() {
        val run = solve("graben_explosion_ersticken", dig + smother)
        assertNotNull(run.result)
        assertTrue(run.blasts >= 1, "the firedamp went off at the fire")
        assertEquals(0, burning(run.play), "the fire is out")
    }

    @Test
    fun `dug out with the fire left burning, the gas burns on at the mouth and the miners do not walk past it`() {
        val run = solve("graben_explosion", dig)
        assertNull(run.result)
        assertTrue(run.blasts >= 1)
        assertTrue(burning(run.play) > 0, "it still burns")
        assertEquals(0, home(run))
    }

    @Test
    fun `water poured into the burning gas turns to steam, the fire burns on`() {
        val run = solve("wasser_ins_gasfeuer", dig + douse)
        assertNull(run.result)
        assertTrue(run.blasts >= 1)
        assertTrue(burning(run.play) > 0, "the water did not put it out")
        assertEquals(0, home(run))
    }

    @Test
    fun `the same actions give the same mine`() {
        val a = solve("gleich_a", dig, limit = 30)
        val b = solve("gleich_b", dig, limit = 30)
        assertEquals(a.play.session.world.hash(), b.play.session.world.hash())
    }
}
