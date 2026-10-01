package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LoadDirection
import com.shootcat.react.engine.model.Phase
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog

/** One applied rule effect. [positive] is false when a rule's else-effect was applied. */
data class RuleEvent(
    val ruleId: String,
    val phase: Phase,
    val positive: Boolean,
    val targetId: String,
    val targetType: String,
    val previousState: String,
    val newState: String?,
    val sourceIds: List<String>,
    val sourceTypes: List<String>,
    val spawnedId: String? = null,
)

data class StepResult(
    val state: GameState,
    val events: List<RuleEvent>,
    val transformations: Int,
    val overloaded: Boolean,
)

/**
 * Deterministic, data-driven rule engine. A step runs three fixed phases:
 *
 * 1. [Phase.STATE]   – TOUCH, LOAD, HEAT and POWER rules, evaluated once against the state at the start of the phase.
 * 2. [Phase.PHYSICS] – falling, floating, flowing, pressure and heat conduction.
 * 3. [Phase.SIGNAL]  – SIGNAL rules, propagated until nothing changes any more.
 *
 * Per target and phase only one effect applies: the first matching rule (in rule order) wins,
 * and else-effects only apply when no rule matched that target. More than
 * [maxTransformationsPerStep] applied effects abort the step (cascade protection).
 */
class RuleEngine(
    private val types: TypeCatalog,
    rules: List<Rule>,
    private val maxTransformationsPerStep: Int = DEFAULT_MAX_TRANSFORMATIONS,
) {
    private val stateRules = rules.filter { it.phase == Phase.STATE }
    private val signalRules = rules.filter { it.phase == Phase.SIGNAL }

    fun step(state: GameState): StepResult {
        val world = MutableWorld(state)
        val events = mutableListOf<RuleEvent>()
        val budget = Budget(maxTransformationsPerStep)

        runPhase(stateRules, Phase.STATE, world, events, budget)
        if (!budget.exceeded) {
            Physics.apply(world, types)
        }
        if (!budget.exceeded) {
            // Signals travel instantly: keep propagating until the network is stable.
            while (runPhase(signalRules, Phase.SIGNAL, world, events, budget) && !budget.exceeded) Unit
        }
        return StepResult(world.snapshot(), events, budget.used, budget.exceeded)
    }

    private class Budget(private val max: Int) {
        var used = 0
            private set
        var exceeded = false
            private set

        fun consume(): Boolean {
            if (exceeded) return false
            if (used >= max) {
                exceeded = true
                return false
            }
            used++
            return true
        }
    }

    private class Match(val rule: Rule, val target: GameObject, val sources: List<GameObject>) {
        val positive: Boolean get() = sources.isNotEmpty()
    }

    /** Returns true if anything changed. */
    private fun runPhase(
        rules: List<Rule>,
        phase: Phase,
        world: MutableWorld,
        events: MutableList<RuleEvent>,
        budget: Budget,
    ): Boolean {
        if (rules.isEmpty()) return false
        val snapshot = world.snapshot()
        val byTarget = LinkedHashMap<String, MutableList<Match>>()
        for (rule in rules) {
            for (match in evaluate(rule, snapshot)) {
                byTarget.getOrPut(match.target.id) { mutableListOf() } += match
            }
        }
        var changed = false
        for (target in snapshot.objectsInReadingOrder()) {
            val matches = byTarget[target.id] ?: continue
            val positive = matches.firstOrNull { it.positive }
            val (match, effect) = when {
                positive != null -> positive to positive.rule.effect
                else -> matches.firstOrNull { it.rule.elseEffect != null }
                    ?.let { it to it.rule.elseEffect!! }
                    ?: continue
            }
            if (applyEffect(match, effect, positive != null, phase, world, events, budget)) changed = true
            if (budget.exceeded) break
        }
        return changed
    }

    private fun evaluate(rule: Rule, state: GameState): List<Match> {
        val c = rule.conditions
        return state.objectsInReadingOrder()
            .filter { it.type == c.target && (c.targetState == null || it.state == c.targetState) }
            .map { target -> Match(rule, target, sourcesFor(rule.trigger, c, target, state)) }
    }

    private fun sourcesFor(trigger: Trigger, c: RuleConditions, target: GameObject, state: GameState): List<GameObject> =
        when (trigger) {
            Trigger.TOUCH -> target.position.neighbours()
                .mapNotNull { state.objectAt(it) }
                .filter { matchesSource(it, c) }

            Trigger.LOAD -> when (c.direction) {
                LoadDirection.DOWN -> state.loadStack(target.position)
                    .takeIf { stack -> stack.sumOf { it.load } >= c.minLoad }
                    .orEmpty()
                LoadDirection.UP -> state.liftRegion(target.position)
                    .takeIf { gas -> gas.sumOf { it.lift } >= c.minLoad }
                    .orEmpty()
            }

            Trigger.HEAT -> if (target.temp < c.minLoad) {
                emptyList()
            } else {
                // Whatever passes the heat on: hot neighbours or warm conductors (else the target's own heat).
                target.position.neighbours().mapNotNull { state.objectAt(it) }
                    .filter { if (it.conducts) it.temp > 0 else it.heatOutput > 0 }
                    .ifEmpty { listOf(target) }
            }

            Trigger.POWER -> if (!state.isPowered(target.position)) {
                emptyList()
            } else {
                // Whatever brings the current: the live network cells touching the target.
                target.position.neighbours().filter { it in state.powered }.mapNotNull { state.objectAt(it) }
                    .ifEmpty { listOf(target) }
            }

            Trigger.SIGNAL -> {
                val channel = target.string(Props.CHANNEL)
                if (channel == null) {
                    emptyList()
                } else {
                    state.objects.filter {
                        it.id != target.id && it.string(Props.CHANNEL) == channel && isSignalling(it, c)
                    }
                }
            }
        }

    private fun matchesSource(o: GameObject, c: RuleConditions): Boolean =
        (c.source == null || o.type == c.source) &&
            (c.sourceState == null || o.state == c.sourceState) &&
            (!c.sourceHot || o.heatOutput > 0)

    /** Without an explicit source, any object in one of its type's signal states sends a signal. */
    private fun isSignalling(o: GameObject, c: RuleConditions): Boolean =
        if (c.source != null) matchesSource(o, c) else o.state in (types[o.type]?.signalStates ?: emptySet())

    private fun applyEffect(
        match: Match,
        effect: RuleEffect,
        positive: Boolean,
        phase: Phase,
        world: MutableWorld,
        events: MutableList<RuleEvent>,
        budget: Budget,
    ): Boolean {
        val target = world.byId(match.target.id) ?: return false
        val source = match.sources.firstOrNull()?.let { world.byId(it.id) }
        val changesState = effect.targetState != null && effect.targetState != target.state
        val spawns = effect.spawnObject != null && (effect.targetState == null || changesState)
        if (!changesState && !spawns) return false
        if (!budget.consume()) return false

        if (changesState) setState(world, target, effect.targetState!!)
        if (effect.targetConsume > 0 && target.isFluid) {
            world.setAmount(target.id, target.amount - effect.targetConsume)
        }
        var spawned: GameObject? = null
        if (spawns) spawned = spawn(world, effect.spawnObject!!, effect.spawnAmount, target.position)
        if (source != null) {
            if (effect.sourceConsume > 0 && source.isFluid) {
                world.setAmount(source.id, source.amount - effect.sourceConsume)
            }
            if (effect.sourceState != null && world.byId(source.id) != null) setState(world, source, effect.sourceState)
        }
        events += RuleEvent(
            ruleId = match.rule.id,
            phase = phase,
            positive = positive,
            targetId = target.id,
            targetType = target.type,
            previousState = target.state,
            newState = if (changesState) effect.targetState else null,
            sourceIds = match.sources.map { it.id },
            sourceTypes = match.sources.map { it.type },
            spawnedId = spawned?.id,
        )
        return true
    }

    private fun setState(world: MutableWorld, o: GameObject, state: String) {
        world.setState(o.id, state)
        if (state in types.require(o.type).vanishStates) world.remove(o.id)
    }

    /**
     * Spawns at [origin] if free, otherwise next to it (up first: steam rises off a fire).
     * Liquids and gases merge into neighbouring cells of the same kind and spill over into further
     * free cells. Gas that finds no room bubbles up through liquid to the nearest free cell.
     */
    private fun spawn(world: MutableWorld, type: String, amount: Int?, origin: Position): GameObject? {
        val t = types.require(type)
        val fluid = t.properties[Props.LIQUID]?.toBooleanStrictOrNull() == true ||
            t.properties[Props.GAS]?.toBooleanStrictOrNull() == true
        val gas = t.properties[Props.GAS]?.toBooleanStrictOrNull() == true
        if (!fluid) {
            val cell = (listOf(origin) + origin.neighbours()).firstOrNull { world.isFree(it) } ?: return null
            return world.spawn(type, cell, types)
        }
        val capacity = world.fluidCapacity(type, types)
        var remaining = amount ?: capacity
        var first: GameObject? = null
        fun pour(cell: Position) {
            if (remaining <= 0) return
            val existing = world.at(cell)
            if (existing == null && world.isFree(cell)) {
                val portion = minOf(remaining, capacity)
                val o = world.spawn(type, cell, types, portion)
                remaining -= portion
                if (first == null) first = o
            } else if (existing != null && existing.type == type && existing.amount < existing.capacity) {
                val portion = minOf(remaining, existing.capacity - existing.amount)
                world.setAmount(existing.id, existing.amount + portion)
                remaining -= portion
                if (first == null) first = world.byId(existing.id)
            }
        }
        for (cell in listOf(origin) + origin.neighbours()) pour(cell)
        if (remaining <= 0 || !gas) return first
        // Bubbles rise through liquid and other gas (never through solids) to the nearest free cells.
        fun passable(p: Position) = world.inBounds(p) && !world.isWall(p) && world.at(p)?.isFluid == true
        val seen = hashSetOf(origin)
        var frontier = origin.neighbours().filter { passable(it) }
        repeat(GAS_SEARCH_DEPTH) {
            val next = mutableListOf<Position>()
            for (cell in frontier) {
                if (!seen.add(cell)) continue
                for (n in cell.neighbours()) {
                    if (n in seen) continue
                    pour(n)
                    if (remaining <= 0) return first
                    if (passable(n)) next += n
                }
            }
            frontier = next
        }
        return first
    }

    companion object {
        const val DEFAULT_MAX_TRANSFORMATIONS = 100
        private const val GAS_SEARCH_DEPTH = 4
    }
}
