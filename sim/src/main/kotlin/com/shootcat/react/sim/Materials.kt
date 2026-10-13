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

/** What a living thing does (Leitfaden V3, 4.2): plants grow on random ticks, animals move every step. */
object Organism {
    const val NONE = 0
    const val GRASS = 1
    const val MOSS = 2
    const val VINE = 3
    const val SEED = 4
    const val TREE = 5
    const val LEAF = 6
    const val FISH = 7
    const val FIREFLY = 8
    const val WORM = 9
    const val BIRD = 10

    fun parse(name: String?): Int = when (name) {
        null -> NONE
        "grass" -> GRASS
        "moss" -> MOSS
        "vine" -> VINE
        "seed" -> SEED
        "tree" -> TREE
        "leaf" -> LEAF
        "fish" -> FISH
        "firefly" -> FIREFLY
        "worm" -> WORM
        "bird" -> BIRD
        else -> error("unknown organism '$name'")
    }

    fun isAnimal(o: Int) = o >= FISH
}

/** How built material is drawn (the pattern of its surface). */
object Pattern {
    const val NONE = 0
    const val PLANKS = 1
    const val BRICK = 2
    const val MASONRY = 3
    const val SHEEN = 4
    const val PORES = 5

    /** Glittering crystals (salt), soft and matte (snow), bluish glass with shining edges (ice): 3b.1, 3b.8. */
    const val CRYSTAL = 6
    const val MATTE = 7
    const val ICE = 8
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
    /** Wet, it does not catch fire until it has dried (wood). */
    @SerialName("wet_blocks_burning") val wetBlocksBurning: Boolean = false,
    @SerialName("melt_temp") val meltTemp: Float? = null,
    @SerialName("melt_product") val meltProduct: String? = null,
    /** Melting point while something salty touches it: salt melts ice and snow without heat. */
    @SerialName("salt_melt_temp") val saltMeltTemp: Float? = null,
    @SerialName("boil_temp") val boilTemp: Float? = null,
    @SerialName("boil_product") val boilProduct: String? = null,
    /** What a boiling cell leaves behind instead, with [boilResidueShare]: salt water leaves salt. */
    @SerialName("boil_residue") val boilResidue: String? = null,
    @SerialName("boil_residue_share") val boilResidueShare: Float = 0f,
    /**
     * Heat (in degrees of the material itself) of its phase change: taken in on top of reaching [boilTemp]
     * to boil, given off on top of cooling to [freezeTemp] to set (if it does not boil), or taken in on top
     * of reaching [meltTemp] to melt (if it neither boils nor sets: ice, snow).
     */
    @SerialName("latent_heat") val latentHeat: Float = 0f,
    @SerialName("freeze_temp") val freezeTemp: Float? = null,
    @SerialName("freeze_product") val freezeProduct: String? = null,
    val conductivity: Float,
    @SerialName("heat_capacity") val heatCapacity: Float,
    val hardness: Float = 0f,
    @SerialName("erodes_in_water") val erodesInWater: Boolean = false,
    /** A brittle material cracks into this when a quenching neighbour cools it by more than [shockRate] degrees in one step. */
    @SerialName("shock_product") val shockProduct: String? = null,
    @SerialName("shock_rate") val shockRate: Float = 0f,
    /** Cools so suddenly that brittle material cracks (water, ice, snow). */
    val quenches: Boolean = false,
    /** Chance per step that water flows through a grain of it (gravel); 0: it holds water back. */
    val permeable: Float = 0f,
    /** Slides off a slope as an avalanche when warmed or shaken (snow). */
    val avalanche: Boolean = false,
    /** Pressed into this under a load of [compactLoad] (density / 1000 summed over the cells above). */
    @SerialName("compact_product") val compactProduct: String? = null,
    @SerialName("compact_load") val compactLoad: Float = 0f,
    /** The pressure (times that of the air) a wall of it withstands; without it by kind, rock never breaks. */
    val strength: Float? = null,
    /** What it becomes when pressure or a blast breaks it (glass: shards, stone: rubble); without it the cell is pushed away whole. */
    @SerialName("break_product") val breakProduct: String? = null,
    /** How much gas one cell holds, as cells of air: steam is water spread out very thin. */
    val expansion: Float = 1f,
    /** A gas that goes off all at once when it meets fire, embers or its ignition heat (firedamp). */
    val explosive: Boolean = false,
    /** Keeps its own temperature: the heat of the deep below the world (magma under a volcano). */
    @SerialName("holds_temp") val holdsTemp: Boolean = false,
    /** Gives off this gas now and then (a crack in the rock of a mine gives off firedamp). */
    val emits: String? = null,
    @SerialName("emit_rate") val emitRate: Float = 0f,
    val absorbs: Float = 0f,
    /** A liquid that soaks into absorbent material and makes it wet. */
    val wets: Boolean = false,
    @SerialName("dissolves_in") val dissolvesIn: String? = null,
    @SerialName("dissolved_product") val dissolvedProduct: String? = null,
    /** Salt and salt water: they lower the melting point of ice and snow next to them. */
    val salty: Boolean = false,
    /** The bucket can take it up (2c). */
    val bucketable: Boolean = false,
    /** The hand can pick it up (2c). */
    val grabbable: Boolean = false,
    /** Light does not pass it (2b). */
    @SerialName("blocks_light") val blocksLight: Boolean = true,
    @SerialName("emits_light") val emitsLight: Float = 0f,
    val color: String,
    @SerialName("color_variance") val colorVariance: Float = 0f,
    val opacity: Float = 1f,
    /** Inclusions (ore): this share of the particles shows [speckleColor]. */
    @SerialName("speckle_color") val speckleColor: String? = null,
    val speckle: Float = 0f,
    /** How built material is drawn: planks, brick, masonry, sheen. */
    val pattern: String? = null,
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
    /** A living thing (2b): grass, moss, vine, seed, tree, leaf, fish, firefly, worm, bird. */
    val organism: String? = null,
    /** Plants: the light they need to grow (0 dark, 1 daylight). */
    @SerialName("light_need") val lightNeed: Float = 0f,
    /** Plants: the most light they grow in (moss keeps to the dark). */
    @SerialName("light_max") val lightMax: Float = 1f,
    /** Plants: chance per random tick to grow. */
    val grow: Float = 0f,
    /** Vines: how many cells one grows. */
    @SerialName("grow_size") val growSize: Int = 0,
    /** Plants: the ground they take root in and spread over. */
    @SerialName("grows_on") val growsOn: String? = null,
    /** Animals: the material they live in (fish: water, worms: earth). */
    @SerialName("lives_in") val livesIn: String? = null,
    /** Animals: chance per step of moving. */
    val move: Float = 0f,
    /** Living things die above this temperature. */
    @SerialName("dies_above") val diesAbove: Float? = null,
    /** Living things die next to salt or salt water. */
    @SerialName("dies_in_salt") val diesInSalt: Boolean = false,
    /** What a dead plant or animal becomes. */
    @SerialName("death_product") val deathProduct: String? = null,
    /** Its roots hold the loose ground below together. */
    val roots: Boolean = false,
    /** Share of light one cell lets through, 0..1; 0 if it blocks light, 1 if not given otherwise. */
    @SerialName("light_pass") val lightPass: Float? = null,
    /** What an object of it gives in the store (2c): a log gives planks. */
    @SerialName("split_into") val splitInto: String? = null,
    /** How many cells of [splitInto] one cell gives. */
    @SerialName("split_yield") val splitYield: Int = 1,
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
    val wetBlocksBurning = BooleanArray(count) { specs[it].wetBlocksBurning }
    val saltMeltTemp = FloatArray(count) { specs[it].saltMeltTemp ?: Float.MAX_VALUE }
    val boilResidue = IntArray(count) { ref(specs[it].boilResidue, -1) }
    val boilResidueShare = FloatArray(count) { specs[it].boilResidueShare }
    val dissolvesIn = IntArray(count) { ref(specs[it].dissolvesIn, -1) }
    val dissolvedProduct = IntArray(count) { ref(specs[it].dissolvedProduct, -1) }
    val salty = BooleanArray(count) { specs[it].salty }
    val bucketable = BooleanArray(count) { specs[it].bucketable }
    val grabbable = BooleanArray(count) { specs[it].grabbable }
    val blocksLight = BooleanArray(count) { specs[it].blocksLight }
    val speckleColor = IntArray(count) { specs[it].speckleColor?.let { c -> parseColor(c) } ?: 0 }
    val speckle = FloatArray(count) { specs[it].speckle }
    val pattern = IntArray(count) {
        when (specs[it].pattern) {
            null -> Pattern.NONE
            "planks" -> Pattern.PLANKS
            "brick" -> Pattern.BRICK
            "masonry" -> Pattern.MASONRY
            "sheen" -> Pattern.SHEEN
            "pores" -> Pattern.PORES
            "crystal" -> Pattern.CRYSTAL
            "matte" -> Pattern.MATTE
            "ice" -> Pattern.ICE
            else -> error("${specs[it].id}: unknown pattern ${specs[it].pattern}")
        }
    }
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
    val hasSpawnTemp = BooleanArray(count) { specs[it].temp != null }
    val lifetimeSteps = IntArray(count) { ((specs[it].lifetime ?: 0f) * STEPS_PER_SECOND).toInt() }
    val decayProduct = IntArray(count) { ref(specs[it].decayProduct, AIR) }
    val friction = FloatArray(count) { specs[it].friction }
    val wetFriction = FloatArray(count) { specs[it].wetFriction ?: specs[it].friction }
    val spread = IntArray(count) { specs[it].spread.coerceAtLeast(1) }
    val reach = IntArray(count) { maxOf(specs[it].reach, specs[it].spread, 1) }
    val viscosity = FloatArray(count) { specs[it].viscosity }
    val emitsLight = FloatArray(count) { specs[it].emitsLight }

    /** Light a cell gives off by itself; flammable solids and powders (charcoal) only while they burn. */
    val glow = FloatArray(count) { if (flammable[it] && kind[it] != Kind.GAS) 0f else specs[it].emitsLight }
    val lightPass = FloatArray(count) { specs[it].lightPass ?: if (specs[it].blocksLight) 0f else 1f }
    val organism = IntArray(count) { Organism.parse(specs[it].organism) }
    val animal = BooleanArray(count) { Organism.isAnimal(organism[it]) }
    val lightNeed = FloatArray(count) { specs[it].lightNeed }
    val lightMax = FloatArray(count) { specs[it].lightMax }
    val grow = FloatArray(count) { specs[it].grow }
    val growSize = IntArray(count) { specs[it].growSize }
    val growsOn = IntArray(count) { ref(specs[it].growsOn, -1) }
    val livesIn = IntArray(count) { ref(specs[it].livesIn, -1) }
    val move = FloatArray(count) { specs[it].move }
    val diesAbove = FloatArray(count) { specs[it].diesAbove ?: Float.MAX_VALUE }
    val diesInSalt = BooleanArray(count) { specs[it].diesInSalt }
    val deathProduct = IntArray(count) { ref(specs[it].deathProduct, AIR) }
    val roots = BooleanArray(count) { specs[it].roots }
    val shockProduct = IntArray(count) { ref(specs[it].shockProduct, -1) }
    val shockRate = FloatArray(count) { specs[it].shockRate }
    val quenches = BooleanArray(count) { specs[it].quenches }
    val permeable = FloatArray(count) { specs[it].permeable }
    val avalanche = BooleanArray(count) { specs[it].avalanche }
    val compactProduct = IntArray(count) { ref(specs[it].compactProduct, -1) }
    val compactLoad = FloatArray(count) { specs[it].compactLoad }
    val strength = FloatArray(count) {
        specs[it].strength ?: when (kind[it]) {
            Kind.STATIC -> Float.POSITIVE_INFINITY
            Kind.SOLID -> SOLID_STRENGTH
            Kind.POWDER, Kind.LIQUID -> LOOSE_STRENGTH
            Kind.LIFE -> 1f
            else -> 0f
        }
    }
    val breakProduct = IntArray(count) { ref(specs[it].breakProduct, -1) }
    val expansion = FloatArray(count) { specs[it].expansion }
    val explosive = BooleanArray(count) { specs[it].explosive }
    val holdsTemp = BooleanArray(count) { specs[it].holdsTemp }
    val emits = IntArray(count) { ref(specs[it].emits, -1) }
    val emitRate = FloatArray(count) { specs[it].emitRate }

    /** Solid parts of plants (trunk, leaves, vine, dead wood): they form structures of their own, apart from built ones. */
    val plant = BooleanArray(count) { kind[it] == Kind.SOLID && ((organism[it] != Organism.NONE && !animal[it]) || roots[it]) }

    /** Soft plant parts (leaves, vine): they carry nothing heavy, a falling block tears through them. */
    val soft = BooleanArray(count) { organism[it] == Organism.LEAF || organism[it] == Organism.VINE }
    val splitInto = IntArray(count) { ref(specs[it].splitInto, it) }
    val splitYield = IntArray(count) { specs[it].splitYield }
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
        (kind[it] == Kind.STATIC || kind[it] == Kind.SOLID) && !flammable[it] && organism[it] == Organism.NONE &&
            meltTemp[it] == Float.MAX_VALUE && boilTemp[it] == Float.MAX_VALUE && freezeTemp[it] == -Float.MAX_VALUE &&
            lifetimeSteps[it] == 0 && dissolvesIn[it] < 0 && shockProduct[it] < 0
    }

    /**
     * Heat exchange between neighbours of material a and b, per step: the cell of a moves `pairA` of the
     * temperature difference towards b, the cell of b `pairB` towards a. Both together conserve heat; the
     * rate is the geometric mean of both conductivities and never more than [MAX_EXCHANGE] of the way to the
     * common temperature. Each exchange leaves both cells between their old temperatures, so it is stable.
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
            val alpha = minOf(MAX_EXCHANGE, k)
            val ca = heatCapacity[a]
            val cb = heatCapacity[b]
            pairA[a * count + b] = alpha * cb / (ca + cb)
            pairB[a * count + b] = alpha * ca / (ca + cb)
            val up = minOf(MAX_EXCHANGE, k * CONVECTION)
            riseA[a * count + b] = up * cb / (ca + cb)
            riseB[a * count + b] = up * ca / (ca + cb)
        }
        for (s in specs) {
            listOfNotNull(
                s.burnProduct, s.quenchProduct, s.meltProduct, s.boilProduct, s.boilResidue, s.freezeProduct, s.decayProduct,
                s.dissolvedProduct, s.dissolvesIn, s.deathProduct, s.growsOn, s.livesIn, s.splitInto, s.shockProduct, s.compactProduct,
                s.breakProduct, s.emits,
            )
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

        /** At most this share of the way to the common temperature per step and pair (1 would be all the way). */
        const val MAX_EXCHANGE = 0.9f

        /** Strength of walls without their own: built solids, and loose or liquid material that a blast pushes aside. */
        const val SOLID_STRENGTH = 5f
        const val LOOSE_STRENGTH = 2.5f

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
