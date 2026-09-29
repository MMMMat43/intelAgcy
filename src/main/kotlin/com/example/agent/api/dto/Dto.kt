package com.example.agent.api.dto

data class AnalyzeRequest(
    val sourcePath: String
)

data class GenerateTestsRequest(
    val sourcePath: String,
    val outputDir: String? = null
)

data class GenerateTestsResponse(
    val functionsCount: Int,
    val testCasesCount: Int,
    val generatedTestFilePath: String?,
    val language: String = "kotlin",
    val executedTestCases: Int = 0,
    val skippedFunctions: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val branchCoverage: Double? = null,
    val coveredBranches: Int? = null,
    val totalBranches: Int? = null,
    val coverageMeasured: Boolean = false
)

data class ErrorResponse(
    val error: String
)

data class HealthResponse(
    val status: String
)
