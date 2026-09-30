package com.example.agent.coverage

import com.example.agent.analysis.AnalysisResult
import com.example.agent.execution.CompilationResult
import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.KotlinInMemoryCompiler
import com.example.agent.execution.TestCaseExecutor
import com.example.agent.execution.dispose
import com.example.agent.model.FunctionInfo
import com.example.agent.model.TestCase
import java.util.concurrent.atomic.AtomicReference

class RunResult(val outcome: ExecutionOutcome, val covered: Set<Int>)

sealed class RunnerCreation {
    class Ready(val runner: InstrumentedRunner) : RunnerCreation()
    class Unavailable(val reason: String) : RunnerCreation()
}

class InstrumentedRunner private constructor(
    private val compiled: CompilationResult.Success,
    val probes: List<BranchProbe>,
    private val executor: TestCaseExecutor
) : AutoCloseable {

    private val handle = CoverageProbeHandle(compiled.classLoader)

    fun run(function: FunctionInfo, testCase: TestCase): RunResult {
        val session = AtomicReference<ProbeSession?>(null)
        val outcome = executor.execute(
            compiled.classLoader,
            function,
            testCase,
            onWorkerStart = { session.set(handle.begin()) },
            onTimeout = { session.get()?.cancel() }
        )
        return RunResult(outcome, session.get()?.snapshot() ?: emptySet())
    }

    override fun close() {
        compiled.dispose()
    }

    companion object {
        fun create(analysis: AnalysisResult, executionTimeoutMillis: Long = 3_000): RunnerCreation {
            val program = try {
                BranchInstrumenter().instrument(analysis)
            } catch (t: Throwable) {
                return RunnerCreation.Unavailable("instrumentation failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            return when (val compilation = KotlinInMemoryCompiler().compile(program.units)) {
                is CompilationResult.Failure ->
                    RunnerCreation.Unavailable(compilation.diagnostics.take(3).joinToString(" | "))
                is CompilationResult.Success -> try {
                    RunnerCreation.Ready(InstrumentedRunner(compilation, program.probes, TestCaseExecutor(executionTimeoutMillis)))
                } catch (t: Throwable) {
                    compilation.dispose()
                    RunnerCreation.Unavailable("probe initialization failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
    }
}
