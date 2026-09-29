package com.example.agent.generation

import com.example.agent.model.CodeStructure
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestScenarioGeneratorTest {

    @Test
    fun `returns non-empty heuristic-only result`() {
        val function = FunctionInfo(
            name = "add",
            className = "MathUtils",
            parameters = listOf(ParameterInfo(name = "a", type = "Int"), ParameterInfo(name = "b", type = "Int")),
            returnType = "Int",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 1
        )
        val structure = CodeStructure(
            sourcePath = "/tmp/MathUtils.kt",
            language = "kotlin",
            functions = listOf(function)
        )

        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),

            postProcessor = TestCasePostProcessor()
        )

        val result = generator.generateForStructure(structure)

        assertTrue(result.testCases.isNotEmpty())
        assertFalse(result.testCases.any { it.id.contains("llm") })
    }
}
