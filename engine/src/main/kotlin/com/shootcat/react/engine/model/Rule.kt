package com.shootcat.react.engine.model

enum class Trigger {
    /** Source and target are orthogonally adjacent. */
    TOUCH,
    /** The weight resting on top of the target reaches a threshold. */
    LOAD,
    /** A source on the same signal channel as the target is in the required state. */
    SIGNAL,
}

/** Fixed evaluation order inside one simulation step. */
enum class Phase(val number: Int, val label: String) {
    STATE(1, "Zustand"),
    PHYSICS(2, "Physik"),
    SIGNAL(3, "Signal"),
}

data class RuleConditions(
    val source: String? = null,
    val sourceState: String? = null,
    val target: String,
    val targetState: String? = null,
    val minLoad: Int = 1,
)

data class RuleEffect(
    val targetState: String? = null,
    val spawnObject: String? = null,
)

data class Rule(
    val id: String,
    val name: String,
    val trigger: Trigger,
    val conditions: RuleConditions,
    val effect: RuleEffect,
    /** Applied while the condition does not hold (e.g. a plate springs back up). */
    val elseEffect: RuleEffect? = null,
) {
    val phase: Phase get() = if (trigger == Trigger.SIGNAL) Phase.SIGNAL else Phase.STATE
}
