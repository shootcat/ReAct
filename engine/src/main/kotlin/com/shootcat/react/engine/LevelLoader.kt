package com.shootcat.react.engine

import com.shootcat.react.engine.model.Area
import com.shootcat.react.engine.model.Catalog
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.LevelGoal
import com.shootcat.react.engine.model.LoadDirection
import com.shootcat.react.engine.model.MapNode
import com.shootcat.react.engine.model.MergeRule
import com.shootcat.react.engine.model.ObjectType
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.TargetCleared
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetExtinguished
import com.shootcat.react.engine.model.TargetMaxMoves
import com.shootcat.react.engine.model.TargetPreserved
import com.shootcat.react.engine.model.TargetRainTriggered
import com.shootcat.react.engine.model.TargetState
import com.shootcat.react.engine.model.Terrain
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.engine.model.WindZone
import com.shootcat.react.engine.model.WorldData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class LevelFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Loads the game data from JSON. The catalog defines every object type, every reaction and every merge;
 * a world file lists its levels and map; level files define the landscape, the objects (and which of
 * them the player may move: `isMovable`), wind, goals and optional extra rules.
 */
object LevelLoader {

    private val json = Json { ignoreUnknownKeys = false }

    /** The element catalog: every object type and every reaction, shared by all worlds. */
    fun parseCatalog(text: String): Catalog {
        val dto = decode<CatalogDto>(text, "catalog")
        val types = TypeCatalog(dto.types.map { it.toModel() })
        requireUnique(dto.types.map { it.type }, "type id in catalog")
        val rules = dto.rules.map { it.toModel() }
        rules.forEach { validateRule(it, types, "catalog") }
        requireUnique(rules.map { it.id }, "rule id in catalog")
        val merges = dto.merges.map { MergeRule(it.id, it.name ?: it.id, it.a, it.b, it.result, it.sound, it.world) }
        merges.forEach { m ->
            listOf(m.a, m.b, m.result).forEach { if (it !in types) fail("catalog: merge '${m.id}' uses unknown type '$it'") }
        }
        requireUnique(merges.map { it.id }, "merge id in catalog")
        return Catalog(types, rules, merges)
    }

    fun parseWorld(text: String, catalog: Catalog): WorldData {
        val dto = decode<WorldDto>(text, "world")
        val types = catalog.types
        dto.map.forEach { n -> n.icon?.let { if (it !in types) fail("world ${dto.world}: map icon '$it' is not a type") } }
        dto.icon?.let { if (it !in types) fail("world ${dto.world}: icon '$it' is not a type") }
        val all = dto.levels + listOfNotNull(dto.bonus)
        requireUnique(all, "level id in world ${dto.world}")
        dto.map.forEach { if (it.level !in all) fail("world ${dto.world}: map node for unknown level '${it.level}'") }
        return WorldData(
            world = dto.world,
            title = dto.title,
            levelIds = dto.levels,
            map = dto.map.map { MapNode(it.level, it.x, it.y, it.icon) },
            types = types,
            rules = catalog.rules,
            bonusLevelId = dto.bonus,
            icon = dto.icon,
            merges = catalog.merges,
        )
    }

    fun parseLevel(text: String, world: WorldData): LevelData {
        val dto = decode<LevelDto>(text, "level")
        val where = "level '${dto.id}'"
        if (dto.world != world.world) fail("$where belongs to world ${dto.world}, not ${world.world}")
        val rows = dto.map
        val width = rows.firstOrNull()?.length ?: 0
        val height = rows.size
        if (width < 1 || height < 1) fail("$where has an empty map")

        val (terrain, noBuild, fields) = parseLayout(rows, width, height, dto.legend.keys, where)
        val placement = when (dto.placement) {
            null -> {
                if (fields.isNotEmpty()) fail("$where has placement fields ('+') but no \"placement\": \"marked\"")
                null
            }
            "marked" -> {
                if (fields.isEmpty()) fail("$where: \"placement\": \"marked\" needs placement fields ('+')")
                fields
            }
            else -> fail("$where: placement '${dto.placement}' is not 'marked'")
        }
        val walls = terrain.keys
        val (mapObjects, legendNoBuild) = objectsFromMap(rows, dto.legend, where)

        val objects = mapObjects.map { o ->
            if (o.type !in world.types) fail("$where: object '${o.id}' has unknown type '${o.type}'")
            world.types.create(o.id, o.type, o.position, o.state, o.properties.toStrings(), o.isMovable, o.amount)
        }
        requireUnique(objects.map { it.id }, "object id in $where")

        val levelRules = dto.rules.map { it.toModel() }
        levelRules.forEach { validateRule(it, world.types, where) }
        val overridden = levelRules.map { it.id }.toSet()
        val rules = world.rules.filter { it.id !in overridden } + levelRules

        fun area(values: List<Int>?, what: String): Area? {
            if (values == null) return null
            if (values.size != 4) fail("$where: $what needs [x0, y0, x1, y1]")
            val a = Area(values[0], values[1], values[2], values[3])
            if (a.x0 > a.x1 || a.y0 > a.y1 || a.x0 < 0 || a.y0 < 0 || a.x1 >= width || a.y1 >= height) {
                fail("$where: $what $values is not inside the map")
            }
            return a
        }
        val byId = objects.associateBy { it.id }
        fun objectFor(id: String?, goal: String): GameObject =
            byId[id ?: fail("$where: goal '$goal' needs an object")] ?: fail("$where: goal '$goal' refers to unknown object '$id'")

        val goals: List<LevelGoal> = dto.goals.map { g ->
            when (g.type) {
                "fill" -> {
                    val liquid = g.liquid ?: "WATER"
                    if (world.types[liquid]?.properties?.get("liquid") != "true") fail("$where: '$liquid' is not a liquid")
                    TargetContainerFilled(area(g.area, "fill area") ?: fail("$where: fill goal needs an area"), liquid, g.min ?: 1, g.text, g.optional)
                }
                "extinguish" -> TargetExtinguished(area(g.area, "extinguish area"), g.text, g.optional)
                "rain" -> TargetRainTriggered(area(g.area, "rain area"), g.text, g.optional)
                "state" -> {
                    val o = objectFor(g.objectId, g.type)
                    val state = g.state ?: fail("$where: state goal needs a state")
                    TargetState(o.id, state, state in world.types.require(o.type).vanishStates, g.text, g.optional)
                }
                "preserve" -> {
                    val o = objectFor(g.objectId, g.type)
                    TargetPreserved(o.id, o.state, g.text, g.optional)
                }
                "clear" -> {
                    if (g.types.isEmpty()) fail("$where: clear goal needs types")
                    g.types.forEach { if (it !in world.types) fail("$where: clear goal uses unknown type '$it'") }
                    TargetCleared(g.types.toSet(), area(g.area, "clear area"), g.text, g.optional)
                }
                "max_moves" -> TargetMaxMoves(g.moves ?: fail("$where: max_moves goal needs moves"), g.text, g.optional)
                else -> fail("$where: unknown goal type '${g.type}'")
            }
        }
        if (goals.none { !it.optional }) fail("$where has no main goal")

        val wind = dto.wind.map { WindZone(area(it.area, "wind area")!!, it.dx) }

        return LevelData(
            id = dto.id,
            title = dto.title,
            world = dto.world,
            intro = dto.intro,
            width = width,
            height = height,
            walls = walls,
            objects = objects.sortedBy { it.id },
            rules = rules,
            goals = goals,
            maxSteps = dto.maxSteps,
            noBuild = noBuild + legendNoBuild,
            terrain = terrain,
            wind = wind,
            merges = world.merges,
            placement = placement,
        )
    }

    private data class Layout(val terrain: Map<Position, Terrain>, val noBuild: Set<Position>, val placement: Set<Position>)

    /**
     * The landscape: '#' earth, '%' rock, '.' open, ':' open but the player may not drop anything there,
     * '+' open and a placement field. Any [legend] symbol stands for an object on an open cell.
     */
    private fun parseLayout(
        layout: List<String>,
        width: Int,
        height: Int,
        legend: Set<Char>,
        where: String,
    ): Layout {
        val terrain = mutableMapOf<Position, Terrain>()
        val noBuild = mutableSetOf<Position>()
        val placement = mutableSetOf<Position>()
        layout.forEachIndexed { y, row ->
            if (row.length != width) fail("$where: map row $y has ${row.length} cells, the first row has $width")
            row.forEachIndexed { x, c ->
                when (c) {
                    '#' -> terrain[Position(x, y)] = Terrain.EARTH
                    '%' -> terrain[Position(x, y)] = Terrain.ROCK
                    '.' -> Unit
                    ':' -> noBuild += Position(x, y)
                    '+' -> placement += Position(x, y)
                    in legend -> Unit
                    else -> fail("$where: unknown map symbol '$c' at ($x,$y)")
                }
            }
        }
        return Layout(terrain, noBuild, placement)
    }

    private class MapObject(
        val id: String,
        val type: String,
        val position: Position,
        val state: String?,
        val isMovable: Boolean,
        val properties: Map<String, JsonPrimitive>,
        val amount: Int?,
    )

    /** Objects placed by symbol in an ASCII map. Ids default to "<type>_<x>_<y>". */
    private fun objectsFromMap(
        map: List<String>,
        legend: Map<Char, LegendDto>,
        where: String,
    ): Pair<List<MapObject>, Set<Position>> {
        val objects = mutableListOf<MapObject>()
        val noBuild = mutableSetOf<Position>()
        val counts = mutableMapOf<Char, Int>()
        map.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val entry = legend[c] ?: return@forEachIndexed
                counts[c] = (counts[c] ?: 0) + 1
                if (entry.id != null && counts.getValue(c) > 1) fail("$where: symbol '$c' has a fixed id but appears more than once")
                objects += MapObject(
                    id = entry.id ?: "${entry.type.lowercase()}_${x}_$y",
                    type = entry.type,
                    position = Position(x, y),
                    state = entry.state,
                    isMovable = entry.isMovable,
                    properties = entry.properties,
                    amount = entry.amount,
                )
                if (entry.nobuild) noBuild += Position(x, y)
            }
        }
        return objects to noBuild
    }

    private fun validateRule(rule: Rule, types: TypeCatalog, where: String) {
        val c = rule.conditions
        val referenced = listOfNotNull(c.source, c.target, rule.effect.spawnObject, rule.elseEffect?.spawnObject, rule.effect.transform)
        referenced.forEach { if (it !in types) fail("$where: rule '${rule.id}' uses unknown type '$it'") }
        if (rule.trigger == Trigger.TOUCH && c.source == null && !c.sourceHot) fail("$where: rule '${rule.id}' needs a source")
        if (rule.trigger != Trigger.TOUCH && c.sourceHot) fail("$where: rule '${rule.id}': source_hot only works for TOUCH")
    }

    private fun <T> requireUnique(values: List<T>, what: String) {
        val duplicate = values.groupBy { it }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) fail("duplicate $what: ${duplicate.key}")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, where: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: fail("$where: '$value' is not one of ${enumValues<T>().joinToString { it.name }}")

    private inline fun <reified T> decode(text: String, what: String): T =
        try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw LevelFormatException("Invalid $what file: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LevelFormatException("Invalid $what file: ${e.message}", e)
        }

    private fun fail(message: String): Nothing = throw LevelFormatException(message)

    private fun Map<String, JsonPrimitive>.toStrings(): Map<String, String> = mapValues { it.value.content }

    private fun TypeDto.toModel() = ObjectType(
        id = type,
        name = name,
        defaultState = defaultState,
        properties = properties.toStrings(),
        vanishStates = vanishStates.toSet(),
        signalStates = signalStates.toSet(),
        stateNames = stateNames,
        description = description,
        world = world,
    )

    private fun RuleDto.toModel() = Rule(
        id = id,
        name = name ?: id,
        trigger = enumValue(trigger, "rule '$id'"),
        conditions = RuleConditions(
            source = conditions.source,
            sourceState = conditions.sourceState,
            target = conditions.target,
            targetState = conditions.targetState,
            minLoad = conditions.minLoad,
            direction = enumValue<LoadDirection>(conditions.direction, "rule '$id' direction"),
            sourceHot = conditions.sourceHot,
            minHeat = conditions.minHeat,
            sourceMinAmount = conditions.sourceMinAmount,
            targetMinAmount = conditions.targetMinAmount,
        ),
        effect = effect.toModel(),
        elseEffect = elseEffect?.toModel(),
        world = world,
        sound = sound,
    )

    private fun EffectDto.toModel() = RuleEffect(
        targetState = targetState,
        spawnObject = spawnObject,
        spawnAmount = spawnAmount,
        sourceState = sourceState,
        sourceConsume = sourceConsume,
        targetConsume = targetConsume,
        transform = transform,
    )

    @Serializable
    private class CatalogDto(
        val types: List<TypeDto>,
        val rules: List<RuleDto> = emptyList(),
        val merges: List<MergeDto> = emptyList(),
    )

    @Serializable
    private class MergeDto(
        val id: String,
        val name: String? = null,
        val a: String,
        val b: String,
        val result: String,
        val sound: String? = null,
        val world: Int = 1,
    )

    @Serializable
    private class WorldDto(
        val world: Int,
        val title: String,
        val icon: String? = null,
        val levels: List<String>,
        val bonus: String? = null,
        val map: List<MapNodeDto> = emptyList(),
    )

    @Serializable
    private class MapNodeDto(val level: String, val x: Float, val y: Float, val icon: String? = null)

    @Serializable
    private class TypeDto(
        val type: String,
        val name: String,
        @SerialName("default_state") val defaultState: String,
        val properties: Map<String, JsonPrimitive> = emptyMap(),
        @SerialName("vanish_states") val vanishStates: List<String> = emptyList(),
        @SerialName("signal_states") val signalStates: List<String> = emptyList(),
        @SerialName("state_names") val stateNames: Map<String, String> = emptyMap(),
        val description: String = "",
        val world: Int = 1,
    )

    @Serializable
    private class RuleDto(
        val id: String,
        val name: String? = null,
        val trigger: String,
        val conditions: ConditionsDto,
        val effect: EffectDto,
        @SerialName("else_effect") val elseEffect: EffectDto? = null,
        val world: Int = 1,
        val sound: String? = null,
    )

    @Serializable
    private class ConditionsDto(
        val source: String? = null,
        @SerialName("source_state") val sourceState: String? = null,
        val target: String,
        @SerialName("target_state") val targetState: String? = null,
        @SerialName("min_load") val minLoad: Int = 1,
        val direction: String = "DOWN",
        @SerialName("source_hot") val sourceHot: Boolean = false,
        @SerialName("min_heat") val minHeat: Int = 1,
        @SerialName("source_min_amount") val sourceMinAmount: Int = 0,
        @SerialName("target_min_amount") val targetMinAmount: Int = 0,
    )

    @Serializable
    private class EffectDto(
        @SerialName("target_state") val targetState: String? = null,
        @SerialName("spawn_object") val spawnObject: String? = null,
        @SerialName("spawn_amount") val spawnAmount: Int? = null,
        @SerialName("source_state") val sourceState: String? = null,
        @SerialName("source_consume") val sourceConsume: Int = 0,
        @SerialName("target_consume") val targetConsume: Int = 0,
        val transform: String? = null,
    )

    @Serializable
    private class LevelDto(
        val id: String,
        val title: String,
        val world: Int,
        val intro: String = "",
        val map: List<String>,
        val legend: Map<Char, LegendDto> = emptyMap(),
        val rules: List<RuleDto> = emptyList(),
        val goals: List<GoalDto>,
        val wind: List<WindDto> = emptyList(),
        @SerialName("max_steps") val maxSteps: Int = 200,
        /** "marked": things may only be put down on the placement fields ('+'). */
        val placement: String? = null,
    )

    @Serializable
    private class LegendDto(
        val type: String,
        val id: String? = null,
        val state: String? = null,
        /** Loose things the player may drag (a log, a stone); the landscape and fixed things are not. */
        val isMovable: Boolean = false,
        val amount: Int? = null,
        val properties: Map<String, JsonPrimitive> = emptyMap(),
        val nobuild: Boolean = false,
    )

    @Serializable
    private class GoalDto(
        val type: String,
        val text: String,
        val optional: Boolean = false,
        val area: List<Int>? = null,
        val liquid: String? = null,
        val min: Int? = null,
        @SerialName("object") val objectId: String? = null,
        val state: String? = null,
        val moves: Int? = null,
        val types: List<String> = emptyList(),
    )

    @Serializable
    private class WindDto(val area: List<Int>, val dx: Int)
}
