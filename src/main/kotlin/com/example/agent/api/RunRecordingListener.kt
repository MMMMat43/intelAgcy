package com.example.agent.api

import com.example.agent.coverage.BranchDescription
import com.example.agent.coverage.FunctionCoverage
import com.example.agent.model.TestCase
import com.example.agent.storage.BranchRecord
import com.example.agent.storage.BranchState
import com.example.agent.storage.FunctionRunRecord
import com.example.agent.storage.RunRecord
import com.example.agent.storage.RunRepository
import com.example.agent.storage.RunStatus
import com.example.agent.storage.TestCaseRecord

class RunRecordingListener(
    private val repository: RunRepository,
    private val projectName: String? = null
) : PipelineListener {

    private val caseCounts = ThreadLocal<MutableMap<String, Int>>()

    override fun onAnalysisCompleted(event: AnalysisCompleted) {
        caseCounts.set(LinkedHashMap())
    }

    override fun onFunctionProcessed(event: FunctionProcessed) {
        val counts = caseCounts.get() ?: LinkedHashMap<String, Int>().also { caseCounts.set(it) }
        counts[event.coverage.functionKey] = event.generatedCases
    }

    override fun onCompleted(event: PipelineCompleted) {
        val counts = caseCounts.get().orEmpty()
        caseCounts.remove()
        val casesByFunction = event.testCases.groupBy { "${it.className}.${it.functionName}(${it.signature})" }
        repository.save(
            RunRecord(
                sourcePath = event.sourcePath,
                startedAt = event.startedAt,
                durationMillis = event.durationMillis,
                status = RunStatus.COMPLETED,
                functionsCount = event.functionsCount,
                testCasesCount = event.testCasesCount,
                executedTestCases = event.executedTestCases,
                totalBranches = event.totalBranches,
                coveredBranches = event.coveredBranches,
                coverageMeasured = event.coverageMeasured,
                outputDir = event.outputDir,
                projectName = projectName,
                functions = event.functions.map { coverage ->
                    val cases = casesByFunction[coverage.functionKey].orEmpty()
                    val detail = event.functionDetails[coverage.functionKey]
                    FunctionRunRecord(
                        className = coverage.className,
                        functionName = coverage.functionName,
                        totalBranches = coverage.totalBranches,
                        coveredBranches = coverage.coveredBranches,
                        testCases = if (event.testCases.isEmpty()) counts[coverage.functionKey] ?: 0 else cases.size,
                        signature = coverage.functionKey.substringAfter('(', "").removeSuffix(")"),
                        complexity = detail?.complexity,
                        fileName = detail?.fileName,
                        packageName = detail?.packageName.orEmpty(),
                        branches = branchRecords(coverage),
                        cases = cases.map { testCaseRecord(it) }
                    )
                }
            )
        )
    }

    override fun onFailed(event: PipelineFailed) {
        caseCounts.remove()
        repository.save(
            RunRecord(
                sourcePath = event.sourcePath,
                startedAt = event.startedAt,
                durationMillis = event.durationMillis,
                status = RunStatus.FAILED,
                message = event.message,
                projectName = projectName
            )
        )
    }

    private fun branchRecords(coverage: FunctionCoverage): List<BranchRecord> =
        coverage.covered.map { it.toRecord(BranchState.COVERED) } +
            coverage.notFoundWithinBudget.map { it.toRecord(BranchState.NOT_FOUND) } +
            coverage.notInstrumentable.map { it.toRecord(BranchState.NOT_INSTRUMENTABLE) }

    private fun BranchDescription.toRecord(state: BranchState) = BranchRecord(id, label, state)

    private fun testCaseRecord(case: TestCase) = TestCaseRecord(
        code = case.id,
        scenarioType = case.type.name,
        origin = case.origin,
        description = case.description,
        expectedResult = case.expectedResult,
        inputs = case.inputData
    )
}
