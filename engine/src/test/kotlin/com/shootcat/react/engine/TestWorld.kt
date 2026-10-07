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
            ObjectType(
                "FIRE", "Feuer", "ACTIVE",
                mapOf("gravity" to "true", "density" to "15", "weight" to "10", "heat" to "6", "heat_state" to "ACTIVE", "flame" to "true"),
            ),
            ObjectType(
                "BIG_FIRE", "Großes Feuer", "ACTIVE",
                mapOf(
                    "gravity" to "true", "density" to "15", "weight" to "20", "heat" to "7", "heat_state" to "ACTIVE",
                    "heat_radius" to "2", "flame" to "true",
                ),
                vanishStates = setOf("OUT"),
            ),
            ObjectType("VAPOR", "Wasserdampf", "GAS", mapOf("gas" to "true", "capacity" to "8", "condense" to "CLOUD")),
            ObjectType("CLOUD", "Wolke", "GATHERING", mapOf("cloud" to "true", "capacity" to "8", "rain" to "WATER", "rain_state" to "RAINING")),
            ObjectType("ICE", "Eis", "SOLID", mapOf("gravity" to "true", "density" to "9", "weight" to "14"), vanishStates = setOf("MELTED")),
            ObjectType("WATER", "Wasser", "LIQUID", mapOf("liquid" to "true", "capacity" to "8", "density" to "10", "weight" to "2", "wire" to "true")),
            ObjectType("STEAM", "Dampf", "GAS", mapOf("gas" to "true", "capacity" to "8", "lift" to "1")),
            ObjectType("STONE", "Stein", "SOLID", mapOf("gravity" to "true", "density" to "25", "weight" to "40")),
            ObjectType(
                "WOOD", "Holz", "DRY",
                mapOf(
                    "gravity" to "true", "density" to "6", "weight" to "8", "heat" to "4", "heat_state" to "BURNING",
                    "fuel" to "10", "burnt_state" to "ASH",
                ),
                vanishStates = setOf("ASH"),
            ),
            ObjectType("CHARCOAL", "Holzkohle", "SOLID", mapOf("gravity" to "true", "density" to "6", "weight" to "5")),
            ObjectType(
                "METAL", "Metall", "COLD",
                mapOf(
                    "gravity" to "true", "density" to "30", "weight" to "30", "conducts" to "true", "heat" to "3", "heat_state" to "HOT",
                    "wire" to "true",
                ),
            ),
            ObjectType("GATE", "Schieber", "IDLE", mapOf("pushable" to "true", "resist" to "2")),
            ObjectType("BUTTON", "Schalter", "UP", signalStates = setOf("PRESSED")),
            ObjectType("PLATE", "Druckplatte", "UP", signalStates = setOf("PRESSED")),
            ObjectType("PISTON", "Kolben", "IDLE", signalStates = setOf("PUSHED")),
            ObjectType("HATCH", "Klappe", "CLOSED", vanishStates = setOf("OPEN")),
            ObjectType("DOOR", "Tür", "LOCKED"),
            ObjectType("LAMP", "Lampe", "OFF", signalStates = setOf("ON")),
            ObjectType("BATTERY", "Batterie", "CHARGED", mapOf("gravity" to "true", "density" to "20", "weight" to "20", "power" to "true")),
            ObjectType("CABLE", "Kabel", "IDLE", mapOf("wire" to "true")),
            ObjectType("COIL", "Heizstab", "COLD", mapOf("gravity" to "true", "density" to "30", "weight" to "20", "heat" to "6", "heat_state" to "HOT")),
            ObjectType("RELAY", "Relais", "OPEN", mapOf("wire" to "true", "wire_state" to "CLOSED")),
            ObjectType("TURBINE", "Turbine", "IDLE", mapOf("power" to "true", "power_state" to "SPINNING")),
            ObjectType(
                "LAVA", "Lava", "MOLTEN",
                mapOf("liquid" to "true", "capacity" to "8", "density" to "30", "weight" to "6", "heat" to "6", "heat_state" to "MOLTEN"),
                vanishStates = setOf("COOLED"),
            ),
            ObjectType(
                "OIL", "Öl", "LIQUID",
                mapOf(
                    "liquid" to "true", "capacity" to "8", "density" to "8", "weight" to "1", "heat" to "5",
                    "heat_state" to "BURNING", "fuel" to "8", "burnt_state" to "BURNT",
                ),
                vanishStates = setOf("BURNT"),
            ),
            ObjectType(
                "SAND", "Sand", "DRY",
                mapOf(
                    "gravity" to "true", "density" to "20", "weight" to "20", "granular" to "true", "granular_state" to "DRY",
                    "wire" to "true", "wire_state" to "WET",
                ),
            ),
        ),
    )

    val melt = Rule(
        "melt", "Schmelzen", Trigger.TOUCH,
        RuleConditions(sourceHot = true, target = "ICE", targetState = "SOLID"),
        RuleEffect(targetState = "MELTED", spawnObject = "WATER"),
    )
    val douse = Rule(
        "douse", "Löschen", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "FIRE", targetState = "ACTIVE"),
        RuleEffect(targetState = "OUT", spawnObject = "STEAM", spawnAmount = 6, sourceConsume = 3),
    )
    val thaw = Rule(
        "thaw", "Tauen", Trigger.TOUCH,
        RuleConditions(source = "STEAM", target = "ICE", targetState = "SOLID"),
        RuleEffect(targetState = "MELTED", spawnObject = "WATER", sourceConsume = 4),
    )
    val ignite = Rule(
        "ignite", "Entzünden", Trigger.TOUCH,
        RuleConditions(sourceHot = true, target = "WOOD", targetState = "DRY"),
        RuleEffect(targetState = "BURNING"),
    )
    val douseWood = Rule(
        "douse_wood", "Ablöschen", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "WOOD", targetState = "BURNING"),
        RuleEffect(transform = "CHARCOAL", spawnObject = "STEAM", spawnAmount = 4, sourceConsume = 2),
    )
    val conduct = Rule(
        "conduct", "Wärmeleitung", Trigger.HEAT,
        RuleConditions(target = "METAL", minLoad = 1),
        RuleEffect(targetState = "HOT"),
        elseEffect = RuleEffect(targetState = "COLD"),
    )
    val boil = Rule(
        "boil", "Sieden", Trigger.TOUCH,
        RuleConditions(source = "METAL", sourceState = "HOT", target = "WATER"),
        RuleEffect(spawnObject = "STEAM", spawnAmount = 2, targetConsume = 1),
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
        RuleConditions(target = "PISTON", minLoad = 9, direction = LoadDirection.UP),
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

    val lampPower = Rule(
        "lamp_power", "Licht", Trigger.POWER,
        RuleConditions(target = "LAMP"),
        RuleEffect(targetState = "ON"),
        elseEffect = RuleEffect(targetState = "OFF"),
    )
    val coilPower = Rule(
        "coil_power", "Glühen", Trigger.POWER,
        RuleConditions(target = "COIL"),
        RuleEffect(targetState = "HOT"),
        elseEffect = RuleEffect(targetState = "COLD"),
    )
    val relaySignal = Rule(
        "relay_signal", "Relais", Trigger.SIGNAL,
        RuleConditions(target = "RELAY"),
        RuleEffect(targetState = "CLOSED"),
        elseEffect = RuleEffect(targetState = "OPEN"),
    )
    val turbineSteam = Rule(
        "turbine_steam", "Turbine", Trigger.LOAD,
        RuleConditions(target = "TURBINE", minLoad = 6, direction = LoadDirection.UP),
        RuleEffect(targetState = "SPINNING"),
        elseEffect = RuleEffect(targetState = "IDLE"),
    )
    val coolLava = Rule(
        "cool_lava", "Erstarren", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "LAVA", targetState = "MOLTEN"),
        RuleEffect(targetState = "COOLED", spawnObject = "STONE", sourceConsume = 2),
    )
    val lavaBoil = Rule(
        "lava_boil", "Verdampfen", Trigger.TOUCH,
        RuleConditions(source = "LAVA", target = "WATER"),
        RuleEffect(spawnObject = "STEAM", spawnAmount = 2, targetConsume = 1),
    )
    val igniteOil = Rule(
        "ignite_oil", "Ölbrand", Trigger.TOUCH,
        RuleConditions(sourceHot = true, target = "OIL", targetState = "LIQUID"),
        RuleEffect(targetState = "BURNING"),
    )
    val soakSand = Rule(
        "soak_sand", "Aufsaugen", Trigger.TOUCH,
        RuleConditions(source = "WATER", target = "SAND", targetState = "DRY"),
        RuleEffect(targetState = "WET", sourceConsume = 4),
    )

    val rules = listOf(
        melt, douse, thaw, ignite, douseWood, conduct, boil, waterButton, plateLoad, pistonLift, doorSignal, hatchSignal,
        lampPower, coilPower, relaySignal, turbineSteam, coolLava, lavaBoil, igniteOil, soakSand,
    )

    fun engine(rules: List<Rule> = this.rules, max: Int = RuleEngine.DEFAULT_MAX_TRANSFORMATIONS) =
        RuleEngine(types, rules, max)

    private val symbols = mapOf(
        'F' to "FIRE", 'I' to "ICE", 'W' to "WATER", 'S' to "STONE", 'V' to "STEAM", 'O' to "WOOD",
        'M' to "METAL", 'm' to "METAL", 'G' to "GATE",
        'B' to "BUTTON", 'P' to "PLATE", 'K' to "PISTON", 'H' to "HATCH", 'D' to "DOOR",
        '+' to "BATTERY", '-' to "CABLE", 'L' to "LAMP", 'Z' to "COIL", 'R' to "RELAY", 'T' to "TURBINE",
        'A' to "LAVA", 'Q' to "OIL", ',' to "SAND",
        'X' to "BIG_FIRE", 'U' to "VAPOR", 'C' to "CLOUD",
    )

    /**
     * Builds a state from ASCII rows: '#' wall, '.' empty, F fire, I ice, W full water, '1'-'7' partial
     * water, V full steam, 'a'-'g' partial steam (1-7), S stone, O wood, M fixed metal, m loose metal,
     * G gate, B button, P plate, K piston, H hatch, D door, + battery, - cable, L lamp, Z coil, R relay,
     * T turbine, A lava, Q oil, ',' sand, X big fire, U full vapor (condenses into clouds), C full cloud.
     * Sensors and actuators share channel "A".
     * Ids are "<type>_<x>_<y>".
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
                    c in 'a'..'g' -> objects += types.create("steam_${x}_$y", "STEAM", p, amount = c - 'a' + 1)
                    else -> {
                        val type = symbols[c] ?: error("unknown symbol $c")
                        val props = when {
                            type in setOf("BUTTON", "PLATE", "PISTON", "HATCH", "DOOR", "LAMP", "RELAY") -> mapOf("channel" to "A")
                            c == 'M' -> mapOf("gravity" to "false")
                            else -> emptyMap()
                        }
                        objects += types.create("${type.lowercase()}_${x}_$y", type, p, properties = props, isMovable = true)
                    }
                }
            }
        }
        return GameState(rows.first().length, rows.size, walls, objects.sortedBy { it.id })
    }

    fun GameState.typeAt(x: Int, y: Int): String? = objectAt(Position(x, y))?.type

    /** The same state with [o] added (e.g. a partly filled cloud). */
    fun GameState.with(o: GameObject): GameState = copy(objects = (objects.filter { it.position != o.position } + o).sortedBy { it.id })

    fun GameState.amountAt(x: Int, y: Int): Int = objectAt(Position(x, y))?.takeIf { it.isLiquid }?.amount ?: 0

    fun GameState.stateOf(id: String): String? = objectById(id)?.state

    fun GameState.count(type: String): Int = objects.count { it.type == type }

    fun GameState.totalWater(): Int = objects.filter { it.isLiquid }.sumOf { it.amount }

    fun GameState.totalSteam(): Int = objects.filter { it.isGas }.sumOf { it.amount }

    fun GameState.tempAt(x: Int, y: Int): Int = objectAt(Position(x, y))?.temp ?: 0

    fun GameState.positionsOf(type: String): List<Position> =
        objects.filter { it.type == type }.map { it.position }.sortedWith(compareBy({ it.y }, { it.x }))

    fun GameState.after(steps: Int, engine: RuleEngine = engine()): GameState {
        var s = this
        repeat(steps) { s = engine.step(s).state }
        return s
    }
}
