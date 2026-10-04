package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.MergeRule
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog

/** What happens when the player drops an object onto a cell. */
sealed interface Drop {
    val state: GameState

    /** The object lands where it was dropped (pushing gas or liquid there aside). */
    data class Placed(override val state: GameState) : Drop

    /** It merged with the object that was there ([targetId] keeps its id). */
    data class Merged(override val state: GameState, val targetId: String, val rule: MergeRule) : Drop

    /** It reacts with [partnerId], so it lands right next to it, at [landing], and the reaction runs. */
    data class NextTo(override val state: GameState, val landing: Position, val partnerId: String) : Drop
}

/**
 * Drag and drop with the same rules everywhere:
 *
 * 1. Onto an empty cell (or one with only gas): the object is put there.
 * 2. Onto an element it merges with (two flames, two logs, water onto water): they become one.
 * 3. A solid onto a liquid: it goes in and pushes the liquid up – the level rises.
 * 4. Onto something it reacts with (water onto fire, fire onto ice): it lands next to it and the
 *    normal physics take over.
 * 5. Anything else bounces off: the move is not allowed.
 *
 * Nobody may drop into walls or cells marked as not buildable.
 *
 * Levels with marked placement ('+' fields) are stricter: an object may be put down (1, 3) only on a
 * placement field; it may merge (2) only with something lying on a placement field or right next to
 * one; and when it reacts (4) it only lands on a placement field next to its partner.
 */
class Drops(private val types: TypeCatalog, private val rules: List<Rule>, private val merges: List<MergeRule>) {

    private val touchRules = rules.filter { it.trigger == Trigger.TOUCH }

    fun resolve(state: GameState, objectId: String, to: Position): Drop? {
        val obj = state.objectById(objectId) ?: return null
        if (!obj.isMovable || obj.position == to) return null
        if (!state.inBounds(to) || state.isWall(to) || to in state.noBuild) return null
        val field = state.isPlacementField(to)
        val target = state.objectAt(to)
        if (target == null) return if (field) state.withObjectMoved(objectId, to)?.let { Drop.Placed(it) } else null

        val merge = merges.firstOrNull { it.matches(obj.type, target.type) }
        if (merge != null) {
            if (!field && to.neighbours().none { state.inBounds(it) && state.placement?.contains(it) == true }) return null
            val into = merged(obj, target, merge)
            return state.withMerged(obj.id, into)?.let { Drop.Merged(it, target.id, merge) }
        }
        if (target.isGas) return if (field) state.withObjectMoved(objectId, to)?.let { Drop.Placed(it) } else null
        if (target.isLiquid && !obj.hasAmount) return if (field) state.withLiquidDisplaced(objectId, to)?.let { Drop.Placed(it) } else null
        if (reacts(obj, target)) {
            val landing = target.position.neighbours().firstOrNull { it != obj.position && state.canPlace(it) } ?: return null
            return state.withObjectMoved(objectId, landing)?.let { Drop.NextTo(it, landing, target.id) }
        }
        return null
    }

    /** The merge result if [obj] were dropped onto [target], or null if they do not merge. */
    fun mergeOf(obj: GameObject, target: GameObject): MergeRule? = merges.firstOrNull { it.matches(obj.type, target.type) }

    /** Whether the two would react on touch (in either direction), ignoring where they are. */
    fun reacts(a: GameObject, b: GameObject): Boolean =
        touchRules.any { (canAct(it, a, b)) || canAct(it, b, a) }

    private fun canAct(rule: Rule, source: GameObject, target: GameObject): Boolean {
        val c = rule.conditions
        if (target.type != c.target || (c.targetState != null && target.state != c.targetState)) return false
        if (c.source != null && source.type != c.source) return false
        if (c.sourceState != null && source.state != c.sourceState) return false
        if (c.sourceHot && source.heatOutput < c.minHeat) return false
        if (c.sourceMinAmount > 0 && source.amount < c.sourceMinAmount) return false
        return c.source != null || c.sourceHot
    }

    /** The object that replaces [target]: the same liquid with both amounts, or the merge's result type. */
    private fun merged(obj: GameObject, target: GameObject, rule: MergeRule): GameObject {
        if (obj.type == rule.result && target.type == rule.result && target.hasAmount) {
            return target.copy(amount = target.amount + obj.amount)
        }
        return types.create(
            id = target.id,
            type = rule.result,
            position = target.position,
            isMovable = target.isMovable,
        )
    }
}
