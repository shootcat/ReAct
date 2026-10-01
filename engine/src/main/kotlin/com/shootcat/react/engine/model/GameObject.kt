package com.shootcat.react.engine.model

/** Property keys the engine itself understands. Everything else is free-form level data. */
object Props {
    /** The object falls down when the cell below is free. */
    const val GRAVITY = "gravity"
    /** The object rises when the cell above is free (e.g. steam). */
    const val RISES = "rises"
    /** A blocked object slides diagonally or flows sideways instead of stopping (water, steam). */
    const val FLOWS = "flows"
    /** Contribution to the load on whatever the object rests on. */
    const val WEIGHT = "weight"
    /** Signal channel shared by sensors and the actuators they drive. */
    const val CHANNEL = "channel"
}

/**
 * Every thing in the world is a [GameObject]: a type, a state, a position and properties.
 * Behaviour never depends on the id, only on type, state and properties.
 */
data class GameObject(
    val id: String,
    val type: String,
    val state: String,
    val position: Position,
    val properties: Map<String, String> = emptyMap(),
    /** Whether the player may drag this object while setting up an experiment. */
    val movable: Boolean = false,
) {
    fun flag(key: String): Boolean = properties[key]?.toBooleanStrictOrNull() ?: false
    fun int(key: String): Int = properties[key]?.toIntOrNull() ?: 0
    fun string(key: String): String? = properties[key]

    val weight: Int get() = int(Props.WEIGHT)
}
