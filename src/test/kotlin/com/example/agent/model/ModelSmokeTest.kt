package com.example.agent.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Smoke-тест, проверяющий, что доменная модель компилируется и её поля
 * корректно доступны после создания экземпляров.
 */
class ModelSmokeTest {

    @Test
    fun `CodeStructure and TestCase can be created and fields are accessible`() {
        val parameter = ParameterInfo(name = "value", type = "int")

        val branch = BranchInfo(kind = "if", condition = "value > 0", lineNumber = 10)
        val loop = LoopInfo(kind = "for", condition = "i < value", lineNumber = 12)
        val exception = ExceptionInfo(exceptionType = "IllegalArgumentException", context = "thrown", lineNumber = 15)

        val function = FunctionInfo(
            name = "process",
            className = "Calculator",
            parameters = listOf(parameter),
            returnType = "int",
            branches = listOf(branch),
            loops = listOf(loop),
            exceptions = listOf(exception),
            cyclomaticComplexity = 3
        )

        val codeStructure = CodeStructure(
            sourcePath = "src/main/java/Calculator.java",
            language = "java",
            functions = listOf(function)
        )

        val testCase = TestCase(
            id = "process-1",
            functionName = "process",
            className = "Calculator",
            type = ScenarioType.BOUNDARY,
            description = "Boundary value for parameter 'value'",
            inputData = mapOf("value" to "0"),
            expectedResult = null,
            steps = listOf("Call process(0)", "Verify no exception is thrown")
        )

        val testSuiteResult = TestSuiteResult(
            sourcePath = codeStructure.sourcePath,
            testCases = listOf(testCase)
        )

        assertEquals("java", codeStructure.language)
        assertEquals(1, codeStructure.functions.size)
        assertEquals("process", codeStructure.functions.first().name)
        assertEquals(3, codeStructure.functions.first().cyclomaticComplexity)

        assertEquals(1, testSuiteResult.testCases.size)
        assertEquals(ScenarioType.BOUNDARY, testSuiteResult.testCases.first().type)
        assertEquals("0", testSuiteResult.testCases.first().inputData["value"])
    }
}
