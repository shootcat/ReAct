package com.shootcat.react.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Later on two or three levels are open at a time; in the test phase all of them (3b.5). */
class LevelGateTest {
    private val ids = listOf("a", "b", "c", "d", "e", "f")

    private fun open(solved: Set<String>) = ids.indices.filter { LevelGate.isOpen(it, ids, solved, allOpen = false) }.map { ids[it] }

    @Test
    fun threeUnsolvedLevelsAreOpenAtATime() {
        assertEquals(listOf("a", "b", "c"), open(emptySet()))
        assertEquals(listOf("a", "b", "c", "d"), open(setOf("a")))
        // a level solved out of order opens the next one behind it as well
        assertEquals(listOf("a", "b", "c", "d"), open(setOf("c")))
        assertEquals(ids, open(setOf("a", "b", "c")))
    }

    @Test
    fun inTheTestPhaseAllAreOpen() {
        assertEquals(ids.size, ids.indices.count { LevelGate.isOpen(it, ids, emptySet(), allOpen = true) })
    }
}
