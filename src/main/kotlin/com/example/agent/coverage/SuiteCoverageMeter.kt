package com.example.agent.coverage

import com.example.agent.analysis.AnalysisResult
import com.example.agent.execution.ConversionResult
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.TypeConversion
import com.example.agent.model.FunctionInfo
import com.example.agent.model.TestCase
import com.example.agent.model.isTestable
import com.example.agent.model.key
import com.example.agent.model.signature

class SuiteCoverageMeter {

    fun measure(analysis: AnalysisResult, cases: List<TestCase>): CoverageReport? {
        val creation = InstrumentedRunner.create(analysis) as? RunnerCreation.Ready ?: return null
        return creation.runner.use { runner ->
            val testable = analysis.functions.filter { it.info.isTestable() }
            val functionInfos = testable.map { it.info }
            val coveredByFunction = HashMap<String, MutableSet<Int>>()

            cases.forEach { testCase ->
                val function = find(functionInfos, testCase) ?: return@forEach
                if (!convertible(function, testCase)) return@forEach
                val result = runner.run(function, testCase)
                if (result.outcome !is ExecutionOutcome.CouldNotExecute) {
                    coveredByFunction.getOrPut(function.key()) { HashSet() } += result.covered
                }
            }

            val reports = testable.map { analyzed ->
                val key = analyzed.info.key()
                val probes = runner.probes.filter { it.functionKey == key }
                val instrumentable = probes.filter { it.instrumentable }
                val hit = coveredByFunction[key].orEmpty()
                val covered = instrumentable.filter { it.index in hit }
                val notFound = instrumentable.filter { it.index !in hit }
                FunctionCoverage(
                    functionKey = key,
                    className = analyzed.info.className,
                    functionName = analyzed.info.name,
                    totalBranches = instrumentable.size,
                    coveredBranches = covered.size,
                    branchCoverage = ratio(covered.size, instrumentable.size),
                    covered = covered.map { BranchDescription(it.id, it.label) },
                    notFoundWithinBudget = notFound.map { BranchDescription(it.id, it.label) },
                    notInstrumentable = probes.filter { !it.instrumentable }.map { BranchDescription(it.id, it.label) },
                    executed = hit.isNotEmpty()
                )
            }
            val total = reports.sumOf { it.totalBranches }
            val covered = reports.sumOf { it.coveredBranches }
            if (total == 0) emptyCoverageReport() else CoverageReport(true, total, covered, ratio(covered, total), reports)
        }
    }

    private fun convertible(function: FunctionInfo, testCase: TestCase): Boolean =
        function.parameters.all {
            TypeConversion.convert(it.type, it.nullable, testCase.inputData[it.name]) is ConversionResult.Converted
        }

    private fun find(functions: List<FunctionInfo>, testCase: TestCase): FunctionInfo? {
        val candidates = functions.filter { it.className == testCase.className && it.name == testCase.functionName }
        if (testCase.signature.isNotEmpty()) {
            candidates.firstOrNull { it.signature() == testCase.signature }?.let { return it }
        }
        return candidates.firstOrNull()
    }
}
