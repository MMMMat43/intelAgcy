package com.example.agent.e2e

import com.example.agent.api.PipelineService
import com.example.agent.model.ScenarioType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PipelineE2ETest {

    private fun generatedSource(outputDir: Path): String =
        Files.walk(outputDir.resolve("generated-tests"))
            .filter { it.toString().endsWith(".kt") }
            .toList()
            .joinToString("\n") { Files.readString(it) }

    @Test
    fun `runs the full pipeline on SampleCalculator and writes real assertions`(@TempDir tempDir: Path) {
        val outputDir = tempDir.resolve("out")

        val result = PipelineService().generateTests("src/test/resources/SampleCalculator.kt", outputDir.toString())

        assertEquals(2, result.functionsCount)
        assertTrue(result.testCasesCount > 0)
        assertTrue(result.executedTestCases > 0)
        assertTrue(Files.exists(outputDir.resolve("analysis.json")))
        assertTrue(Files.exists(outputDir.resolve("test-cases.json")))

        val code = generatedSource(outputDir)
        assertTrue(code.contains("Assertions.assertEquals(5, instance.divide(10, 2))") || code.contains("assertEquals("), code)
        assertTrue(code.contains("assertThrows(ArithmeticException::class.java)"), code)
        assertTrue(code.contains("SampleCalculator()"), code)
    }

    @Test
    fun `runs the full pipeline on the whole samples directory`(@TempDir tempDir: Path) {
        val outputDir = tempDir.resolve("out")

        val result = PipelineService().generateTests("src/test/resources", outputDir.toString())

        assertEquals(9, result.functionsCount)
        val suite = result.testSuiteResult
        listOf("SampleCalculator", "SampleStringUtils", "OrderProcessor").forEach { className ->
            assertTrue(suite.testCases.any { it.className == className }, "Expected scenarios for $className")
        }
        assertTrue(suite.testCases.any { it.type == ScenarioType.BOUNDARY })
        assertTrue(suite.testCases.any { it.type == ScenarioType.NEGATIVE })

        val code = generatedSource(outputDir)
        assertTrue(code.contains("assertThrows(IllegalArgumentException::class.java)"), code)
        assertTrue(code.contains("assertThrows(IllegalStateException::class.java)"), code)
        assertTrue(code.contains("OrderProcessor()"), code)
    }

    @Test
    fun `non-compilable input still yields scenarios with a warning`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("Broken.kt").toFile()
        source.writeText("class Broken { fun f(x: Int): Int = \"text\" }")

        val result = PipelineService().generateTests(source.path, tempDir.resolve("out").toString())

        assertTrue(result.testCasesCount > 0)
        assertEquals(0, result.executedTestCases)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `unsupported functions are reported as skipped`(@TempDir tempDir: Path) {
        val source = File(tempDir.toFile(), "Mixed.kt")
        source.writeText(
            """
            class Mixed {
                fun ok(x: Int): Int = x
                fun custom(list: List<Int>): Int = list.size
            }
            """.trimIndent()
        )

        val result = PipelineService().generateTests(source.path, null)

        assertTrue(result.skippedFunctions.any { it.contains("custom") })
        assertTrue(result.testSuiteResult.testCases.none { it.functionName == "custom" })
    }
}
