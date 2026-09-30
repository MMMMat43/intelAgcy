package com.example.agent.generation

import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeuristicScenarioGeneratorTest {

    private val generator = HeuristicScenarioGenerator()

    private fun function(vararg parameters: ParameterInfo) = FunctionInfo(
        name = "f",
        className = "C",
        parameters = parameters.toList(),
        returnType = "Int",
        branches = emptyList(),
        loops = emptyList(),
        exceptions = emptyList(),
        cyclomaticComplexity = 1
    )

    @Test
    fun `generates min max zero negative and positive scenarios for Int parameter`() {
        val cases = generator.generate(function(ParameterInfo("value", "Int")))

        val valueInputs = cases.map { it.inputData["value"] }
        assertTrue(valueInputs.contains(Int.MIN_VALUE.toString()))
        assertTrue(valueInputs.contains(Int.MAX_VALUE.toString()))
        assertTrue(valueInputs.contains("0"))
        assertTrue(valueInputs.contains("-1"))
        assertTrue(cases.any { it.type == ScenarioType.POSITIVE })
    }

    @Test
    fun `generates empty and long string scenarios and no null for non-nullable String`() {
        val cases = generator.generate(function(ParameterInfo("name", "String")))

        assertTrue(cases.any { it.inputData["name"] == "" })
        assertTrue(cases.any { it.inputData["name"]?.length == 10_000 })
        assertFalse(cases.any { it.inputData.containsKey("name") && it.inputData["name"] == null })
    }

    @Test
    fun `nullable parameter gets a null negative scenario`() {
        val cases = generator.generate(function(ParameterInfo("name", "String", nullable = true)))

        assertTrue(cases.any { it.type == ScenarioType.NEGATIVE && it.inputData["name"] == null })
    }

    @Test
    fun `Boolean and Char parameters get boundary scenarios`() {
        val cases = generator.generate(function(ParameterInfo("flag", "Boolean"), ParameterInfo("ch", "Char")))

        assertTrue(cases.any { it.inputData["flag"] == "false" })
        assertTrue(cases.any { it.inputData["ch"] == " " })
    }
}
