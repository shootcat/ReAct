package com.shootcat.react.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a material moves (Leitfaden V3, 6.3). */
object Kind {
    /** Level ground that never moves (rock). */
    const val STATIC = 0
    const val POWDER = 1
    const val LIQUID = 2
    const val GAS = 3

    /** Firm material that forms structures: they stand if anchored, otherwise fall as a whole (3.1). */
    const val SOLID = 4

    /** Plants and animals (phase 2b). */
    const val LIFE = 5

    fun parse(name: String): Int = when (name) {
        "static" -> STATIC
        "powder" -> POWDER
        "liquid" -> LIQUID
        "gas" -> GAS
        "solid" -> SOLID
        "life" -> LIFE
        else -> error("unknown kind '$name'")
    }
}

/** One entry of `materials.json`, exactly as written there. */
@Serializable
data class MaterialSpec(
    val id: String,
    val name: String,
    val kind: String,
    val density: Float,
    /** Anchored ground: structures that touch it stand (3.1). */
    val anchored: Boolean = false,
    val flammable: Boolean = false,
    @SerialName("ignite_temp") val igniteTemp: Float? = null,
    @SerialName("burn_time") val burnTime: Float? = null,
    @SerialName("burn_temp") val burnTemp: Float? = null,
    @SerialName("burn_product") val burnProduct: String? = null,
    /** Heat a burning cell gives off per step (degrees of a material with heat capacity 1). */
    @SerialName("burn_heat") val burnHeat: Float = 40f,
    /** Share of burnt-out cells that leave [burnProduct]; the rest goes up in the air. */
    val residue: Float = 1f,
    /** Chance per step that a burning cell sends a flame into the air next to it. */
    val flame: Float = 0f,
    /** Chance per step that a burning cell gives off smoke. */
    val smoke: Float = 0f,
    @SerialName("spawn_burning") val spawnBurning: Boolean = false,
    @SerialName("quench_product") val quenchProduct: String? = null,
    @SerialName("melt_temp") val meltTemp: Float? = null,
    @SerialName("melt_product") val meltProduct: String? = null,
    @SerialName("boil_temp") val boilTemp: Float? = null,
    @SerialName("boil_product") val boilProduct: String? = null,
    /**
     * Heat (in degrees of the material itself) of its phase change: taken in on top of reaching [boilTemp]
     * to boil, or given off on top of cooling to [freezeTemp] to set (if it does not boil).
     */
    @SerialName("latent_heat") val latentHeat: Float = 0f,
    @SerialName("freeze_temp") val freezeTemp: Float? = null,
    @SerialName("freeze_product") val freezeProduct: String? = null,
    val conductivity: Float,
    @SerialName("heat_capacity") val heatCapacity: Float,
    val hardness: Float = 0f,
    @SerialName("erodes_in_water") val erodesInWater: Boolean = false,
    val absorbs: Float = 0f,
    /** A liquid that soaks into absorbent material and makes it wet. */
    val wets: Boolean = false,
    @SerialName("dissolves_in") val dissolvesIn: String? = null,
    @SerialName("dissolved_product") val dissolvedProduct: String? = null,
    @SerialName("emits_light") val emitsLight: Float = 0f,
    val color: String,
    @SerialName("color_variance") val colorVariance: Float = 0f,
    val opacity: Float = 1f,
    val cost: Int = 0,
    /** Temperature of newly placed material; the surroundings' temperature if not given. */
    val temp: Float? = null,
    val lifetime: Float? = null,
    @SerialName("decay_product") val decayProduct: String? = null,
    /** Powders: chance per step of not sliding sideways off a slope (dry and wet). */
    val friction: Float = 0.5f,
    @SerialName("wet_friction") val wetFriction: Float? = null,
    /** Liquids: how many cells they flow sideways per step at most. */
    val spread: Int = 1,
    /** Liquids: how far sideways they look for a lower place to flow to; level liquid lies still. */
    val reach: Int = 16,
    /** Liquids: chance per step of not flowing sideways at all. */
    val viscosity: Float = 0f,
)

@Serializable
data class MaterialFile(
    val units: Map<String, String> = emptyMap(),
    val ambient: Float = 20f,
    val materials: List<MaterialSpec>,
)

/**
 * The materials as flat arrays indexed by material number, the way the simulation reads them in its inner
 * loops. Number 0 is always air. Missing thresholds become values that are never reached.
 */
class MaterialTable(val specs: List<MaterialSpec>, val ambient: Float) {
    val count = specs.size
    private val index = specs.withIndex().associate { (i, s) -> s.id to i }

    fun id(name: String): Int = index[name] ?: error("unknown material '$name'")
    fun idOrNull(name: String): Int? = index[name]
    fun spec(id: Int): MaterialSpec = specs[id]

    private fun ref(name: String?, fallback: Int): Int = if (name == null) fallback else id(name)

    val kind = IntArray(count) { Kind.parse(specs[it].kind) }
    val density = FloatArray(count) { specs[it].density }
    val anchored = BooleanArray(count) { specs[it].anchored }
    val flammable = BooleanArray(count) { specs[it].flammable }
    val igniteTemp = FloatArray(count) { specs[it].igniteTemp ?: Float.MAX_VALUE }

    /** Below this a burning cell goes out (a little under the ignition point, so it does not flicker). */
    val quenchTemp = FloatArray(count) { (specs[it].igniteTemp ?: 0f) * 0.85f }
    val burnSteps = IntArray(count) { ((specs[it].burnTime ?: 0f) * STEPS_PER_SECOND).toInt().coerceAtLeast(1) }
    val burnTemp = FloatArray(count) { specs[it].burnTemp ?: 0f }
    val burnProduct = IntArray(count) { ref(specs[it].burnProduct, AIR) }
    val burnHeat = FloatArray(count) { specs[it].burnHeat }
    val residue = FloatArray(count) { specs[it].residue }
    val flame = FloatArray(count) { specs[it].flame }
    val smoke = FloatArray(count) { specs[it].smoke }
    val spawnBurning = BooleanArray(count) { specs[it].spawnBurning }
    val quenchProduct = IntArray(count) { ref(specs[it].quenchProduct, it) }
    val meltTemp = FloatArray(count) { specs[it].meltTemp ?: Float.MAX_VALUE }
    val meltProduct = IntArray(count) { ref(specs[it].meltProduct, it) }
    val boilTemp = FloatArray(count) { specs[it].boilTemp ?: Float.MAX_VALUE }
    val boilProduct = IntArray(count) { ref(specs[it].boilProduct, it) }
    val latent = IntArray(count) { specs[it].latentHeat.toInt() }
    val freezeTemp = FloatArray(count) { specs[it].freezeTemp ?: -Float.MAX_VALUE }
    val freezeProduct = IntArray(count) { ref(specs[it].freezeProduct, it) }
    val conductivity = FloatArray(count) { specs[it].conductivity }
    val heatCapacity = FloatArray(count) { specs[it].heatCapacity }
    val hardness = FloatArray(count) { specs[it].hardness }
    val erodes = BooleanArray(count) { specs[it].erodesInWater }
    val absorbs = FloatArray(count) { specs[it].absorbs }
    val wets = BooleanArray(count) { specs[it].wets }
    val spawnTemp = FloatArray(count) { specs[it].temp ?: ambient }
    val lifetimeSteps = IntArray(count) { ((specs[it].lifetime ?: 0f) * STEPS_PER_SECOND).toInt() }
    val decayProduct = IntArray(count) { ref(specs[it].decayProduct, AIR) }
    val friction = FloatArray(count) { specs[it].friction }
    val wetFriction = FloatArray(count) { specs[it].wetFriction ?: specs[it].friction }
    val spread = IntArray(count) { specs[it].spread.coerceAtLeast(1) }
    val reach = IntArray(count) { maxOf(specs[it].reach, specs[it].spread, 1) }
    val viscosity = FloatArray(count) { specs[it].viscosity }
    val emitsLight = FloatArray(count) { specs[it].emitsLight }
    val opacity = FloatArray(count) { specs[it].opacity }
    val cost = IntArray(count) { specs[it].cost }
    val color = IntArray(count) { parseColor(specs[it].color) }
    val colorVariance = FloatArray(count) { specs[it].colorVariance }

    /** Air and flames: a burning neighbour of one of these gets the air it needs. */
    val oxygen = BooleanArray(count) { it == AIR || (kind[it] == Kind.GAS && spawnBurning[it]) }

    /** Gases lighter than air rise; the lighter, the more eagerly. */
    val rise = FloatArray(count) {
        if (kind[it] != Kind.GAS) 0f else ((density[AIR] - density[it]) / density[AIR] * 1.6f + 0.1f).coerceIn(0.1f, 0.92f)
    }

    /** Nothing ever happens to it on its own: no movement, no fire, no phase change. */
    val inert = BooleanArray(count) {
        (kind[it] == Kind.STATIC || kind[it] == Kind.SOLID) && !flammable[it] &&
            meltTemp[it] == Float.MAX_VALUE && boilTemp[it] == Float.MAX_VALUE && freezeTemp[it] == -Float.MAX_VALUE &&
            lifetimeSteps[it] == 0
    }

    /**
     * Heat exchange between neighbours of material a and b, per step: the cell of a moves `pairA` of the
     * temperature difference towards b, the cell of b `pairB` towards a. Both together conserve heat; the
     * rate is the geometric mean of both conductivities and never more than 0.45 of the way to the common
     * temperature, so the exchange is always stable.
     */
    val pairA = FloatArray(count * count)
    val pairB = FloatArray(count * count)

    /**
     * The same exchange upwards from a hotter gas below (hot air, flames, smoke): heat rises, so fire climbs
     * faster than it spreads sideways (3.2).
     */
    val riseA = FloatArray(count * count)
    val riseB = FloatArray(count * count)

    init {
        require(specs.isNotEmpty() && specs[0].id == "air") { "material 0 must be air" }
        require(count <= 127) { "at most 127 materials fit into a byte" }
        for (a in 0 until count) for (b in 0 until count) {
            val ka = conductivity[a]
            val kb = conductivity[b]
            val k = kotlin.math.sqrt(ka * kb)
            val alpha = minOf(0.45f, k)
            val ca = heatCapacity[a]
            val cb = heatCapacity[b]
            pairA[a * count + b] = alpha * cb / (ca + cb)
            pairB[a * count + b] = alpha * ca / (ca + cb)
            val up = minOf(0.45f, k * CONVECTION)
            riseA[a * count + b] = up * cb / (ca + cb)
            riseB[a * count + b] = up * ca / (ca + cb)
        }
        for (s in specs) {
            listOfNotNull(s.burnProduct, s.quenchProduct, s.meltProduct, s.boilProduct, s.freezeProduct, s.decayProduct, s.dissolvedProduct, s.dissolvesIn)
                .forEach { require(it in index) { "${s.id} refers to unknown material '$it'" } }
            if (s.flammable) require(s.igniteTemp != null && s.burnTime != null) { "${s.id} burns but has no ignite_temp or burn_time" }
            require(!(s.lifetime != null && (s.flammable || s.boilTemp != null))) { "${s.id}: lifetime cannot be combined with burning or boiling" }
            require(s.heatCapacity > 0f) { "${s.id}: heat_capacity must be positive" }
            require(s.meltTemp == null || s.freezeTemp == null || s.meltTemp > s.freezeTemp) { "${s.id} would melt and freeze at once" }
        }
    }

    companion object {
        const val AIR = 0
        const val STEPS_PER_SECOND = 60

        /** How much better heat passes upwards out of a hotter gas than by plain conduction. */
        const val CONVECTION = 3f

        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): MaterialTable {
            val file = json.decodeFromString(MaterialFile.serializer(), text)
            return MaterialTable(file.materials, file.ambient)
        }

        /** The materials that ship with the game. */
        fun load(): MaterialTable {
            val stream = MaterialTable::class.java.getResourceAsStream("/materials.json") ?: error("materials.json is missing")
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun parseColor(hex: String): Int {
            val h = hex.removePrefix("#")
            require(h.length == 6) { "colour '$hex' must be #RRGGBB" }
            return (0xFF000000.toInt()) or h.toInt(16)
        }
    }
}
