package com.example.agent.api

import com.example.agent.analysis.AnalysisResult
import com.example.agent.analysis.KotlinCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.execution.CompilationResult
import com.example.agent.execution.ConversionResult
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.KotlinInMemoryCompiler
import com.example.agent.execution.SourceUnit
import com.example.agent.execution.TestCaseExecutor
import com.example.agent.execution.TypeConversion
import com.example.agent.execution.dispose
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
import com.example.agent.model.isTestable
import com.example.agent.model.signature
import com.example.agent.source.KotlinSourceFile
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import java.nio.file.Paths

class PipelineService {

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
        val warnings: List<String> = emptyList()
    )

    fun generateTests(sourcePath: String, outputDir: String?): GenerateTestsResult {
        val files = loadFilesOrThrow(sourcePath)
        val analysis = KotlinCodeAnalyzer().analyzeDetailed(sourcePath, files)
        val structure = analysis.structure

        val llmClient = OpenAiCompatibleLlmClient(LlmConfig.fromEnv())
        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),
            llmEnricher = LlmScenarioEnricher(llmClient),
            postProcessor = TestCasePostProcessor()
        )
        val testSuiteResult = generator.generateForStructure(structure)

        val warnings = mutableListOf<String>()
        val outcomes = computeExecutionOutcomes(files, analysis, testSuiteResult, warnings)

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
            testSuiteResult = testSuiteResult,
            skippedFunctions = structure.functions
                .filter { !it.isTestable() }
                .map { "${it.className}.${it.name}: ${it.skipReason}" },
            executedTestCases = outcomes.values.count { it !is ExecutionOutcome.CouldNotExecute },
            warnings = warnings
        )
    }

    private fun computeExecutionOutcomes(
        files: List<KotlinSourceFile>,
        analysis: AnalysisResult,
        testSuiteResult: TestSuiteResult,
        warnings: MutableList<String>
    ): Map<String, ExecutionOutcome> {
        if (files.isEmpty()) return emptyMap()

        val compilation = KotlinInMemoryCompiler().compile(files.map { SourceUnit(it.fileName, it.content) })
        val success = when (compilation) {
            is CompilationResult.Success -> compilation
            is CompilationResult.Failure -> {
                warnings += "Компиляция не удалась, тесты сгенерированы без реальных проверок: " +
                    compilation.diagnostics.take(3).joinToString(" | ")
                return emptyMap()
            }
        }

        try {
            val functions = analysis.structure.functions
            val executor = TestCaseExecutor()

            return testSuiteResult.testCases.mapNotNull { testCase ->
                val function = findFunction(functions, testCase) ?: return@mapNotNull null
                if (!allParametersConvertible(function, testCase)) return@mapNotNull null
                testCase.id to executor.execute(success.classLoader, function, testCase)
            }.toMap()
        } finally {
            success.dispose()
        }
    }

    private fun findFunction(functions: List<FunctionInfo>, testCase: TestCase): FunctionInfo? {
        val candidates = functions.filter { it.className == testCase.className && it.name == testCase.functionName }
        if (testCase.signature.isNotEmpty()) {
            candidates.firstOrNull { it.signature() == testCase.signature }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun allParametersConvertible(function: FunctionInfo, testCase: TestCase): Boolean {
        return function.parameters.all { parameter ->
            TypeConversion.convert(parameter.type, parameter.nullable, testCase.inputData[parameter.name]) is ConversionResult.Converted
        }
    }

    private fun loadFilesOrThrow(sourcePath: String) = try {
        LocalFileSourceLoader().load(sourcePath)
    } catch (e: IllegalArgumentException) {
        throw InvalidSourceException(e.message ?: "Invalid source path: $sourcePath")
    }
}
