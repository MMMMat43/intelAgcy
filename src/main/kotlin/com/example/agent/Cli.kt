package com.example.agent

import com.example.agent.analysis.JavaCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmScenarioEnricher
import com.example.agent.generation.TestCasePostProcessor
import com.example.agent.generation.TestScenarioGenerator
import com.example.agent.llm.LlmConfig
import com.example.agent.llm.OpenAiCompatibleLlmClient
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Paths

/**
 * Точка входа CLI интеллектуального агента.
 *
 * Поддерживаемые флаги:
 * - `--source <path>` — загружает Java-исходники (файл или директория) через
 *   [LocalFileSourceLoader] и анализирует их через [JavaCodeAnalyzer], печатая
 *   структуру кода (функции, ветвления, циклы, исключения, цикломатическую
 *   сложность) в виде JSON.
 * - `--generate-tests` — дополнительно к анализу генерирует тестовые сценарии
 *   через [TestScenarioGenerator] (эвристики + опциональное обогащение LLM,
 *   зависящее от переменных окружения `LLM_API_BASE_URL`/`LLM_API_KEY`/`LLM_MODEL`)
 *   и печатает результат ([com.example.agent.model.TestSuiteResult]) в виде JSON.
 * - `--output <dir>` — при указании дополнительно сохраняет все артефакты пайплайна
 *   (анализ, JSON тест-кейсов и, если указан --generate-tests, сгенерированный
 *   Kotlin/JUnit5 код) через [ArtifactStorage] и [JUnit5TestCodeGenerator].
 */
private val jsonMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .enable(SerializationFeature.INDENT_OUTPUT)

private const val GENERATED_TESTS_PACKAGE = "com.example.agent.generated"

fun main(args: Array<String>) {
    val source = parseSourceArgument(args)
    if (source == null) {
        println("Usage: --source <path> [--generate-tests] [--output <dir>]")
        return
    }

    val files = try {
        LocalFileSourceLoader().load(source)
    } catch (e: IllegalArgumentException) {
        println("Source: $source")
        println("Error: ${e.message}")
        return
    }

    println("Source: $source")
    println("Found ${files.size} Java source file(s)")

    val structure = JavaCodeAnalyzer().analyze(source, files)
    val outputDir = parseOutputArgument(args)

    if (hasGenerateTestsFlag(args)) {
        val llmClient = OpenAiCompatibleLlmClient(LlmConfig.fromEnv())
        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),
            llmEnricher = LlmScenarioEnricher(llmClient),
            postProcessor = TestCasePostProcessor()
        )
        val testSuiteResult = generator.generateForStructure(structure)
        println(jsonMapper.writeValueAsString(testSuiteResult))

        if (outputDir != null) {
            val fileSpec = JUnit5TestCodeGenerator().generate(testSuiteResult, GENERATED_TESTS_PACKAGE)
            val storage = ArtifactStorage(Paths.get(outputDir))
            storage.saveAnalysis(structure)
            storage.saveTestCases(testSuiteResult)
            storage.saveGeneratedTestCode(fileSpec)
            println("Artifacts saved to: $outputDir")
        }
    } else {
        println(jsonMapper.writeValueAsString(structure))

        if (outputDir != null) {
            val storage = ArtifactStorage(Paths.get(outputDir))
            storage.saveAnalysis(structure)
            println("Analysis artifact saved to: $outputDir")
        }
    }
}

internal fun parseSourceArgument(args: Array<String>): String? {
    val index = args.indexOf("--source")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}

internal fun hasGenerateTestsFlag(args: Array<String>): Boolean {
    return args.contains("--generate-tests")
}

internal fun parseOutputArgument(args: Array<String>): String? {
    val index = args.indexOf("--output")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}
