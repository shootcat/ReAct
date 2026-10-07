package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The material chains of the sandbox (Leitfaden V2): tools that change things without being used up,
 * wood quenched into charcoal, heat with and without a flame, metal that carries heat, burning counted
 * in moves.
 */
class MaterialsTest {

    private val world by lazy { Levels.worlds.first() }
    private val drops = Drops(Levels.catalog.types, Levels.catalog.rules, Levels.catalog.merges)

    private fun level(map: List<String>, legend: String = ""): LevelData {
        val rows = map.joinToString(", ") { "\"$it\"" }
        val json = """
            {"id": "m", "title": "M", "world": ${world.world}, "map": [$rows], "placement": "marked",
             "legend": {"S": {"type": "STONE", "isMovable": true}, "t": {"type": "TREE"}${if (legend.isEmpty()) "" else ", $legend"}},
             "goals": [{"type": "extinguish", "text": "Lösche das Feuer"}]}
        """.trimIndent()
        return LevelLoader.parseLevel(json, world)
    }

    @Test
    fun `a stone dropped on a tree fells it into wood and stays where it was`() {
        val start = level(listOf("S..+t...", "%%%%%%%%")).initialState()
        val drop = drops.resolve(start, "stone_0_0", Position(4, 0))
        assertIs<Drop.Merged>(drop)
        assertEquals(Position(0, 0), drop.state.objectById("stone_0_0")?.position, "the stone stays on its own cell")
        val wood = drop.state.objectAt(Position(4, 0))!!
        assertEquals("WOOD", wood.type)
        assertTrue(wood.isMovable, "the felled wood can be carried away")
        assertEquals(2, drop.state.objects.size)
    }

    @Test
    fun `felling counts as one move in live mode`() {
        val level = level(listOf("S..+t...", "%%%%%%%%"))
        val live = Levels.live(level)
        val run = live.play(live.start(), "stone_0_0", Position(4, 0))!!
        assertEquals(1, run.moves.size)
        assertEquals("WOOD", run.state.objectAt(Position(4, 0))?.type)
        assertEquals("STONE", run.state.objectAt(Position(0, 0))?.type)
    }

    @Test
    fun `a tool needs its target on or next to a placement field`() {
        val far = level(listOf("S+..t...", "%%%%%%%%")).initialState()
        assertNull(drops.resolve(far, "stone_0_0", Position(4, 0)))
    }

    @Test
    fun `burning wood quenched in time becomes loose charcoal`() {
        val level = level(listOf("%%%%", "+...", "..W.", "..~.", "%%%%"), "\"W\": {\"type\": \"WOOD\", \"state\": \"BURNING\", \"isMovable\": true}, \"~\": {\"type\": \"WATER\", \"amount\": 3}")
        val after = Levels.engine(level).step(level.initialState()).state
        val coal = after.objects.single { it.type == "CHARCOAL" }
        assertTrue(coal.isMovable)
        assertTrue(after.objects.none { it.type == "WOOD" })
        assertTrue(after.objects.any { it.type == "STEAM" })
    }

    @Test
    fun `a burning tree quenched in time becomes charcoal too`() {
        val level = level(listOf("+...", ".~u.", "%%%%"), "\"~\": {\"type\": \"WATER\", \"amount\": 3}, \"u\": {\"type\": \"TREE\", \"state\": \"BURNING\"}")
        val after = Levels.engine(level).step(level.initialState()).state
        assertEquals("CHARCOAL", after.objectAt(Position(2, 1))?.type)
    }

    @Test
    fun `only an open flame sets wood alight, glowing heat does not`() {
        val legend = "\"W\": {\"type\": \"WOOD\", \"isMovable\": true}, \"F\": {\"type\": \"FIRE\", \"isMovable\": true}, " +
            "\"G\": {\"type\": \"EMBER\"}"
        val flame = level(listOf("+...", ".FW.", "%%%%"), legend)
        val lit = Levels.engine(flame).step(flame.initialState()).state
        assertEquals("BURNING", lit.objectAt(Position(2, 1))?.state)
        val glow = level(listOf("+...", ".GW.", "%%%%"), legend)
        val warm = Levels.engine(glow).step(glow.initialState()).state
        assertEquals("DRY", warm.objectAt(Position(2, 1))?.state, "embers are hot but have no flame")
    }

    @Test
    fun `dropping wood next to glowing heat is no reaction, next to a flame it is`() {
        val legend = "\"W\": {\"type\": \"WOOD\", \"isMovable\": true}, \"f\": {\"type\": \"FIRE\"}, \"G\": {\"type\": \"EMBER\"}"
        val start = level(listOf("W.+f+G..", "%%%%%%%%"), legend).initialState()
        assertIs<Drop.NextTo>(drops.resolve(start, "wood_0_0", Position(3, 0)))
        assertNull(drops.resolve(start, "wood_0_0", Position(5, 0)), "wood dropped on embers bounces off")
    }

    private val hot = "\"G\": {\"type\": \"EMBER\"}, \"M\": {\"type\": \"METAL\"}, \"W\": {\"type\": \"WOOD\"}, " +
        "\"i\": {\"type\": \"ICE\"}"

    private fun steps(level: LevelData, n: Int, from: GameState = level.initialState()): GameState {
        val engine = Levels.engine(level)
        var state = from
        repeat(n) { state = engine.step(state).state }
        return state
    }

    @Test
    fun `metal next to embers gets hot, passes the heat on and still sets nothing alight`() {
        val level = level(listOf("+.....", ".GMMW.", "%%%%%%"), hot)
        val after = steps(level, 6)
        val metal = after.objects.filter { it.type == "METAL" }
        assertEquals(listOf("HOT", "HOT"), metal.map { it.state }, "the metal touching the embers and the metal behind it")
        assertTrue(metal.all { it.heatOutput > 0 && !it.isFlame })
        assertEquals("DRY", after.objectAt(Position(4, 1))?.state, "hot metal has no flame")
    }

    @Test
    fun `hot metal stuck in a wall slot heats what lies behind the wall`() {
        val level = level(listOf("+.....", "..G...", "%%M%%%", "%%%%%%", "%%i%%%", "%%%%%%"), hot)
        val after = steps(level, 6)
        assertEquals("HOT", after.objectAt(Position(2, 2))?.state)
        assertTrue(after.objects.none { it.type == "ICE" }, "the ice behind the wall melted")
        assertTrue(after.objects.any { it.type == "WATER" })
    }

    @Test
    fun `heat does not pass a wall without metal in the slot`() {
        val embers = level(listOf("+.....", "......", "%%G%%%", "%%%%%%", "%%i%%%", "%%%%%%"), hot)
        assertEquals("ICE", steps(embers, 10).objectAt(Position(2, 4))?.type, "embers in the slot only heat what they touch")
        val onFloor = level(listOf("+.....", ".GM...", "%%%%%%", "%%i%%%", "%%%%%%"), hot)
        val floor = steps(onFloor, 10)
        assertEquals("HOT", floor.objectAt(Position(2, 1))?.state)
        assertEquals("ICE", floor.objectAt(Position(2, 3))?.type, "metal lying on the floor is not stuck in a slot")
        val thick = level(listOf("+.....", "..G...", "%%M%%%", "%%%%%%", "%%%%%%", "%%i%%%", "%%%%%%"), hot)
        assertEquals("ICE", steps(thick, 10).objectAt(Position(2, 5))?.type, "one wall cell, never two")
    }

    @Test
    fun `hot metal cools down a few steps after its heat source is gone`() {
        val level = level(listOf("+....", ".GM..", "%%%%%"), hot)
        val heated = steps(level, 6)
        assertEquals("HOT", heated.objectAt(Position(2, 1))?.state)
        val engine = Levels.engine(level)
        var state = heated.copy(objects = heated.objects.filter { it.type != "EMBER" })
        var cooledAfter = 0
        while (state.objectAt(Position(2, 1))?.state == "HOT" && cooledAfter < 50) {
            state = engine.step(state).state
            cooledAfter++
        }
        assertEquals("COLD", state.objectAt(Position(2, 1))?.state)
        assertTrue(cooledAfter in 3..12, "cooled after $cooledAfter steps")
    }

    private val burn = "\"F\": {\"type\": \"FIRE\", \"isMovable\": true}, \"W\": {\"type\": \"WOOD\", \"isMovable\": true}, " +
        "\"~\": {\"type\": \"WATER\", \"amount\": 3, \"isMovable\": true}, \"s\": {\"type\": \"SNOW\", \"isMovable\": true}, " +
        "\"B\": {\"type\": \"WOOD\", \"state\": \"BURNING\", \"isMovable\": true}"

    @Test
    fun `wood burns through the move it caught fire in and one more, then crumbles to ash`() {
        val level = level(listOf("F..+W..S+.", "%%%%%%%%%%"), burn)
        val live = Levels.live(level)
        val lit = live.play(live.start(), "fire_0_0", Position(3, 0))!!
        val wood = lit.state.objectAt(Position(4, 0))!!
        assertEquals("BURNING", wood.state, "a whole move of settling does not burn it down")
        assertEquals(1, wood.burnMovesLeft, "one move left to put it out")
        val later = live.play(lit, "stone_7_0", Position(8, 0))!!
        assertTrue(later.state.objects.none { it.type == "WOOD" })
        val ash = later.state.objectAt(Position(4, 0))!!
        assertEquals("ASH", ash.type)
        assertTrue(ash.isMovable)
        assertEquals("ACTIVE", later.state.objectAt(Position(3, 0))?.state, "the fire itself burns on")
    }

    @Test
    fun `a burning tree can still be put out two moves after it caught fire`() {
        val level = level(listOf("F.+t+..S+.~+", "%%%%%%%%%%%%"), burn)
        val live = Levels.live(level)
        val lit = live.play(live.start(), "fire_0_0", Position(2, 0))!!
        assertEquals(2, lit.state.objectAt(Position(3, 0))?.burnMovesLeft)
        val waited = live.play(lit, "stone_7_0", Position(8, 0))!!
        assertEquals(1, waited.state.objectAt(Position(3, 0))?.burnMovesLeft)
        val doused = live.play(waited, "water_10_0", Position(4, 0))!!
        assertEquals("CHARCOAL", doused.state.objectAt(Position(3, 0))?.type)
        val tooLate = live.play(waited, "stone_7_0", Position(11, 0))!!
        assertEquals("ASH", tooLate.state.objectAt(Position(3, 0))?.type)
    }

    @Test
    fun `boiling sea water gives plain steam whose cloud rains fresh water`() {
        val level = level(
            listOf("%%%%%%%%%%", "+.........", "..........", "..........", "..........", "%G~~~~~~~%", "%%%%%%%%%%"),
            "\"G\": {\"type\": \"EMBER\"}, \"~\": {\"type\": \"SEAWATER\"}",
        )
        val engine = Levels.engine(level)
        var state = level.initialState()
        val seen = mutableSetOf<String>()
        repeat(150) {
            state = engine.step(state).state
            seen += state.objects.map { it.type }
            assertTrue(state.objects.none { it.type == "SEAWATER" && it.position.y < 5 }, "no salt water above the sea")
        }
        assertEquals(setOf("EMBER", "SEAWATER", "STEAM", "CLOUD", "WATER"), seen, "plain steam, a cloud and its fresh rain")
        assertTrue(state.objects.any { it.isRaining })
    }

    @Test
    fun `snow next to burning wood melts and its water puts the wood out in the same move`() {
        val level = level(listOf("s..+B..", "%%%%%%%"), burn)
        val live = Levels.live(level)
        val run = live.play(live.start(), "snow_0_0", Position(3, 0))!!
        assertEquals(1, run.moves.size)
        assertTrue(run.state.objects.none { it.type == "SNOW" || it.type == "WOOD" || it.type == "ASH" })
        assertEquals("CHARCOAL", run.state.objectAt(Position(4, 0))?.type)
    }

    private val stuff = "\"F\": {\"type\": \"FIRE\", \"isMovable\": true}, \"C\": {\"type\": \"CHARCOAL\", \"isMovable\": true}, " +
        "\"O\": {\"type\": \"ORE\", \"isMovable\": true}, \"E\": {\"type\": \"EARTH\", \"isMovable\": true}, " +
        "\"~\": {\"type\": \"WATER\", \"amount\": 3, \"isMovable\": true}, \"G\": {\"type\": \"EMBER\"}, " +
        "\"X\": {\"type\": \"SMELT\"}, \"s\": {\"type\": \"SAND\"}, \"h\": {\"type\": \"HUT\"}, " +
        "\"n\": {\"type\": \"SNOW\"}, \"L\": {\"type\": \"SALT\", \"isMovable\": true}"

    @Test
    fun `fire dropped on charcoal is used up and leaves glowing embers`() {
        val start = level(listOf("F..+C.", "%%%%%%"), stuff).initialState()
        val drop = drops.resolve(start, "fire_0_0", Position(4, 0))
        assertIs<Drop.Merged>(drop)
        assertEquals(listOf("EMBER"), drop.state.objects.map { it.type })
        val ember = drop.state.objects.single()
        assertTrue(ember.isMovable && ember.heatOutput >= 6 && !ember.isFlame, "hot enough to boil, but no flame")
    }

    @Test
    fun `ore dropped on charcoal becomes smelt, which melts to metal next to embers`() {
        val start = level(listOf("O..+C.", "%%%%%%"), stuff).initialState()
        val drop = drops.resolve(start, "ore_0_0", Position(4, 0))
        assertIs<Drop.Merged>(drop)
        assertEquals(listOf("SMELT"), drop.state.objects.map { it.type })
        val melt = level(listOf("+....", ".GX..", "%%%%%"), stuff)
        assertEquals("METAL", steps(melt, 2).objectAt(Position(2, 1))?.type)
    }

    @Test
    fun `smelt next to embers turns to metal, gets hot and melts the sand beside it to glass`() {
        val level = level(listOf("+.....", ".GXs..", "%%%%%%"), stuff)
        val after = steps(level, 10)
        assertEquals("METAL", after.objectAt(Position(2, 1))?.type)
        assertEquals("HOT", after.objectAt(Position(2, 1))?.state)
        val glass = after.objectAt(Position(3, 1))!!
        assertEquals("GLASS", glass.type)
        assertTrue(glass.isMovable)
    }

    @Test
    fun `sand melts next to embers, but not next to a fire`() {
        val embers = level(listOf("+....", ".Gs..", "%%%%%"), stuff)
        assertEquals("GLASS", steps(embers, 3).objectAt(Position(2, 1))?.type)
        val fire = level(listOf("+....", ".Fs..", "%%%%%"), stuff)
        assertEquals("SAND", steps(fire, 3).objectAt(Position(2, 1))?.type)
    }

    @Test
    fun `earth and a puddle make mud, and the water is gone`() {
        val start = level(listOf("E..+~.", "%%%%%%"), stuff).initialState()
        val drop = drops.resolve(start, "earth_0_0", Position(4, 0))
        assertIs<Drop.Merged>(drop)
        assertEquals(listOf("MUD"), drop.state.objects.map { it.type })
        val back = drops.resolve(level(listOf("~..+E.", "%%%%%%"), stuff).initialState(), "water_0_0", Position(4, 0))
        assertIs<Drop.Merged>(back)
        assertEquals(listOf("MUD"), back.state.objects.map { it.type })
    }

    @Test
    fun `a hut catches fire from a flame only, and water leaves it charred`() {
        val flame = level(listOf("+....", ".Fh..", "%%%%%"), stuff)
        val lit = steps(flame, 2).objectAt(Position(2, 1))!!
        assertEquals("BURNING", lit.state)
        assertEquals(3, lit.burnMovesLeft)
        val glow = level(listOf("+....", ".Gh..", "%%%%%"), stuff)
        assertEquals("INTACT", steps(glow, 5).objectAt(Position(2, 1))?.state)
        val doused = level(listOf("+....", ".~u..", "%%%%%"), "$stuff, \"u\": {\"type\": \"HUT\", \"state\": \"BURNING\"}")
        assertEquals("CHARRED", steps(doused, 2).objectAt(Position(2, 1))?.state)
    }

    @Test
    fun `snow melts next to embers, and salt melts snow but is used up`() {
        val embers = level(listOf("+....", ".Gn..", "%%%%%"), stuff)
        val melted = steps(embers, 2)
        assertTrue(melted.objects.none { it.type == "SNOW" })
        assertTrue(melted.objects.any { it.type == "WATER" })
        val salted = level(listOf("+....", ".Ln..", "%%%%%"), stuff)
        val after = steps(salted, 2)
        assertTrue(after.objects.none { it.type == "SNOW" || it.type == "SALT" })
        assertTrue(after.objects.any { it.type == "WATER" })
    }
}
