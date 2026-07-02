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
    val generatedTestFilePath: String?
)

data class ErrorResponse(
    val error: String
)

data class HealthResponse(
    val status: String
)
