package com.shootcat.react.engine

import com.shootcat.react.engine.model.EventRequirement
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.Goal
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.MapNode
import com.shootcat.react.engine.model.ObjectType
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.RuleConditions
import com.shootcat.react.engine.model.RuleEffect
import com.shootcat.react.engine.model.SolutionKind
import com.shootcat.react.engine.model.SolutionSpec
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.engine.model.WorldData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class LevelFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Loads worlds and levels from JSON. A world file defines the object types and the rules that hold
 * everywhere in that world; level files define layout, objects, goals and optional extra rules.
 */
object LevelLoader {

    private val json = Json { ignoreUnknownKeys = false }

    fun parseWorld(text: String): WorldData {
        val dto = decode<WorldDto>(text, "world")
        val types = TypeCatalog(dto.types.map { it.toModel() })
        dto.map.forEach { n -> n.icon?.let { if (it !in types) fail("world ${dto.world}: map icon '$it' is not a type") } }
        val rules = dto.rules.map { it.toModel() }
        rules.forEach { validateRule(it, types, "world ${dto.world}") }
        requireUnique(rules.map { it.id }, "rule id in world ${dto.world}")
        return WorldData(
            world = dto.world,
            title = dto.title,
            levelIds = dto.levels,
            map = dto.map.map { MapNode(it.level, it.x, it.y, it.icon) },
            types = types,
            rules = rules,
        )
    }

    fun parseLevel(text: String, world: WorldData): LevelData {
        val dto = decode<LevelDto>(text, "level")
        val where = "level '${dto.id}'"
        if (dto.world != world.world) fail("$where belongs to world ${dto.world}, not ${world.world}")
        val width = dto.grid.width
        val height = dto.grid.height
        if (width < 1 || height < 1) fail("$where has an empty grid")

        val walls = parseLayout(dto.layout, width, height, where)

        val objects = dto.objects.map { o ->
            if (o.type !in world.types) fail("$where: object '${o.id}' has unknown type '${o.type}'")
            val p = Position(o.position.x, o.position.y)
            if (p.x !in 0 until width || p.y !in 0 until height) fail("$where: object '${o.id}' is outside the grid")
            if (p in walls) fail("$where: object '${o.id}' is placed inside a wall at $p")
            world.types.create(o.id, o.type, p, o.state, o.properties.toStrings(), o.movable)
        }
        requireUnique(objects.map { it.id }, "object id in $where")
        requireUnique(objects.map { it.position }, "object position in $where")

        val levelRules = dto.rules.map { it.toModel() }
        levelRules.forEach { validateRule(it, world.types, where) }
        val overridden = levelRules.map { it.id }.toSet()
        val rules = world.rules.filter { it.id !in overridden } + levelRules

        val ids = objects.map { it.id }.toSet()
        val goals = dto.goals.map { Goal(it.objectId, it.requiredState) }
        if (goals.isEmpty()) fail("$where has no goals")
        goals.forEach { if (it.objectId !in ids) fail("$where: goal refers to unknown object '${it.objectId}'") }

        val ruleIds = rules.map { it.id }.toSet()
        val solutions = dto.solutions.map { s ->
            val kind = enumValue<SolutionKind>(s.kind, "$where solution '${s.id}'")
            s.requires.forEach { if (it.rule !in ruleIds) fail("$where: solution '${s.id}' needs unknown rule '${it.rule}'") }
            s.unmoved.forEach { if (it !in ids) fail("$where: solution '${s.id}' refers to unknown object '$it'") }
            SolutionSpec(
                id = s.id,
                kind = kind,
                label = s.label ?: kind.label,
                requires = s.requires.map { EventRequirement(it.rule, it.source, it.withoutSource) },
                maxMoved = s.maxMoved,
                unmoved = s.unmoved,
            )
        }
        requireUnique(solutions.map { it.id }, "solution id in $where")

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
            solutions = solutions,
            maxSteps = dto.maxSteps,
        )
    }

    private fun parseLayout(layout: List<String>, width: Int, height: Int, where: String): Set<Position> {
        if (layout.isEmpty()) return emptySet()
        if (layout.size != height) fail("$where: layout has ${layout.size} rows, grid height is $height")
        val walls = mutableSetOf<Position>()
        layout.forEachIndexed { y, row ->
            if (row.length != width) fail("$where: layout row $y has ${row.length} cells, grid width is $width")
            row.forEachIndexed { x, c ->
                when (c) {
                    '#' -> walls += Position(x, y)
                    '.' -> Unit
                    else -> fail("$where: unknown layout symbol '$c' at ($x,$y)")
                }
            }
        }
        return walls
    }

    private fun validateRule(rule: Rule, types: TypeCatalog, where: String) {
        val c = rule.conditions
        val referenced = listOfNotNull(c.source, c.target, rule.effect.spawnObject, rule.elseEffect?.spawnObject)
        referenced.forEach { if (it !in types) fail("$where: rule '${rule.id}' uses unknown type '$it'") }
        if (rule.trigger != Trigger.LOAD && c.source == null) fail("$where: rule '${rule.id}' needs a source")
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
        stateNames = stateNames,
        description = description,
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
        ),
        effect = effect.toModel(),
        elseEffect = elseEffect?.toModel(),
    )

    private fun EffectDto.toModel() = RuleEffect(targetState = targetState, spawnObject = spawnObject)

    @Serializable
    private class WorldDto(
        val world: Int,
        val title: String,
        val levels: List<String>,
        val map: List<MapNodeDto> = emptyList(),
        val types: List<TypeDto>,
        val rules: List<RuleDto> = emptyList(),
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
        @SerialName("state_names") val stateNames: Map<String, String> = emptyMap(),
        val description: String = "",
    )

    @Serializable
    private class RuleDto(
        val id: String,
        val name: String? = null,
        val trigger: String,
        val conditions: ConditionsDto,
        val effect: EffectDto,
        @SerialName("else_effect") val elseEffect: EffectDto? = null,
    )

    @Serializable
    private class ConditionsDto(
        val source: String? = null,
        @SerialName("source_state") val sourceState: String? = null,
        val target: String,
        @SerialName("target_state") val targetState: String? = null,
        @SerialName("min_load") val minLoad: Int = 1,
    )

    @Serializable
    private class EffectDto(
        @SerialName("target_state") val targetState: String? = null,
        @SerialName("spawn_object") val spawnObject: String? = null,
    )

    @Serializable
    private class LevelDto(
        val id: String,
        val title: String,
        val world: Int,
        val intro: String = "",
        val grid: GridDto,
        val layout: List<String> = emptyList(),
        val objects: List<ObjectDto>,
        val rules: List<RuleDto> = emptyList(),
        val goals: List<GoalDto>,
        val solutions: List<SolutionDto> = emptyList(),
        @SerialName("max_steps") val maxSteps: Int = 150,
    )

    @Serializable
    private class GridDto(val width: Int, val height: Int)

    @Serializable
    private class PositionDto(val x: Int, val y: Int)

    @Serializable
    private class ObjectDto(
        val id: String,
        val type: String,
        val position: PositionDto,
        val state: String? = null,
        val movable: Boolean = false,
        val properties: Map<String, JsonPrimitive> = emptyMap(),
    )

    @Serializable
    private class GoalDto(
        @SerialName("object_id") val objectId: String,
        @SerialName("required_state") val requiredState: String,
    )

    @Serializable
    private class SolutionDto(
        val id: String,
        val kind: String,
        val label: String? = null,
        val requires: List<RequirementDto> = emptyList(),
        @SerialName("max_moved") val maxMoved: Int? = null,
        val unmoved: List<String> = emptyList(),
    )

    @Serializable
    private class RequirementDto(
        val rule: String,
        val source: String? = null,
        @SerialName("without_source") val withoutSource: String? = null,
    )
}
