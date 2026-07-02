package com.example.agent.codegen

import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JUnit5TestCodeGeneratorTest {

    @Test
    fun `generates a compilable-looking FileSpec with expected number of Test methods`() {
        val testCases = listOf(
            TestCase(
                id = "add-1",
                functionName = "add",
                className = "MathUtils",
                type = ScenarioType.POSITIVE,
                description = "Positive scenario for add",
                inputData = mapOf("a" to "1", "b" to "2"),
                expectedResult = "3",
                steps = listOf("call add(1, 2)", "assert result is 3")
            ),
            TestCase(
                id = "add-2",
                functionName = "add",
                className = "MathUtils",
                type = ScenarioType.BOUNDARY,
                description = "Boundary scenario for add",
                inputData = mapOf("a" to Int.MAX_VALUE.toString(), "b" to "1"),
                expectedResult = "overflow",
                steps = listOf("call add(MAX_VALUE, 1)")
            ),
            TestCase(
                id = "add-3",
                functionName = "add",
                className = "MathUtils",
                type = ScenarioType.NEGATIVE,
                description = "Negative scenario for add",
                inputData = mapOf("a" to "null", "b" to "1"),
                expectedResult = "exception",
                steps = listOf("call add(null, 1)")
            )
        )
        val testSuite = TestSuiteResult(sourcePath = "/tmp/MathUtils.java", testCases = testCases)

        val fileSpec = JUnit5TestCodeGenerator().generate(testSuite, "com.example.generated")
        val generatedCode = fileSpec.toString()

        assertTrue(generatedCode.contains("import org.junit.jupiter.api.Test"), "Expected JUnit5 Test import")
        assertTrue(generatedCode.contains("class MathUtilsTest"), "Expected generated test class")

        val testMethodCount = Regex("@Test").findAll(generatedCode).count()
        assertTrue(testMethodCount == 3, "Expected 3 @Test methods, found $testMethodCount")
    }
}
