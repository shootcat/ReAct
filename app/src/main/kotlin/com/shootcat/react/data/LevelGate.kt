package com.shootcat.react.data

/**
 * Which levels can be played (3b.5): every level already solved, and the next few unsolved ones in their
 * order, so two or three new ones are always open at once. While the levels are being tested all are open.
 */
object LevelGate {
    /** The test phase: every level open. */
    const val ALL_OPEN = true

    /** How many unsolved levels are open at a time. */
    const val OPEN_AHEAD = 3

    /** Whether the level [ids]`[index]` is open, with the levels in [solved] done. */
    fun isOpen(index: Int, ids: List<String>, solved: Set<String>, allOpen: Boolean = ALL_OPEN): Boolean {
        if (allOpen || ids[index] in solved) return true
        var unsolvedBefore = 0
        for (k in 0 until index) if (ids[k] !in solved) unsolvedBefore++
        return unsolvedBefore < OPEN_AHEAD
    }
}
