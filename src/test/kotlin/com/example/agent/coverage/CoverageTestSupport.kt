package com.example.agent.coverage

import com.example.agent.analysis.AnalysisResult
import com.example.agent.analysis.KotlinCodeAnalyzer
import com.example.agent.source.KotlinSourceFile
import java.io.File

object CoverageTestSupport {

    fun analysisOf(resource: String): AnalysisResult {
        val file = File("src/test/resources/$resource")
        return KotlinCodeAnalyzer().analyzeDetailed(file.path, listOf(KotlinSourceFile(file.path, file.readText())))
    }

    fun analysisOfCode(code: String, fileName: String = "Sample.kt"): AnalysisResult =
        KotlinCodeAnalyzer().analyzeDetailed("/tmp/$fileName", listOf(KotlinSourceFile("/tmp/$fileName", code.trimIndent())))
}
