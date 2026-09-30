package com.example.agent.generation

import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.example.agent.model.isTestable

class TestScenarioGenerator(
    private val heuristicGenerator: HeuristicScenarioGenerator,
    private val postProcessor: TestCasePostProcessor
) {
    fun generateForStructure(structure: CodeStructure): TestSuiteResult {
        val allCases = structure.functions.filter { it.isTestable() }.flatMap { heuristicGenerator.generate(it) }

        return TestSuiteResult(
            sourcePath = structure.sourcePath,
            testCases = postProcessor.deduplicateAndNormalize(allCases)
        )
    }
}
