package com.shootcat.react.engine

import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LoadDirection
import com.shootcat.react.engine.model.ObjectType
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog

/** Small, content-independent world for unit tests (same physics as world 1). */
object TestWorld {
    val types = TypeCatalog(
        listOf(
            ObjectType("FIRE", "Feuer", "ACTIVE", mapOf("gravity" to "true", "density" to "15", "weight" to "10")),
            ObjectType("ICE", "Eis", "SOLID", mapOf("gravity" to "true", "density" to "9", "weight" to "14"), vanishStates = setOf("MELTED")),
            ObjectType("WATER", "Wasser", "LIQUID", mapOf("liquid" to "true", "capacity" to "8", "density" to "10", "weight" to "2")),
            ObjectType("STEAM", "Dampf", "GAS", mapOf("rises" to "true", "flows" to "true", "lift" to "1"), vanishStates = setOf("CONDENSED")),
            ObjectType("STONE", "Stein", "SOLID", mapOf("gravity" to "true", "density" to "25", "weight" to "40")),
            ObjectType("BUTTON", "Schalter", "UP", signalStates = setOf("PRESSED")),
            ObjectType("PLATE", "Druckplatte", "UP", signalStates = setOf("PRESSED")),
            ObjectType("PISTON", "Kolben", "IDLE", signalStates = setOf("PUSHED")),
            ObjectType("HATCH", "Klappe", "CLOSED", vanishStates = setOf("OPEN")),
            ObjectType("DOOR", "Tür", "LOCKED"),
            ObjectType("LAMP", "Lampe", "OFF"),
        ),
    )

    val melt = Rule(
        "melt", "Schmelzen", Trigger.TOUCH,
        RuleConditions(source = "FIRE", sourceState = "ACTIVE", target = "ICE", targetState = "SOLID"),
        RuleEffect(targetState = "MELTED", spawnObject = "WATER"),
    )
    val douse = Rule(
        "douse", "Löschen", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "FIRE", targetState = "ACTIVE"),
        RuleEffect(targetState = "OUT", spawnObject = "STEAM", sourceConsume = 3),
    )
    val thaw = Rule(
        "thaw", "Tauen", Trigger.TOUCH,
        RuleConditions(source = "STEAM", target = "ICE", targetState = "SOLID"),
        RuleEffect(targetState = "MELTED", spawnObject = "WATER", sourceState = "CONDENSED"),
    )
    val waterButton = Rule(
        "water_button", "Wasserkontakt", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "BUTTON", targetState = "UP"),
        RuleEffect(targetState = "PRESSED"),
    )
    val plateLoad = Rule(
        "plate_load", "Gewicht", Trigger.LOAD,
        RuleConditions(target = "PLATE", minLoad = 32),
        RuleEffect(targetState = "PRESSED"),
        elseEffect = RuleEffect(targetState = "UP"),
    )
    val pistonLift = Rule(
        "piston_lift", "Dampfdruck", Trigger.LOAD,
        RuleConditions(target = "PISTON", minLoad = 2, direction = LoadDirection.UP),
        RuleEffect(targetState = "PUSHED"),
        elseEffect = RuleEffect(targetState = "IDLE"),
    )
    val doorSignal = Rule(
        "door_signal", "Tür-Signal", Trigger.SIGNAL,
        RuleConditions(target = "DOOR"),
        RuleEffect(targetState = "UNLOCKED"),
        elseEffect = RuleEffect(targetState = "LOCKED"),
    )
    val hatchSignal = Rule(
        "hatch_signal", "Klappen-Signal", Trigger.SIGNAL,
        RuleConditions(target = "HATCH"),
        RuleEffect(targetState = "OPEN"),
    )

    val rules = listOf(melt, douse, thaw, waterButton, plateLoad, pistonLift, doorSignal, hatchSignal)

    fun engine(rules: List<Rule> = this.rules, max: Int = RuleEngine.DEFAULT_MAX_TRANSFORMATIONS) =
        RuleEngine(types, rules, max)

    private val symbols = mapOf(
        'F' to "FIRE", 'I' to "ICE", 'W' to "WATER", 'S' to "STONE", 'V' to "STEAM",
        'B' to "BUTTON", 'P' to "PLATE", 'K' to "PISTON", 'H' to "HATCH", 'D' to "DOOR",
    )

    /**
     * Builds a state from ASCII rows: '#' wall, '.' empty, F fire, I ice, W full water, '1'-'7' partial
     * water, S stone, V steam, B button, P plate, K piston, H hatch, D door. Sensors and actuators share
     * channel "A". Ids are "<type>_<x>_<y>".
     */
    fun state(vararg rows: String): GameState {
        val walls = mutableSetOf<Position>()
        val objects = mutableListOf<GameObject>()
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val p = Position(x, y)
                when {
                    c == '#' -> walls += p
                    c == '.' -> Unit
                    c in '1'..'7' -> objects += types.create("water_${x}_$y", "WATER", p, amount = c - '0')
                    else -> {
                        val type = symbols[c] ?: error("unknown symbol $c")
                        val channel = if (type in setOf("BUTTON", "PLATE", "PISTON", "HATCH", "DOOR")) mapOf("channel" to "A") else emptyMap()
                        objects += types.create("${type.lowercase()}_${x}_$y", type, p, properties = channel, movable = true)
                    }
                }
            }
        }
        return GameState(rows.first().length, rows.size, walls, objects.sortedBy { it.id })
    }

    fun GameState.typeAt(x: Int, y: Int): String? = objectAt(Position(x, y))?.type

    fun GameState.amountAt(x: Int, y: Int): Int = objectAt(Position(x, y))?.takeIf { it.isLiquid }?.amount ?: 0

    fun GameState.stateOf(id: String): String? = objectById(id)?.state

    fun GameState.count(type: String): Int = objects.count { it.type == type }

    fun GameState.totalWater(): Int = objects.filter { it.isLiquid }.sumOf { it.amount }

    fun GameState.positionsOf(type: String): List<Position> =
        objects.filter { it.type == type }.map { it.position }.sortedWith(compareBy({ it.y }, { it.x }))

    fun GameState.after(steps: Int, engine: RuleEngine = engine()): GameState {
        var s = this
        repeat(steps) { s = engine.step(s).state }
        return s
    }
}
