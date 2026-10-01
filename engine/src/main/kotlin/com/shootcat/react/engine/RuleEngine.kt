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
 * 1. [Phase.STATE]   – TOUCH and LOAD rules, evaluated once against the state at the start of the phase.
 * 2. [Phase.PHYSICS] – falling, rising and flowing.
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
        (c.source == null || o.type == c.source) && (c.sourceState == null || o.state == c.sourceState)

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
        var spawned: GameObject? = null
        if (spawns) spawned = spawn(world, effect.spawnObject!!, effect.spawnAmount, target.position)
        if (source != null) {
            if (effect.sourceConsume > 0 && source.isLiquid) {
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
     * Liquids merge into neighbouring liquid. Gases bubble up through liquid to the nearest free cell.
     */
    private fun spawn(world: MutableWorld, type: String, amount: Int?, origin: Position): GameObject? {
        val t = types.require(type)
        val liquid = t.properties[Props.LIQUID]?.toBooleanStrictOrNull() == true
        val gas = t.properties[Props.RISES]?.toBooleanStrictOrNull() == true
        for (cell in listOf(origin) + origin.neighbours()) {
            if (world.isFree(cell)) return world.spawn(type, cell, types, amount)
            val existing = world.at(cell)
            if (liquid && existing != null && existing.type == type && existing.amount < existing.capacity) {
                val add = amount ?: existing.capacity
                world.setAmount(existing.id, minOf(existing.capacity, existing.amount + add))
                return world.byId(existing.id)
            }
        }
        if (!gas) return null
        // Bubbles squeeze past liquid and loose objects (never through walls) to the nearest free cell.
        fun passable(p: Position) = world.inBounds(p) && !world.isWall(p) && world.at(p) != null
        val seen = hashSetOf(origin)
        var frontier = origin.neighbours().filter { passable(it) }
        repeat(GAS_SEARCH_DEPTH) {
            val next = mutableListOf<Position>()
            for (cell in frontier) {
                if (!seen.add(cell)) continue
                for (n in cell.neighbours()) {
                    if (n in seen) continue
                    if (world.isFree(n)) return world.spawn(type, n, types, amount)
                    if (passable(n)) next += n
                }
            }
            frontier = next
        }
        return null
    }

    companion object {
        const val DEFAULT_MAX_TRANSFORMATIONS = 100
        private const val GAS_SEARCH_DEPTH = 4
    }
}
