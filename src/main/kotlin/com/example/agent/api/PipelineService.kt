package com.example.agent.api

import com.example.agent.analysis.KotlinCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.coverage.CoverageGenerationResult
import com.example.agent.coverage.CoverageGuidedGenerator
import com.example.agent.coverage.CoverageReport
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.generation.LlmUncoveredBranchSuggester
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmConfig
import com.example.agent.llm.OpenAiCompatibleLlmClient
import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.example.agent.model.isTestable
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import java.nio.file.Paths

class PipelineService(
    private val llmClientFactory: () -> LlmClient = { OpenAiCompatibleLlmClient(LlmConfig.fromEnv()) }
) {

    class InvalidSourceException(message: String) : RuntimeException(message)

    fun analyze(sourcePath: String): CodeStructure {
        val files = loadFilesOrThrow(sourcePath)
        return KotlinCodeAnalyzer().analyze(sourcePath, files)
    }

    data class GenerateTestsResult(
        val functionsCount: Int,
        val testCasesCount: Int,
        val generatedTestFilePath: String?,
        val testSuiteResult: TestSuiteResult,
        val skippedFunctions: List<String> = emptyList(),
        val executedTestCases: Int = 0,
        val warnings: List<String> = emptyList(),
        val coverage: CoverageReport? = null
    )

    fun generateTests(sourcePath: String, outputDir: String?): GenerateTestsResult {
        val files = loadFilesOrThrow(sourcePath)
        val analysis = KotlinCodeAnalyzer().analyzeDetailed(sourcePath, files)
        val structure = analysis.structure

        val generation: CoverageGenerationResult = CoverageGuidedGenerator(
            suggester = LlmUncoveredBranchSuggester(llmClientFactory())
        ).generate(analysis)

        val suite = generation.suite ?: TestSuiteResult(sourcePath, emptyList())
        val outcomes = generation.outcomes

        var generatedTestFilePath: String? = null
        if (!outputDir.isNullOrBlank()) {
            val fileSpec = JUnit5TestCodeGenerator().generate(
                suite,
                structure.packageName,
                structure.functions,
                outcomes
            )
            val storage = ArtifactStorage(Paths.get(outputDir))
            storage.saveAnalysis(structure)
            storage.saveTestCases(suite)
            generation.report?.let { storage.saveCoverage(it) }
            storage.saveGeneratedTestCode(fileSpec)
            generatedTestFilePath = Paths.get(outputDir, "generated-tests").toString()
        }

        return GenerateTestsResult(
            functionsCount = structure.functions.size,
            testCasesCount = suite.testCases.size,
            generatedTestFilePath = generatedTestFilePath,
            testSuiteResult = suite,
            skippedFunctions = structure.functions
                .filter { !it.isTestable() }
                .map { "${it.className}.${it.name}: ${it.skipReason}" },
            executedTestCases = outcomes.values.count { it !is ExecutionOutcome.CouldNotExecute },
            warnings = generation.warnings,
            coverage = generation.report
        )
    }

    private fun loadFilesOrThrow(sourcePath: String) = try {
        LocalFileSourceLoader().load(sourcePath)
    } catch (e: IllegalArgumentException) {
        throw InvalidSourceException(e.message ?: "Invalid source path: $sourcePath")
    }
}
