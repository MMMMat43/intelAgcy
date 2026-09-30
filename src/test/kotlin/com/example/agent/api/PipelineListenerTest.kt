package com.example.agent.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

class PipelineListenerTest {

    private class Recorder : PipelineListener {
        val events = mutableListOf<String>()
        var completed: PipelineCompleted? = null
        var failed: PipelineFailed? = null

        override fun onAnalysisCompleted(event: AnalysisCompleted) {
            events += "analysis"
        }

        override fun onFunctionProcessed(event: FunctionProcessed) {
            events += "function:${event.coverage.functionName}"
        }

        override fun onSuiteGenerated(event: SuiteGenerated) {
            events += "suite"
        }

        override fun onArtifactsSaved(event: ArtifactsSaved) {
            events += "artifacts"
        }

        override fun onCompleted(event: PipelineCompleted) {
            events += "completed"
            completed = event
        }

        override fun onFailed(event: PipelineFailed) {
            events += "failed"
            failed = event
        }
    }

    private class Exploding : PipelineListener {
        override fun onAnalysisCompleted(event: AnalysisCompleted) = throw IllegalStateException("listener failure")
        override fun onCompleted(event: PipelineCompleted) = throw IllegalStateException("listener failure")
    }

    private fun service(vararg listeners: PipelineListener) =
        PipelineService(valueProviderFactory = { null }, listeners = listeners.toList())

    @Test
    fun `listener receives stage events in pipeline order`(@TempDir out: Path) {
        val recorder = Recorder()

        service(recorder).generateTests("src/test/resources/SampleCalculator.kt", out.toString())

        assertEquals("analysis", recorder.events.first())
        assertEquals("completed", recorder.events.last())
        val functions = recorder.events.filter { it.startsWith("function:") }
        assertEquals(listOf("function:divide", "function:isPositive"), functions)
        val order = listOf("analysis", "function:divide", "function:isPositive", "suite", "artifacts", "completed")
        assertEquals(order, recorder.events)
    }

    @Test
    fun `completed event carries measured coverage`() {
        val recorder = Recorder()

        service(recorder).generateTests("src/test/resources/SampleCalculator.kt", null)

        val completed = recorder.completed!!
        assertEquals(2, completed.functionsCount)
        assertEquals(7, completed.totalBranches)
        assertEquals(7, completed.coveredBranches)
        assertTrue(completed.coverageMeasured)
        assertEquals(2, completed.functions.size)
        assertTrue(recorder.events.none { it == "artifacts" })
    }

    @Test
    fun `failing listener never breaks the pipeline`() {
        val recorder = Recorder()

        val result = service(Exploding(), recorder).generateTests("src/test/resources/SampleCalculator.kt", null)

        assertEquals(2, result.functionsCount)
        assertEquals("completed", recorder.events.last())
    }

    @Test
    fun `invalid source produces failed event and keeps the original exception`() {
        val recorder = Recorder()

        assertThrows<PipelineService.InvalidSourceException> {
            service(recorder).generateTests("src/test/resources/does-not-exist.kt", null)
        }

        assertEquals(listOf("failed"), recorder.events)
        assertTrue(recorder.failed!!.message.isNotBlank())
    }

    @Test
    fun `console listener prints progress lines`() {
        val buffer = ByteArrayOutputStream()
        val listener = ConsoleProgressListener(PrintStream(buffer, true, "UTF-8"))

        service(listener).generateTests("src/test/resources/SampleCalculator.kt", null)

        val text = buffer.toString("UTF-8")
        assertTrue(text.contains("[progress]"))
        assertTrue(text.contains("SampleCalculator.divide"))
        assertTrue(text.contains("Готово"))
    }

    @Test
    fun `pipeline without listeners behaves as before`() {
        val result = PipelineService(valueProviderFactory = { null })
            .generateTests("src/test/resources/SampleCalculator.kt", null)

        assertEquals(2, result.functionsCount)
        assertEquals(7, result.coverage!!.coveredBranches)
    }
}
