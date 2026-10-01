package com.shootcat.react.engine

import com.shootcat.react.engine.model.Phase
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog

/** One piece of a reaction, e.g. "Eis". [typeId] and [state] let the UI draw a matching icon. */
data class ReactionToken(val text: String, val typeId: String? = null, val state: String? = null)

/** Human readable form of a rule for the Discovery Log: inputs → output. */
data class Reaction(
    val ruleId: String,
    val name: String,
    val phase: Phase,
    val inputs: List<ReactionToken>,
    val output: ReactionToken,
) {
    val text: String get() = inputs.joinToString(" + ") { it.text } + " → " + output.text
}

object Reactions {

    fun describe(rule: Rule, types: TypeCatalog): Reaction {
        val c = rule.conditions
        val target = types[c.target]
        val targetName = target?.name ?: c.target
        val resultState = rule.effect.targetState
        val output = when {
            rule.effect.spawnObject != null -> ReactionToken(types.name(rule.effect.spawnObject), rule.effect.spawnObject)
            resultState != null -> ReactionToken("$targetName ${target?.stateName(resultState) ?: resultState}", c.target, resultState)
            else -> ReactionToken(targetName, c.target)
        }
        val inputs = when (rule.trigger) {
            Trigger.TOUCH -> listOf(sourceToken(rule, types), ReactionToken(targetName, c.target))
            Trigger.LOAD -> listOf(ReactionToken("Gewicht ≥ ${c.minLoad}"), ReactionToken(targetName, c.target))
            Trigger.SIGNAL -> listOf(sourceToken(rule, types))
        }
        return Reaction(rule.id, rule.name, rule.phase, inputs, output)
    }

    private fun sourceToken(rule: Rule, types: TypeCatalog): ReactionToken {
        val source = rule.conditions.source ?: return ReactionToken("?")
        val name = types.name(source)
        val state = rule.conditions.sourceState
        val text = if (state != null) "$name ${types[source]?.stateName(state) ?: state}" else name
        return ReactionToken(text, source, state)
    }

    /** One line for the step log, e.g. "Feuer + Eis → Wasser" or "Druckplatte → oben". */
    fun describeEvent(event: RuleEvent, rules: List<Rule>, types: TypeCatalog): String {
        val rule = rules.firstOrNull { it.id == event.ruleId }
        if (event.positive && rule != null) return describe(rule, types).text
        val target = types.name(event.targetType)
        val state = event.newState?.let { types[event.targetType]?.stateName(it) ?: it } ?: ""
        return "$target → $state".trim()
    }

    /** Types that react with [type] on touch (either way round). Used for the subtle reaction preview. */
    fun touchPartners(type: String, rules: List<Rule>): Set<String> =
        rules.filter { it.trigger == Trigger.TOUCH }.flatMap { rule ->
            val source = rule.conditions.source
            val target = rule.conditions.target
            when (type) {
                source -> listOf(target)
                target -> listOfNotNull(source)
                else -> emptyList()
            }
        }.toSet()
}
