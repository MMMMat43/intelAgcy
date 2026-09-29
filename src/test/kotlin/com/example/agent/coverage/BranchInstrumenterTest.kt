package com.example.agent.coverage

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.signature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BranchInstrumenterTest {

    private fun caseOf(function: com.example.agent.model.FunctionInfo, vararg values: Pair<String, String?>) = TestCase(
        id = "t",
        functionName = function.name,
        className = function.className,
        type = ScenarioType.POSITIVE,
        description = "",
        inputData = mapOf(*values),
        expectedResult = null,
        steps = emptyList(),
        signature = function.signature()
    )

    @Test
    fun `counts branches of SampleCalculator and records hits at runtime`() {
        val analysis = CoverageTestSupport.analysisOf("SampleCalculator.kt")
        val creation = InstrumentedRunner.create(analysis)
        assertTrue(creation is RunnerCreation.Ready, creation.toString())
        (creation as RunnerCreation.Ready).runner.use { runner ->
            val divide = analysis.functions.first { it.info.name == "divide" }.info
            val probes = runner.probes.filter { it.functionKey.contains("divide") }
            assertEquals(5, probes.size, probes.joinToString { it.id })

            val result = runner.run(divide, caseOf(divide, "numerator" to "4", "denominator" to "2"))
            assertTrue(result.outcome is ExecutionOutcome.ReturnedValue)
            val hitKinds = probes.filter { it.index in result.covered }.map { it.kind }.toSet()
            assertTrue("for" in hitKinds, hitKinds.toString())
            assertTrue("if-else" in hitKinds, hitKinds.toString())
            assertFalse(probes.first { it.label.contains("denominator == 0") && it.kind == "if-then" }.index in result.covered)

            val thrown = runner.run(divide, caseOf(divide, "numerator" to "4", "denominator" to "0"))
            assertTrue(thrown.outcome is ExecutionOutcome.ThrewException)
            assertTrue(probes.first { it.label.contains("denominator == 0") && it.kind == "if-then" }.index in thrown.covered)
        }
    }

    @Test
    fun `instruments expression bodied branches and when entries without breaking compilation`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Shapes {
                fun kind(sides: Int): String = when (sides) {
                    3 -> "triangle"
                    4 -> "square"
                    else -> "other"
                }

                fun sign(x: Int): Int = if (x > 0) 1 else if (x < 0) -1 else 0
            }
            """
        )
        val creation = InstrumentedRunner.create(analysis)
        assertTrue(creation is RunnerCreation.Ready, creation.toString())
        (creation as RunnerCreation.Ready).runner.use { runner ->
            val kind = analysis.functions.first { it.info.name == "kind" }.info
            val sign = analysis.functions.first { it.info.name == "sign" }.info
            val whenProbes = runner.probes.filter { it.functionKey.contains("kind") }
            assertEquals(3, whenProbes.size)

            val triangle = runner.run(kind, caseOf(kind, "sides" to "3"))
            assertEquals(ExecutionOutcome.ReturnedValue("triangle"), triangle.outcome)
            assertEquals(1, triangle.covered.size)

            val negative = runner.run(sign, caseOf(sign, "x" to "-5"))
            assertEquals(ExecutionOutcome.ReturnedValue(-1), negative.outcome)
            assertTrue(negative.covered.isNotEmpty())
        }
    }

    @Test
    fun `catch blocks are instrumented`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Parser {
                fun parse(text: String): Int {
                    return try {
                        text.toInt()
                    } catch (e: NumberFormatException) {
                        -1
                    }
                }
            }
            """
        )
        val runner = (InstrumentedRunner.create(analysis) as RunnerCreation.Ready).runner
        runner.use {
            val parse = analysis.functions.first { f -> f.info.name == "parse" }.info
            val bad = it.run(parse, caseOf(parse, "text" to "abc"))
            assertEquals(ExecutionOutcome.ReturnedValue(-1), bad.outcome)
            assertTrue(it.probes.filter { p -> p.kind == "catch" }.all { p -> p.index in bad.covered })
        }
    }

    @Test
    fun `runner is unavailable for non compilable code`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Broken {
                fun f(x: Int): Int = "text"
            }
            """
        )
        assertTrue(InstrumentedRunner.create(analysis) is RunnerCreation.Unavailable)
    }
}
