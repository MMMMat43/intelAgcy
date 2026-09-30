package com.example.agent.api

import com.example.agent.coverage.FunctionCoverage
import com.example.agent.model.TestCase
import java.time.Instant

data class FunctionDetail(
    val fileName: String,
    val packageName: String,
    val complexity: Int
)

data class AnalysisCompleted(
    val sourcePath: String,
    val functionsCount: Int,
    val testableFunctions: Int,
    val warnings: List<String>
)

data class FunctionProcessed(
    val sourcePath: String,
    val coverage: FunctionCoverage,
    val generatedCases: Int
)

data class SuiteGenerated(
    val sourcePath: String,
    val testCasesCount: Int,
    val executedTestCases: Int,
    val coveredBranches: Int,
    val totalBranches: Int,
    val coverageMeasured: Boolean
)

data class ArtifactsSaved(
    val sourcePath: String,
    val outputDir: String
)

data class PipelineCompleted(
    val sourcePath: String,
    val startedAt: Instant,
    val durationMillis: Long,
    val functionsCount: Int,
    val testCasesCount: Int,
    val executedTestCases: Int,
    val coveredBranches: Int,
    val totalBranches: Int,
    val coverageMeasured: Boolean,
    val outputDir: String?,
    val functions: List<FunctionCoverage>,
    val warnings: List<String>,
    val testCases: List<TestCase> = emptyList(),
    val functionDetails: Map<String, FunctionDetail> = emptyMap()
)

data class PipelineFailed(
    val sourcePath: String,
    val startedAt: Instant,
    val durationMillis: Long,
    val message: String
)

interface PipelineListener {
    fun onAnalysisCompleted(event: AnalysisCompleted) {}
    fun onFunctionProcessed(event: FunctionProcessed) {}
    fun onSuiteGenerated(event: SuiteGenerated) {}
    fun onArtifactsSaved(event: ArtifactsSaved) {}
    fun onCompleted(event: PipelineCompleted) {}
    fun onFailed(event: PipelineFailed) {}
}
