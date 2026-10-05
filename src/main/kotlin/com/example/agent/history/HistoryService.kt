package com.example.agent.history

import com.example.agent.storage.BranchState
import com.example.agent.storage.RunNote
import com.example.agent.storage.RunRecord
import com.example.agent.storage.RunRepository
import java.time.Clock
import java.time.Instant

class HistoryService(
    private val repository: RunRepository,
    private val clock: Clock = Clock.systemUTC()
) {

    fun projects(): List<String> = repository.findAll().map { projectOf(it) }.distinct().sorted()

    fun runs(filter: RunFilter = RunFilter()): List<RunSummaryRow> =
        repository.findAll().map { summaryOf(it) }.filter { matches(it, filter) }

    fun details(runId: String): RunDetails? {
        val record = repository.findById(runId) ?: return null
        val functions = record.functions.map {
            FunctionRow(
                className = it.className,
                functionName = it.functionName,
                signature = it.signature,
                complexity = it.complexity,
                coveredBranches = it.coveredBranches,
                totalBranches = it.totalBranches,
                testCases = it.cases.size
            )
        }
        val branches = record.functions.flatMap { function ->
            function.branches.map { BranchRow("${function.className}.${function.functionName}", it.code, it.label, it.state) }
        }
        val cases = record.functions.flatMap { function ->
            function.cases.map { case ->
                TestCaseRow(
                    function = "${function.className}.${function.functionName}",
                    code = case.code,
                    scenarioType = case.scenarioType,
                    origin = case.origin,
                    inputs = case.inputs.entries.joinToString(", ") { (name, value) -> "$name = ${value ?: "null"}" },
                    expectedResult = case.expectedResult,
                    description = case.description
                )
            }
        }
        return RunDetails(summaryOf(record), record.sourcePath, functions, branches, cases)
    }

    fun statistics(filter: RunFilter = RunFilter(), topComplex: Int = 5): HistoryStatistics {
        val records = repository.findAll().filter { matches(summaryOf(it), filter) }
        val summaries = records.map { summaryOf(it) }
        val coverages = summaries.mapNotNull { it.coverage }
        val complex = records.flatMap { record ->
            record.functions.mapNotNull { function ->
                function.complexity?.let { ComplexFunction(projectOf(record), "${function.className}.${function.functionName}", it) }
            }
        }.sortedWith(compareByDescending<ComplexFunction> { it.complexity }.thenBy { it.function }).take(topComplex)
        return HistoryStatistics(
            runs = summaries.size,
            projects = summaries.map { it.project }.distinct().size,
            averageCoverage = if (coverages.isEmpty()) null else coverages.average(),
            totalTestCases = summaries.sumOf { it.testCases },
            uncoveredBranches = summaries.sumOf { it.uncoveredBranches },
            mostComplex = complex
        )
    }

    fun notes(runId: String): List<RunNote> = repository.notes(runId)

    fun addNote(runId: String, text: String): RunNote {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Текст заметки не может быть пустым" }
        require(repository.findById(runId) != null) { "Запуск не найден: $runId" }
        return repository.addNote(runId, trimmed, Instant.now(clock))
    }

    fun deleteRun(runId: String): Boolean = repository.deleteById(runId)

    private fun matches(row: RunSummaryRow, filter: RunFilter): Boolean {
        if (filter.project != null && row.project != filter.project) return false
        if (filter.onlyWithUncovered && row.uncoveredBranches <= 0) return false
        val coverage = row.coverage
        if (filter.minCoverage != null && (coverage == null || coverage < filter.minCoverage)) return false
        if (filter.maxCoverage != null && (coverage == null || coverage > filter.maxCoverage)) return false
        return true
    }

    private fun summaryOf(record: RunRecord): RunSummaryRow {
        val total = record.functions.sumOf { it.totalBranches }
        val covered = record.functions.sumOf { it.coveredBranches }
        val coverage = if (record.coverageMeasured && total > 0) covered.toDouble() / total else null
        return RunSummaryRow(
            runId = record.id,
            project = projectOf(record),
            startedAt = record.startedAt,
            status = record.status,
            durationMillis = record.durationMillis,
            functions = record.functions.size,
            testCases = record.functions.sumOf { it.cases.size },
            coveredBranches = covered,
            totalBranches = total,
            coverage = coverage
        )
    }

    private fun projectOf(record: RunRecord): String = record.projectName?.takeIf { it.isNotBlank() } ?: record.sourcePath

    companion object {
        fun isUncovered(state: BranchState): Boolean = state != BranchState.COVERED
    }
}
