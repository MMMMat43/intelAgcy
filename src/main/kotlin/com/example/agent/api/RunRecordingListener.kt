package com.example.agent.api

import com.example.agent.storage.FunctionRunRecord
import com.example.agent.storage.RunRecord
import com.example.agent.storage.RunRepository
import com.example.agent.storage.RunStatus

class RunRecordingListener(private val repository: RunRepository) : PipelineListener {

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
                functions = event.functions.map {
                    FunctionRunRecord(
                        className = it.className,
                        functionName = it.functionName,
                        totalBranches = it.totalBranches,
                        coveredBranches = it.coveredBranches,
                        testCases = counts[it.functionKey] ?: 0
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
                message = event.message
            )
        )
    }
}
