package com.shootcat.react.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4, body physics ("Körperphysik, schlanke Fassung"): loose structures are rigid bodies. They fall, tip
 * over, turn and roll (1 to 3), float or sink by their density (4), break when they hit hard (5), stay cells
 * of the material world (6) and the hand can take them (7).
 */
class BodyTest {

    private fun cells(world: World, name: String): List<Pair<Int, Int>> {
        val m = Lab.materials.id(name)
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until world.height) for (x in 0 until world.width) if (world.material(x, y) == m) out += x to y
        return out
    }

    private fun session(w: Int, h: Int, build: (World) -> Unit) = Session(Lab.materials, w, h, 7L, build).also { it.begin(Session.Supply.UNLIMITED) }

    private fun rockFloor(w: World, from: Int) {
        val rock = Lab.materials.id("rock")
        for (y in from until w.height) for (x in 0 until w.width) w.set(x, y, rock)
    }

    // ------------------------------------------------------------------ 1. fall, tip, turn, roll

    @Test
    fun `a bar laid slanting on the rock floor falls over and lies flat`() {
        // the bug report: slanting metal moved instead of falling over
        val s = session(160, 70) { rockFloor(it, 60) }
        s.shape(Session.ShapeKind.LINE, 60, 59, 80, 35, Lab.materials.id("metal"), 1)
        val before = cells(s.world, "metal")
        repeat(240) { s.tick() }
        val after = cells(s.world, "metal")
        Pictures.write(s.world, IntArray(s.world.size) { 0xFF9CC3E0.toInt() }, "body_bar_fallen", scale = 4)
        assertEquals(before.size, after.size, "nothing lost")
        assertTrue(before.maxOf { it.second } - before.minOf { it.second } > 20, "it stood slanting")
        assertTrue(after.maxOf { it.second } - after.minOf { it.second } <= 4, "it lies flat: rows ${after.minOf { it.second }}..${after.maxOf { it.second }}")
        assertEquals(59, after.maxOf { it.second }, "on the floor")
        assertTrue(after.minOf { it.first } in 50..70, "where it stood, not slid away: ${after.minOf { it.first }}")
        assertTrue(s.world.bodyStates().all { it.asleep }, "and lies still")
    }

    @Test
    fun `a block whose weight is beside its foot tips over`() {
        val lab = Lab()
        // a post of metal with a heavy head to one side
        lab.rect("metal", 30, 40, 30, 61)
        lab.rect("metal", 31, 38, 37, 40)
        lab.seconds(3f)
        val m = cells(lab.world, "metal")
        lab.picture("body_tipped")
        assertTrue(m.minOf { it.second } > 50, "it fell over: top at ${m.minOf { it.second }}")
        assertTrue(m.maxOf { it.first } > 40, "to the side of its weight: ${m.maxOf { it.first }}")
    }

    @Test
    fun `a balanced post stands, even with embers heaped at its foot`() {
        val lab = Lab()
        lab.rect("deadwood", 30, 36, 31, 61)
        lab.seconds(1f)
        lab.rect("ember", 26, 59, 29, 61)
        lab.seconds(2f)
        assertEquals((30..31).flatMap { x -> (36..61).map { x to it } }.toSet(), cells(lab.world, "deadwood").toSet(), "still standing")
    }

    /** A rock slope from the upper left, one row down per [run] columns. */
    private fun slope(lab: Lab, run: Int, top: Int) {
        for (x in 2 until lab.width - 2) lab.rect("rock", x, top + (x - 2) / run, x, lab.height - 3)
    }

    private fun disc(lab: Lab, name: String, cx: Int, cy: Int, r: Int) {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) lab.world.set(x, y, lab.id(name))
    }

    @Test
    fun `a round boulder rolls down a slope of rock`() {
        val lab = Lab(128, 80)
        slope(lab, 3, 20)
        // a boulder resting on the upper part of the slope, touching the steps
        disc(lab, "stone", 12, 20 + (12 - 2) / 3 - 6, 5)
        val start = cells(lab.world, "stone")
        var turned = 0f
        var rolled = 0
        repeat(240) {
            lab.world.step()
            for (b in lab.world.bodyStates()) turned = maxOf(turned, abs(b.angle))
            rolled += lab.world.seen[Seen.ROLL * lab.materials.count + lab.id("stone")]
        }
        val end = cells(lab.world, "stone")
        lab.picture("body_rolled")
        assertEquals(start.size, end.size)
        assertTrue(end.minOf { it.first } > start.minOf { it.first } + 40, "it rolled far down: from x ${start.minOf { it.first }} to ${end.minOf { it.first }}")
        assertTrue(turned > 90f, "turning as it went: $turned°")
        assertTrue(rolled > 0, "the book saw it roll")
    }

    @Test
    fun `a log on a steep slope comes down it, on a gentle one it stays`() {
        fun log(run: Int): Int {
            val lab = Lab(128, 80)
            slope(lab, run, 14)
            // laid onto the slope from just above its upper end
            val y = 14 + (16 - 2) / run - 4
            lab.rect("deadwood", 16, y, 27, y + 2)
            lab.seconds(5f)
            return cells(lab.world, "deadwood").minOf { it.first }
        }
        val steep = log(1)
        val gentle = log(4)
        assertTrue(steep > 40, "down the steep slope: $steep")
        assertTrue(gentle < 24, "on the gentle one it stays (friction): $gentle")
    }

    // ------------------------------------------------------------------ 2. a trunk burnt through tips over

    @Test
    fun `a trunk burnt through at its foot tips over to the side and falls`() {
        val lab = Lab(96, 80)
        lab.rect("earth", 2, 66, 93, 77)
        lab.rect("trunk", 48, 40, 49, 65)
        lab.seconds(0.5f)
        assertEquals(0, lab.world.bodyCount, "a living trunk on the earth stands by its roots")
        // a small fire against its foot
        lab.rect("ember", 44, 63, 47, 65)
        var tipped = 0f
        repeat(480) {
            lab.world.step()
            for (b in lab.world.bodyStates()) if (b.material == lab.id("trunk")) tipped = maxOf(tipped, abs(b.angle))
            if (it == 239) lab.picture("body_trunk_burning")
        }
        assertTrue(tipped > 60f, "the part above tipped over: $tipped°")
    }

    // ------------------------------------------------------------------ 4. floating

    @Test
    fun `wood floats, stone and metal sink`() {
        val lab = Lab()
        lab.rect("water", 2, 30, 61, 61)
        lab.rect("wood", 6, 10, 15, 13)
        lab.rect("stone", 24, 10, 29, 14)
        lab.rect("metal", 40, 10, 45, 12)
        lab.seconds(6f)
        val surface = (50..61).maxOf { x -> (0 until lab.height).first { lab.at(x, it) == "water" } }
        val wood = cells(lab.world, "wood")
        assertTrue(wood.minOf { it.second } < surface && wood.maxOf { it.second } >= surface, "the wood floats in the surface at $surface: ${wood.minOf { it.second }}..${wood.maxOf { it.second }}")
        assertEquals(61, cells(lab.world, "stone").maxOf { it.second }, "the stone lies on the bottom")
        assertEquals(61, cells(lab.world, "metal").maxOf { it.second }, "the metal lies on the bottom")
    }

    @Test
    fun `a raft carries a stone, and a heavier load sinks it`() {
        fun raft(stone: Int): Pair<Int, Int> {
            val lab = Lab()
            lab.rect("water", 2, 30, 61, 61)
            lab.rect("wood", 16, 26, 45, 28)
            lab.seconds(3f)
            lab.rect("stone", 30 - stone / 2, 20, 29 + (stone + 1) / 2, 19 + stone)
            lab.seconds(8f)
            lab.picture("body_raft_$stone")
            return cells(lab.world, "wood").maxOf { it.second } to cells(lab.world, "stone").maxOf { it.second }
        }
        val (woodLight, stoneLight) = raft(3)
        assertTrue(woodLight < 40, "with a small stone the raft floats: bottom at $woodLight")
        assertTrue(stoneLight < woodLight, "and the stone rides on it: $stoneLight")
        // a big stone is too much for it: the raft goes under or tips, the stone sinks
        val (_, stoneHeavy) = raft(8)
        assertTrue(stoneHeavy > 50, "the big stone went down into the water: $stoneHeavy")
    }

    // ------------------------------------------------------------------ 5. breaking

    @Test
    fun `glass breaks when it lands hard, not when it is set down from a little height`() {
        val high = Lab()
        high.rect("glass", 20, 8, 35, 9)
        high.seconds(3f)
        assertEquals(0, high.count("glass"), "dropped from high up it shatters")
        assertTrue(high.count("glass_shards") >= 30)
        val low = Lab()
        low.rect("glass", 20, 58, 35, 59)
        low.seconds(3f)
        assertEquals(32, low.count("glass"), "set down from two cells it holds")
    }

    @Test
    fun `stone stands a fall that breaks glass, ice falling far breaks into pieces`() {
        val lab = Lab()
        lab.rect("stone", 6, 4, 13, 9)
        lab.rect("ice", 30, 4, 39, 11, -10f)
        lab.rect("glass", 50, 4, 57, 5)
        lab.seconds(3f)
        lab.picture("body_falls")
        assertEquals(48, lab.count("stone"), "the stone is whole")
        assertEquals(0, lab.count("glass"), "the glass is in shards")
        assertTrue(lab.count("snow") > 0, "the ice broke: crushed ice where it hit")
        assertTrue(lab.world.bodyStates().count { it.material == lab.id("ice") } >= 2, "into pieces: ${lab.world.bodyStates().map { lab.materials.spec(it.material).id }}")
    }

    @Test
    fun `a boulder breaks the ice over a pond, a pebble does not`() {
        fun drop(r: Int): Pair<Int, Int> {
            val lab = Lab(96, 64)
            lab.world.setAmbient(0f)
            // the banks as cold as the air: warmer rock would melt the ends of the ice off them (Phase 4b)
            lab.rect("rock", 2, 40, 20, 61, 0f)
            lab.rect("rock", 76, 40, 93, 61, 0f)
            lab.rect("water", 21, 46, 75, 61, 0.5f)
            lab.rect("ice", 21, 44, 75, 45, -1f)
            disc(lab, "stone", 48, 10, r)
            lab.seconds(3f)
            lab.picture("body_ice_$r")
            // what breaks off the ice where the stone hits is crushed ice (snow)
            return lab.count("snow") to cells(lab.world, "stone").maxOf { it.second }
        }
        val (broken, deep) = drop(5)
        assertTrue(broken > 4, "the boulder broke the ice: $broken cells")
        assertTrue(deep > 46, "and went through into the water: $deep")
        val (pebble, rests) = drop(1)
        assertEquals(0, pebble, "a pebble does not break it")
        assertTrue(rests < 46, "it stays on the ice: $rests")
    }

    // ------------------------------------------------------------------ 6. still part of the material world

    @Test
    fun `a burning beam loses cells and falls apart into pieces, each a body`() {
        val lab = Lab()
        // a beam of wood lying across two stone blocks, a fire under its middle
        lab.rect("stone", 10, 50, 15, 61)
        lab.rect("stone", 48, 50, 53, 61)
        lab.rect("wood", 10, 48, 53, 49)
        lab.rect("fire", 30, 46, 33, 47)
        lab.world.setTemperature(31, 48, 600f)
        lab.world.setTemperature(32, 48, 600f)
        var pieces = 0
        repeat(900) {
            lab.world.step()
            pieces = maxOf(pieces, lab.world.bodyStates().count { b -> b.material == lab.id("wood") })
        }
        lab.picture("body_beam_burnt")
        assertTrue(lab.count("wood") < 88, "it burnt: ${lab.count("wood")} of 88 left")
        assertTrue(pieces >= 2, "it fell apart into $pieces pieces")
    }

    @Test
    fun `a body coming to rest wedged between rock walls is a fixed part of the world again`() {
        val lab = Lab()
        // a shaft one block wide: the block falls in and sticks where it narrows
        lab.rect("rock", 2, 30, 25, 61)
        lab.rect("rock", 31, 30, 61, 61)
        lab.rect("rock", 26, 45, 30, 61)
        lab.rect("brick", 26, 10, 30, 14)
        lab.seconds(3f)
        assertEquals(0, lab.world.bodyCount, "no body any more")
        assertEquals(44, cells(lab.world, "brick").maxOf { it.second }, "it sits on the narrowing")
        // and stands like any structure: the hand cannot take it out
        val s = Session(Lab.materials, 64, 64, 7L) { w ->
            for (y in 0 until 64) for (x in 0 until 64) w.set(x, y, lab.world.material(x, y))
        }
        s.press(28, 42, Session.HAND, 0)
        assertEquals("Das ist fest verankert", s.notice)
    }

    // ------------------------------------------------------------------ 7. the hand

    @Test
    fun `the hand takes a light body and puts it down as a body, a heavy one it cannot lift`() {
        val s = session(64, 64) { rockFloor(it, 62) }
        s.shape(Session.ShapeKind.RECT, 10, 50, 14, 61, Lab.materials.id("wood"), 1)
        s.shape(Session.ShapeKind.RECT, 34, 56, 41, 61, Lab.materials.id("metal"), 1)
        repeat(30) { s.tick() }
        s.press(12, 55, Session.HAND, 0)
        assertTrue(s.held != null, "the wood is in the hand: ${s.notice}")
        // put down in the air above the metal: it falls onto it and lies there, a body of its own
        s.press(38, 20, Session.HAND, 0)
        assertEquals("Abgelegt", s.notice)
        repeat(90) { s.tick() }
        val wood = cells(s.world, "wood")
        assertEquals(60, wood.size)
        assertEquals(55, wood.maxOf { it.second }, "it fell onto the metal block")
        s.press(38, 58, Session.HAND, 0)
        assertEquals("Zu schwer", s.notice)
    }

    // ------------------------------------------------------------------ determinism

    @Test
    fun `bodies move the same with the same actions, and after an undo as if it never happened`() {
        fun play(undo: Boolean): Long {
            val s = session(128, 80) { w ->
                rockFloor(w, 76)
                val rock = Lab.materials.id("rock")
                for (x in 0 until 128) for (y in 30 + x / 3 until 76) w.set(x, y, rock)
            }
            s.shape(Session.ShapeKind.LINE, 10, 20, 24, 10, Lab.materials.id("metal"), 1)
            repeat(60) { s.tick() }
            if (undo) {
                s.shape(Session.ShapeKind.RECT, 60, 10, 70, 14, Lab.materials.id("glass"), 1)
                repeat(30) { s.tick() }
                s.undo()
            }
            s.shape(Session.ShapeKind.RECT, 40, 5, 46, 9, Lab.materials.id("stone"), 1)
            repeat(300) { s.tick() }
            return s.world.hash()
        }
        assertEquals(play(false), play(false))
        assertEquals(play(false), play(true))
    }
}
