package com.shootcat.react.engine.model

/** Property keys the engine itself understands. Everything else is free-form level data. */
object Props {
    /** Solid that falls when nothing holds it. */
    const val GRAVITY = "gravity"
    /** Volume liquid (water): each cell holds an amount that falls, spreads and levels out. */
    const val LIQUID = "liquid"
    /** Volume gas (steam): rises, bubbles through liquids, collects under ceilings and fills chambers. */
    const val GAS = "gas"
    /** Maximum amount per cell of a liquid or gas. */
    const val CAPACITY = "capacity"
    /** Relative density, liquids are 10: lighter solids float, heavier ones sink. */
    const val DENSITY = "density"
    /** Load on whatever the object rests on (for liquids: per unit of amount). */
    const val WEIGHT = "weight"
    /** Upward push of a gas per unit of amount, e.g. steam pressure against a piston. */
    const val LIFT = "lift"
    /** Signal channel shared by sensors and the actuators they drive. */
    const val CHANNEL = "channel"
    /** Heat the object gives off (fire, burning wood, hot metal) … */
    const val HEAT = "heat"
    /** … but only while it is in this state. Without it the object is always hot. */
    const val HEAT_STATE = "heat_state"
    /** Passes heat on to touching conductors; every cell of metal costs one degree. */
    const val CONDUCTS = "conducts"
    /** Steps a burning object lasts before it is used up. */
    const val FUEL = "fuel"
    /**
     * Burns for this many player moves instead (the move it caught fire in counts), so the player has
     * the moves in between to put it out …
     */
    const val BURN_MOVES = "burn_moves"
    /** … and then crumbles into this type (ash). */
    const val BURNS_INTO = "burns_into"
    /** State a burnt-out object ends up in. */
    const val BURNT_STATE = "burnt_state"
    /** A barrier that gas pressure pushes along when the pressure difference is high enough. */
    const val PUSHABLE = "pushable"
    /** Pressure difference (gas units per cell) a pushable barrier withstands. */
    const val RESIST = "resist"
    /** A source of electric current (battery, spinning turbine) … */
    const val POWER = "power"
    /** … only while in this state. */
    const val POWER_STATE = "power_state"
    /** Carries electric current to touching objects (cable, metal, water) … */
    const val WIRE = "wire"
    /** … only while in this state (e.g. a closed relay, wet sand). */
    const val WIRE_STATE = "wire_state"
    /** Loose material (sand) that slides off to the side when it cannot fall straight down … */
    const val GRANULAR = "granular"
    /** … only while in this state (wet sand sticks together). */
    const val GRANULAR_STATE = "granular_state"
    /** An open flame while it gives off heat (fire, burning wood): what "extinguish" goals look for. */
    const val FLAME = "flame"
    /** How far (in cells, around obstacles) the heat of a hot object reaches; 1 means touching only. */
    const val HEAT_RADIUS = "heat_radius"
    /** While in this state it cannot be picked up: glowing hot metal would burn the hand. */
    const val UNTOUCHABLE_STATE = "untouchable_state"
    /** Stuck in a slot of a wall, its heat passes through one wall cell to the cell behind (metal). */
    const val HEAT_THROUGH_WALL = "heat_through_wall"
    /** A cloud: hovers in place, drifts with the wind, takes up steam and rains once it is dense enough. */
    const val CLOUD = "cloud"
    /** Liquid a cloud rains … */
    const val RAIN = "rain"
    /** … and the state it is in while it rains (it starts raining when it is full). */
    const val RAIN_STATE = "rain_state"
    /** Cloud type a gas condenses into once enough of it gathers under an obstacle. */
    const val CONDENSE = "condense"
    /** Gas units in one row under an obstacle needed to form a cloud (default: the gas's capacity). */
    const val CONDENSE_AT = "condense_at"
    /** Gas units per unit of cloud (steam takes twice the room of the water it came from). */
    const val CONDENSE_RATIO = "condense_ratio"
}

/** Liquid density everything else is compared with. */
const val LIQUID_DENSITY = 10

/**
 * Every thing in the world is a [GameObject]: a type, a state, a position and properties.
 * Liquids and gases additionally carry an [amount] (how full their cell is), conductors a
 * temperature [temp] and burning things the number of steps (or moves) they have [burnt]. Behaviour never
 * depends on the id.
 */
data class GameObject(
    val id: String,
    val type: String,
    val state: String,
    val position: Position,
    val properties: Map<String, String> = emptyMap(),
    /** Whether the player may drag this object; landscape and anything the world creates never is. */
    val isMovable: Boolean = false,
    val amount: Int = 0,
    val temp: Int = 0,
    val burnt: Int = 0,
) {
    fun flag(key: String): Boolean = properties[key]?.toBooleanStrictOrNull() ?: false
    fun int(key: String, default: Int = 0): Int = properties[key]?.toIntOrNull() ?: default
    fun string(key: String): String? = properties[key]

    val isLiquid: Boolean get() = flag(Props.LIQUID)
    val isGas: Boolean get() = flag(Props.GAS)
    /** Liquids and gases are volumes rather than single things. */
    val isFluid: Boolean get() = isLiquid || isGas
    val falls: Boolean get() = flag(Props.GRAVITY)
    val rises: Boolean get() = isGas
    val conducts: Boolean get() = flag(Props.CONDUCTS)
    val density: Int get() = int(Props.DENSITY, LIQUID_DENSITY)
    val capacity: Int get() = int(Props.CAPACITY, 8)
    val isCloud: Boolean get() = flag(Props.CLOUD)
    val isRaining: Boolean get() = isCloud && string(Props.RAIN_STATE)?.let { it == state } == true
    /** Liquids, gases and clouds carry an amount. */
    val hasAmount: Boolean get() = isFluid || isCloud
    /** Air-like: falling things pass through gas and clouds. */
    val isAiry: Boolean get() = isGas || isCloud
    val heatRadius: Int get() = int(Props.HEAT_RADIUS, 1)
    val heatsThroughWalls: Boolean get() = flag(Props.HEAT_THROUGH_WALL)

    /** Load this object puts on what is below it. Gas weighs nothing. */
    val load: Int
        get() = when {
            isLiquid -> amount * int(Props.WEIGHT)
            isGas -> 0
            else -> int(Props.WEIGHT)
        }

    /** Upward push: gas pushes with its whole amount. */
    val lift: Int get() = if (isGas) amount * int(Props.LIFT) else int(Props.LIFT)

    /** Heat given off right now (0 when cold, out or not a heat source at all). */
    val heatOutput: Int
        get() {
            val heat = int(Props.HEAT)
            return if (heat > 0 && inState(Props.HEAT_STATE)) heat else 0
        }

    /** The player may pick it up right now: it is movable and not too hot to touch. */
    val canBePickedUp: Boolean get() = isMovable && string(Props.UNTOUCHABLE_STATE) != state

    /** Moves this thing still burns before it crumbles to [Props.BURNS_INTO]; 0 if it is not burning down. */
    val burnMovesLeft: Int
        get() {
            val moves = int(Props.BURN_MOVES)
            return if (moves > 0 && heatOutput > 0) (moves - burnt).coerceAtLeast(0) else 0
        }

    /** Burns with an open flame right now. */
    val isFlame: Boolean get() = flag(Props.FLAME) && heatOutput > 0

    /** Emits electric current right now. */
    val isPowerSource: Boolean get() = flag(Props.POWER) && inState(Props.POWER_STATE)

    /** Carries electric current right now. */
    val carriesPower: Boolean get() = flag(Props.WIRE) && inState(Props.WIRE_STATE)

    /** Slides off to the side like sand right now. */
    val isGranular: Boolean get() = flag(Props.GRANULAR) && inState(Props.GRANULAR_STATE)

    /** True if the optional "<property>_state" condition [stateKey] is absent or met. */
    private fun inState(stateKey: String): Boolean {
        val required = string(stateKey) ?: return true
        return required == state
    }
}
