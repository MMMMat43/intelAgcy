package com.example.agent.storage

import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class RunStatus { COMPLETED, FAILED }

data class FunctionRunRecord(
    val className: String,
    val functionName: String,
    val totalBranches: Int,
    val coveredBranches: Int,
    val testCases: Int
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
    val functions: List<FunctionRunRecord> = emptyList()
) {
    val branchCoverage: Double?
        get() = if (coverageMeasured && totalBranches > 0) coveredBranches.toDouble() / totalBranches else null
}

interface RunRepository {
    fun save(record: RunRecord): RunRecord
    fun findById(id: String): RunRecord?
    fun findAll(): List<RunRecord>
    fun count(): Int
}

class InMemoryRunRepository : RunRepository {

    private val lock = ReentrantLock()
    private val records = LinkedHashMap<String, RunRecord>()

    override fun save(record: RunRecord): RunRecord = lock.withLock {
        records[record.id] = record
        record
    }

    override fun findById(id: String): RunRecord? = lock.withLock { records[id] }

    override fun findAll(): List<RunRecord> = lock.withLock { records.values.sortedByDescending { it.startedAt } }

    override fun count(): Int = lock.withLock { records.size }
}
