package com.example.agent.storage

import com.example.agent.coverage.CoverageReport
import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.squareup.kotlinpoet.FileSpec

interface ArtifactStore {
    fun saveAnalysis(structure: CodeStructure)
    fun saveTestCases(testSuite: TestSuiteResult)
    fun saveCoverage(report: CoverageReport)
    fun saveGeneratedTestCode(fileSpec: FileSpec)
}
