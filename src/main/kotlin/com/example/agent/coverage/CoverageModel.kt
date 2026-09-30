package com.example.agent.coverage

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.model.TestSuiteResult

data class BranchProbe(
    val index: Int,
    val id: String,
    val functionKey: String,
    val kind: String,
    val label: String,
    val line: Int,
    val instrumentable: Boolean = true
)

data class BranchDescription(
    val id: String,
    val label: String
)

data class FunctionCoverage(
    val functionKey: String,
    val className: String,
    val functionName: String,
    val totalBranches: Int,
    val coveredBranches: Int,
    val branchCoverage: Double?,
    val covered: List<BranchDescription>,
    val notFoundWithinBudget: List<BranchDescription>,
    val notInstrumentable: List<BranchDescription>,
    val executed: Boolean
)

data class CoverageReport(
    val measured: Boolean,
    val totalBranches: Int,
    val coveredBranches: Int,
    val branchCoverage: Double?,
    val functions: List<FunctionCoverage>,
    val note: String? = null
)

data class CoverageGenerationResult(
    val measured: Boolean,
    val suite: TestSuiteResult?,
    val outcomes: Map<String, ExecutionOutcome>,
    val report: CoverageReport?,
    val warnings: List<String>
)

fun ratio(covered: Int, total: Int): Double? = if (total == 0) null else covered.toDouble() / total.toDouble()

const val NO_BRANCHES_WARNING = "В файле нет тестируемых функций или ветвей: покрытие не вычислялось"

fun emptyCoverageReport(): CoverageReport =
    CoverageReport(false, 0, 0, null, emptyList(), NO_BRANCHES_WARNING)
