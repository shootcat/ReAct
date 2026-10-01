package com.shootcat.react.engine.model

/** Property keys the engine itself understands. Everything else is free-form level data. */
object Props {
    /** Solid that falls when nothing holds it. */
    const val GRAVITY = "gravity"
    /** Gas that rises (steam). */
    const val RISES = "rises"
    /** A blocked gas slides diagonally or drifts sideways towards an opening. */
    const val FLOWS = "flows"
    /** Volume liquid (water): each cell holds an amount that falls, spreads and levels out. */
    const val LIQUID = "liquid"
    /** Maximum liquid amount per cell. */
    const val CAPACITY = "capacity"
    /** Relative density, liquids are 10: lighter solids float, heavier ones sink. */
    const val DENSITY = "density"
    /** Load on whatever the object rests on (for liquids: per unit of amount). */
    const val WEIGHT = "weight"
    /** Upward push of a gas, e.g. steam pressure against a piston. */
    const val LIFT = "lift"
    /** Signal channel shared by sensors and the actuators they drive. */
    const val CHANNEL = "channel"
}

/** Liquid density everything else is compared with. */
const val LIQUID_DENSITY = 10

/**
 * Every thing in the world is a [GameObject]: a type, a state, a position and properties.
 * Liquids additionally carry an [amount] (how full their cell is). Behaviour never depends on the id.
 */
data class GameObject(
    val id: String,
    val type: String,
    val state: String,
    val position: Position,
    val properties: Map<String, String> = emptyMap(),
    /** Whether the player may drag this object while setting up an experiment. */
    val movable: Boolean = false,
    val amount: Int = 0,
) {
    fun flag(key: String): Boolean = properties[key]?.toBooleanStrictOrNull() ?: false
    fun int(key: String, default: Int = 0): Int = properties[key]?.toIntOrNull() ?: default
    fun string(key: String): String? = properties[key]

    val isLiquid: Boolean get() = flag(Props.LIQUID)
    val falls: Boolean get() = flag(Props.GRAVITY)
    val rises: Boolean get() = flag(Props.RISES)
    val density: Int get() = int(Props.DENSITY, LIQUID_DENSITY)
    val capacity: Int get() = int(Props.CAPACITY, 8)

    /** Load this object puts on what is below it. */
    val load: Int get() = if (isLiquid) amount * int(Props.WEIGHT) else int(Props.WEIGHT)

    val lift: Int get() = int(Props.LIFT)
}
