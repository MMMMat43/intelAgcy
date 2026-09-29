package com.example.agent.storage

import com.example.agent.codegen.JUnit5TestCodeGenerator
import com.example.agent.model.CodeStructure
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArtifactStorageTest {

    @Test
    fun `saves all three artifacts and round-trips JSON without data loss`(@TempDir tempDir: Path) {
        val structure = CodeStructure(
            sourcePath = "/tmp/MathUtils.kt",
            language = "kotlin",
            functions = listOf(
                FunctionInfo(
                    name = "add",
                    className = "MathUtils",
                    parameters = emptyList(),
                    returnType = "Int",
                    branches = emptyList(),
                    loops = emptyList(),
                    exceptions = emptyList(),
                    cyclomaticComplexity = 1
                )
            )
        )
        val testSuite = TestSuiteResult(
            sourcePath = "/tmp/MathUtils.kt",
            testCases = listOf(
                TestCase(
                    id = "add-1",
                    functionName = "add",
                    className = "MathUtils",
                    type = ScenarioType.POSITIVE,
                    description = "Positive scenario",
                    inputData = mapOf("a" to "1", "b" to null),
                    expectedResult = "3",
                    steps = listOf("step1", "step2")
                )
            )
        )
        val fileSpec = JUnit5TestCodeGenerator().generate(testSuite, "com.example.generated")

        val storage = ArtifactStorage(tempDir)
        storage.saveAnalysis(structure)
        storage.saveTestCases(testSuite)
        storage.saveGeneratedTestCode(fileSpec)

        assertTrue(Files.exists(tempDir.resolve("analysis.json")), "Expected analysis.json to exist")
        assertTrue(Files.exists(tempDir.resolve("test-cases.json")), "Expected test-cases.json to exist")

        val generatedTestsDir = tempDir.resolve("generated-tests")
        val ktFiles = Files.walk(generatedTestsDir)
            .filter { it.toString().endsWith(".kt") }
            .toList()
        assertTrue(ktFiles.isNotEmpty(), "Expected at least one generated .kt file under generated-tests/")

        val loadedStructure = storage.loadAnalysis()
        assertEquals(structure, loadedStructure, "Expected analysis.json to round-trip without data loss")

        val loadedTestSuite = storage.loadTestCases()
        assertEquals(testSuite, loadedTestSuite, "Expected test-cases.json to round-trip without data loss")
    }
}
