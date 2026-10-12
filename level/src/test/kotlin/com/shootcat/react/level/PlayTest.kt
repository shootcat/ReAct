package com.shootcat.react.level

import com.shootcat.react.sim.Flag
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The level format, goals and rating on a small hand-made level: a pool of water on the left, an empty
 * basin on the right, an overhang of anchored earth, a heap of sand.
 */
class PlayTest {
    private val mt = MaterialTable.load()
    private val w = 80
    private val h = 40

    private fun colour(name: String) = (0xFF shl 24) or (mt.color[mt.id(name)] and 0xFFFFFF)

    /** The ground painted cell by cell, as a painting program would. */
    private fun ground(): Pair<Picture, Picture> {
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        val mask = IntArray(w * h) { 0xFF000000.toInt() }
        fun put(x: Int, y: Int, m: String, anchored: Boolean = false) {
            px[y * w + x] = colour(m)
            if (anchored) mask[y * w + x] = -1
        }
        for (x in 0 until w) for (y in 32 until h) put(x, y, "rock")
        // the pool on the left, walled in
        for (y in 20 until 32) {
            put(0, y, "rock")
            put(21, y, "rock")
        }
        for (y in 22 until 32) for (x in 1 until 21) put(x, y, "water")
        // the basin on the right
        for (y in 24 until 32) {
            put(50, y, "rock")
            put(71, y, "rock")
        }
        // an overhang of anchored earth on a rock pillar, and a loose column of earth beside it
        for (y in 10 until 32) put(30, y, "rock")
        for (x in 31 until 40) for (y in 10 until 13) put(x, y, "earth", anchored = true)
        for (y in 10 until 13) put(44, y, "earth")
        // sand on the floor between
        for (x in 24 until 28) for (y in 29 until 32) put(x, y, "sand")
        return Picture(w, h, px) to Picture(w, h, mask)
    }

    private fun spec(goals: List<GoalSpec>, tools: Tools = Tools(bucket = true, bucketSize = 60)) = LevelSpec(
        id = "test",
        name = "Test",
        area = "Wald",
        tier = "leicht",
        seed = 7,
        tools = tools,
        goals = goals,
        rating = Rating(Limit(20, 8), Limit(40, 16), Limit(80, 30)),
    )

    private val basin = listOf(listOf(51, 24), listOf(71, 24), listOf(71, 32), listOf(51, 32))
    private val fillBasin = GoalSpec("fill", "Fülle das Becken mit Wasser", basin, material = "water", share = 0.5f, hold = 1f)

    private fun level(goals: List<GoalSpec> = listOf(fillBasin), tools: Tools = Tools(bucket = true, bucketSize = 60)): Level {
        val (g, m) = ground()
        return Level(spec(goals, tools), g, m)
    }

    @Test
    fun `the colours of the ground are its materials, the mask anchors loose ground`() {
        val play = Play(level(), mt)
        val world = play.session.world
        assertEquals(mt.id("water"), world.material(5, 25))
        assertEquals(mt.id("rock"), world.material(30, 20))
        assertEquals(MaterialTable.AIR, world.material(60, 20))
        assertTrue(world.flags[world.index(35, 11)].toInt() and Flag.ANCHORED != 0)
        repeat(300) { play.session.tick() }
        // the anchored overhang holds over the air, the loose column has fallen
        for (x in 31 until 40) for (y in 10 until 13) assertEquals(mt.id("earth"), world.material(x, y), "overhang at ($x, $y)")
        assertEquals(MaterialTable.AIR, world.material(44, 10))
        assertEquals(mt.id("earth"), world.material(44, 31))
        // anchored ground cannot be scooped up
        play.session.press(35, 12, Session.SCOOP, 2)
        play.session.release()
        assertEquals(0, play.session.bucketAmount)
    }

    @Test
    fun `a colour that is no material is refused`() {
        val (g, m) = ground()
        g.argb[5] = 0xFF123456.toInt()
        val e = assertFailsWith<IllegalStateException> { Play(Level(spec(listOf(fillBasin)), g, m), mt) }
        assertTrue("#123456" in e.message!!)
    }

    @Test
    fun `water carried into the basin fills it, the goal counts once it has held, the actions are counted`() {
        val play = Play(level(), mt)
        val trips = Action.parse(
            """
            # five buckets from the pool into the basin
            scoop 10 27 4 30
            pour 60 20 3 30
            scoop 10 28 4 30
            pour 60 20 3 30
            scoop 10 29 4 30
            pour 60 20 3 30
            scoop 10 30 4 30
            pour 60 20 3 30
            """.trimIndent(),
        )
        val result = assertNotNull(play.perform(trips, limitSeconds = 60))
        assertEquals(Play.State.WON, play.state)
        // half the basin is 80 cells: more than one bucket of 60, and some water soaks away on the way;
        // once the level is won, the rest of the list is not played
        assertTrue(result.actions in 4..7, "${result.actions} actions")
        assertEquals(Play.countActions(play.session.log), result.actions)
        assertTrue(play.goals[0].met(play.session.world))
        // two trips of a second each at least, and the goal held for a second
        assertTrue(result.seconds > 2f, "it took ${result.seconds} s")
        assertEquals(play.rate(result.seconds, result.actions), result.medal)
    }

    @Test
    fun `the medal goes by time and actions together`() {
        val play = Play(level(), mt)
        assertEquals(Medal.GOLD, play.rate(19f, 8))
        assertEquals(Medal.SILVER, play.rate(19f, 9))
        assertEquals(Medal.SILVER, play.rate(39f, 16))
        assertEquals(Medal.BRONZE, play.rate(41f, 4))
        assertEquals(Medal.NONE, play.rate(81f, 4))
        assertEquals(Medal.NONE, play.rate(10f, 31))
    }

    @Test
    fun `undo goes back before the win and the level is open again`() {
        val play = Play(level(), mt)
        play.perform(Action.parse("scoop 10 27 4 30\npour 60 20 3 30\nscoop 10 28 4 30\npour 60 20 3 30\n"), limitSeconds = 30)
        assertEquals(Play.State.WON, play.state)
        play.session.undo()
        repeat(Play.CHECK_EVERY + 1) { play.session.tick() }
        assertEquals(Play.State.PLAYING, play.state)
        assertNull(play.result)
        assertEquals(3, play.actions)
    }

    @Test
    fun `a guarded thing that is taken away loses the level`() {
        val heap = listOf(listOf(23, 28), listOf(29, 28), listOf(29, 32), listOf(23, 32))
        val play = Play(level(listOf(fillBasin, GoalSpec("protect", "Lass den Sand liegen", heap, material = "sand", amount = 10))), mt)
        repeat(30) { play.session.tick() }
        assertEquals(Play.State.PLAYING, play.state)
        play.perform(Action.parse("scoop 25 30 3 30"), limitSeconds = 10)
        assertEquals(Play.State.LOST, play.state)
    }

    @Test
    fun `only the tools of the level can be used`() {
        val play = Play(level(tools = Tools(bucket = true, bucketSize = 20)), mt)
        assertFailsWith<IllegalStateException> { play.perform(Action.parse("hand 25 30")) }
        assertFailsWith<IllegalStateException> { play.perform(Action.parse("line wood 52 20 70 20 1")) }
        // the bucket holds what the level says
        play.perform(Action.parse("scoop 10 27 4 30"), limitSeconds = 1)
        assertEquals(20, play.session.bucketAmount)
        assertEquals(Session.Supply.PROCURE, play.session.supply)
    }

    @Test
    fun `the same actions give the same result`() {
        val list = Action.parse("scoop 10 27 4 30\npour 60 20 3 30\nwait 30\nscoop 10 28 4 30\npour 64 20 3 30\n")
        val a = Play(level(), mt).also { it.perform(list, 30) }
        val b = Play(level(), mt).also { it.perform(list, 30) }
        assertEquals(a.result, b.result)
        assertEquals(a.session.world.hash(), b.session.world.hash())
    }

    @Test
    fun `action lists are read line by line`() {
        val list = Action.parse(
            """
            wait 5            # a comment
            scoop 1 2
            pour 3 4 5 6
            hand 7 8
            store
            frame brick 1 2 3 4 2
            paint wood 5 6
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Action.Wait(5), Action.Scoop(1, 2), Action.Pour(3, 4, 5, 6), Action.Hand(7, 8), Action.Store,
                Action.Build(Session.ShapeKind.FRAME, "brick", 1, 2, 3, 4, 2), Action.Paint("wood", 5, 6),
            ),
            list,
        )
        assertFailsWith<IllegalStateException> { Action.parse("jump 1 2") }
    }
}
