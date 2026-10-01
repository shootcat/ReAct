package com.shootcat.react.engine.model

enum class Trigger {
    /** Source and target are orthogonally adjacent. */
    TOUCH,
    /** Weight resting on the target (DOWN) or gas pressure pushing it from below (UP) reaches a threshold. */
    LOAD,
    /** A signal arrives on the target's channel. */
    SIGNAL,
    /** Heat conducted into the target (its temperature) reaches a threshold. */
    HEAT,
    /** Electric current reaches the target: it carries current itself or touches something that does. */
    POWER,
}

enum class LoadDirection { DOWN, UP }

/** Fixed evaluation order inside one simulation step. */
enum class Phase(val number: Int, val label: String) {
    STATE(1, "Zustand"),
    PHYSICS(2, "Physik"),
    SIGNAL(3, "Signal"),
}

data class RuleConditions(
    /** Source type; for SIGNAL rules null means "any object that is currently signalling". */
    val source: String? = null,
    val sourceState: String? = null,
    val target: String,
    val targetState: String? = null,
    val minLoad: Int = 1,
    val direction: LoadDirection = LoadDirection.DOWN,
    /** TOUCH: any hot neighbour counts as source (fire, burning wood, hot metal …). */
    val sourceHot: Boolean = false,
)

data class RuleEffect(
    val targetState: String? = null,
    val spawnObject: String? = null,
    /** Amount for a spawned liquid or gas; defaults to a full cell. */
    val spawnAmount: Int? = null,
    /** New state of the (first) source, e.g. steam condensing. */
    val sourceState: String? = null,
    /** Amount the (first) source loses if it is a liquid or gas, e.g. water evaporating on a fire. */
    val sourceConsume: Int = 0,
    /** Amount the target loses if it is a liquid or gas, e.g. water boiling on hot metal. */
    val targetConsume: Int = 0,
)

data class Rule(
    val id: String,
    val name: String,
    val trigger: Trigger,
    val conditions: RuleConditions,
    val effect: RuleEffect,
    /** Applied while the condition does not hold (e.g. a plate springs back up). */
    val elseEffect: RuleEffect? = null,
    /** The world that introduces this reaction (Discovery Log grouping). */
    val world: Int = 1,
) {
    val phase: Phase get() = if (trigger == Trigger.SIGNAL) Phase.SIGNAL else Phase.STATE
}
