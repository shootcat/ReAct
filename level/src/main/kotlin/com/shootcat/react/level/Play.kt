package com.shootcat.react.level

import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session

/** Gold, silver, bronze, or done without a medal. */
enum class Medal(val label: String) { GOLD("Gold"), SILVER("Silber"), BRONZE("Bronze"), NONE("Geschafft") }

/** How a level was solved: time in seconds of the world, the player's actions, and the medal for both. */
data class Result(val seconds: Float, val actions: Int, val medal: Medal)

/**
 * One level being played (Leitfaden V3, Phase 3): the session with only the level's tools and store, its
 * goals checked after every few steps, the actions counted, and the rating at the end.
 *
 * The goals are checked on the thread that steps the world (through [Session.onStep]); the UI reads [state],
 * [result] and [progress]. Undo and reset go back in world time, and the goals start counting again from there.
 */
class Play(val level: Level, val materials: MaterialTable) {

    enum class State { PLAYING, WON, LOST }

    val tools = level.spec.tools
    val session = Session(materials, level.width, level.height, level.spec.seed) { level.build(it) }
    val goals = level.goals(materials)

    @Volatile
    var state = State.PLAYING
        private set

    @Volatile
    var result: Result? = null
        private set

    /** How far along each goal is, 0 to 1, as of the last check. */
    @Volatile
    var progress = FloatArray(goals.size)
        private set

    /** Actions taken so far (Leitfaden V3, 5.1.3): filling and pouring the bucket, the hand, building. */
    @Volatile
    var actions = 0
        private set

    /** The step since which each goal has been met without a break, or -1. */
    private val since = IntArray(goals.size) { -1 }
    private var lastStep = -1

    init {
        session.bucketSize = tools.bucketSize
        session.reachLimited = true
        val stock = IntArray(materials.count)
        for ((name, n) in level.spec.stock) stock[materials.id(name)] = n
        session.begin(Session.Supply.PROCURE, stock)
        session.onStep = { check() }
    }

    private fun check() {
        val world = session.world
        if (world.step <= lastStep) {
            // undo or reset: the world went back in time
            since.fill(-1)
            state = State.PLAYING
            result = null
        }
        lastStep = world.step
        actions = countActions(session.log)
        if (state != State.PLAYING || world.step % CHECK_EVERY != 0) return
        val p = FloatArray(goals.size)
        var done = true
        for ((k, g) in goals.withIndex()) {
            val met = g.met(world)
            p[k] = g.progress(world)
            if (g.guards) {
                if (!met) {
                    state = State.LOST
                    progress = p
                    return
                }
                continue
            }
            if (!met) since[k] = -1 else if (since[k] < 0) since[k] = world.step
            if (since[k] < 0 || world.step - since[k] < g.holdSteps) done = false
        }
        progress = p
        if (done) {
            val seconds = world.step / STEPS_PER_SECOND.toFloat()
            result = Result(seconds, actions, rate(seconds, actions))
            state = State.WON
        }
    }

    /** The best medal whose limits both the time and the actions keep. */
    fun rate(seconds: Float, actions: Int): Medal {
        val r = level.spec.rating
        for ((limit, medal) in listOf(r.gold to Medal.GOLD, r.silver to Medal.SILVER, r.bronze to Medal.BRONZE)) {
            if (seconds <= limit.seconds && actions <= limit.actions) return medal
        }
        return Medal.NONE
    }

    companion object {
        /** Goals are looked at every few steps: often enough for the eye, cheap for the phone. */
        const val CHECK_EVERY = 10

        /** Each touch with the bucket or the hand, each built shape or stroke, each store counts as one action. */
        fun countActions(log: List<Session.Input>): Int = log.count {
            it is Session.Press || it is Session.Shape || it is Session.Store
        }
    }
}
