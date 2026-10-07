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
    /**
     * TOUCH: anything hot counts as source (fire, burning wood, lava …): touching it, or within its
     * heat radius.
     */
    val sourceHot: Boolean = false,
    /** With [sourceHot]: the heat the source must give off at least (a big fire boils water, embers do not). */
    val minHeat: Int = 1,
    /**
     * With [sourceHot]: the source must burn with an open flame (fire, burning wood). Embers and hot metal
     * are hot but have no flame, so they never set anything alight.
     */
    val sourceFlame: Boolean = false,
    /** A liquid or gas source must hold at least this much (only deep water douses a big fire). */
    val sourceMinAmount: Int = 0,
    /** A liquid or gas target must hold at least this much (only a full cell of water freezes). */
    val targetMinAmount: Int = 0,
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
    /** The target turns into another element in place (water freezing to ice); liquids keep their amount. */
    val transform: String? = null,
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
    /** Sound effect played when the reaction happens (e.g. "hiss", "crackle"). */
    val sound: String? = null,
) {
    val phase: Phase get() = if (trigger == Trigger.SIGNAL) Phase.SIGNAL else Phase.STATE
}
