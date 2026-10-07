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
import com.shootcat.react.engine.model.WindZone

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
    /** Where the target was when the rule acted on it. */
    val position: Position = Position(0, 0),
)

/** Something audible happened at [position]: a reaction's sound, a cloud forming, rain starting … */
data class Cue(val sound: String, val position: Position)

data class StepResult(
    val state: GameState,
    val events: List<RuleEvent>,
    val transformations: Int,
    val overloaded: Boolean,
    val cues: List<Cue> = emptyList(),
)

/**
 * Deterministic, data-driven rule engine. A step runs three fixed phases:
 *
 * 1. [Phase.STATE]   – TOUCH, LOAD, HEAT and POWER rules, evaluated once against the state at the start of the phase.
 * 2. [Phase.PHYSICS] – falling, floating, flowing, clouds, wind and rain, pressure and heat conduction.
 * 3. [Phase.SIGNAL]  – SIGNAL rules, propagated until nothing changes any more.
 *
 * Per target and phase only one effect applies: the first matching rule (in rule order) wins,
 * and else-effects only apply when no rule matched that target. More than
 * [maxTransformationsPerStep] applied effects abort the step (cascade protection).
 */
class RuleEngine(
    val types: TypeCatalog,
    val rules: List<Rule>,
    private val maxTransformationsPerStep: Int = DEFAULT_MAX_TRANSFORMATIONS,
    /** The level's steady winds that carry clouds along. */
    private val wind: List<WindZone> = emptyList(),
) {
    private val stateRules = rules.filter { it.phase == Phase.STATE }
    private val signalRules = rules.filter { it.phase == Phase.SIGNAL }
    private val sounds = rules.mapNotNull { r -> r.sound?.let { r.id to it } }.toMap()

    fun step(state: GameState): StepResult {
        val world = MutableWorld(state)
        val events = mutableListOf<RuleEvent>()
        val budget = Budget(maxTransformationsPerStep)

        runPhase(stateRules, Phase.STATE, world, events, budget)
        if (!budget.exceeded) {
            Physics.apply(world, types, wind)
        }
        if (!budget.exceeded) {
            // Signals travel instantly: keep propagating until the network is stable.
            while (runPhase(signalRules, Phase.SIGNAL, world, events, budget) && !budget.exceeded) Unit
        }
        val ruleCues = events.filter { it.positive }.mapNotNull { e ->
            val sound = sounds[e.ruleId] ?: return@mapNotNull null
            Cue(sound, e.position)
        }
        return StepResult(world.snapshot(), events, budget.used, budget.exceeded, ruleCues + world.cues)
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
        val heat = if (phase == Phase.STATE) farHeat(snapshot) else emptyMap()
        val byTarget = LinkedHashMap<String, MutableList<Match>>()
        for (rule in rules) {
            for (match in evaluate(rule, snapshot, heat)) {
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

    private fun evaluate(rule: Rule, state: GameState, heat: Map<Position, List<GameObject>>): List<Match> {
        val c = rule.conditions
        return state.objectsInReadingOrder()
            .filter { it.type == c.target && (c.targetState == null || it.state == c.targetState) }
            .filter { c.targetMinAmount <= 0 || it.amount >= c.targetMinAmount }
            .map { target -> Match(rule, target, sourcesFor(rule.trigger, c, target, state, heat)) }
    }

    /**
     * Hot objects whose heat reaches a cell from further away than touching (a big fire, lava): it
     * spreads up to their [GameObject.heatRadius] through open air and gas. Whatever stands in the
     * way (rock, water, a stone) takes the heat but does not pass it on. Hot metal stuck in a slot of
     * a wall (walls on two opposite sides) also heats the cell straight behind each wall cell it
     * touches; one wall cell thick, never more.
     */
    private fun farHeat(state: GameState): Map<Position, List<GameObject>> {
        val result = HashMap<Position, MutableList<GameObject>>()
        for (o in state.objects) {
            if (o.heatOutput <= 0) continue
            if (o.heatsThroughWalls && inSlot(state, o.position)) {
                for (wall in o.position.neighbours()) {
                    val behind = Position(2 * wall.x - o.position.x, 2 * wall.y - o.position.y)
                    if (state.isWall(wall) && state.inBounds(behind) && !state.isWall(behind)) {
                        result.getOrPut(behind) { mutableListOf() } += o
                    }
                }
            }
            val radius = o.heatRadius
            if (radius < 2) continue
            val distance = hashMapOf(o.position to 0)
            val queue = ArrayDeque(listOf(o.position))
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val d = distance.getValue(p)
                if (d >= 2) result.getOrPut(p) { mutableListOf() } += o
                val occupant = state.objectAt(p)
                if (d == radius || (p != o.position && occupant != null && !occupant.isAiry)) continue
                for (n in p.neighbours()) {
                    if (n in distance || !state.inBounds(n) || state.isWall(n)) continue
                    distance[n] = d + 1
                    queue += n
                }
            }
        }
        return result
    }

    private fun inSlot(state: GameState, p: Position): Boolean =
        state.isWall(p.left()) && state.isWall(p.right()) || state.isWall(p.up()) && state.isWall(p.down())

    private fun sourcesFor(
        trigger: Trigger,
        c: RuleConditions,
        target: GameObject,
        state: GameState,
        heat: Map<Position, List<GameObject>>,
    ): List<GameObject> =
        when (trigger) {
            Trigger.TOUCH -> {
                val touching = target.position.neighbours().mapNotNull { state.objectAt(it) }.filter { matchesSource(it, c) }
                if (!c.sourceHot) touching else touching + heat[target.position].orEmpty().filter { matchesSource(it, c) && it !in touching }
            }

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
            (!c.sourceHot || (o.heatOutput > 0 && o.heatOutput >= c.minHeat)) &&
            (!c.sourceFlame || o.isFlame) &&
            (c.sourceMinAmount <= 0 || o.amount >= c.sourceMinAmount)

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
        val transforms = effect.transform != null && effect.transform != target.type
        if (!changesState && !spawns && !transforms) return false
        if (!budget.consume()) return false

        if (transforms) {
            world.remove(target.id)
            val amount = target.amount.takeIf { target.hasAmount }
            // What turns into a loose solid thing is a block the player can pick up.
            val loose = target.isMovable || types[effect.transform!!]?.properties?.get(Props.GRAVITY) == "true" &&
                types[effect.transform]?.properties?.get(Props.LIQUID) != "true"
            world.spawn(effect.transform, target.position, types, amount, isMovable = loose)
        } else if (changesState) {
            setState(world, target, effect.targetState!!)
        }
        if (effect.targetConsume > 0 && target.isFluid && !transforms) {
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
            position = target.position,
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
