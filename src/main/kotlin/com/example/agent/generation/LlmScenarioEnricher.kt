package com.example.agent.generation

import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.llm.PromptTemplates
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enriches heuristic scenarios with additional, LLM-suggested scenarios.
 *
 * The LLM is only ever used as an *enrichment* source. If the underlying
 * [LlmClient] call fails (network error, missing API key, etc.), this class
 * returns an empty list rather than throwing, so the caller can safely fall
 * back to heuristic-only generation.
 *
 * [PromptTemplates.functionAnalysisPrompt] returns free-form text, not
 * structured JSON, so the LLM's response is parsed with a simple line-based
 * heuristic: each non-blank line of the response becomes the description of
 * one additional scenario. The scenario type is guessed from keywords in the
 * line (e.g. "негатив"/"negative", "гранич"/"boundary"); everything else is
 * treated as positive.
 */
class LlmScenarioEnricher(private val llmClient: LlmClient) {

    private val counter = AtomicInteger(0)

    fun enrich(function: FunctionInfo): List<TestCase> {
        val result = llmClient.complete(PromptTemplates.functionAnalysisPrompt(function))
        if (result !is LlmResult.Success) {
            return emptyList()
        }

        return parseResponseIntoTestCases(function, result.text)
    }

    private fun parseResponseIntoTestCases(function: FunctionInfo, responseText: String): List<TestCase> {
        val inputData = function.parameters.associate { it.name to "llm-suggested" }

        return responseText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> line.trimStart('-', '*', '•', ' ').trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val id = "${function.name}-llm-${counter.incrementAndGet()}"
                TestCase(
                    id = id,
                    functionName = function.name,
                    className = function.className,
                    type = guessScenarioType(line),
                    description = line,
                    inputData = inputData,
                    expectedResult = null,
                    steps = listOf(
                        "Подготовить входные данные по описанию LLM: $line",
                        "Вызвать ${function.className}.${function.name}",
                        "Проверить результат согласно описанию сценария"
                    )
                )
            }
    }

    private fun guessScenarioType(line: String): ScenarioType {
        val lower = line.lowercase()
        return when {
            lower.contains("негатив") || lower.contains("negative") || lower.contains("ошиб") -> ScenarioType.NEGATIVE
            lower.contains("гранич") || lower.contains("boundary") || lower.contains("предел") -> ScenarioType.BOUNDARY
            else -> ScenarioType.POSITIVE
        }
    }
}
