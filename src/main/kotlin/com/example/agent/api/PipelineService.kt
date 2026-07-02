package com.example.agent.api

import com.example.agent.analysis.JavaCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmScenarioEnricher
import com.example.agent.generation.TestCasePostProcessor
import com.example.agent.generation.TestScenarioGenerator
import com.example.agent.llm.LlmConfig
import com.example.agent.llm.OpenAiCompatibleLlmClient
import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import java.nio.file.Paths

private const val GENERATED_TESTS_PACKAGE = "com.example.agent.generated"

/**
 * Thin service layer shared between the CLI and the REST API, wiring
 * together source loading, analysis, scenario generation, code generation
 * and artifact storage.
 *
 * Kept separate from [com.example.agent.Cli] and [Routes] so both entry
 * points reuse exactly the same pipeline wiring instead of duplicating it.
 */
class PipelineService {

    /** Thrown when a request references a source path that cannot be found or read. */
    class InvalidSourceException(message: String) : RuntimeException(message)

    fun analyze(sourcePath: String): CodeStructure {
        val files = loadFilesOrThrow(sourcePath)
        return JavaCodeAnalyzer().analyze(sourcePath, files)
    }

    data class GenerateTestsResult(
        val functionsCount: Int,
        val testCasesCount: Int,
        val generatedTestFilePath: String?,
        val testSuiteResult: TestSuiteResult
    )

    fun generateTests(sourcePath: String, outputDir: String?): GenerateTestsResult {
        val structure = analyze(sourcePath)

        val llmClient = OpenAiCompatibleLlmClient(LlmConfig.fromEnv())
        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),
            llmEnricher = LlmScenarioEnricher(llmClient),
            postProcessor = TestCasePostProcessor()
        )
        val testSuiteResult = generator.generateForStructure(structure)

        var generatedTestFilePath: String? = null
        if (!outputDir.isNullOrBlank()) {
            val fileSpec = JUnit5TestCodeGenerator().generate(testSuiteResult, GENERATED_TESTS_PACKAGE)
            val storage = ArtifactStorage(Paths.get(outputDir))
            storage.saveAnalysis(structure)
            storage.saveTestCases(testSuiteResult)
            storage.saveGeneratedTestCode(fileSpec)
            generatedTestFilePath = Paths.get(outputDir, "generated-tests").toString()
        }

        return GenerateTestsResult(
            functionsCount = structure.functions.size,
            testCasesCount = testSuiteResult.testCases.size,
            generatedTestFilePath = generatedTestFilePath,
            testSuiteResult = testSuiteResult
        )
    }

    private fun loadFilesOrThrow(sourcePath: String) = try {
        LocalFileSourceLoader().load(sourcePath)
    } catch (e: IllegalArgumentException) {
        throw InvalidSourceException(e.message ?: "Invalid source path: $sourcePath")
    }
}
