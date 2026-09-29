package com.example.agent.codegen

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.model.FunctionInfo
import com.example.agent.model.FunctionKind
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JUnit5TestCodeGeneratorTest {

    private fun function(
        name: String = "divide",
        className: String = "SampleCalculator",
        kind: String = FunctionKind.MEMBER,
        packageName: String = "",
        parameters: List<ParameterInfo> = listOf(ParameterInfo("numerator", "Int"), ParameterInfo("denominator", "Int")),
        returnType: String = "Int"
    ) = FunctionInfo(
        name = name,
        className = className,
        parameters = parameters,
        returnType = returnType,
        branches = emptyList(),
        loops = emptyList(),
        exceptions = emptyList(),
        cyclomaticComplexity = 1,
        kind = kind,
        packageName = packageName
    )

    private fun case(
        id: String,
        function: FunctionInfo,
        type: ScenarioType = ScenarioType.POSITIVE,
        vararg input: Pair<String, String?>
    ) = TestCase(
        id = id,
        functionName = function.name,
        className = function.className,
        type = type,
        description = "d",
        inputData = mapOf(*input),
        expectedResult = null,
        steps = listOf("step")
    )

    private fun generate(function: FunctionInfo, testCase: TestCase, outcome: ExecutionOutcome?): String {
        val suite = TestSuiteResult("/tmp/x.kt", listOf(testCase))
        val outcomes = if (outcome == null) emptyMap() else mapOf(testCase.id to outcome)
        return JUnit5TestCodeGenerator().generate(suite, function.packageName, listOf(function), outcomes).toString()
    }

    @Test
    fun `generates one Test method per scenario without outcomes`() {
        val f = function()
        val cases = listOf(
            case("a", f, ScenarioType.POSITIVE, "numerator" to "1", "denominator" to "2"),
            case("b", f, ScenarioType.BOUNDARY, "numerator" to "1", "denominator" to "2"),
            case("c", f, ScenarioType.NEGATIVE, "numerator" to "1", "denominator" to "2")
        )
        val code = JUnit5TestCodeGenerator().generate(TestSuiteResult("/tmp/x.kt", cases), "").toString()

        assertTrue(code.contains("import org.junit.jupiter.api.Test"))
        assertTrue(code.contains("class SampleCalculatorTest"))
        assertEquals3(Regex("@Test").findAll(code).count())
    }

    private fun assertEquals3(actual: Int) = assertTrue(actual == 3, "Expected 3 @Test methods, found $actual")

    @Test
    fun `member function with returned value generates assertEquals on a new instance`() {
        val f = function()
        val code = generate(f, case("d1", f, ScenarioType.POSITIVE, "numerator" to "10", "denominator" to "2"), ExecutionOutcome.ReturnedValue(5))

        assertTrue(code.contains("val instance = SampleCalculator()"), code)
        assertTrue(code.contains("assertEquals(5, instance.divide(10, 2))"), code)
        assertFalse(code.contains("TODO"), code)
    }

    @Test
    fun `thrown exception generates assertThrows`() {
        val f = function()
        val code = generate(
            f,
            case("d2", f, ScenarioType.NEGATIVE, "numerator" to "10", "denominator" to "0"),
            ExecutionOutcome.ThrewException("java.lang.ArithmeticException")
        )

        assertTrue(code.contains("assertThrows(ArithmeticException::class.java)"), code)
        assertTrue(code.contains("instance.divide(10, 0)"), code)
    }

    @Test
    fun `object member is called through the object name`() {
        val f = function(name = "twice", className = "Single", kind = FunctionKind.OBJECT_MEMBER, packageName = "demo", parameters = listOf(ParameterInfo("x", "Int")))
        val code = generate(f, case("o1", f, ScenarioType.POSITIVE, "x" to "4"), ExecutionOutcome.ReturnedValue(8))

        assertTrue(code.contains("assertEquals(8, Single.twice(4))"), code)
        assertFalse(code.contains("val instance"), code)
    }

    @Test
    fun `top level function is called directly and imported`() {
        val f = function(name = "topLevel", className = "DemoKt", kind = FunctionKind.TOP_LEVEL, packageName = "demo.pkg", parameters = listOf(ParameterInfo("x", "Int")))
        val code = generate(f, case("t1", f, ScenarioType.POSITIVE, "x" to "4"), ExecutionOutcome.ReturnedValue(5))

        assertTrue(code.contains("package demo.pkg"), code)
        assertTrue(code.contains("assertEquals(5, topLevel(4))"), code)
    }

    @Test
    fun `nullable string and char arguments are rendered as literals`() {
        val f = function(
            name = "mix",
            parameters = listOf(ParameterInfo("s", "String", nullable = true), ParameterInfo("c", "Char"), ParameterInfo("d", "Double")),
            returnType = "String"
        )
        val code = generate(
            f,
            case("m1", f, ScenarioType.BOUNDARY, "s" to null, "c" to "a", "d" to "1.5"),
            ExecutionOutcome.ReturnedValue("ok")
        )

        assertTrue(code.contains("instance.mix(null, 'a', 1.5)"), code)
        assertTrue(code.contains("assertEquals(\"ok\""), code)
    }

    @Test
    fun `Int MIN_VALUE is rendered as a compilable expression`() {
        val f = function(name = "id", parameters = listOf(ParameterInfo("x", "Int")))
        val code = generate(f, case("i1", f, ScenarioType.BOUNDARY, "x" to Int.MIN_VALUE.toString()), ExecutionOutcome.ReturnedValue(0))

        assertTrue(code.contains("Int.MIN_VALUE"), code)
    }

    @Test
    fun `CouldNotExecute falls back to the TODO comment style`() {
        val f = function()
        val code = generate(
            f,
            case("d3", f, ScenarioType.NEGATIVE, "numerator" to "10", "denominator" to "2"),
            ExecutionOutcome.CouldNotExecute("unsupported")
        )

        assertTrue(code.contains("TODO"), code)
    }

    @Test
    fun `missing outcome falls back to the TODO comment style`() {
        val f = function()
        val code = generate(f, case("d4", f, ScenarioType.POSITIVE, "numerator" to "10", "denominator" to "2"), null)

        assertTrue(code.contains("TODO"), code)
    }
}
