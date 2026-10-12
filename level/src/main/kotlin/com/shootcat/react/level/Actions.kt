package com.shootcat.react.level

import com.shootcat.react.sim.Session

/**
 * What a player does in a level, one action after the other, as a list that plays the same way every time
 * (Leitfaden V3, 5.3.1: every way to solve a level is a deterministic list of actions in the tests).
 *
 * Written one per line, `#` starts a comment:
 * ```
 * wait 120                  # let 120 steps pass
 * scoop 120 80 3 20         # the bucket at (120, 80), radius 3, the finger held down for 20 steps
 *                           # (the radii of the app's brush: 0, 1, 3, 6, 10)
 * pour 60 40 3 20
 * hand 50 60                # pick up there, or put down what the hand holds
 * store                     # what the hand holds goes into the store
 * line wood 10 20 30 20 1   # build from the store: line, rect or frame, with a thickness
 * paint wood 40 50 1 5      # build with the brush
 * ```
 */
sealed interface Action {
    data class Wait(val steps: Int) : Action
    data class Scoop(val x: Int, val y: Int, val radius: Int = 3, val steps: Int = 20) : Action
    data class Pour(val x: Int, val y: Int, val radius: Int = 3, val steps: Int = 20) : Action
    data class Hand(val x: Int, val y: Int) : Action
    data object Store : Action
    data class Build(val kind: Session.ShapeKind, val material: String, val x0: Int, val y0: Int, val x1: Int, val y1: Int, val size: Int = 1) : Action
    data class Paint(val material: String, val x: Int, val y: Int, val radius: Int = 1, val steps: Int = 5) : Action

    companion object {
        fun parse(text: String): List<Action> = text.lines().mapIndexedNotNull { n, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@mapIndexedNotNull null
            val w = line.split(Regex("\\s+"))
            fun int(k: Int, default: Int? = null): Int =
                w.getOrNull(k)?.toIntOrNull() ?: default ?: error("line ${n + 1}: '$line' needs a number at position ${k + 1}")
            when (w[0]) {
                "wait" -> Wait(int(1))
                "scoop" -> Scoop(int(1), int(2), int(3, 3), int(4, 20))
                "pour" -> Pour(int(1), int(2), int(3, 3), int(4, 20))
                "hand" -> Hand(int(1), int(2))
                "store" -> Store
                "line", "rect", "frame" -> Build(
                    when (w[0]) {
                        "line" -> Session.ShapeKind.LINE
                        "rect" -> Session.ShapeKind.RECT
                        else -> Session.ShapeKind.FRAME
                    },
                    w.getOrNull(1) ?: error("line ${n + 1}: '$line' names no material"),
                    int(2), int(3), int(4), int(5), int(6, 1),
                )
                "paint" -> Paint(w.getOrNull(1) ?: error("line ${n + 1}: '$line' names no material"), int(2), int(3), int(4, 1), int(5, 5))
                else -> error("line ${n + 1}: unknown action '${w[0]}'")
            }
        }
    }
}

/**
 * Carries out [actions] in order, each at the step the one before it ended, then lets the world run on until
 * the level is won or lost or [limitSeconds] have passed. Fails if an action needs a tool the level does not
 * give. Returns the result, or null if the level was not won.
 */
fun Play.perform(actions: List<Action>, limitSeconds: Int = 600): Result? {
    val s = session
    val mt = materials
    fun tick(n: Int) = repeat(n) { if (state == Play.State.PLAYING) s.tick() }
    for (a in actions) {
        if (state != Play.State.PLAYING) break
        when (a) {
            is Action.Wait -> tick(a.steps)
            is Action.Scoop, is Action.Pour -> {
                check(tools.bucket) { "${level.spec.id} gives no bucket" }
                val (x, y, r, steps) = if (a is Action.Scoop) listOf(a.x, a.y, a.radius, a.steps) else (a as Action.Pour).let { listOf(it.x, it.y, it.radius, it.steps) }
                s.press(x, y, if (a is Action.Scoop) Session.SCOOP else Session.POUR, r)
                tick(steps)
                s.release()
            }
            is Action.Hand -> {
                check(tools.hand) { "${level.spec.id} gives no hand" }
                s.press(a.x, a.y, Session.HAND, 0)
                tick(1)
            }
            Action.Store -> {
                check(tools.build) { "${level.spec.id} has no store to build from" }
                s.store()
                tick(1)
            }
            is Action.Build -> {
                check(tools.build) { "${level.spec.id} gives no building" }
                s.shape(a.kind, a.x0, a.y0, a.x1, a.y1, mt.id(a.material), a.size)
                tick(1)
            }
            is Action.Paint -> {
                check(tools.build) { "${level.spec.id} gives no building" }
                s.press(a.x, a.y, mt.id(a.material), a.radius)
                tick(a.steps)
                s.release()
            }
        }
    }
    val end = limitSeconds * STEPS_PER_SECOND
    while (state == Play.State.PLAYING && s.world.step < end) s.tick()
    return result
}
