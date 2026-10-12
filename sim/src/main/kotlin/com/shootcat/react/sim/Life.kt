package com.shootcat.react.sim

import com.shootcat.react.sim.MaterialTable.Companion.AIR
import kotlin.math.abs

/**
 * Plants and animals (Leitfaden V3, 4.2): cells of the world like everything else, with simple rules
 * instead of a mind of their own.
 *
 * Plants grow slowly. Rather than looking at every plant in every step, a fixed number of cells picked at
 * random get a "random tick" each step, so every cell is ticked about once a second. A ticked plant grows
 * if it has the light and water it needs, and dies when it gets too hot or meets salt. Animals act in every
 * step their chunk is awake, and keep it awake.
 *
 * Trees, branches, leaves and vines are solid: they belong to structures (3.1), so when a trunk burns
 * through, the part above it falls.
 */
internal class Life(private val world: World) {

    private val mt = world.materials
    private val w = world.width
    private val h = world.height
    private val mat = world.mat
    private val temp = world.temp
    private val life = world.life
    private val flags = world.flags
    private val light = world.light
    private val kind = mt.kind

    private val water = mt.idOrNull("water") ?: -1
    private val earth = mt.idOrNull("earth") ?: -1
    private val trunk = mt.idOrNull("trunk") ?: -1
    private val leaves = mt.idOrNull("leaves") ?: -1
    private val seed = mt.idOrNull("seed") ?: -1
    private val fire = mt.idOrNull("fire") ?: -1
    private val smoke = mt.idOrNull("smoke") ?: -1

    /** What a dying tree turns into: it spreads up the trunk and the leaves wither next to it. */
    private val deadwood = if (trunk >= 0) mt.deathProduct[trunk] else -1

    /** Offsets around a crown's centre, nearest first, so every new leaf can touch one already there. */
    private val crownX: IntArray
    private val crownY: IntArray

    init {
        val offsets = ArrayList<IntArray>()
        for (dy in -CROWN_MAX..CROWN_MAX) for (dx in -CROWN_MAX..CROWN_MAX) offsets += intArrayOf(dx, dy, dx * dx + dy * dy)
        offsets.sortBy { it[2] }
        crownX = IntArray(offsets.size) { offsets[it][0] }
        crownY = IntArray(offsets.size) { offsets[it][1] }
    }

    // ------------------------------------------------------------------ plants

    fun randomTicks() {
        val size = world.size
        val org = mt.organism
        val animal = mt.animal
        repeat(size shr TICK_SHIFT) {
            val i = world.below(size)
            val m = mat[i].toInt()
            if (org[m] != Organism.NONE && !animal[m]) tick(i % w, i / w, i, m)
        }
    }

    private fun tick(x: Int, y: Int, i: Int, m: Int) {
        val f = flags[i].toInt()
        if (f and Flag.BURNING != 0) return
        if (temp[i] > mt.diesAbove[m] || (mt.diesInSalt[m] && world.saltyNeighbour(x, y, i))) {
            world.note(if (temp[i] > mt.diesAbove[m]) Seen.DIE_HEAT else Seen.DIE_SALT, m)
            convert(x, y, i, mt.deathProduct[m])
            return
        }
        when (mt.organism[m]) {
            Organism.GRASS -> grass(x, y, i, m)
            Organism.MOSS -> moss(x, y, i, m)
            Organism.VINE -> if (f and Flag.MARK != 0) vine(x, y, i, m, false)
            Organism.SEED -> seed(x, y, i, m)
            Organism.TREE -> {
                // a tree dies from where it was hurt, upwards
                if (deadwood >= 0 && touches(x, y, i, deadwood) && world.chance(SPREAD_DEATH)) convert(x, y, i, deadwood)
                else if (f and Flag.MARK != 0) tree(x, y, i, m, false)
            }
            Organism.LEAF -> leaf(x, y, i, m)
        }
    }

    /** Grass spreads over moist earth next to it (level, up or down a step), in the light. */
    private fun grass(x: Int, y: Int, i: Int, m: Int) {
        if (light.at(x, y) < mt.lightNeed[m] || !world.chance(mt.grow[m])) return
        val nx = x + if (world.coin()) 1 else -1
        if (nx < 0 || nx >= w) return
        for (dy in STEP_Y) {
            val ny = y + dy
            if (ny < 1 || ny >= h) continue
            val j = ny * w + nx
            if (mat[j].toInt() != mt.growsOn[m] || flags[j].toInt() and Flag.WET == 0) continue
            // only at the surface, where light falls on it
            if (mt.lightPass[mat[j - w].toInt()] <= 0f) continue
            convert(nx, ny, j, m)
            world.note(Seen.GROW, m)
            return
        }
    }

    /**
     * Moss creeps along walls, floors and ceilings in the dark, as far as it stays damp: [World.life] holds
     * how many cells of moss lie between this one and water or wet ground.
     */
    private fun moss(x: Int, y: Int, i: Int, m: Int) {
        var d = MOSS_DRY
        if (damp(x, y, i)) {
            d = 0
        } else {
            if (y > 0 && mat[i - w].toInt() == m) d = minOf(d, life[i - w] + 1)
            if (y < h - 1 && mat[i + w].toInt() == m) d = minOf(d, life[i + w] + 1)
            if (x > 0 && mat[i - 1].toInt() == m) d = minOf(d, life[i - 1] + 1)
            if (x < w - 1 && mat[i + 1].toInt() == m) d = minOf(d, life[i + 1] + 1)
        }
        life[i] = d.toShort()
        if (d >= MOSS_REACH || light.at(x, y) > mt.lightMax[m] || !world.chance(mt.grow[m])) return
        // the first free place round about, starting somewhere at random
        val start = world.below(8)
        for (k in 0 until 8) {
            val dir = (start + k) and 7
            val nx = x + DX[dir]
            val ny = y + DY[dir]
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
            val j = ny * w + nx
            if (mat[j].toInt() != AIR || !onGround(nx, ny, j, m)) continue
            world.spawn(nx, ny, j, m, temp[j])
            life[j] = (d + 1).toShort()
            world.note(Seen.GROW, m)
            return
        }
    }

    /** A cell next to ground moss can cling to: rock, solids, powders. */
    private fun onGround(x: Int, y: Int, j: Int, m: Int): Boolean {
        fun firm(q: Int) = q != m && (kind[q] == Kind.STATIC || kind[q] == Kind.SOLID || kind[q] == Kind.POWDER)
        return (y > 0 && firm(mat[j - w].toInt())) || (y < h - 1 && firm(mat[j + w].toInt())) ||
            (x > 0 && firm(mat[j - 1].toInt())) || (x < w - 1 && firm(mat[j + 1].toInt()))
    }

    /**
     * The tip of a vine grows one cell towards the nearest water around it, or hangs down if there is none.
     * The vine is solid: anchored on rock it holds as a living bridge or ladder. [World.life] of the tip
     * holds how much more it can grow. Returns whether it grew.
     */
    private fun vine(x: Int, y: Int, i: Int, m: Int, force: Boolean): Boolean {
        if (!force && (light.at(x, y) < mt.lightNeed[m] || !world.chance(mt.grow[m]))) return false
        val budget = life[i].toInt()
        if (budget <= 0 || (water >= 0 && touches(x, y, i, water))) {
            unmark(i)
            return false
        }
        val target = nearestWater(x, y)
        // a step along x or along y, so the vine holds together; the longer way to go is the likelier
        var ax: Int
        var ay: Int
        var bx: Int
        var by: Int
        if (target >= 0) {
            // towards the air just above the water surface: over the rim, not into the side of the basin
            val tx = target % w
            var ty = target / w
            while (ty > 0 && mat[(ty - 1) * w + tx].toInt() == water) ty--
            val dx = tx - x
            val dy = ty - 1 - y
            val sx = if (dx > 0) 1 else if (dx < 0) -1 else 0
            val sy = if (dy > 0) 1 else if (dy < 0) -1 else 0
            val alongX = world.below(abs(dx) + abs(dy)) < abs(dx)
            ax = if (alongX) sx else 0
            ay = if (alongX) 0 else sy
            bx = if (alongX) 0 else sx
            by = if (alongX) sy else 0
        } else {
            ax = 0
            ay = 1
            bx = if (world.coin()) 1 else -1
            by = 0
            if (!world.chance(HANG)) {
                ax = bx
                ay = 0
                bx = 0
                by = 1
            }
        }
        if (!sprout(x, y, i, m, budget, ax, ay) && !sprout(x, y, i, m, budget, bx, by)) {
            // the way is blocked: climb up or round it
            val side = if (world.coin()) 1 else -1
            if (!sprout(x, y, i, m, budget, 0, -1) && !sprout(x, y, i, m, budget, side, 0) && !sprout(x, y, i, m, budget, -side, 0)) return false
        }
        // now and then the vine forks: the old tip grows on as well
        if (budget > FORK_MIN && world.chance(FORK)) life[i] = (budget / 3).toShort() else unmark(i)
        return true
    }

    /** Grows the vine from (x, y) one cell by (dx, dy), if there is air. */
    private fun sprout(x: Int, y: Int, i: Int, m: Int, budget: Int, dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return false
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || nx >= w || ny < 0 || ny >= h) return false
        val j = ny * w + nx
        if (mat[j].toInt() != AIR) return false
        world.growSolid(i, nx, ny, j, m)
        flags[j] = Flag.MARK.toByte()
        life[j] = (budget - 1).toShort()
        world.note(Seen.GROW, m)
        return true
    }

    /** The nearest water within [VINE_SIGHT] cells, looked for on a coarse grid; -1 if there is none. */
    private fun nearestWater(x: Int, y: Int): Int {
        if (water < 0) return -1
        var best = -1
        var bestD = Int.MAX_VALUE
        var dy = -VINE_SIGHT
        while (dy <= VINE_SIGHT) {
            val yy = y + dy
            if (yy in 0 until h) {
                var dx = -VINE_SIGHT
                while (dx <= VINE_SIGHT) {
                    val xx = x + dx
                    if (xx in 0 until w && mat[yy * w + xx].toInt() == water) {
                        val d = dx * dx + dy * dy
                        if (d < bestD) {
                            bestD = d
                            best = yy * w + xx
                        }
                    }
                    dx += SCAN_STEP
                }
            }
            dy += SCAN_STEP
        }
        return best
    }

    /** A seed on moist earth in the light sprouts into a tree or a bush. */
    private fun seed(x: Int, y: Int, i: Int, m: Int) {
        if (y >= h - 1 || trunk < 0) return
        val b = i + w
        if (!soil(mat[b].toInt(), m)) return
        if (!damp(x, y + 1, b) && !damp(x, y, i)) return
        if (light.at(x, y) < mt.lightNeed[m] || !world.chance(mt.grow[m])) return
        val tree = world.chance(TREE_SHARE)
        world.note(Seen.GROW, m)
        world.change(x, y, i, trunk)
        sprout(i, tree)
    }

    private fun soil(q: Int, m: Int): Boolean {
        val g = mt.growsOn[m]
        return q == g || (g >= 0 && mt.growsOn[q] == g)
    }

    /** Makes the trunk cell [i] the growing tip of a new tree or bush. */
    private fun sprout(i: Int, tree: Boolean) {
        flags[i] = (flags[i].toInt() or Flag.MARK).toByte()
        val height = if (tree) TREE_MIN + world.below(TREE_SPREAD) else 1 + world.below(2)
        life[i] = tip(if (tree) TRUNK else BUSH, height)
    }

    /**
     * The tip of a tree grows: a trunk upwards, putting out branches in its upper part, a branch sideways and
     * up in turn. Where it ends (or runs into something) it leafs out into a crown. [World.life] holds the
     * kind of tip and how far it still grows. Returns whether it grew.
     */
    private fun tree(x: Int, y: Int, i: Int, m: Int, force: Boolean): Boolean {
        if (!force && (light.at(x, y) < mt.lightNeed[m] || !world.chance(mt.grow[m]))) return false
        val l = life[i].toInt()
        val type = l shr 8
        val budget = l and 0xFF
        unmark(i)
        if (type == TRUNK || type == BUSH) {
            if (budget == 0 || y == 0 || mat[i - w].toInt() != AIR) {
                // a tree's crown sits a little down over the top of its trunk
                if (type == TRUNK) crown(x, minOf(h - 1, y + 2), TREE_CROWN) else crown(x, y, BUSH_CROWN)
                return true
            }
            val j = i - w
            world.growSolid(i, x, y - 1, j, m)
            flags[j] = Flag.MARK.toByte()
            life[j] = tip(type, budget - 1)
            world.note(Seen.GROW, m)
            if (type == TRUNK && budget in 2..BRANCH_FROM && world.chance(BRANCH)) {
                val d = if (world.coin()) 1 else -1
                val k = j + d
                if (x + d in 0 until w && mat[k].toInt() == AIR) {
                    world.growSolid(j, x + d, y - 1, k, m)
                    flags[k] = Flag.MARK.toByte()
                    life[k] = tip(if (d < 0) BRANCH_LEFT else BRANCH_RIGHT, BRANCH_MIN + world.below(3))
                }
            }
            return true
        }
        val dx = if (type == BRANCH_LEFT) -1 else 1
        val nx = if (budget and 1 == 0) x + dx else x
        val ny = if (budget and 1 == 0) y else y - 1
        if (budget == 0 || nx < 0 || nx >= w || ny < 0 || mat[ny * w + nx].toInt() != AIR) {
            crown(x, y, BRANCH_CROWN)
            return true
        }
        val j = ny * w + nx
        world.growSolid(i, nx, ny, j, m)
        flags[j] = Flag.MARK.toByte()
        life[j] = tip(type, budget - 1)
        return true
    }

    /** Leaves in a rough disc of radius [r] around (x, y), ring by ring, each touching one already there. */
    private fun crown(x: Int, y: Int, r: Int) {
        if (leaves < 0) return
        val lim = r * r + r
        val edge = (r - 1) * (r - 1) + (r - 1)
        for (k in crownX.indices) {
            val dx = crownX[k]
            val dy = crownY[k]
            val d2 = dx * dx + dy * dy
            if (d2 == 0) continue
            if (d2 > lim) break
            if (d2 > edge && !world.chance(CROWN_EDGE)) continue
            val nx = x + dx
            val ny = y + dy
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
            val j = ny * w + nx
            if (mat[j].toInt() != AIR) continue
            if (!touches(nx, ny, j, leaves) && !touches(nx, ny, j, trunk)) continue
            world.spawn(nx, ny, j, leaves, temp[j])
        }
    }

    /** Leaves wither next to dead wood; now and then a tree drops a seed. */
    private fun leaf(x: Int, y: Int, i: Int, m: Int) {
        if (deadwood >= 0 && touches(x, y, i, deadwood)) {
            convert(x, y, i, mt.deathProduct[m])
            return
        }
        if (seed >= 0 && y < h - 1 && mat[i + w].toInt() == AIR && world.chance(SEED_DROP)) world.spawn(x, y + 1, i + w, seed, temp[i + w])
    }

    /** Lets every plant tip inside the box grow [rounds] times at once, ignoring light and chance (building worlds). */
    fun growNow(x0: Int, y0: Int, x1: Int, y1: Int, rounds: Int) {
        repeat(rounds) {
            for (y in maxOf(0, y0)..minOf(h - 1, y1)) for (x in maxOf(0, x0)..minOf(w - 1, x1)) {
                val i = y * w + x
                if (flags[i].toInt() and Flag.MARK == 0) continue
                val m = mat[i].toInt()
                when (mt.organism[m]) {
                    Organism.TREE -> tree(x, y, i, m, true)
                    Organism.VINE -> vine(x, y, i, m, true)
                }
            }
        }
    }

    /** A full-grown tree (or bush) standing on the ground at (x, y + 1), for building worlds. */
    fun plantTree(x: Int, y: Int, tree: Boolean) {
        if (trunk < 0) return
        val i = y * w + x
        world.spawn(x, y, i, trunk, temp[i])
        sprout(i, tree)
        growNow(x - 10, y - TREE_MIN - TREE_SPREAD - 8, x + 10, y, 60)
    }

    // ------------------------------------------------------------------ animals

    /** One step of an animal: dying of heat or salt, otherwise its own way of moving. */
    fun animal(x: Int, y: Int, i: Int, m: Int) {
        if (temp[i] > mt.diesAbove[m] || (mt.diesInSalt[m] && world.saltyNeighbour(x, y, i))) {
            world.note(if (temp[i] > mt.diesAbove[m]) Seen.DIE_HEAT else Seen.DIE_SALT, m)
            world.change(x, y, i, mt.deathProduct[m])
            return
        }
        world.wake(x, y)
        when (mt.organism[m]) {
            Organism.FISH -> fish(x, y, i, m)
            Organism.FIREFLY -> firefly(x, y, i, m)
            Organism.WORM -> worm(x, y, i, m)
            Organism.BIRD -> bird(x, y, i, m)
        }
    }

    /**
     * Fish swim to and fro in fresh water ([Flag.MARK]: heading right), now and then up or down. Out of the
     * water each one flops about on its own, falls, slides off a heap, and suffocates after a few seconds;
     * [World.life] counts the steps. What is left of it rots away (its material's lifetime).
     */
    private fun fish(x: Int, y: Int, i: Int, m: Int) {
        val home = mt.livesIn[m]
        if (!near(x, y, i, home)) {
            val l = life[i] + 1
            if (l > SUFFOCATE) {
                world.note(Seen.DIE_DRY, m)
                world.change(x, y, i, mt.deathProduct[m])
                return
            }
            life[i] = l.toShort()
            if (!world.fall(x, y, i, m)) flop(x, y, i, home, FLOP)
            return
        }
        life[i] = 0
        if (!world.chance(mt.move[m])) return
        if (world.chance(TURN)) flags[i] = (flags[i].toInt() xor Flag.MARK).toByte()
        val dx = if (flags[i].toInt() and Flag.MARK != 0) 1 else -1
        val r = world.below(6)
        val dy = if (r == 0) -1 else if (r == 1) 1 else 0
        if (go(x, y, i, dx, dy, home) || (dy != 0 && go(x, y, i, dx, 0, home))) return
        // blocked: turn round
        flags[i] = (flags[i].toInt() xor Flag.MARK).toByte()
    }

    /**
     * An animal out of its element that lies on something: with [chance] per step it moves one cell to a
     * side, down the slope of a heap if it can, along the ground, or in a little hop up; into its element
     * if that is beside it. Animals never stack into a firm heap.
     */
    private fun flop(x: Int, y: Int, i: Int, home: Int, chance: Float) {
        if (!world.chance(chance)) return
        val d = if (world.coin()) 1 else -1
        // into its element, down off the heap, along the ground, a hop up: the first that has room
        for (way in 0 until 4) for (dx in intArrayOf(d, -d)) {
            val nx = x + dx
            if (nx < 0 || nx >= w) continue
            val side = i + dx
            val q = mat[side].toInt()
            val to = when (way) {
                0 -> if (q == home) side else -1
                1 -> if (y < h - 1 && free(q) && free(mat[side + w].toInt())) side + w else -1
                2 -> if (free(q)) side else -1
                else -> if (y > 0 && free(mat[side - w].toInt()) && free(mat[i - w].toInt())) side - w else -1
            }
            if (to >= 0) {
                world.swap(i, to, x, y, to % w, to / w)
                return
            }
        }
    }

    private fun go(x: Int, y: Int, i: Int, dx: Int, dy: Int, into: Int): Boolean {
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || nx >= w || ny < 0 || ny >= h) return false
        val j = ny * w + nx
        if (mat[j].toInt() != into) return false
        world.swap(i, j, x, y, nx, ny)
        return true
    }

    /** Fireflies flit about in the air and keep to the dark: they mostly turn back from brighter places. */
    private fun firefly(x: Int, y: Int, i: Int, m: Int) {
        if (!world.chance(mt.move[m])) return
        val dx = world.below(3) - 1
        val dy = world.below(3) - 1
        if (dx == 0 && dy == 0) return
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || nx >= w || ny < 0 || ny >= h) return
        val j = ny * w + nx
        if (mat[j].toInt() != AIR) return
        val there = light.at(nx, ny)
        if (there > light.at(x, y) && there > DARK && !world.chance(INTO_LIGHT)) return
        world.swap(i, j, x, y, nx, ny)
    }

    /**
     * Worms crawl slowly through earth, mostly sideways, and leave the earth behind them loosened
     * ([Flag.MARK]): water seeps down through it. Above ground they dry out; [World.life] counts the steps.
     */
    private fun worm(x: Int, y: Int, i: Int, m: Int) {
        val home = mt.livesIn[m]
        if (!near(x, y, i, home)) {
            val l = life[i] + 1
            if (l > DRY_OUT) {
                world.note(Seen.DIE_DRY, m)
                world.change(x, y, i, mt.deathProduct[m])
                return
            }
            life[i] = l.toShort()
            if (!world.fall(x, y, i, m)) flop(x, y, i, home, CRAWL)
            return
        }
        life[i] = 0
        if (!world.chance(mt.move[m])) return
        val r = world.below(10)
        val dx = if (r < 4) -1 else if (r < 8) 1 else 0
        val dy = if (r == 8) 1 else if (r == 9) -1 else 0
        if (go(x, y, i, dx, dy, home)) {
            flags[i] = (flags[i].toInt() or Flag.MARK).toByte()
            world.note(Seen.DIG, m)
        }
    }

    /**
     * Birds sit on trees and the ground. When they notice fire or smoke nearby they take off ([Flag.MARK])
     * and fly up and away out of the world; [World.life] holds their direction. Just for the mood.
     */
    private fun bird(x: Int, y: Int, i: Int, m: Int) {
        val f = flags[i].toInt()
        if (f and Flag.MARK == 0) {
            if (world.chance(LOOK) && danger(x, y)) {
                world.note(Seen.FLEE, m)
                flags[i] = (f or Flag.MARK).toByte()
                life[i] = (if (world.coin()) 1 else 0).toShort()
                return
            }
            if (y < h - 1 && open(mat[i + w].toInt())) {
                // nothing to sit on: it glides down to land
                if (world.chance(GLIDE)) {
                    val nx = (x + world.below(3) - 1).coerceIn(0, w - 1)
                    val j = (y + 1) * w + nx
                    if (mat[j].toInt() == AIR) world.swap(i, j, x, y, nx, y + 1)
                }
                return
            }
            if (world.chance(HOP)) {
                val nx = x + if (world.coin()) 1 else -1
                if (nx in 0 until w && y < h - 1 && mat[i - x + nx].toInt() == AIR && !open(mat[i - x + nx + w].toInt())) {
                    world.swap(i, i - x + nx, x, y, nx, y)
                }
            }
            return
        }
        if (!world.chance(mt.move[m])) return
        val dx = if (life[i].toInt() == 1) 1 else -1
        val dy = if (world.chance(CLIMB)) -1 else 0
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || nx >= w || ny < 0) {
            // out of the world
            world.clear(x, y, i)
            return
        }
        val j = ny * w + nx
        when {
            free(mat[j].toInt()) -> world.swap(i, j, x, y, nx, ny)
            y > 0 && free(mat[i - w].toInt()) -> world.swap(i, i - w, x, y, x, y - 1)
            else -> life[i] = (1 - life[i]).toShort()
        }
    }

    /** Looks at a few cells nearby, picked at random, for fire or smoke. */
    private fun danger(x: Int, y: Int): Boolean {
        repeat(LOOK_SAMPLES) {
            val sx = x + world.below(2 * LOOK_RADIUS + 1) - LOOK_RADIUS
            val sy = y + world.below(2 * LOOK_RADIUS + 1) - LOOK_RADIUS
            if (sx in 0 until w && sy in 0 until h) {
                val j = sy * w + sx
                val q = mat[j].toInt()
                if (q == fire || q == smoke || flags[j].toInt() and Flag.BURNING != 0) return true
            }
        }
        return false
    }

    private fun open(q: Int) = q == AIR || kind[q] == Kind.GAS || kind[q] == Kind.LIQUID
    private fun free(q: Int) = q == AIR || (kind[q] == Kind.GAS && q != fire)

    // ------------------------------------------------------------------ helpers

    /** A neighbour (4) is [home]: water for a fish, earth for a worm. Others of its kind do not count. */
    private fun near(x: Int, y: Int, i: Int, home: Int): Boolean =
        (y > 0 && mat[i - w].toInt() == home) || (y < h - 1 && mat[i + w].toInt() == home) ||
            (x > 0 && mat[i - 1].toInt() == home) || (x < w - 1 && mat[i + 1].toInt() == home)

    private fun touches(x: Int, y: Int, i: Int, q: Int): Boolean =
        (y > 0 && mat[i - w].toInt() == q) || (y < h - 1 && mat[i + w].toInt() == q) ||
            (x > 0 && mat[i - 1].toInt() == q) || (x < w - 1 && mat[i + 1].toInt() == q)

    /** Wet itself, or next to a liquid that wets or to wet ground. */
    private fun damp(x: Int, y: Int, i: Int): Boolean {
        fun wet(j: Int) = mt.wets[mat[j].toInt()] || flags[j].toInt() and Flag.WET != 0
        return flags[i].toInt() and Flag.WET != 0 || (y > 0 && wet(i - w)) || (y < h - 1 && wet(i + w)) ||
            (x > 0 && wet(i - 1)) || (x < w - 1 && wet(i + 1))
    }

    /** The cell turns into [product]; ground stays as wet as it was (grass dying back to earth). */
    private fun convert(x: Int, y: Int, i: Int, product: Int) {
        val wet = flags[i].toInt() and Flag.WET
        world.change(x, y, i, product)
        flags[i] = (flags[i].toInt() or wet).toByte()
    }

    private fun unmark(i: Int) {
        flags[i] = (flags[i].toInt() and Flag.MARK.inv()).toByte()
    }

    private fun tip(type: Int, budget: Int): Short = ((type shl 8) or budget.coerceIn(0, 255)).toShort()

    private companion object {
        /** One random tick per 64 cells and step: each cell about once a second. */
        const val TICK_SHIFT = 6

        val DX = intArrayOf(0, 0, -1, 1, -1, 1, -1, 1)
        val STEP_Y = intArrayOf(0, -1, 1)
        val DY = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)

        const val MOSS_DRY = 99
        const val MOSS_REACH = 6

        const val VINE_SIGHT = 30
        const val SCAN_STEP = 3
        const val HANG = 0.7f
        const val FORK = 0.06f
        const val FORK_MIN = 10

        const val TRUNK = 0
        const val BUSH = 1
        const val BRANCH_LEFT = 2
        const val BRANCH_RIGHT = 3
        const val TREE_SHARE = 0.6f
        const val TREE_MIN = 14
        const val TREE_SPREAD = 8
        const val BRANCH = 0.5f
        const val BRANCH_FROM = 10
        const val BRANCH_MIN = 4
        const val CROWN_MAX = 6
        const val TREE_CROWN = 5
        const val BUSH_CROWN = 3
        const val BRANCH_CROWN = 3
        const val CROWN_EDGE = 0.6f
        const val SEED_DROP = 0.0008f
        const val SPREAD_DEATH = 0.5f

        const val SUFFOCATE = 240
        const val FLOP = 0.2f
        const val CRAWL = 0.01f
        const val TURN = 0.02f
        const val DARK = 0.45f
        const val INTO_LIGHT = 0.1f
        const val DRY_OUT = 900
        const val LOOK = 0.3f
        const val LOOK_SAMPLES = 4
        const val LOOK_RADIUS = 14
        const val GLIDE = 0.5f
        const val HOP = 0.004f
        const val CLIMB = 0.7f
    }
}
