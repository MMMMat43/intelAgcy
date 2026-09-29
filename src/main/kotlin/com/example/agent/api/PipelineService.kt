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

class PipelineService {

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
