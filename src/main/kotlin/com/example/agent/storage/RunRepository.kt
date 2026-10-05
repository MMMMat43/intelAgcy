package com.example.agent.storage

import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class RunStatus { COMPLETED, FAILED }

enum class BranchState { COVERED, NOT_FOUND, NOT_INSTRUMENTABLE }

data class BranchRecord(
    val code: String,
    val label: String,
    val state: BranchState
)

data class TestCaseRecord(
    val code: String,
    val scenarioType: String,
    val origin: String,
    val description: String,
    val expectedResult: String?,
    val inputs: Map<String, String?>
)

data class FunctionRunRecord(
    val className: String,
    val functionName: String,
    val totalBranches: Int,
    val coveredBranches: Int,
    val testCases: Int,
    val signature: String = "",
    val complexity: Int? = null,
    val fileName: String? = null,
    val packageName: String = "",
    val branches: List<BranchRecord> = emptyList(),
    val cases: List<TestCaseRecord> = emptyList()
)

data class RunRecord(
    val id: String = UUID.randomUUID().toString(),
    val sourcePath: String,
    val startedAt: Instant,
    val durationMillis: Long,
    val status: RunStatus,
    val functionsCount: Int = 0,
    val testCasesCount: Int = 0,
    val executedTestCases: Int = 0,
    val totalBranches: Int = 0,
    val coveredBranches: Int = 0,
    val coverageMeasured: Boolean = false,
    val outputDir: String? = null,
    val message: String? = null,
    val functions: List<FunctionRunRecord> = emptyList(),
    val projectName: String? = null
) {
    val branchCoverage: Double?
        get() = if (coverageMeasured && totalBranches > 0) coveredBranches.toDouble() / totalBranches else null
}

data class RunNote(
    val id: Long,
    val runId: String,
    val createdAt: Instant,
    val text: String
)

interface RunRepository {
    fun save(record: RunRecord): RunRecord
    fun findById(id: String): RunRecord?
    fun findAll(): List<RunRecord>
    fun count(): Int
    fun deleteById(id: String): Boolean
    fun addNote(runId: String, text: String, createdAt: Instant): RunNote
    fun notes(runId: String): List<RunNote>
}

class InMemoryRunRepository : RunRepository {

    private val lock = ReentrantLock()
    private val records = LinkedHashMap<String, RunRecord>()
    private val runNotes = mutableListOf<RunNote>()
    private var nextNoteId = 1L

    override fun save(record: RunRecord): RunRecord = lock.withLock {
        records[record.id] = record
        record
    }

    override fun findById(id: String): RunRecord? = lock.withLock { records[id] }

    override fun findAll(): List<RunRecord> = lock.withLock { records.values.sortedByDescending { it.startedAt } }

    override fun count(): Int = lock.withLock { records.size }

    override fun deleteById(id: String): Boolean = lock.withLock {
        val removed = records.remove(id) != null
        if (removed) runNotes.removeAll { it.runId == id }
        removed
    }

    override fun addNote(runId: String, text: String, createdAt: Instant): RunNote = lock.withLock {
        require(runId in records) { "Run not found: $runId" }
        require(text.isNotBlank()) { "Note text must not be blank" }
        RunNote(nextNoteId++, runId, createdAt, text).also { runNotes += it }
    }

    override fun notes(runId: String): List<RunNote> = lock.withLock {
        runNotes.filter { it.runId == runId }.sortedWith(compareBy<RunNote> { it.createdAt }.thenBy { it.id })
    }
}
