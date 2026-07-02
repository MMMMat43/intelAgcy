package com.example.agent.e2e

import com.example.agent.analysis.JavaCodeAnalyzer
import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmScenarioEnricher
import com.example.agent.generation.TestCasePostProcessor
import com.example.agent.generation.TestScenarioGenerator
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.model.ScenarioType
import com.example.agent.source.LocalFileSourceLoader
import com.example.agent.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end verification of the full pipeline: source loading -> code
 * analysis -> test scenario generation -> JUnit5 code generation ->
 * artifact storage, run against two small, deliberately varied Java
 * samples (branches, loops, exceptions, numeric and String parameters).
 *
 * Runs in fallback mode without a real LLM API key/network access, matching
 * how the pipeline behaves in CI/local environments without LLM_API_KEY set
 * (see Task 04/05).
 */
class PipelineE2ETest {

    /** Deterministic stand-in for a network LLM: always fails, like the real
     *  client does when no API key is configured. Used to keep this test
     *  fast and offline, without depending on environment variables. */
    private class AlwaysFailingLlmClient : LlmClient {
        override fun complete(prompt: String): LlmResult = LlmResult.Failure("e2e test runs without a real LLM")
    }

    private val calculatorSource = """
        public class SampleCalculator {

            public int divide(int numerator, int denominator) {
                if (denominator == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                int result = 0;
                for (int i = 0; i < numerator; i++) {
                    if (i % denominator == 0) {
                        result++;
                    }
                }
                return result;
            }

            public boolean isPositive(int value) {
                if (value > 0) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    """.trimIndent()

    private val stringUtilsSource = """
        public class SampleStringUtils {

            public String normalize(String input) {
                if (input == null) {
                    throw new IllegalArgumentException("Input must not be null");
                }
                String trimmed = input.trim();
                if (trimmed.isEmpty()) {
                    return "";
                }
                return trimmed.toLowerCase();
            }

            public boolean containsDigit(String text) {
                for (int i = 0; i < text.length(); i++) {
                    if (Character.isDigit(text.charAt(i))) {
                        return true;
                    }
                }
                return false;
            }
        }
    """.trimIndent()

    @Test
    fun `runs the full pipeline end-to-end on two sample Java files`(@TempDir tempDir: Path) {
        val startTimeMillis = System.currentTimeMillis()

        // 1. Prepare sample sources and load them via SourceLoader.
        val sourcesDir = tempDir.resolve("sources")
        Files.createDirectories(sourcesDir)
        Files.writeString(sourcesDir.resolve("SampleCalculator.java"), calculatorSource)
        Files.writeString(sourcesDir.resolve("SampleStringUtils.java"), stringUtilsSource)

        val files = LocalFileSourceLoader().load(sourcesDir.toString())
        assertEquals(2, files.size, "Expected both sample Java files to be loaded")

        // 2. Analyze the code.
        val structure = JavaCodeAnalyzer().analyze(sourcesDir.toString(), files)
        assertEquals(4, structure.functions.size, "Expected 4 functions across both sample files")

        val averageCyclomaticComplexity = structure.functions.map { it.cyclomaticComplexity }.average()

        // 3. Generate test scenarios (fallback mode, no working LLM).
        val generator = TestScenarioGenerator(
            heuristicGenerator = HeuristicScenarioGenerator(),
            llmEnricher = LlmScenarioEnricher(AlwaysFailingLlmClient()),
            postProcessor = TestCasePostProcessor()
        )
        val testSuiteResult = generator.generateForStructure(structure)
        assertTrue(testSuiteResult.testCases.isNotEmpty(), "Expected non-empty test suite result")

        structure.functions.forEach { function ->
            val casesForFunction = testSuiteResult.testCases.filter { it.functionName == function.name }
            assertTrue(casesForFunction.isNotEmpty(), "Expected at least one scenario for function ${function.name}")
            assertTrue(
                casesForFunction.any { it.type == ScenarioType.POSITIVE },
                "Expected at least one POSITIVE scenario for function ${function.name}"
            )
        }
        // Numeric/String parameters guarantee BOUNDARY and (for String/reference) NEGATIVE scenarios;
        // verify at least one of each exists across the whole suite (not necessarily per function).
        assertTrue(testSuiteResult.testCases.any { it.type == ScenarioType.BOUNDARY }, "Expected BOUNDARY scenarios in the suite")
        assertTrue(testSuiteResult.testCases.any { it.type == ScenarioType.NEGATIVE }, "Expected NEGATIVE scenarios in the suite")

        val scenarioCounts = ScenarioType.entries.associateWith { type ->
            testSuiteResult.testCases.count { it.type == type }
        }

        // 4. Generate JUnit5 code and persist all artifacts.
        val fileSpec = JUnit5TestCodeGenerator().generate(testSuiteResult, "com.example.agent.generated")
        val outputDir = tempDir.resolve("output")
        val storage = ArtifactStorage(outputDir)
        storage.saveAnalysis(structure)
        storage.saveTestCases(testSuiteResult)
        storage.saveGeneratedTestCode(fileSpec)

        assertTrue(Files.exists(outputDir.resolve("analysis.json")), "Expected analysis.json to be written")
        assertTrue(Files.exists(outputDir.resolve("test-cases.json")), "Expected test-cases.json to be written")
        val generatedKtFiles = Files.walk(outputDir.resolve("generated-tests"))
            .filter { it.toString().endsWith(".kt") }
            .toList()
        assertTrue(generatedKtFiles.isNotEmpty(), "Expected at least one generated .kt test file")

        val elapsedMillis = System.currentTimeMillis() - startTimeMillis

        // Print metrics for manual inspection / RESULTS.md cross-check.
        println("=== Pipeline E2E metrics ===")
        println("Functions analyzed: ${structure.functions.size}")
        println("Average cyclomatic complexity: $averageCyclomaticComplexity")
        println("Total test cases generated: ${testSuiteResult.testCases.size}")
        println("Scenario type breakdown: $scenarioCounts")
        println("Pipeline execution time (ms): $elapsedMillis")
    }
}
