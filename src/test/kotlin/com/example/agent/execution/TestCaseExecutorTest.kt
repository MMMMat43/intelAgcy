package com.example.agent.execution

import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.source.JavaSourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TestCaseExecutorTest {

    private val calculatorSource = Files.readString(Path.of("src", "test", "resources", "SampleCalculator.java"))

    private val divideFunction = FunctionInfo(
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

    @Test
    fun `positive scenario divide(10,2) returns 5`() {
        val compilation = InMemoryJavaCompiler().compile(listOf(JavaSourceFile("SampleCalculator.java", calculatorSource)))
        val success = compilation as CompilationResult.Success
        try {
            val testCase = TestCase(
                id = "divide-positive",
                functionName = "divide",
                className = "SampleCalculator",
                type = ScenarioType.POSITIVE,
                description = "positive",
                inputData = mapOf("numerator" to "10", "denominator" to "2"),
                expectedResult = null,
                steps = emptyList()
            )

            val outcome = TestCaseExecutor().execute(success.classLoader, "", divideFunction, testCase)

            assertTrue(outcome is ExecutionOutcome.ReturnedValue, "Expected ReturnedValue, got: $outcome")
            assertEquals(5, (outcome as ExecutionOutcome.ReturnedValue).value)
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `division by zero throws ArithmeticException`() {
        val compilation = InMemoryJavaCompiler().compile(listOf(JavaSourceFile("SampleCalculator.java", calculatorSource)))
        val success = compilation as CompilationResult.Success
        try {
            val testCase = TestCase(
                id = "divide-by-zero",
                functionName = "divide",
                className = "SampleCalculator",
                type = ScenarioType.NEGATIVE,
                description = "division by zero",
                inputData = mapOf("numerator" to "10", "denominator" to "0"),
                expectedResult = null,
                steps = emptyList()
            )

            val outcome = TestCaseExecutor().execute(success.classLoader, "", divideFunction, testCase)

            assertTrue(outcome is ExecutionOutcome.ThrewException, "Expected ThrewException, got: $outcome")
            assertEquals("java.lang.ArithmeticException", (outcome as ExecutionOutcome.ThrewException).exceptionClassName)
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `unsupported parameter type results in CouldNotExecute`() {
        val compilation = InMemoryJavaCompiler().compile(listOf(JavaSourceFile("SampleCalculator.java", calculatorSource)))
        val success = compilation as CompilationResult.Success
        try {
            val functionWithUnsupportedParam = divideFunction.copy(
                parameters = listOf(ParameterInfo("numerator", "CustomType"), ParameterInfo("denominator", "int"))
            )
            val testCase = TestCase(
                id = "divide-unsupported",
                functionName = "divide",
                className = "SampleCalculator",
                type = ScenarioType.POSITIVE,
                description = "unsupported type",
                inputData = mapOf("numerator" to "10", "denominator" to "2"),
                expectedResult = null,
                steps = emptyList()
            )

            val outcome = TestCaseExecutor().execute(success.classLoader, "", functionWithUnsupportedParam, testCase)

            assertTrue(outcome is ExecutionOutcome.CouldNotExecute, "Expected CouldNotExecute, got: $outcome")
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }
}
