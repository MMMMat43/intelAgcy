package com.example.agent.generation

import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeuristicScenarioGeneratorTest {

    private val generator = HeuristicScenarioGenerator()

    @Test
    fun `generates min max zero negative and positive scenarios for int parameter`() {
        val function = FunctionInfo(
            name = "divide",
            className = "Calculator",
            parameters = listOf(ParameterInfo(name = "value", type = "int")),
            returnType = "int",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 1
        )

        val cases = generator.generate(function)

        val valueInputs = cases.map { it.inputData["value"] }
        assertTrue(valueInputs.contains(Int.MIN_VALUE.toString()), "Expected min value scenario")
        assertTrue(valueInputs.contains(Int.MAX_VALUE.toString()), "Expected max value scenario")
        assertTrue(valueInputs.contains("0"), "Expected zero value scenario")
        assertTrue(valueInputs.contains("-1"), "Expected negative value scenario")
        assertTrue(cases.any { it.type == ScenarioType.POSITIVE }, "Expected at least one positive scenario")
    }

    @Test
    fun `generates empty string and null scenarios for String parameter`() {
        val function = FunctionInfo(
            name = "greet",
            className = "Greeter",
            parameters = listOf(ParameterInfo(name = "name", type = "String")),
            returnType = "String",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 1
        )

        val cases = generator.generate(function)

        assertTrue(cases.any { it.inputData["name"] == "" }, "Expected empty string scenario")
        assertTrue(cases.any { it.inputData.containsKey("name") && it.inputData["name"] == null }, "Expected null scenario")
        assertTrue(cases.any { it.type == ScenarioType.POSITIVE }, "Expected at least one positive scenario")
    }
}
