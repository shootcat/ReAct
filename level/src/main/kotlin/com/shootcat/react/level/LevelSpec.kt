package com.shootcat.react.level

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A level as written in its `level.json` (Leitfaden V3, 5.1). The ground is a painted PNG next to it, every
 * colour one material (the material's own colour from materials.json), and a mask of the same size marks the
 * anchored ground (white): loose ground that stays where it is and cannot be taken.
 *
 * Points and areas are in cells of the level, (0, 0) top left.
 */
@Serializable
data class LevelSpec(
    val id: String,
    val name: String,
    /** The region the level lies in (Wald, Vulkan, Mine). */
    val area: String,
    /** How hard it is meant to be: leicht, Kernniveau, Obergrenze. */
    val tier: String,
    val seed: Long,
    /** Temperature of the open air; the materials' default if not given. */
    val ambient: Float? = null,
    val terrain: String = "terrain.png",
    val anchor: String = "anchor.png",
    val tools: Tools,
    /** Building material in store at the start, cells per material (mostly nothing). */
    val stock: Map<String, Int> = emptyMap(),
    /** Places hotter or colder than their material's own temperature (a warm rock around a magma chamber). */
    val heat: List<Heat> = emptyList(),
    /** Trees (or bushes, [Tree.tree] false) grown at the start, standing on the ground below (x, y). */
    val trees: List<Tree> = emptyList(),
    /** The weather (3c): where clouds form, the wind. Fixed for the level; there is no random weather in a level. */
    val weather: WeatherSpec? = null,
    /** Inhabitants (Phase 5) and their village. */
    val folk: FolkSpec? = null,
    val goals: List<GoalSpec>,
    val rating: Rating,
)

/** What the player may use (Leitfaden V3, 4.3). */
@Serializable
data class Tools(
    val bucket: Boolean = false,
    /** How many cells the bucket holds. */
    @SerialName("bucket_size") val bucketSize: Int = 0,
    val hand: Boolean = false,
    /** Building from the store with line, rectangle, frame and brush. */
    val build: Boolean = false,
)

/** Cells of the [area] at [temp]; with [material], only the cells of that material (a fire, not the rock under it). */
@Serializable
data class Heat(val area: List<List<Int>>, val temp: Float, val material: String? = null)

/**
 * A level's weather (3c.5). [cloudBase]: the row at and above which steam in the open air turns into cloud
 * (none: no clouds, steam in the open condenses as it cools). [wind]: -1 (a gale to the left) to 1 (to the
 * right). [gust]: how far gusts take it above and below that, one gust every [gustSeconds]. [stormSeconds]: the
 * level begins in a thunderstorm that lasts this long: lightning comes as often as when the sandbox calls one
 * up, but only out of the clouds that are there; its strokes fall by the world's seed like everything else.
 */
@Serializable
data class WeatherSpec(
    @SerialName("cloud_base") val cloudBase: Int? = null,
    val wind: Float = 0f,
    val gust: Float = 0f,
    @SerialName("gust_seconds") val gustSeconds: Float = 10f,
    @SerialName("storm_seconds") val stormSeconds: Float = 0f,
)

@Serializable
data class Tree(val x: Int, val y: Int, val tree: Boolean = true)

/**
 * The inhabitants of a level (Phase 5): where each stands at the start, `[x, y]` or `[x, y, dir]` (-1 looking
 * left), the village (x, y on its ground), and where one that fainted is brought back to: "village", or "start"
 * (where it stood at the start), for a level whose goal is to get them out.
 */
@Serializable
data class FolkSpec(
    val figures: List<List<Int>>,
    val village: List<Int>? = null,
    @SerialName("faint_return") val faintReturn: String = "village",
)

/**
 * One goal (Leitfaden V3, 5.2). [type] is one of material, fill, temperature, ignite, extinguish, light, grow,
 * protect, and for the inhabitants (Phase 5) bring, rescue, keep_warm. A goal counts once its state has held for
 * [hold] seconds.
 */
@Serializable
data class GoalSpec(
    val type: String,
    /** What the player is told: what, never how. */
    val text: String,
    val area: List<List<Int>>,
    val material: String? = null,
    val materials: List<String> = emptyList(),
    /** Cells needed (material, ignite, grow, protect). */
    val amount: Int = 0,
    /** Share of the area (fill). */
    val share: Float = 0f,
    /** Average temperature at least (temperature; keep_warm: of the air in the area, without it nobody may freeze). */
    val min: Float? = null,
    /** Average temperature at most (temperature). */
    val max: Float? = null,
    /** Average light at least (light). */
    val light: Float = 0f,
    /** How long the village must stay warm (keep_warm): the night, in seconds. */
    val seconds: Float = 0f,
    val hold: Float = 2f,
)

/** Gold, silver and bronze: the most seconds and the most actions for each (Leitfaden V3, 5.1.3). */
@Serializable
data class Rating(val gold: Limit, val silver: Limit, val bronze: Limit)

@Serializable
data class Limit(val seconds: Int, val actions: Int)
