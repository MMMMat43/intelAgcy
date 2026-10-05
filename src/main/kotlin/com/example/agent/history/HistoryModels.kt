package com.example.agent.history

import com.example.agent.storage.BranchState
import com.example.agent.storage.RunStatus
import java.time.Instant

data class RunFilter(
    val project: String? = null,
    val minCoverage: Double? = null,
    val maxCoverage: Double? = null,
    val onlyWithUncovered: Boolean = false
)

data class RunSummaryRow(
    val runId: String,
    val project: String,
    val startedAt: Instant,
    val status: RunStatus,
    val durationMillis: Long,
    val functions: Int,
    val testCases: Int,
    val coveredBranches: Int,
    val totalBranches: Int,
    val coverage: Double?
) {
    val uncoveredBranches: Int get() = totalBranches - coveredBranches
}

data class FunctionRow(
    val className: String,
    val functionName: String,
    val signature: String,
    val complexity: Int?,
    val coveredBranches: Int,
    val totalBranches: Int,
    val testCases: Int
) {
    val coverage: Double? get() = if (totalBranches > 0) coveredBranches.toDouble() / totalBranches else null
}

data class BranchRow(
    val function: String,
    val code: String,
    val label: String,
    val state: BranchState
)

data class TestCaseRow(
    val function: String,
    val code: String,
    val scenarioType: String,
    val origin: String,
    val inputs: String,
    val expectedResult: String?,
    val description: String
)

data class RunDetails(
    val summary: RunSummaryRow,
    val sourcePath: String,
    val functions: List<FunctionRow>,
    val branches: List<BranchRow>,
    val testCases: List<TestCaseRow>
)

data class ComplexFunction(
    val project: String,
    val function: String,
    val complexity: Int
)

data class HistoryStatistics(
    val runs: Int,
    val projects: Int,
    val averageCoverage: Double?,
    val totalTestCases: Int,
    val uncoveredBranches: Int,
    val mostComplex: List<ComplexFunction>
)
