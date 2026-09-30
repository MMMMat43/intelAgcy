package com.example.agent.e2e

import com.example.agent.analysis.KotlinCodeAnalyzer
import com.example.agent.api.PipelineService
import com.example.agent.coverage.CoverageGuidedGenerator
import com.example.agent.coverage.CoverageTestSupport
import com.example.agent.coverage.NO_BRANCHES_WARNING
import com.example.agent.source.KotlinSourceFile
import com.example.agent.source.LocalFileSourceLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertNotNull
import java.nio.file.Files
import java.nio.file.Path

class InputRobustnessTest {

    private val bom = "\uFEFF"

    private val source = """
        class Sample {
            fun clamp(x: Int): Int {
                if (x > 10) return 10
                return x
            }
        }
    """.trimIndent()

    private fun pipeline() = PipelineService()

    @Test
    fun `loader strips byte order mark`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("Sample.kt")
        Files.write(file, (bom + source).toByteArray(Charsets.UTF_8))

        val loaded = LocalFileSourceLoader().load(file.toString()).single()

        assertFalse(loaded.content.startsWith(bom))
        assertEquals(source, loaded.content)
    }

    @Test
    fun `analyzer finds the same functions with and without byte order mark`() {
        val plain = KotlinCodeAnalyzer().analyze("Sample.kt", listOf(KotlinSourceFile("Sample.kt", source)))
        val marked = KotlinCodeAnalyzer().analyze("Sample.kt", listOf(KotlinSourceFile("Sample.kt", bom + source)))

        assertEquals(1, plain.functions.size)
        assertEquals(plain.functions.map { it.name }, marked.functions.map { it.name })
    }

    @Test
    fun `pipeline gives identical results for files with and without byte order mark`(@TempDir tempDir: Path) {
        val plain = tempDir.resolve("Plain.kt")
        val marked = tempDir.resolve("Marked.kt")
        Files.writeString(plain, source)
        Files.write(marked, (bom + source).toByteArray(Charsets.UTF_8))

        val a = pipeline().generateTests(plain.toString(), null)
        val b = pipeline().generateTests(marked.toString(), null)

        assertTrue(a.functionsCount > 0)
        assertEquals(a.functionsCount, b.functionsCount)
        assertEquals(a.testCasesCount, b.testCasesCount)
        assertEquals(a.coverage?.coveredBranches, b.coverage?.coveredBranches)
    }

    @Test
    fun `syntax error is reported with file name and line and nothing is executed`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("Broken.kt")
        Files.writeString(file, "class Broken {\n    fun a(x: Int): Int {\n        if (x > 0 { return 1 }\n        return 2\n    }\n}\n")

        val result = pipeline().generateTests(file.toString(), tempDir.resolve("out").toString())

        assertEquals(0, result.functionsCount)
        assertEquals(0, result.testCasesCount)
        assertEquals(0, result.executedTestCases)
        val message = result.warnings.firstOrNull { it.contains("Broken.kt") }
        assertNotNull(message, result.warnings.toString())
        assertTrue(message.contains("строке 3"), message)
        assertNull(result.coverage?.branchCoverage)
    }

    @Test
    fun `broken file does not prevent analysis of healthy files in the same directory`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("Broken.kt"), "class Broken { fun a( : }")
        Files.writeString(tempDir.resolve("Good.kt"), source)

        val result = pipeline().generateTests(tempDir.toString(), null)

        assertEquals(1, result.functionsCount)
        assertTrue(result.warnings.any { it.contains("Broken.kt") })
        assertNotNull(result.coverage?.branchCoverage)
    }

    @Test
    fun `empty file gives no coverage figure and an explicit warning`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("Empty.kt")
        Files.writeString(file, "")

        val result = pipeline().generateTests(file.toString(), tempDir.resolve("out").toString())

        assertEquals(0, result.functionsCount)
        assertNull(result.coverage?.branchCoverage)
        assertFalse(result.coverage?.measured ?: true)
        assertTrue(result.warnings.contains(NO_BRANCHES_WARNING), result.warnings.toString())
        val json = Files.readString(tempDir.resolve("out").resolve("coverage.json"))
        assertTrue(json.contains("\"measured\" : false"), json)
    }

    @Test
    fun `file without branches reports coverage as not computed instead of one hundred percent`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Plain {
                fun add(a: Int, b: Int): Int = a + b
            }
            """
        )

        val result = CoverageGuidedGenerator(valueProvider = null).generate(analysis)

        assertNull(result.report?.branchCoverage)
        assertFalse(result.measured)
        assertTrue(result.warnings.contains(NO_BRANCHES_WARNING))
        assertTrue(assertNotNull(result.suite).testCases.isNotEmpty())
    }

    @Test
    fun `file with only skipped functions reports coverage as not computed`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Only {
                suspend fun a(x: Int): Int = x
                fun <T> id(v: T): T = v
            }
            """
        )

        val result = CoverageGuidedGenerator(valueProvider = null).generate(analysis)

        assertNull(result.report?.branchCoverage)
        assertTrue(result.warnings.contains(NO_BRANCHES_WARNING))
    }

    @Test
    fun `character predicates in loops give the search a string containing them`() {
        val analysis = CoverageTestSupport.analysisOf("SampleStringUtils.kt")

        val result = CoverageGuidedGenerator(valueProvider = null).generate(analysis)

        val report = assertNotNull(result.report)
        val containsDigit = report.functions.first { it.functionName == "containsDigit" }
        assertEquals(containsDigit.totalBranches, containsDigit.coveredBranches, containsDigit.notFoundWithinBudget.toString())
    }
}
