package com.example.agent.api

import com.example.agent.analysis.JavaCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.execution.CompilationResult
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.InMemoryJavaCompiler
import com.example.agent.execution.TestCaseExecutor
import com.example.agent.execution.TypeConversion
import com.example.agent.execution.ConversionResult
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmScenarioEnricher
import com.example.agent.generation.TestCasePostProcessor
import com.example.agent.generation.TestScenarioGenerator
import com.example.agent.llm.LlmConfig
import com.example.agent.llm.OpenAiCompatibleLlmClient
import com.example.agent.model.CodeStructure
import com.example.agent.model.FunctionInfo
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import com.example.agent.source.JavaSourceFile
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import java.nio.file.Paths

/**
 * Thin service layer shared between the CLI and the REST API, wiring
 * together source loading, analysis, scenario generation, real in-memory
 * execution (oracle-based test generation), code generation and artifact
 * storage.
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
        val files = loadFilesOrThrow(sourcePath)
        val structure = JavaCodeAnalyzer().analyze(sourcePath, files)

        val llmClient = OpenAiCompatibleLlmClient(LlmConfig.fromEnv())
        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),
            llmEnricher = LlmScenarioEnricher(llmClient),
            postProcessor = TestCasePostProcessor()
        )
        val testSuiteResult = generator.generateForStructure(structure)

        val outcomes = computeExecutionOutcomes(files, structure, testSuiteResult)

        var generatedTestFilePath: String? = null
        if (!outputDir.isNullOrBlank()) {
            val fileSpec = JUnit5TestCodeGenerator().generate(
                testSuiteResult,
                structure.packageName,
                structure.functions,
                outcomes
            )
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

    /**
     * Compiles [files] in memory and, for every [TestCase] whose parameter
     * values can all be resolved to real typed objects (i.e. not the
     * untyped `"llm-suggested"` placeholder used by [LlmScenarioEnricher],
     * and not any other unsupported type), actually executes it via
     * reflection, recording the real observed outcome.
     *
     * On any compilation failure, returns an empty map: the caller then
     * falls back to the always-safe TODO-comment style for every test case.
     * The temporary compilation directory is always cleaned up.
     */
    private fun computeExecutionOutcomes(
        files: List<JavaSourceFile>,
        structure: CodeStructure,
        testSuiteResult: TestSuiteResult
    ): Map<String, ExecutionOutcome> {
        val compilationResult = InMemoryJavaCompiler().compile(files)
        val success = compilationResult as? CompilationResult.Success ?: return emptyMap()

        try {
            val functionLookup = structure.functions.associateBy { it.className to it.name }
            val executor = TestCaseExecutor()

            return testSuiteResult.testCases.mapNotNull { testCase ->
                val function = functionLookup[testCase.className to testCase.functionName] ?: return@mapNotNull null
                if (!allParametersConvertible(function, testCase)) return@mapNotNull null

                val outcome = executor.execute(success.classLoader, structure.packageName, function, testCase)
                testCase.id to outcome
            }.toMap()
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }

    /**
     * Checks (without executing anything) whether every parameter value in
     * [testCase] can be converted to the real type declared by [function] -
     * this is what excludes LLM-suggested scenarios (whose `inputData`
     * values are the literal string `"llm-suggested"`, not real typed
     * values) from real execution, without depending on that literal string
     * by name; any value that fails real type conversion is simply skipped.
     */
    private fun allParametersConvertible(function: FunctionInfo, testCase: TestCase): Boolean {
        return function.parameters.all { parameter ->
            val rawValue = testCase.inputData[parameter.name]
            TypeConversion.convert(parameter.type, rawValue) is ConversionResult.Converted
        }
    }

    private fun loadFilesOrThrow(sourcePath: String) = try {
        LocalFileSourceLoader().load(sourcePath)
    } catch (e: IllegalArgumentException) {
        throw InvalidSourceException(e.message ?: "Invalid source path: $sourcePath")
    }
}
