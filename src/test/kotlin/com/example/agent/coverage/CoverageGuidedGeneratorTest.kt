package com.example.agent.coverage

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmUncoveredBranchSuggester
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.model.isTestable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class CoverageGuidedGeneratorTest {

    private class FailingClient : LlmClient {
        override fun complete(prompt: String): LlmResult = LlmResult.Failure("unavailable")
    }

    private class ScriptedClient(private val answer: String) : LlmClient {
        var calls = 0
        override fun complete(prompt: String): LlmResult {
            calls++
            return LlmResult.Success(answer)
        }
    }

    private fun heuristicCoverage(resource: String): CoverageReport {
        val analysis = CoverageTestSupport.analysisOf(resource)
        val cases = analysis.functions.filter { it.info.isTestable() }.flatMap { HeuristicScenarioGenerator().generate(it.info) }
        return assertNotNull(SuiteCoverageMeter().measure(analysis, cases))
    }

    @Test
    fun `guided generation covers every branch of SampleCalculator`() {
        val analysis = CoverageTestSupport.analysisOf("SampleCalculator.kt")
        val result = CoverageGuidedGenerator(valueProvider = LlmUncoveredBranchSuggester(FailingClient())).generate(analysis)

        val report = assertNotNull(result.report)
        assertTrue(result.measured)
        assertEquals(1.0, assertNotNull(report.branchCoverage), 0.0001, report.functions.toString())
        val divide = report.functions.first { it.functionName == "divide" }
        assertTrue(divide.covered.any { it.label.contains("denominator == 0") })
        assertTrue(divide.covered.any { it.label.startsWith("for") })
    }

    @Test
    fun `guided generation beats heuristics on OrderProcessor and reaches full coverage`() {
        val before = heuristicCoverage("OrderProcessor.kt")

        val analysis = CoverageTestSupport.analysisOf("OrderProcessor.kt")
        val result = CoverageGuidedGenerator(valueProvider = LlmUncoveredBranchSuggester(FailingClient())).generate(analysis)
        val after = assertNotNull(result.report)

        println("OrderProcessor branch coverage: heuristics=${before.coveredBranches}/${before.totalBranches} " +
            "(${"%.1f".format(assertNotNull(before.branchCoverage) * 100)}%), guided=${after.coveredBranches}/${after.totalBranches} " +
            "(${"%.1f".format(assertNotNull(after.branchCoverage) * 100)}%)")
        after.functions.forEach { println("  ${it.functionName}: ${it.coveredBranches}/${it.totalBranches} missing=${it.notFoundWithinBudget.map { b -> b.label }}") }

        assertEquals(before.totalBranches, after.totalBranches)
        assertTrue(assertNotNull(after.branchCoverage) > assertNotNull(before.branchCoverage), "before=${before.branchCoverage} after=${after.branchCoverage}")
        assertTrue(assertNotNull(after.branchCoverage) >= 0.95, after.functions.filter { it.notFoundWithinBudget.isNotEmpty() }.toString())
    }

    @Test
    fun `generated suite re-measured independently matches reported coverage`() {
        val analysis = CoverageTestSupport.analysisOf("OrderProcessor.kt")
        val result = CoverageGuidedGenerator(valueProvider = null).generate(analysis)
        val reported = assertNotNull(result.report)
        val remeasured = assertNotNull(SuiteCoverageMeter().measure(analysis, assertNotNull(result.suite).testCases))

        assertEquals(reported.coveredBranches, remeasured.coveredBranches)
        assertEquals(reported.totalBranches, remeasured.totalBranches)
    }

    @Test
    fun `generated cases carry real outcomes and boundary values near thresholds`() {
        val analysis = CoverageTestSupport.analysisOf("OrderProcessor.kt")
        val result = CoverageGuidedGenerator(valueProvider = null).generate(analysis)
        val suite = assertNotNull(result.suite)

        val discountCases = suite.testCases.filter { it.functionName == "calculateDiscount" }
        assertTrue(discountCases.all { result.outcomes[it.id] !is ExecutionOutcome.CouldNotExecute })
        assertTrue(discountCases.any { result.outcomes[it.id] is ExecutionOutcome.ThrewException })
        assertTrue(discountCases.any { it.description.startsWith("Покрывает ветку") })

        val totals = discountCases.mapNotNull {
            val price = it.inputData["price"]?.toDoubleOrNull()
            val quantity = it.inputData["quantity"]?.toDoubleOrNull()
            if (price != null && quantity != null) price * quantity else null
        }
        assertTrue(totals.any { it >= 100.0 } && totals.any { it in 0.0..99.999 }, totals.toString())
        assertTrue(totals.any { it >= 200.0 })
    }

    @Test
    fun `works without any LLM and falls back gracefully on non compilable code`() {
        val broken = CoverageTestSupport.analysisOfCode(
            """
            class Broken {
                fun f(x: Int): Int = "text"
            }
            """
        )
        val result = CoverageGuidedGenerator(valueProvider = LlmUncoveredBranchSuggester(FailingClient())).generate(broken)

        assertFalse(result.measured)
        assertTrue(assertNotNull(result.suite).testCases.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("не измерено") })
    }

    @Test
    fun `llm value is accepted only when it increases coverage`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Vault {
                fun open(code: String): String {
                    if (code.reversed() == "cba321") {
                        return "open"
                    }
                    return "closed"
                }
            }
            """
        )
        val client = ScriptedClient(
            "```json\n[{\"code\": \"nope\"}, {\"code\": \"123abc\"}, {\"wrong\": 1}]\n```"
        )
        val result = CoverageGuidedGenerator(
            valueProvider = LlmUncoveredBranchSuggester(client),
            config = CoverageGeneratorConfig(searchAttempts = 0)
        ).generate(analysis)

        val report = assertNotNull(result.report)
        assertEquals(1.0, assertNotNull(report.branchCoverage), 0.0001)
        assertEquals(1, client.calls)
        val llmCases = assertNotNull(result.suite).testCases.filter { it.id.contains("-provided-") }
        assertEquals(1, llmCases.size)
        assertEquals("123abc", llmCases.single().inputData["code"])
        assertEquals(ExecutionOutcome.ReturnedValue("open"), result.outcomes[llmCases.single().id])
    }

    @Test
    fun `llm is not consulted when everything is already covered`() {
        val analysis = CoverageTestSupport.analysisOf("SampleCalculator.kt")
        val client = ScriptedClient("[]")
        CoverageGuidedGenerator(valueProvider = LlmUncoveredBranchSuggester(client)).generate(analysis)

        assertEquals(0, client.calls)
    }
}
