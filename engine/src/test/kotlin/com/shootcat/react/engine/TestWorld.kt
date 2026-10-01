package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.ObjectType
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog

/** Small, content-independent world for unit tests. */
object TestWorld {
    val types = TypeCatalog(
        listOf(
            ObjectType("FIRE", "Feuer", "ACTIVE"),
            ObjectType("ICE", "Eis", "SOLID", vanishStates = setOf("MELTED")),
            ObjectType("WATER", "Wasser", "LIQUID", mapOf("gravity" to "true", "liquid" to "true", "weight" to "1")),
            ObjectType("STONE", "Stein", "SOLID", mapOf("gravity" to "true", "weight" to "3")),
            ObjectType("BUTTON", "Schalter", "UP"),
            ObjectType("PLATE", "Druckplatte", "UP"),
            ObjectType("DOOR", "Tür", "LOCKED"),
            ObjectType("LAMP", "Lampe", "OFF"),
        ),
    )

    val melt = Rule(
        "melt", "Schmelzen", Trigger.TOUCH,
        RuleConditions(source = "FIRE", target = "ICE", targetState = "SOLID"),
        RuleEffect(targetState = "MELTED", spawnObject = "WATER"),
    )
    val waterButton = Rule(
        "water_button", "Wasserkontakt", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "BUTTON", targetState = "UP"),
        RuleEffect(targetState = "PRESSED"),
    )
    val plateLoad = Rule(
        "plate_load", "Gewicht", Trigger.LOAD,
        RuleConditions(target = "PLATE", minLoad = 3),
        RuleEffect(targetState = "PRESSED"),
        elseEffect = RuleEffect(targetState = "UP"),
    )
    val buttonDoor = Rule(
        "button_door", "Signal", Trigger.SIGNAL,
        RuleConditions(source = "BUTTON", sourceState = "PRESSED", target = "DOOR"),
        RuleEffect(targetState = "UNLOCKED"),
        elseEffect = RuleEffect(targetState = "LOCKED"),
    )
    val plateDoor = Rule(
        "plate_door", "Signal", Trigger.SIGNAL,
        RuleConditions(source = "PLATE", sourceState = "PRESSED", target = "DOOR"),
        RuleEffect(targetState = "UNLOCKED"),
        elseEffect = RuleEffect(targetState = "LOCKED"),
    )

    val rules = listOf(melt, waterButton, plateLoad, buttonDoor, plateDoor)

    fun engine(rules: List<Rule> = this.rules, max: Int = RuleEngine.DEFAULT_MAX_TRANSFORMATIONS) =
        RuleEngine(types, rules, max)

    private val symbols = mapOf(
        'F' to "FIRE", 'I' to "ICE", 'W' to "WATER", 'S' to "STONE",
        'B' to "BUTTON", 'P' to "PLATE", 'D' to "DOOR",
    )

    /**
     * Builds a state from ASCII rows: '#' wall, '.' empty, F fire, I ice, W water, S stone,
     * B button, P plate, D door. Sensors and doors share channel "A". Ids are "<type>_<x>_<y>".
     */
    fun state(vararg rows: String): GameState {
        val walls = mutableSetOf<Position>()
        val objects = mutableListOf<GameObject>()
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val p = Position(x, y)
                when (c) {
                    '#' -> walls += p
                    '.' -> Unit
                    else -> {
                        val type = symbols[c] ?: error("unknown symbol $c")
                        val props = if (type in setOf("BUTTON", "PLATE", "DOOR")) mapOf("channel" to "A") else emptyMap()
                        objects += types.create("${type.lowercase()}_${x}_$y", type, p, properties = props, movable = true)
                    }
                }
            }
        }
        return GameState(rows.first().length, rows.size, walls, objects.sortedBy { it.id })
    }

    fun GameState.typeAt(x: Int, y: Int): String? = objectAt(Position(x, y))?.type

    fun GameState.stateOf(id: String): String? = objectById(id)?.state

    fun GameState.count(type: String): Int = objects.count { it.type == type }

    fun GameState.positionsOf(type: String): List<Position> =
        objects.filter { it.type == type }.map { it.position }.sortedWith(compareBy({ it.y }, { it.x }))
}
