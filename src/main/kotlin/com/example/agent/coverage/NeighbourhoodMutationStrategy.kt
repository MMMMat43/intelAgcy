package com.example.agent.coverage

import com.example.agent.execution.TypeConversion
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import kotlin.random.Random

private val STRING_ALPHABET = listOf("0", "7", "A", "z", " ", "-", "_", ".")

class NeighbourhoodMutationStrategy : MutationStrategy {

    override fun mutate(context: CandidateContext, parent: Map<String, String?>): Map<String, String?> =
        mutate(context.info, context.hints, parent, context.random)

    private fun mutate(
        info: FunctionInfo,
        hints: BoundaryHints,
        parent: Map<String, String?>,
        random: Random
    ): Map<String, String?> {
        val vector = LinkedHashMap(parent)
        if (info.parameters.isEmpty()) return vector
        val changes = if (info.parameters.size > 1 && random.nextInt(3) == 0) 2 else 1
        repeat(changes) {
            val parameter = info.parameters[random.nextInt(info.parameters.size)]
            vector[parameter.name] = mutateValue(info, parameter, hints, vector, random)
        }
        return vector
    }

    private fun mutateValue(
        info: FunctionInfo,
        parameter: ParameterInfo,
        hints: BoundaryHints,
        vector: Map<String, String?>,
        random: Random
    ): String? {
        val type = TypeConversion.normalize(parameter.type)
        if (parameter.nullable && random.nextInt(10) == 0) return null
        val current = vector[parameter.name]
        return when {
            type == "Boolean" -> if (current == "true") "false" else "true"
            type == "Char" -> listOf("a", " ", "0", "Z")[random.nextInt(4)]
            type == "String" -> {
                val hinted = hints.values[parameter.name].orEmpty().filterNotNull()
                when (random.nextInt(5)) {
                    0 -> if (hinted.isNotEmpty()) hinted[random.nextInt(hinted.size)] else "a"
                    1 -> (current ?: "") + "a"
                    2 -> (current ?: "").dropLast(1)
                    3 -> (current ?: "") + STRING_ALPHABET[random.nextInt(STRING_ALPHABET.size)]
                    else -> ""
                }
            }
            NumericFormat.isNumeric(type) -> mutateNumber(info, parameter, type, hints, vector, current, random)
            else -> current
        }
    }

    private fun mutateNumber(
        info: FunctionInfo,
        parameter: ParameterInfo,
        type: String,
        hints: BoundaryHints,
        vector: Map<String, String?>,
        current: String?,
        random: Random
    ): String? {
        val value = current?.toDoubleOrNull() ?: 1.0
        val others = info.parameters
            .filter { it.name != parameter.name && NumericFormat.isNumeric(TypeConversion.normalize(it.type)) }
            .mapNotNull { vector[it.name]?.toDoubleOrNull() }
            .filter { it != 0.0 }
        val constants = hints.constants
        val candidate = when (random.nextInt(9)) {
            0 -> value * 2
            1 -> value / 2
            2 -> value * 10
            3 -> value / 10
            4 -> value + 1
            5 -> value - 1
            6 -> if (constants.isNotEmpty()) constants[random.nextInt(constants.size)] else value + 1
            7 -> if (constants.isNotEmpty() && others.isNotEmpty()) {
                constants[random.nextInt(constants.size)] / others[random.nextInt(others.size)]
            } else {
                -value
            }
            else -> if (constants.isNotEmpty() && others.isNotEmpty()) {
                constants[random.nextInt(constants.size)] * others[random.nextInt(others.size)]
            } else {
                value * 3
            }
        }
        return NumericFormat.format(type, candidate)
    }
}
