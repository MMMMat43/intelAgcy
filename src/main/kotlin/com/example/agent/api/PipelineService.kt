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
import com.example.agent.source.SourceLoaderFactory
import com.example.agent.storage.ArtifactStorage
import com.example.agent.storage.ArtifactStore
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

class PipelineService(
    private val listeners: List<PipelineListener> = emptyList(),
    private val sourceLoaderFactory: SourceLoaderFactory = SourceLoaderFactory(),
    private val artifactStoreFactory: (Path) -> ArtifactStore = { ArtifactStorage(it) },
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
        val startedAt = Instant.now()
        val startedNanos = System.nanoTime()
        fun elapsedMillis() = (System.nanoTime() - startedNanos) / 1_000_000

        try {
            val files = loadFilesOrThrow(sourcePath)
            val analysis = KotlinCodeAnalyzer().analyzeDetailed(sourcePath, files)
            val structure = analysis.structure
            notify {
                it.onAnalysisCompleted(
                    AnalysisCompleted(
                        sourcePath = sourcePath,
                        functionsCount = structure.functions.size,
                        testableFunctions = structure.functions.count { f -> f.isTestable() },
                        warnings = analysis.warnings
                    )
                )
            }

            val generation: CoverageGenerationResult = CoverageGuidedGenerator(
                suggester = LlmUncoveredBranchSuggester(llmClientFactory()),
                onFunctionGenerated = { coverage, cases ->
                    notify { it.onFunctionProcessed(FunctionProcessed(sourcePath, coverage, cases)) }
                }
            ).generate(analysis)

            val suite = generation.suite ?: TestSuiteResult(sourcePath, emptyList())
            val outcomes = generation.outcomes
            val executed = outcomes.values.count { it !is ExecutionOutcome.CouldNotExecute }
            val report = generation.report
            notify {
                it.onSuiteGenerated(
                    SuiteGenerated(
                        sourcePath = sourcePath,
                        testCasesCount = suite.testCases.size,
                        executedTestCases = executed,
                        coveredBranches = report?.coveredBranches ?: 0,
                        totalBranches = report?.totalBranches ?: 0,
                        coverageMeasured = report?.measured ?: false
                    )
                )
            }

            var generatedTestFilePath: String? = null
            if (!outputDir.isNullOrBlank()) {
                val fileSpec = JUnit5TestCodeGenerator().generate(
                    suite,
                    structure.packageName,
                    structure.functions,
                    outcomes
                )
                val storage = artifactStoreFactory(Paths.get(outputDir))
                storage.saveAnalysis(structure)
                storage.saveTestCases(suite)
                report?.let { storage.saveCoverage(it) }
                storage.saveGeneratedTestCode(fileSpec)
                generatedTestFilePath = Paths.get(outputDir, "generated-tests").toString()
                notify { it.onArtifactsSaved(ArtifactsSaved(sourcePath, outputDir)) }
            }

            val warnings = analysis.warnings + generation.warnings
            notify {
                it.onCompleted(
                    PipelineCompleted(
                        sourcePath = sourcePath,
                        startedAt = startedAt,
                        durationMillis = elapsedMillis(),
                        functionsCount = structure.functions.size,
                        testCasesCount = suite.testCases.size,
                        executedTestCases = executed,
                        coveredBranches = report?.coveredBranches ?: 0,
                        totalBranches = report?.totalBranches ?: 0,
                        coverageMeasured = report?.measured ?: false,
                        outputDir = outputDir?.takeIf { dir -> dir.isNotBlank() },
                        functions = report?.functions.orEmpty(),
                        warnings = warnings
                    )
                )
            }

            return GenerateTestsResult(
                functionsCount = structure.functions.size,
                testCasesCount = suite.testCases.size,
                generatedTestFilePath = generatedTestFilePath,
                testSuiteResult = suite,
                skippedFunctions = structure.functions
                    .filter { !it.isTestable() }
                    .map { "${it.className}.${it.name}: ${it.skipReason}" },
                executedTestCases = executed,
                warnings = warnings,
                coverage = report
            )
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            notify { it.onFailed(PipelineFailed(sourcePath, startedAt, elapsedMillis(), message)) }
            throw e
        }
    }

    private fun notify(action: (PipelineListener) -> Unit) {
        listeners.forEach { listener -> runCatching { action(listener) } }
    }

    private fun loadFilesOrThrow(sourcePath: String) = try {
        sourceLoaderFactory.create(sourcePath).load(sourcePath)
    } catch (e: IllegalArgumentException) {
        throw InvalidSourceException(e.message ?: "Invalid source path: $sourcePath")
    } catch (e: IllegalStateException) {
        throw InvalidSourceException(e.message ?: "Could not load sources: $sourcePath")
    }
}
