package com.example.agent.coverage

import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.execution.TypeConversion
import kotlin.random.Random

enum class CandidateOrigin { HEURISTIC, BOUNDARY, SEARCH, LLM }

class CandidateContext(
    val info: FunctionInfo,
    val hints: BoundaryHints,
    val config: CoverageGeneratorConfig,
    val random: Random
)

class CandidatePool(private val limit: Int) {

    private val entries = LinkedHashMap<Map<String, String?>, CandidateOrigin>()

    val size: Int get() = entries.size

    fun add(vector: Map<String, String?>, origin: CandidateOrigin): Boolean =
        entries.putIfAbsent(vector, origin) == null

    fun ordered(): List<Pair<Map<String, String?>, CandidateOrigin>> =
        entries.entries.take(limit).map { it.key to it.value }
}

interface CandidateStrategy {
    fun fill(context: CandidateContext, pool: CandidatePool)
}

interface MutationStrategy {
    fun mutate(context: CandidateContext, parent: Map<String, String?>): Map<String, String?>
}

class BoundaryVariationStrategy : CandidateStrategy {

    override fun fill(context: CandidateContext, pool: CandidatePool) {
        val info = context.info
        val typical = CandidateSupport.typicalVector(info)
        val domains = CandidateSupport.domains(info, context.hints)
        val booleanParameters = info.parameters.filter { TypeConversion.normalize(it.type) == "Boolean" }.take(4)

        val bases = mutableListOf<Map<String, String?>>()
        val combinations = 1 shl booleanParameters.size
        for (mask in 0 until combinations) {
            val base = LinkedHashMap(typical)
            booleanParameters.forEachIndexed { index, parameter ->
                base[parameter.name] = if ((mask shr index) and 1 == 0) "true" else "false"
            }
            bases += base
        }

        bases.forEach { pool.add(it, CandidateOrigin.BOUNDARY) }

        bases.forEach { base ->
            info.parameters.forEach { parameter ->
                domains.getValue(parameter.name).forEach { value ->
                    val vector = LinkedHashMap(base)
                    vector[parameter.name] = value
                    pool.add(vector, CandidateOrigin.BOUNDARY)
                }
            }
        }
    }
}

class HeuristicCandidateStrategy(
    private val heuristic: HeuristicScenarioGenerator = HeuristicScenarioGenerator()
) : CandidateStrategy {

    override fun fill(context: CandidateContext, pool: CandidatePool) {
        heuristic.generate(context.info).forEach { pool.add(it.inputData, CandidateOrigin.HEURISTIC) }
    }
}

class CombinationStrategy : CandidateStrategy {

    override fun fill(context: CandidateContext, pool: CandidatePool) {
        val info = context.info
        val config = context.config
        val domains = CandidateSupport.domains(info, context.hints)

        var product = 1L
        domains.values.forEach { product = minOf(product * maxOf(it.size, 1), Long.MAX_VALUE / 1000) }
        val remaining = config.maxCandidatesPerFunction - pool.size
        if (remaining > 0 && info.parameters.isNotEmpty()) {
            if (product <= remaining) {
                cartesian(info.parameters, domains).forEach { pool.add(it, CandidateOrigin.BOUNDARY) }
            } else {
                var attempts = 0
                while (pool.size < config.maxCandidatesPerFunction && attempts < remaining * 6) {
                    attempts++
                    val vector = LinkedHashMap<String, String?>()
                    info.parameters.forEach { parameter ->
                        val values = domains.getValue(parameter.name)
                        vector[parameter.name] = values[context.random.nextInt(values.size)]
                    }
                    pool.add(vector, CandidateOrigin.BOUNDARY)
                }
            }
        }
    }

    private fun cartesian(
        parameters: List<ParameterInfo>,
        domains: Map<String, List<String?>>
    ): List<Map<String, String?>> {
        var result: List<Map<String, String?>> = listOf(emptyMap())
        parameters.forEach { parameter ->
            result = result.flatMap { partial ->
                domains.getValue(parameter.name).map { value -> LinkedHashMap(partial).also { it[parameter.name] = value } }
            }
        }
        return result
    }
}

fun standardCandidateStrategies(
    heuristic: HeuristicScenarioGenerator = HeuristicScenarioGenerator()
): List<CandidateStrategy> = listOf(
    BoundaryVariationStrategy(),
    HeuristicCandidateStrategy(heuristic),
    CombinationStrategy()
)
