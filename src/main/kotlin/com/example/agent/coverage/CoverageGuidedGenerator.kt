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

private enum class Origin { HEURISTIC, BOUNDARY, SEARCH, LLM }

private class Execution(
    val vector: Map<String, String?>,
    val outcome: ExecutionOutcome,
    val covered: Set<Int>,
    val origin: Origin
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
    private val config: CoverageGeneratorConfig = CoverageGeneratorConfig()
) {

    private val counter = AtomicInteger(0)

    fun generate(analysis: AnalysisResult): CoverageGenerationResult {
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
            cases += result.cases
            outcomes += result.outcomes
            reports += result.coverage
            warnings += result.warnings
        }

        val total = reports.sumOf { it.totalBranches }
        val covered = reports.sumOf { it.coveredBranches }
        val processed = TestCasePostProcessor().deduplicateAndNormalize(cases)
        val keptIds = processed.map { it.id }.toSet()

        return CoverageGenerationResult(
            measured = true,
            suite = TestSuiteResult(analysis.structure.sourcePath, processed),
            outcomes = outcomes.filterKeys { it in keptIds },
            report = CoverageReport(true, total, covered, ratio(covered, total), reports),
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

        fun run(vector: Map<String, String?>, origin: Origin): Execution? {
            if (executions.containsKey(vector)) return null
            if (!allConvertible(info, vector)) return null
            val testCase = syntheticCase(info, vector)
            val result = runner.run(info, testCase)
            val execution = Execution(vector, result.outcome, result.covered.intersect(instrumentableIndexes), origin)
            executions[vector] = execution
            return execution
        }

        val pool = buildPool(info, hints, random)
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
                val mutant = mutate(info, hints, parent.vector, random)
                val execution = run(mutant, Origin.SEARCH) ?: continue
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
                val execution = run(vector, Origin.LLM) ?: return@forEach
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
            val prefix = if (execution.origin == Origin.LLM) "Предложено LLM, покрывает" else "Покрывает"
            val text = if (gained.isEmpty()) "Проверка типичного входа" else "$prefix ветку ${gained.take(2).joinToString("; ")}"
            emit(execution, "$text: вход ${vectorText(info, execution.vector)}", execution.origin == Origin.LLM)
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
            .filter { it.usable && it.vector !in excluded && (it.origin == Origin.BOUNDARY || it.origin == Origin.HEURISTIC) }
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

    private fun buildPool(
        info: FunctionInfo,
        hints: BoundaryHints,
        random: Random
    ): List<Pair<Map<String, String?>, Origin>> {
        val typical = typicalVector(info)
        val domains = info.parameters.associate { it.name to domain(it, hints) }
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

        val pool = LinkedHashMap<Map<String, String?>, Origin>()
        bases.forEach { pool.putIfAbsent(it, Origin.BOUNDARY) }

        bases.forEach { base ->
            info.parameters.forEach { parameter ->
                domains.getValue(parameter.name).forEach { value ->
                    val vector = LinkedHashMap(base)
                    vector[parameter.name] = value
                    pool.putIfAbsent(vector, Origin.BOUNDARY)
                }
            }
        }

        heuristic.generate(info).forEach { pool.putIfAbsent(it.inputData, Origin.HEURISTIC) }

        var product = 1L
        domains.values.forEach { product = minOf(product * maxOf(it.size, 1), Long.MAX_VALUE / 1000) }
        val remaining = config.maxCandidatesPerFunction - pool.size
        if (remaining > 0 && info.parameters.isNotEmpty()) {
            if (product <= remaining) {
                cartesian(info.parameters, domains).forEach { pool.putIfAbsent(it, Origin.BOUNDARY) }
            } else {
                var attempts = 0
                while (pool.size < config.maxCandidatesPerFunction && attempts < remaining * 6) {
                    attempts++
                    val vector = LinkedHashMap<String, String?>()
                    info.parameters.forEach { parameter ->
                        val values = domains.getValue(parameter.name)
                        vector[parameter.name] = values[random.nextInt(values.size)]
                    }
                    pool.putIfAbsent(vector, Origin.BOUNDARY)
                }
            }
        }

        return pool.entries.take(config.maxCandidatesPerFunction).map { it.key to it.value }
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

    private fun domain(parameter: ParameterInfo, hints: BoundaryHints): List<String?> {
        val type = TypeConversion.normalize(parameter.type)
        val values = LinkedHashSet<String?>()
        values += typicalValue(parameter)
        hints.values[parameter.name]?.let { values.addAll(it) }
        when {
            type == "Boolean" -> {
                values += "true"
                values += "false"
            }
            NumericFormat.isNumeric(type) -> {
                values += "0"
                values += "-1"
            }
            type == "String" -> values += ""
            type == "Char" -> {
                values += " "
                values += "0"
            }
        }
        if (parameter.nullable) values += null
        return values.filter { allConvertible(parameter, it) }
    }

    private fun mutate(
        info: FunctionInfo,
        hints: BoundaryHints,
        parent: Map<String, String?>,
        random: Random
    ): Map<String, String?> {
        val vector = LinkedHashMap(parent)
        if (info.parameters.isEmpty()) return vector
        val changes = if (info.parameters.size > 1 && random.nextInt(3) == 0) 2 else 1
        repeat(changes) {
            val parameter = info.parameters[random.nextInt(info.parameters.size)]
            vector[parameter.name] = mutateValue(info, parameter, hints, vector, random)
        }
        return vector
    }

    private fun mutateValue(
        info: FunctionInfo,
        parameter: ParameterInfo,
        hints: BoundaryHints,
        vector: Map<String, String?>,
        random: Random
    ): String? {
        val type = TypeConversion.normalize(parameter.type)
        if (parameter.nullable && random.nextInt(10) == 0) return null
        val current = vector[parameter.name]
        return when {
            type == "Boolean" -> if (current == "true") "false" else "true"
            type == "Char" -> listOf("a", " ", "0", "Z")[random.nextInt(4)]
            type == "String" -> {
                val hinted = hints.values[parameter.name].orEmpty().filterNotNull()
                when (random.nextInt(4)) {
                    0 -> if (hinted.isNotEmpty()) hinted[random.nextInt(hinted.size)] else "a"
                    1 -> (current ?: "") + "a"
                    2 -> (current ?: "").dropLast(1)
                    else -> ""
                }
            }
            NumericFormat.isNumeric(type) -> mutateNumber(info, parameter, type, hints, vector, current, random)
            else -> current
        }
    }

    private fun mutateNumber(
        info: FunctionInfo,
        parameter: ParameterInfo,
        type: String,
        hints: BoundaryHints,
        vector: Map<String, String?>,
        current: String?,
        random: Random
    ): String? {
        val value = current?.toDoubleOrNull() ?: 1.0
        val others = info.parameters
            .filter { it.name != parameter.name && NumericFormat.isNumeric(TypeConversion.normalize(it.type)) }
            .mapNotNull { vector[it.name]?.toDoubleOrNull() }
            .filter { it != 0.0 }
        val constants = hints.constants
        val candidate = when (random.nextInt(9)) {
            0 -> value * 2
            1 -> value / 2
            2 -> value * 10
            3 -> value / 10
            4 -> value + 1
            5 -> value - 1
            6 -> if (constants.isNotEmpty()) constants[random.nextInt(constants.size)] else value + 1
            7 -> if (constants.isNotEmpty() && others.isNotEmpty()) {
                constants[random.nextInt(constants.size)] / others[random.nextInt(others.size)]
            } else {
                -value
            }
            else -> if (constants.isNotEmpty() && others.isNotEmpty()) {
                constants[random.nextInt(constants.size)] * others[random.nextInt(others.size)]
            } else {
                value * 3
            }
        }
        return NumericFormat.format(type, candidate)
    }

    private fun typicalVector(info: FunctionInfo): Map<String, String?> =
        info.parameters.associate { it.name to typicalValue(it) }

    private fun typicalValue(parameter: ParameterInfo): String? = when (TypeConversion.normalize(parameter.type)) {
        "Boolean" -> "true"
        "String" -> "example"
        "Char" -> "a"
        else -> "1"
    }

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
        info.parameters.all { allConvertible(it, vector[it.name]) }

    private fun allConvertible(parameter: ParameterInfo, value: String?): Boolean =
        TypeConversion.convert(parameter.type, parameter.nullable, value) is ConversionResult.Converted

    private fun findFunction(functions: List<FunctionInfo>, testCase: TestCase): FunctionInfo? {
        val candidates = functions.filter { it.className == testCase.className && it.name == testCase.functionName }
        if (testCase.signature.isNotEmpty()) {
            candidates.firstOrNull { it.signature() == testCase.signature }?.let { return it }
        }
        return candidates.firstOrNull()
    }
}
