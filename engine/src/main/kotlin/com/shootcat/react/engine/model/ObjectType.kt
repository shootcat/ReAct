package com.shootcat.react.engine.model

data class ObjectType(
    val id: String,
    val name: String,
    val defaultState: String,
    val properties: Map<String, String> = emptyMap(),
    /** Entering one of these states removes the object from the world (e.g. ICE -> MELTED). */
    val vanishStates: Set<String> = emptySet(),
    /** In these states the object sends a signal on its channel (e.g. a pressed plate). */
    val signalStates: Set<String> = emptySet(),
    val stateNames: Map<String, String> = emptyMap(),
    val description: String = "",
) {
    fun stateName(state: String): String = stateNames[state] ?: state.lowercase()
}

class TypeCatalog(types: List<ObjectType>) {
    private val byId: Map<String, ObjectType> = types.associateBy { it.id }

    val all: List<ObjectType> = types

    operator fun get(id: String): ObjectType? = byId[id]

    operator fun contains(id: String): Boolean = id in byId

    fun require(id: String): ObjectType = byId[id] ?: throw IllegalArgumentException("Unknown object type '$id'")

    fun name(id: String): String = byId[id]?.name ?: id

    fun create(
        id: String,
        type: String,
        position: Position,
        state: String? = null,
        properties: Map<String, String> = emptyMap(),
        movable: Boolean = false,
        amount: Int? = null,
    ): GameObject {
        val t = require(type)
        val o = GameObject(id, type, state ?: t.defaultState, position, t.properties + properties, movable)
        return if (o.isLiquid) o.copy(amount = (amount ?: o.capacity).coerceIn(1, o.capacity)) else o
    }
}
