package com.example.agent.coverage

import com.example.agent.analysis.AnalysisResult
import com.example.agent.analysis.AnalyzedFunction
import com.example.agent.execution.CompilationResult
import com.example.agent.execution.ConversionResult
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.KotlinInMemoryCompiler
import com.example.agent.execution.SourceUnit
import com.example.agent.execution.TestCaseExecutor
import com.example.agent.execution.TypeConversion
import com.example.agent.execution.dispose
import com.example.agent.generation.HeuristicScenarioGenerator
import com.example.agent.generation.LlmUncoveredBranchSuggester
import com.example.agent.generation.TestCasePostProcessor
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import com.example.agent.model.isTestable
import com.example.agent.model.key
import com.example.agent.model.signature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class CoverageGeneratorConfig(
    val maxCandidatesPerFunction: Int = 200,
    val searchAttempts: Int = 150,
    val timeBudgetMillisPerFunction: Long = 8_000,
    val maxBoundaryKeepers: Int = 10,
    val seed: Long = 42
)

private class Execution(
    val vector: Map<String, String?>,
    val outcome: ExecutionOutcome,
    val covered: Set<Int>,
    val origin: CandidateOrigin
) {
    val usable: Boolean get() = outcome !is ExecutionOutcome.CouldNotExecute
}

private class FunctionResult(
    val cases: List<TestCase>,
    val outcomes: Map<String, ExecutionOutcome>,
    val coverage: FunctionCoverage,
    val warnings: List<String>
)

class CoverageGuidedGenerator(
    private val heuristic: HeuristicScenarioGenerator = HeuristicScenarioGenerator(),
    private val extractor: BoundaryConditionExtractor = BoundaryConditionExtractor(),
    private val suggester: LlmUncoveredBranchSuggester? = null,
    private val config: CoverageGeneratorConfig = CoverageGeneratorConfig(),
    candidateStrategies: List<CandidateStrategy>? = null,
    private val mutationStrategy: MutationStrategy = NeighbourhoodMutationStrategy(),
    private val onFunctionGenerated: (FunctionCoverage, Int) -> Unit = { _, _ -> }
) {

    private val strategies: List<CandidateStrategy> = candidateStrategies ?: standardCandidateStrategies(heuristic)

    private val counter = AtomicInteger(0)

    fun generate(analysis: AnalysisResult): CoverageGenerationResult {
        if (analysis.functions.none { it.info.isTestable() }) {
            return CoverageGenerationResult(
                measured = false,
                suite = TestSuiteResult(analysis.structure.sourcePath, emptyList()),
                outcomes = emptyMap(),
                report = emptyCoverageReport(),
                warnings = listOf(NO_BRANCHES_WARNING)
            )
        }
        return when (val creation = InstrumentedRunner.create(analysis)) {
            is RunnerCreation.Unavailable -> fallback(analysis, creation.reason)
            is RunnerCreation.Ready -> creation.runner.use { generateMeasured(analysis, it) }
        }
    }

    private fun generateMeasured(analysis: AnalysisResult, runner: InstrumentedRunner): CoverageGenerationResult {
        val cases = mutableListOf<TestCase>()
        val outcomes = LinkedHashMap<String, ExecutionOutcome>()
        val reports = mutableListOf<FunctionCoverage>()
        val warnings = mutableListOf<String>()

        analysis.functions.filter { it.info.isTestable() }.forEach { analyzed ->
            val result = generateForFunction(runner, analyzed)
            runCatching { onFunctionGenerated(result.coverage, result.cases.size) }
            cases += result.cases
            outcomes += result.outcomes
            reports += result.coverage
            warnings += result.warnings
        }

        val total = reports.sumOf { it.totalBranches }
        val covered = reports.sumOf { it.coveredBranches }
        val processed = TestCasePostProcessor().deduplicateAndNormalize(cases)
        val keptIds = processed.map { it.id }.toSet()

        val hasBranches = total > 0
        if (!hasBranches) warnings += NO_BRANCHES_WARNING

        return CoverageGenerationResult(
            measured = hasBranches,
            suite = TestSuiteResult(analysis.structure.sourcePath, processed),
            outcomes = outcomes.filterKeys { it in keptIds },
            report = if (hasBranches) CoverageReport(true, total, covered, ratio(covered, total), reports) else emptyCoverageReport(),
            warnings = warnings
        )
    }

    private fun fallback(analysis: AnalysisResult, reason: String): CoverageGenerationResult {
        val heuristicCases = analysis.functions.filter { it.info.isTestable() }.flatMap { heuristic.generate(it.info) }
        val processed = TestCasePostProcessor().deduplicateAndNormalize(heuristicCases)
        val outcomes = executePlain(analysis, processed)
        return CoverageGenerationResult(
            measured = false,
            suite = TestSuiteResult(analysis.structure.sourcePath, processed),
            outcomes = outcomes,
            report = null,
            warnings = listOf("Покрытие ветвей не измерено: $reason")
        )
    }

    private fun executePlain(analysis: AnalysisResult, cases: List<TestCase>): Map<String, ExecutionOutcome> {
        if (analysis.parsedSources.isEmpty()) return emptyMap()
        val units = analysis.parsedSources.map { SourceUnit(it.file.fileName, it.file.content) }
        val compiled = KotlinInMemoryCompiler().compile(units) as? CompilationResult.Success ?: return emptyMap()
        try {
            val executor = TestCaseExecutor()
            return cases.mapNotNull { testCase ->
                val function = findFunction(analysis.functions.map { it.info }, testCase) ?: return@mapNotNull null
                if (!allConvertible(function, testCase.inputData)) return@mapNotNull null
                testCase.id to executor.execute(compiled.classLoader, function, testCase)
            }.toMap()
        } finally {
            compiled.dispose()
        }
    }

    private fun generateForFunction(runner: InstrumentedRunner, analyzed: AnalyzedFunction): FunctionResult {
        val info = analyzed.info
        val probes = runner.probes.filter { it.functionKey == info.key() }
        val instrumentable = probes.filter { it.instrumentable }
        val instrumentableIndexes = instrumentable.map { it.index }.toSet()
        val probeByIndex = probes.associateBy { it.index }
        val hints = extractor.extract(analyzed)
        val random = Random(config.seed + info.key().hashCode())
        val deadline = System.currentTimeMillis() + config.timeBudgetMillisPerFunction
        val executions = LinkedHashMap<Map<String, String?>, Execution>()
        val warnings = mutableListOf<String>()

        fun run(vector: Map<String, String?>, origin: CandidateOrigin): Execution? {
            if (executions.containsKey(vector)) return null
            if (!allConvertible(info, vector)) return null
            val testCase = syntheticCase(info, vector)
            val result = runner.run(info, testCase)
            val execution = Execution(vector, result.outcome, result.covered.intersect(instrumentableIndexes), origin)
            executions[vector] = execution
            return execution
        }

        val context = CandidateContext(info, hints, config, random)
        val pool = buildPool(context)
        for ((vector, origin) in pool) {
            if (System.currentTimeMillis() > deadline) break
            run(vector, origin)
        }

        val selected = LinkedHashMap<Map<String, String?>, Execution>()
        val coveredSoFar = HashSet<Int>()

        fun select(execution: Execution) {
            selected[execution.vector] = execution
            coveredSoFar += execution.covered
        }

        while (true) {
            val best = executions.values
                .filter { it.usable && !selected.containsKey(it.vector) }
                .maxWithOrNull(compareBy<Execution> { (it.covered - coveredSoFar).size }.thenBy { -it.vector.size })
            if (best == null || (best.covered - coveredSoFar).isEmpty()) break
            select(best)
        }

        var uncovered = instrumentableIndexes - coveredSoFar

        if (uncovered.isNotEmpty()) {
            val archive = executions.values.filter { it.usable }.toMutableList()
            var attempts = 0
            while (attempts < config.searchAttempts && uncovered.isNotEmpty() && System.currentTimeMillis() < deadline) {
                attempts++
                val parentPool = if (selected.isNotEmpty() && random.nextInt(10) < 6) selected.values.toList() else archive
                if (parentPool.isEmpty()) break
                val parent = parentPool[random.nextInt(parentPool.size)]
                val mutant = mutationStrategy.mutate(context, parent.vector)
                val execution = run(mutant, CandidateOrigin.SEARCH) ?: continue
                if (execution.usable) {
                    archive += execution
                    if ((execution.covered - coveredSoFar).isNotEmpty()) {
                        select(execution)
                        uncovered = instrumentableIndexes - coveredSoFar
                    }
                }
            }
        }

        if (uncovered.isNotEmpty() && suggester != null) {
            val labels = uncovered.mapNotNull { probeByIndex[it]?.label }
            suggester.suggest(info, labels).forEach { vector ->
                if (uncovered.isEmpty()) return@forEach
                val execution = run(vector, CandidateOrigin.LLM) ?: return@forEach
                if (execution.usable && (execution.covered - coveredSoFar).isNotEmpty()) {
                    select(execution)
                    uncovered = instrumentableIndexes - coveredSoFar
                }
            }
        }

        val representatives = LinkedHashMap<Map<String, String?>, Execution>()
        val seenSignatures = selected.values.map { outcomeSignature(it.outcome) }.toMutableSet()
        executions.values.filter { it.usable && !selected.containsKey(it.vector) }.forEach { execution ->
            if (seenSignatures.add(outcomeSignature(execution.outcome))) {
                representatives[execution.vector] = execution
            }
        }

        val keepers = boundaryKeepers(info, executions.values.toList(), selected.keys + representatives.keys)

        val typical = typicalVector(info)
        val extremes = extremeValues()
        val cases = mutableListOf<TestCase>()
        val outcomes = LinkedHashMap<String, ExecutionOutcome>()

        fun emit(execution: Execution, description: String, llm: Boolean = false) {
            val id = if (llm) "${info.name}-llm-${counter.incrementAndGet()}" else "${info.name}-${counter.incrementAndGet()}"
            cases += buildCase(info, id, execution, description, hints, typical, extremes)
            outcomes[id] = execution.outcome
        }

        val alreadyCovered = HashSet<Int>()
        selected.values.forEach { execution ->
            val gained = (execution.covered - alreadyCovered).sorted().mapNotNull { probeByIndex[it]?.label }
            alreadyCovered += execution.covered
            val prefix = if (execution.origin == CandidateOrigin.LLM) "Предложено LLM, покрывает" else "Покрывает"
            val text = if (gained.isEmpty()) "Проверка типичного входа" else "$prefix ветку ${gained.take(2).joinToString("; ")}"
            emit(execution, "$text: вход ${vectorText(info, execution.vector)}", execution.origin == CandidateOrigin.LLM)
        }
        representatives.values.forEach { execution ->
            emit(execution, "${outcomeText(execution.outcome)}: вход ${vectorText(info, execution.vector)}")
        }
        keepers.forEach { execution ->
            emit(execution, "Проверка граничного значения: вход ${vectorText(info, execution.vector)}")
        }

        if (cases.isEmpty()) {
            val fallbackCases = heuristic.generate(info)
            cases += fallbackCases
            warnings += "Не удалось выполнить ни одного сценария для ${info.className}.${info.name}: тесты без реальных проверок"
        }

        val coveredFinal = (selected.values + representatives.values + keepers).flatMap { it.covered }.toSet()
        val covered = probes.filter { it.instrumentable && it.index in coveredFinal }
        val notFound = probes.filter { it.instrumentable && it.index !in coveredFinal }
        val notInstrumentable = probes.filter { !it.instrumentable }

        return FunctionResult(
            cases = cases,
            outcomes = outcomes,
            coverage = FunctionCoverage(
                functionKey = info.key(),
                className = info.className,
                functionName = info.name,
                totalBranches = instrumentable.size,
                coveredBranches = covered.size,
                branchCoverage = ratio(covered.size, instrumentable.size),
                covered = covered.map { BranchDescription(it.id, it.label) },
                notFoundWithinBudget = notFound.map { BranchDescription(it.id, it.label) },
                notInstrumentable = notInstrumentable.map { BranchDescription(it.id, it.label) },
                executed = executions.values.any { it.usable }
            ),
            warnings = warnings
        )
    }

    private fun buildCase(
        info: FunctionInfo,
        id: String,
        execution: Execution,
        description: String,
        hints: BoundaryHints,
        typical: Map<String, String?>,
        extremes: Set<String>
    ): TestCase {
        val type = when {
            execution.outcome is ExecutionOutcome.ThrewException -> ScenarioType.NEGATIVE
            isBoundary(info, execution.vector, hints, typical, extremes) -> ScenarioType.BOUNDARY
            else -> ScenarioType.POSITIVE
        }
        val expected = when (val outcome = execution.outcome) {
            is ExecutionOutcome.ThrewException -> "Выбрасывается исключение ${outcome.exceptionClassName}"
            is ExecutionOutcome.ReturnedValue -> "Возвращается значение ${outcome.value}"
            is ExecutionOutcome.CouldNotExecute -> null
        }
        return TestCase(
            id = id,
            functionName = info.name,
            className = info.className,
            type = type,
            description = description,
            inputData = execution.vector,
            expectedResult = expected,
            steps = listOf(
                "Подготовить входные данные: ${vectorText(info, execution.vector)}",
                "Вызвать ${info.className}.${info.name}",
                "Проверить результат: ${expected ?: "не определён"}"
            ),
            signature = info.signature()
        )
    }

    private fun isBoundary(
        info: FunctionInfo,
        vector: Map<String, String?>,
        hints: BoundaryHints,
        typical: Map<String, String?>,
        extremes: Set<String>
    ): Boolean = info.parameters.any { parameter ->
        val value = vector[parameter.name]
        value != typical[parameter.name] &&
            (value in (hints.values[parameter.name] ?: emptyList()) || (value != null && (value in extremes || value.length >= 1000)))
    }

    private fun boundaryKeepers(
        info: FunctionInfo,
        executions: List<Execution>,
        excluded: Set<Map<String, String?>>
    ): List<Execution> {
        val typical = typicalVector(info)
        val groups = LinkedHashMap<String, MutableList<Execution>>()
        executions
            .filter { it.usable && it.vector !in excluded && (it.origin == CandidateOrigin.BOUNDARY || it.origin == CandidateOrigin.HEURISTIC) }
            .forEach { execution ->
                val varied = info.parameters.filter { execution.vector[it.name] != typical[it.name] }.map { it.name }
                if (varied.size == 1) groups.getOrPut(varied.first()) { mutableListOf() } += execution
            }
        val result = mutableListOf<Execution>()
        val cursors = groups.keys.associateWith { 0 }.toMutableMap()
        var progressed = true
        while (result.size < config.maxBoundaryKeepers && progressed) {
            progressed = false
            for ((name, list) in groups) {
                if (result.size >= config.maxBoundaryKeepers) break
                val position = cursors.getValue(name)
                if (position < list.size) {
                    result += list[position]
                    cursors[name] = position + 1
                    progressed = true
                }
            }
        }
        return result
    }

    private fun buildPool(context: CandidateContext): List<Pair<Map<String, String?>, CandidateOrigin>> {
        val pool = CandidatePool(config.maxCandidatesPerFunction)
        strategies.forEach { it.fill(context, pool) }
        return pool.ordered()
    }

    private fun typicalVector(info: FunctionInfo): Map<String, String?> =
        CandidateSupport.typicalVector(info)

    private fun extremeValues(): Set<String> = setOf(
        Int.MIN_VALUE.toString(), Int.MAX_VALUE.toString(),
        Long.MIN_VALUE.toString(), Long.MAX_VALUE.toString(),
        Short.MIN_VALUE.toString(), Short.MAX_VALUE.toString(),
        Byte.MIN_VALUE.toString(), Byte.MAX_VALUE.toString(),
        Double.MIN_VALUE.toString(), Double.MAX_VALUE.toString(),
        Float.MIN_VALUE.toString(), Float.MAX_VALUE.toString(),
        "0", "-1", "0.0", "-1.0", ""
    )

    private fun outcomeSignature(outcome: ExecutionOutcome): String = when (outcome) {
        is ExecutionOutcome.ThrewException -> "threw:${outcome.exceptionClassName}"
        is ExecutionOutcome.ReturnedValue -> if (outcome.value is Boolean) "returned:${outcome.value}" else "returned"
        is ExecutionOutcome.CouldNotExecute -> "none"
    }

    private fun outcomeText(outcome: ExecutionOutcome): String = when (outcome) {
        is ExecutionOutcome.ThrewException -> "Приводит к исключению ${outcome.exceptionClassName.substringAfterLast('.')}"
        is ExecutionOutcome.ReturnedValue -> "Возвращает результат ${outcome.value}"
        is ExecutionOutcome.CouldNotExecute -> "Не выполнен"
    }

    private fun vectorText(info: FunctionInfo, vector: Map<String, String?>): String =
        info.parameters.joinToString(", ") { parameter ->
            val value = vector[parameter.name]
            val rendered = when {
                value == null -> "null"
                TypeConversion.normalize(parameter.type) == "String" ->
                    if (value.length > 24) "\"${value.take(8)}…\"(${value.length} символов)" else "\"$value\""
                else -> value
            }
            "${parameter.name}=$rendered"
        }

    private fun syntheticCase(info: FunctionInfo, vector: Map<String, String?>) = TestCase(
        id = "probe",
        functionName = info.name,
        className = info.className,
        type = ScenarioType.POSITIVE,
        description = "",
        inputData = vector,
        expectedResult = null,
        steps = emptyList(),
        signature = info.signature()
    )

    private fun allConvertible(info: FunctionInfo, vector: Map<String, String?>): Boolean =
        info.parameters.all { CandidateSupport.allConvertible(it, vector[it.name]) }
    private fun findFunction(functions: List<FunctionInfo>, testCase: TestCase): FunctionInfo? {
        val candidates = functions.filter { it.className == testCase.className && it.name == testCase.functionName }
        if (testCase.signature.isNotEmpty()) {
            candidates.firstOrNull { it.signature() == testCase.signature }?.let { return it }
        }
        return candidates.firstOrNull()
    }
}
