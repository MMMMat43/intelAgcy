package com.example.agent.coverage

import com.example.agent.generation.HeuristicScenarioGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CandidateStrategyTest {

    private fun contextFor(function: String, resource: String = "OrderProcessor.kt"): CandidateContext {
        val analysis = CoverageTestSupport.analysisOf(resource)
        val analyzed = analysis.functions.first { it.info.name == function }
        val config = CoverageGeneratorConfig()
        return CandidateContext(analyzed.info, BoundaryConditionExtractor().extract(analyzed), config, Random(1))
    }

    @Test
    fun `boundary variation strategy puts neighbours of thresholds into the pool`() {
        val context = contextFor("calculateDiscount")
        val pool = CandidatePool(200)

        BoundaryVariationStrategy().fill(context, pool)

        val quantities = pool.ordered().mapNotNull { it.first["quantity"] }.toSet()
        assertTrue(quantities.containsAll(listOf("9", "10", "11")), quantities.toString())
        assertTrue(pool.ordered().all { it.second == CandidateOrigin.BOUNDARY })
    }

    @Test
    fun `heuristic strategy adds candidates marked as heuristic`() {
        val context = contextFor("isPositive", "SampleCalculator.kt")
        val pool = CandidatePool(200)

        HeuristicCandidateStrategy(HeuristicScenarioGenerator()).fill(context, pool)

        assertTrue(pool.size > 0)
        assertTrue(pool.ordered().all { it.second == CandidateOrigin.HEURISTIC })
    }

    @Test
    fun `pool keeps first origin of a duplicate vector and respects its limit`() {
        val pool = CandidatePool(2)
        val vector = mapOf("x" to "1")

        assertTrue(pool.add(vector, CandidateOrigin.BOUNDARY))
        assertFalse(pool.add(vector, CandidateOrigin.HEURISTIC))
        pool.add(mapOf("x" to "2"), CandidateOrigin.HEURISTIC)
        pool.add(mapOf("x" to "3"), CandidateOrigin.HEURISTIC)

        assertEquals(CandidateOrigin.BOUNDARY, pool.ordered().first().second)
        assertEquals(2, pool.ordered().size)
    }

    @Test
    fun `combination strategy stays within the configured candidate limit`() {
        val context = contextFor("calculateDiscount")
        val pool = CandidatePool(context.config.maxCandidatesPerFunction)

        standardCandidateStrategies().forEach { it.fill(context, pool) }

        assertTrue(pool.ordered().size <= context.config.maxCandidatesPerFunction)
        assertTrue(pool.ordered().size > 20)
    }

    @Test
    fun `neighbourhood mutation changes at least one parameter and keeps the vector shape`() {
        val context = contextFor("calculateDiscount")
        val parent = CandidateSupport.typicalVector(context.info)
        val strategy = NeighbourhoodMutationStrategy()

        val mutants = (1..30).map { strategy.mutate(context, parent) }

        assertTrue(mutants.all { it.keys == parent.keys })
        assertTrue(mutants.any { it != parent })
    }

    @Test
    fun `custom strategy list replaces the standard one and coverage follows it`() {
        val analysis = CoverageTestSupport.analysisOf("SampleCalculator.kt")

        val onlyHeuristics = CoverageGuidedGenerator(
            suggester = null,
            candidateStrategies = listOf(HeuristicCandidateStrategy()),
            config = CoverageGeneratorConfig(searchAttempts = 0)
        ).generate(analysis)
        val standard = CoverageGuidedGenerator(suggester = null).generate(analysis)

        assertTrue(onlyHeuristics.report!!.coveredBranches <= standard.report!!.coveredBranches)
        assertEquals(7, standard.report!!.coveredBranches)
    }

    @Test
    fun `generator reports every processed function through the callback`() {
        val analysis = CoverageTestSupport.analysisOf("SampleCalculator.kt")
        val seen = mutableListOf<String>()

        CoverageGuidedGenerator(suggester = null, onFunctionGenerated = { coverage, _ -> seen += coverage.functionName })
            .generate(analysis)

        assertEquals(listOf("divide", "isPositive"), seen)
    }
}
