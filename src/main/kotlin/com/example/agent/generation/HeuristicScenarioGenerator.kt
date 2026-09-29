package com.example.agent.generation

import com.example.agent.execution.TypeConversion
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.signature
import java.util.concurrent.atomic.AtomicInteger

class HeuristicScenarioGenerator {

    private val counter = AtomicInteger(0)

    fun generate(function: FunctionInfo): List<TestCase> {
        val cases = mutableListOf<TestCase>()

        cases += positiveScenario(function)
        function.parameters.forEach { parameter ->
            cases += scenariosFor(function, parameter)
        }
        cases += exceptionScenarios(function)

        return cases
    }

    private fun positiveScenario(function: FunctionInfo): TestCase {
        val inputData = function.parameters.associate { it.name to typicalValueFor(it.type) as String? }
        return newTestCase(
            function = function,
            type = ScenarioType.POSITIVE,
            description = "Вызов ${function.name} с типичными допустимыми значениями всех параметров",
            inputData = inputData,
            expectedResult = "Корректный результат без исключений"
        )
    }

    private fun scenariosFor(function: FunctionInfo, parameter: ParameterInfo): List<TestCase> {
        val type = TypeConversion.normalize(parameter.type)
        val cases = mutableListOf<TestCase>()

        val labeledValues: List<Pair<String, String>> = when (type) {
            in NUMERIC_TYPES -> numericBoundaryValues(type)
            "String" -> listOf("пустая строка" to "", "строка максимальной длины" to "a".repeat(10_000))
            "Boolean" -> listOf("значение false" to "false")
            "Char" -> listOf("пробел" to " ", "цифра" to "0")
            else -> emptyList()
        }

        labeledValues.forEach { (label, value) ->
            cases += newTestCase(
                function = function,
                type = ScenarioType.BOUNDARY,
                description = "Граничное значение параметра '${parameter.name}' ($type): $label",
                inputData = baseInputWithOverride(function, parameter.name, value),
                expectedResult = "Система должна корректно обработать граничное значение"
            )
        }

        if (parameter.nullable) {
            cases += newTestCase(
                function = function,
                type = ScenarioType.NEGATIVE,
                description = "Значение null для параметра '${parameter.name}' ($type?)",
                inputData = baseInputWithOverride(function, parameter.name, null),
                expectedResult = "Ожидается корректная обработка null (исключение или явная проверка)"
            )
        }

        return cases
    }

    private fun exceptionScenarios(function: FunctionInfo): List<TestCase> {
        return function.exceptions
            .filter { it.context == "thrown" }
            .map { exceptionInfo ->
                val inputData = function.parameters.associate { it.name to typicalValueFor(it.type) as String? }
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
            ),
            signature = function.signature()
        )
    }

    companion object {
        private val NUMERIC_TYPES = setOf("Int", "Long", "Short", "Byte", "Double", "Float")

        private fun numericBoundaryValues(type: String): List<Pair<String, String>> {
            return when (type) {
                "Int" -> listOf(
                    "минимальное" to Int.MIN_VALUE.toString(),
                    "максимальное" to Int.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "Long" -> listOf(
                    "минимальное" to Long.MIN_VALUE.toString(),
                    "максимальное" to Long.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "Short" -> listOf(
                    "минимальное" to Short.MIN_VALUE.toString(),
                    "максимальное" to Short.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "Byte" -> listOf(
                    "минимальное" to Byte.MIN_VALUE.toString(),
                    "максимальное" to Byte.MAX_VALUE.toString(),
                    "ноль" to "0",
                    "отрицательное" to "-1"
                )
                "Double" -> listOf(
                    "минимальное" to Double.MIN_VALUE.toString(),
                    "максимальное" to Double.MAX_VALUE.toString(),
                    "ноль" to "0.0",
                    "отрицательное" to "-1.0"
                )
                else -> listOf(
                    "минимальное" to Float.MIN_VALUE.toString(),
                    "максимальное" to Float.MAX_VALUE.toString(),
                    "ноль" to "0.0",
                    "отрицательное" to "-1.0"
                )
            }
        }

        private fun typicalValueFor(type: String): String {
            val normalized = TypeConversion.normalize(type)
            return when {
                normalized in NUMERIC_TYPES -> "1"
                normalized == "String" -> "example"
                normalized == "Boolean" -> "true"
                normalized == "Char" -> "a"
                else -> "validInstance"
            }
        }
    }
}
