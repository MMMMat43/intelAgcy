package com.example.agent.api

import java.io.PrintStream

class ConsoleProgressListener(private val out: PrintStream = System.out) : PipelineListener {

    override fun onAnalysisCompleted(event: AnalysisCompleted) {
        out.println("[progress] Анализ завершён: функций ${event.functionsCount}, пригодных к тестированию ${event.testableFunctions}")
    }

    override fun onFunctionProcessed(event: FunctionProcessed) {
        val coverage = event.coverage
        out.println(
            "[progress] ${coverage.className}.${coverage.functionName}: " +
                "ветки ${coverage.coveredBranches}/${coverage.totalBranches}, тест-кейсов ${event.generatedCases}"
        )
    }

    override fun onSuiteGenerated(event: SuiteGenerated) {
        out.println("[progress] Набор тестов сформирован: ${event.testCasesCount} кейсов, выполнено ${event.executedTestCases}")
    }

    override fun onArtifactsSaved(event: ArtifactsSaved) {
        out.println("[progress] Артефакты сохранены: ${event.outputDir}")
    }

    override fun onCompleted(event: PipelineCompleted) {
        out.println("[progress] Готово за ${event.durationMillis} мс")
    }

    override fun onFailed(event: PipelineFailed) {
        out.println("[progress] Ошибка: ${event.message}")
    }
}
