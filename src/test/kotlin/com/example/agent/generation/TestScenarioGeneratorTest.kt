package com.example.agent.generation

import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.model.CodeStructure
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestScenarioGeneratorTest {

    private class FailingLlmClient : LlmClient {
        override fun complete(prompt: String): LlmResult = LlmResult.Failure("no api key configured")
    }

    @Test
    fun `returns non-empty heuristic-only result when LLM client fails`() {
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
            llmEnricher = LlmScenarioEnricher(FailingLlmClient()),
            postProcessor = TestCasePostProcessor()
        )

        val result = generator.generateForStructure(structure)

        assertTrue(result.testCases.isNotEmpty(), "Expected non-empty result even without a working LLM")
        assertFalse(result.testCases.any { it.id.contains("llm") }, "Expected only heuristic scenarios, no LLM-derived ones")
    }
}
