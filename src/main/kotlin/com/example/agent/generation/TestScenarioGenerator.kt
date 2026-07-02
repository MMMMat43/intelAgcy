package com.example.agent.generation

import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult

/**
 * Facade combining heuristic generation, optional LLM enrichment, and
 * post-processing into a single entry point that turns a [CodeStructure]
 * into a [TestSuiteResult].
 *
 * The result is always non-empty (as long as [CodeStructure.functions] is
 * non-empty) even if the LLM is unavailable, because [HeuristicScenarioGenerator]
 * works fully offline and [LlmScenarioEnricher] degrades gracefully to an
 * empty list on failure.
 */
class TestScenarioGenerator(
    private val heuristicGenerator: HeuristicScenarioGenerator,
    private val llmEnricher: LlmScenarioEnricher,
    private val postProcessor: TestCasePostProcessor
) {
    fun generateForStructure(structure: CodeStructure): TestSuiteResult {
        val allCases = structure.functions.flatMap { function ->
            heuristicGenerator.generate(function) + llmEnricher.enrich(function)
        }

        val processed = postProcessor.deduplicateAndNormalize(allCases)

        return TestSuiteResult(
            sourcePath = structure.sourcePath,
            testCases = processed
        )
    }
}
