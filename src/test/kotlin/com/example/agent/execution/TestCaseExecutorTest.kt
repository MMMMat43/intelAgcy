package com.example.agent.execution

import com.example.agent.model.FunctionInfo
import com.example.agent.model.FunctionKind
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCaseExecutorTest {

    private val source = """
        package demo

        class Calc {
            fun divide(a: Int, b: Int): Int {
                if (b == 0) throw ArithmeticException("zero")
                return a / b
            }
            fun name(value: String?): String = value ?: "none"
            fun custom(list: List<Int>): Int = list.size
        }

        object Single {
            fun twice(x: Int): Int = x * 2
        }

        class Holder {
            companion object {
                fun triple(x: Int): Int = x * 3
            }
        }

        fun topLevel(x: Int): Int = x + 1
    """.trimIndent()

    private val compiled = KotlinInMemoryCompiler().compile(listOf(SourceUnit("Demo.kt", source))) as CompilationResult.Success
    private val executor = TestCaseExecutor()

    @AfterAll
    fun cleanup() {
        compiled.dispose()
    }

    private fun function(
        name: String,
        className: String,
        kind: String,
        parameters: List<ParameterInfo>
    ) = FunctionInfo(
        name = name,
        className = className,
        parameters = parameters,
        returnType = "Int",
        branches = emptyList(),
        loops = emptyList(),
        exceptions = emptyList(),
        cyclomaticComplexity = 1,
        kind = kind,
        packageName = "demo"
    )

    private fun case(vararg input: Pair<String, String?>) = TestCase(
        id = "t",
        functionName = "f",
        className = "C",
        type = ScenarioType.POSITIVE,
        description = "d",
        inputData = mapOf(*input),
        expectedResult = null,
        steps = emptyList()
    )

    private val intParams = listOf(ParameterInfo("a", "Int"), ParameterInfo("b", "Int"))

    @Test
    fun `member function returns the real value`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("divide", "Calc", FunctionKind.MEMBER, intParams),
            case("a" to "10", "b" to "2")
        )

        assertEquals(ExecutionOutcome.ReturnedValue(5), outcome)
    }

    @Test
    fun `member function records the thrown exception type`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("divide", "Calc", FunctionKind.MEMBER, intParams),
            case("a" to "10", "b" to "0")
        )

        assertEquals(ExecutionOutcome.ThrewException("java.lang.ArithmeticException"), outcome)
    }

    @Test
    fun `object member is invoked through INSTANCE`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("twice", "Single", FunctionKind.OBJECT_MEMBER, listOf(ParameterInfo("x", "Int"))),
            case("x" to "4")
        )

        assertEquals(ExecutionOutcome.ReturnedValue(8), outcome)
    }

    @Test
    fun `companion member is invoked through Companion`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("triple", "Holder", FunctionKind.COMPANION_MEMBER, listOf(ParameterInfo("x", "Int"))),
            case("x" to "4")
        )

        assertEquals(ExecutionOutcome.ReturnedValue(12), outcome)
    }

    @Test
    fun `top level function is invoked as a static method of the file class`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("topLevel", "DemoKt", FunctionKind.TOP_LEVEL, listOf(ParameterInfo("x", "Int"))),
            case("x" to "4")
        )

        assertEquals(ExecutionOutcome.ReturnedValue(5), outcome)
    }

    @Test
    fun `nullable parameter accepts null`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("name", "Calc", FunctionKind.MEMBER, listOf(ParameterInfo("value", "String", nullable = true))),
            case("value" to null)
        )

        assertEquals(ExecutionOutcome.ReturnedValue("none"), outcome)
    }

    @Test
    fun `unsupported parameter type yields CouldNotExecute`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("custom", "Calc", FunctionKind.MEMBER, listOf(ParameterInfo("list", "List<Int>"))),
            case("list" to "validInstance")
        )

        assertTrue(outcome is ExecutionOutcome.CouldNotExecute)
    }

    @Test
    fun `unknown class yields CouldNotExecute`() {
        val outcome = executor.execute(
            compiled.classLoader,
            function("divide", "Missing", FunctionKind.MEMBER, intParams),
            case("a" to "1", "b" to "1")
        )

        assertTrue(outcome is ExecutionOutcome.CouldNotExecute)
    }
}
