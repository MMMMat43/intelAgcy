package com.example.agent.generation

import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.example.agent.model.isTestable

class TestScenarioGenerator(
    private val heuristicGenerator: HeuristicScenarioGenerator,
    private val llmEnricher: LlmScenarioEnricher,
    private val postProcessor: TestCasePostProcessor
) {
    fun generateForStructure(structure: CodeStructure): TestSuiteResult {
        val allCases = structure.functions.filter { it.isTestable() }.flatMap { function ->
            heuristicGenerator.generate(function) + llmEnricher.enrich(function)
        }

        val processed = postProcessor.deduplicateAndNormalize(allCases)

        return TestSuiteResult(
            sourcePath = structure.sourcePath,
            testCases = processed
        )
    }
}
