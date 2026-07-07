package com.example.agent.codegen

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
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

    @Test
    fun `ReturnedValue outcome generates a real assertEquals call`() {
        val testCase = TestCase(
            id = "divide-1",
            functionName = "divide",
            className = "SampleCalculator",
            type = ScenarioType.POSITIVE,
            description = "positive",
            inputData = mapOf("numerator" to "10", "denominator" to "2"),
            expectedResult = null,
            steps = emptyList()
        )
        val testSuite = TestSuiteResult(sourcePath = "/tmp/SampleCalculator.java", testCases = listOf(testCase))
        val function = FunctionInfo(
            name = "divide",
            className = "SampleCalculator",
            parameters = listOf(ParameterInfo("numerator", "int"), ParameterInfo("denominator", "int")),
            returnType = "int",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 4,
            isStatic = false
        )
        val outcomes = mapOf("divide-1" to ExecutionOutcome.ReturnedValue(5))

        val generatedCode = JUnit5TestCodeGenerator()
            .generate(testSuite, "", listOf(function), outcomes)
            .toString()

        assertTrue(generatedCode.contains("assertEquals"), "Expected a real assertEquals call:\n$generatedCode")
        assertTrue(generatedCode.contains("5"), "Expected the literal return value 5 in generated code:\n$generatedCode")
        assertTrue(!generatedCode.contains("TODO"), "Did not expect a TODO placeholder:\n$generatedCode")
    }

    @Test
    fun `ThrewException outcome generates a real assertThrows call`() {
        val testCase = TestCase(
            id = "divide-2",
            functionName = "divide",
            className = "SampleCalculator",
            type = ScenarioType.NEGATIVE,
            description = "division by zero",
            inputData = mapOf("numerator" to "10", "denominator" to "0"),
            expectedResult = null,
            steps = emptyList()
        )
        val testSuite = TestSuiteResult(sourcePath = "/tmp/SampleCalculator.java", testCases = listOf(testCase))
        val function = FunctionInfo(
            name = "divide",
            className = "SampleCalculator",
            parameters = listOf(ParameterInfo("numerator", "int"), ParameterInfo("denominator", "int")),
            returnType = "int",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 4,
            isStatic = false
        )
        val outcomes = mapOf("divide-2" to ExecutionOutcome.ThrewException("java.lang.ArithmeticException"))

        val generatedCode = JUnit5TestCodeGenerator()
            .generate(testSuite, "", listOf(function), outcomes)
            .toString()

        assertTrue(generatedCode.contains("assertThrows"), "Expected a real assertThrows call:\n$generatedCode")
        assertTrue(
            generatedCode.contains("ArithmeticException"),
            "Expected ArithmeticException reference in generated code:\n$generatedCode"
        )
    }

    @Test
    fun `CouldNotExecute outcome falls back to TODO-comment style`() {
        val testCase = TestCase(
            id = "divide-3",
            functionName = "divide",
            className = "SampleCalculator",
            type = ScenarioType.NEGATIVE,
            description = "unsupported",
            inputData = mapOf("numerator" to "10", "denominator" to "2"),
            expectedResult = "n/a",
            steps = listOf("step")
        )
        val testSuite = TestSuiteResult(sourcePath = "/tmp/SampleCalculator.java", testCases = listOf(testCase))
        val function = FunctionInfo(
            name = "divide",
            className = "SampleCalculator",
            parameters = listOf(ParameterInfo("numerator", "int"), ParameterInfo("denominator", "int")),
            returnType = "int",
            branches = emptyList(),
            loops = emptyList(),
            exceptions = emptyList(),
            cyclomaticComplexity = 4,
            isStatic = false
        )
        val outcomes = mapOf("divide-3" to ExecutionOutcome.CouldNotExecute("unsupported") as ExecutionOutcome)

        val generatedCode = JUnit5TestCodeGenerator()
            .generate(testSuite, "", listOf(function), outcomes)
            .toString()

        assertTrue(generatedCode.contains("TODO"), "Expected TODO fallback:\n$generatedCode")
    }

    @Test
    fun `missing outcome falls back to TODO-comment style as before`() {
        val testCase = TestCase(
            id = "divide-4",
            functionName = "divide",
            className = "SampleCalculator",
            type = ScenarioType.POSITIVE,
            description = "no outcome supplied",
            inputData = mapOf("numerator" to "10", "denominator" to "2"),
            expectedResult = "n/a",
            steps = listOf("step")
        )
        val testSuite = TestSuiteResult(sourcePath = "/tmp/SampleCalculator.java", testCases = listOf(testCase))

        val generatedCode = JUnit5TestCodeGenerator().generate(testSuite, "").toString()

        assertTrue(generatedCode.contains("TODO"), "Expected TODO fallback:\n$generatedCode")
    }
}
