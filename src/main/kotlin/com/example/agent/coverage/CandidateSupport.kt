package com.example.agent.coverage

import com.example.agent.execution.ConversionResult
import com.example.agent.execution.TypeConversion
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo

internal object CandidateSupport {

    fun typicalValue(parameter: ParameterInfo): String? = when (TypeConversion.normalize(parameter.type)) {
        "Boolean" -> "true"
        "String" -> "example"
        "Char" -> "a"
        else -> "1"
    }

    fun typicalVector(info: FunctionInfo): Map<String, String?> =
        info.parameters.associate { it.name to typicalValue(it) }

    fun allConvertible(parameter: ParameterInfo, value: String?): Boolean =
        TypeConversion.convert(parameter.type, parameter.nullable, value) is ConversionResult.Converted

    fun domains(info: FunctionInfo, hints: BoundaryHints): Map<String, List<String?>> =
        info.parameters.associate { it.name to domain(it, hints) }

    fun domain(parameter: ParameterInfo, hints: BoundaryHints): List<String?> {
        val type = TypeConversion.normalize(parameter.type)
        val values = LinkedHashSet<String?>()
        values += typicalValue(parameter)
        hints.values[parameter.name]?.let { values.addAll(it) }
        when {
            type == "Boolean" -> {
                values += "true"
                values += "false"
            }
            NumericFormat.isNumeric(type) -> {
                values += "0"
                values += "-1"
            }
            type == "String" -> values += ""
            type == "Char" -> {
                values += " "
                values += "0"
            }
        }
        if (parameter.nullable) values += null
        return values.filter { allConvertible(parameter, it) }
    }
}
