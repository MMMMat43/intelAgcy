package com.example.agent.storage

import com.example.agent.api.PipelineService
import com.example.agent.api.RunRecordingListener
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class RunRepositoryTest {

    private class Unavailable : LlmClient {
        override fun complete(prompt: String): LlmResult = LlmResult.Failure("unavailable")
    }

    private fun record(source: String, at: Instant, status: RunStatus = RunStatus.COMPLETED) = RunRecord(
        sourcePath = source,
        startedAt = at,
        durationMillis = 10,
        status = status,
        totalBranches = 4,
        coveredBranches = 3,
        coverageMeasured = true
    )

    @Test
    fun `saved run can be read back by id`() {
        val repository = InMemoryRunRepository()
        val saved = repository.save(record("A.kt", Instant.parse("2026-01-01T10:00:00Z")))

        assertEquals(saved, repository.findById(saved.id))
        assertNull(repository.findById("missing"))
        assertEquals(1, repository.count())
    }

    @Test
    fun `runs are listed newest first`() {
        val repository = InMemoryRunRepository()
        repository.save(record("old.kt", Instant.parse("2026-01-01T10:00:00Z")))
        repository.save(record("new.kt", Instant.parse("2026-03-01T10:00:00Z")))
        repository.save(record("mid.kt", Instant.parse("2026-02-01T10:00:00Z")))

        assertEquals(listOf("new.kt", "mid.kt", "old.kt"), repository.findAll().map { it.sourcePath })
    }

    @Test
    fun `branch coverage is derived only from measured runs`() {
        val measured = record("A.kt", Instant.now())
        val unmeasured = measured.copy(coverageMeasured = false)
        val empty = measured.copy(totalBranches = 0, coveredBranches = 0)

        assertEquals(0.75, measured.branchCoverage!!, 0.0001)
        assertNull(unmeasured.branchCoverage)
        assertNull(empty.branchCoverage)
    }

    @Test
    fun `recording listener stores a completed run with per function data`() {
        val repository = InMemoryRunRepository()
        val service = PipelineService(
            llmClientFactory = { Unavailable() },
            listeners = listOf(RunRecordingListener(repository))
        )

        service.generateTests("src/test/resources/SampleCalculator.kt", null)

        val stored = repository.findAll().single()
        assertEquals(RunStatus.COMPLETED, stored.status)
        assertEquals(2, stored.functionsCount)
        assertEquals(7, stored.coveredBranches)
        assertEquals(7, stored.totalBranches)
        assertEquals(listOf("divide", "isPositive"), stored.functions.map { it.functionName })
        assertTrue(stored.functions.all { it.testCases > 0 })
        assertEquals(stored.testCasesCount, stored.functions.sumOf { it.testCases })
    }

    @Test
    fun `recording listener stores a failed run and the error is still thrown`() {
        val repository = InMemoryRunRepository()
        val service = PipelineService(
            llmClientFactory = { Unavailable() },
            listeners = listOf(RunRecordingListener(repository))
        )

        assertThrows<PipelineService.InvalidSourceException> {
            service.generateTests("src/test/resources/does-not-exist.kt", null)
        }

        val stored = repository.findAll().single()
        assertEquals(RunStatus.FAILED, stored.status)
        assertNotNull(stored.message)
        assertEquals(0, stored.functionsCount)
    }
}
