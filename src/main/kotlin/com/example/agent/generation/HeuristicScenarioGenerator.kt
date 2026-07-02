package com.example.agent.generation

import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generates test scenarios for a single [FunctionInfo] purely from formal
 * heuristics, without any dependency on an LLM. This generator must always
 * return a non-empty, valid list of [TestCase]s for any function that has
 * at least one parameter or a name, so that the rest of the pipeline keeps
 * working even when no LLM is configured.
 *
 * Rules implemented:
 * - Numeric parameters (`int`, `long`, `double`/`float` and their boxed
 *   equivalents): boundary values (min, max, zero, negative).
 * - `String` parameters: empty string, `null`, and a very long string.
 * - Other reference types: a `null` value negative scenario.
 * - One positive scenario with "typical" valid values for all parameters.
 * - One negative scenario per thrown exception recorded in [FunctionInfo.exceptions].
 */
class HeuristicScenarioGenerator {

    private val counter = AtomicInteger(0)

    fun generate(function: FunctionInfo): List<TestCase> {
        val cases = mutableListOf<TestCase>()

        cases += positiveScenario(function)
        function.parameters.forEach { parameter ->
            cases += boundaryOrNegativeScenariosFor(function, parameter)
        }
        cases += exceptionScenarios(function)

        return cases
    }

    private fun positiveScenario(function: FunctionInfo): TestCase {
        val inputData = function.parameters.associate { it.name to typicalValueFor(it.type) }
        return newTestCase(
            function = function,
            type = ScenarioType.POSITIVE,
            description = "Вызов ${function.name} с типичными допустимыми значениями всех параметров",
            inputData = inputData,
            expectedResult = "Корректный результат без исключений"
        )
    }

    private fun boundaryOrNegativeScenariosFor(function: FunctionInfo, parameter: ParameterInfo): List<TestCase> {
        val normalizedType = parameter.type.trim()
        return when {
            isNumericType(normalizedType) -> numericBoundaryScenarios(function, parameter)
            isStringType(normalizedType) -> stringBoundaryScenarios(function, parameter)
            else -> referenceTypeScenarios(function, parameter)
        }
    }

    private fun numericBoundaryScenarios(function: FunctionInfo, parameter: ParameterInfo): List<TestCase> {
        val boundaries = numericBoundaryValues(parameter.type)
        return boundaries.map { (label, value) ->
            val inputData = baseInputWithOverride(function, parameter.name, value)
            newTestCase(
                function = function,
                type = ScenarioType.BOUNDARY,
                description = "Граничное значение параметра '${parameter.name}' (${parameter.type}): $label",
                inputData = inputData,
                expectedResult = "Система должна корректно обработать граничное значение"
            )
        }
    }

    private fun stringBoundaryScenarios(function: FunctionInfo, parameter: ParameterInfo): List<TestCase> {
        val longString = "a".repeat(10_000)
        val values = listOf(
            "пустая строка" to "",
            "null значение" to null,
            "строка максимальной длины" to longString
        )
        return values.map { (label, value) ->
            val inputData = baseInputWithOverride(function, parameter.name, value)
            newTestCase(
                function = function,
                type = if (label == "null значение") ScenarioType.NEGATIVE else ScenarioType.BOUNDARY,
                description = "Граничное/некорректное значение параметра '${parameter.name}' (String): $label",
                inputData = inputData,
                expectedResult = "Система должна корректно обработать или отклонить значение"
            )
        }
    }

    private fun referenceTypeScenarios(function: FunctionInfo, parameter: ParameterInfo): List<TestCase> {
        val inputData = baseInputWithOverride(function, parameter.name, null)
        return listOf(
            newTestCase(
                function = function,
                type = ScenarioType.NEGATIVE,
                description = "Значение null для параметра '${parameter.name}' (${parameter.type})",
                inputData = inputData,
                expectedResult = "Ожидается корректная обработка null (исключение или явная проверка)"
            )
        )
    }

    private fun exceptionScenarios(function: FunctionInfo): List<TestCase> {
        return function.exceptions
            .filter { it.context == "thrown" }
            .map { exceptionInfo ->
                val inputData = function.parameters.associate { it.name to typicalValueFor(it.type) }
                newTestCase(
                    function = function,
                    type = ScenarioType.NEGATIVE,
                    description = "Сценарий, приводящий к выбросу исключения ${exceptionInfo.exceptionType} " +
                        "(строка ${exceptionInfo.lineNumber})",
                    inputData = inputData,
                    expectedResult = "Ожидается выброс исключения ${exceptionInfo.exceptionType}"
                )
            }
    }

    private fun baseInputWithOverride(function: FunctionInfo, paramName: String, value: String?): Map<String, String?> {
        val base: MutableMap<String, String?> = function.parameters
            .associate { it.name to typicalValueFor(it.type) as String? }
            .toMutableMap()
        base[paramName] = value
        return base
    }

    private fun newTestCase(
        function: FunctionInfo,
        type: ScenarioType,
        description: String,
        inputData: Map<String, String?>,
        expectedResult: String?
    ): TestCase {
        val id = "${function.name}-${counter.incrementAndGet()}"
        return TestCase(
            id = id,
            functionName = function.name,
            className = function.className,
            type = type,
            description = description,
            inputData = inputData,
            expectedResult = expectedResult,
            steps = listOf(
                "Подготовить входные данные: $inputData",
                "Вызвать ${function.className}.${function.name}",
                "Проверить результат: ${expectedResult ?: "не определён"}"
            )
        )
    }

    companion object {
        private val NUMERIC_TYPES = setOf(
            "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
            "double", "Double", "float", "Float"
        )

        private fun isNumericType(type: String): Boolean = NUMERIC_TYPES.contains(type)

        private fun isStringType(type: String): Boolean = type == "String" || type == "java.lang.String"

        private fun numericBoundaryValues(type: String): List<Pair<String, String>> {
            return when (type) {
                "int", "Integer" -> listOf(
                    "минимальное" to Int.MIN_VALUE.toString(),
                    "максимальное" to Int.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "long", "Long" -> listOf(
                    "минимальное" to Long.MIN_VALUE.toString(),
                    "максимальное" to Long.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "short", "Short" -> listOf(
                    "минимальное" to Short.MIN_VALUE.toString(),
                    "максимальное" to Short.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "byte", "Byte" -> listOf(
                    "минимальное" to Byte.MIN_VALUE.toString(),
                    "максимальное" to Byte.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "double", "Double" -> listOf(
                    "минимальное" to Double.MIN_VALUE.toString(),
                    "максимальное" to Double.MAX_VALUE.toString(),
                    "ноль" to "0.0",
                    "отрицательное" to "-1.0"
                )
                "float", "Float" -> listOf(
                    "минимальное" to Float.MIN_VALUE.toString(),
                    "максимальное" to Float.MAX_VALUE.toString(),
                    "ноль" to "0.0",
                    "отрицательное" to "-1.0"
                )
                else -> listOf("значение по умолчанию" to "0")
            }
        }

        private fun typicalValueFor(type: String): String {
            return when {
                isNumericType(type) -> "1"
                isStringType(type) -> "example"
                type == "boolean" || type == "Boolean" -> "true"
                else -> "validInstance"
            }
        }
    }
}
