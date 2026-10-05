package com.example.agent.history

import com.example.agent.storage.BranchRecord
import com.example.agent.storage.BranchState
import com.example.agent.storage.FunctionRunRecord
import com.example.agent.storage.RunRecord
import com.example.agent.storage.RunStatus
import com.example.agent.storage.SqliteRunRepository
import com.example.agent.storage.TestCaseRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HistoryServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var dbFile: String
    private lateinit var repository: SqliteRunRepository
    private lateinit var service: HistoryService

    private fun function(name: String, complexity: Int, covered: Int, total: Int, cases: Int) = FunctionRunRecord(
        className = "Calc",
        functionName = name,
        totalBranches = total,
        coveredBranches = covered,
        testCases = cases,
        signature = "x: Int",
        complexity = complexity,
        fileName = "Calc.kt",
        branches = (1..total).map { index ->
            BranchRecord("Calc.$name#$index:if-then", "if (x > $index)", if (index <= covered) BranchState.COVERED else BranchState.NOT_FOUND)
        },
        cases = (1..cases).map { index ->
            TestCaseRecord("$name-$index", if (index == 1) "POSITIVE" else "BOUNDARY", "BOUNDARY", "case $index", "$index", mapOf("x" to "$index"))
        }
    )

    private fun run(id: String, project: String, day: Int, vararg functions: FunctionRunRecord) = RunRecord(
        id = id,
        sourcePath = "src/$project.kt",
        startedAt = Instant.parse("2026-07-0${day}T10:00:00Z"),
        durationMillis = 1000L * day,
        status = RunStatus.COMPLETED,
        coverageMeasured = true,
        projectName = project,
        functions = functions.toList()
    )

    @BeforeEach
    fun setUp() {
        dbFile = tempDir.resolve("history.db").toString()
        repository = SqliteRunRepository(dbFile)
        repository.save(run("full", "Orders", 1, function("discount", 7, 4, 4, 3), function("validate", 4, 2, 2, 2)))
        repository.save(run("partial", "Strings", 2, function("normalize", 3, 1, 4, 1)))
        repository.save(run("again", "Orders", 3, function("discount", 9, 3, 4, 2)))
        service = HistoryService(repository, Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun runsAreListedNewestFirstWithAggregates() {
        val runs = service.runs()

        assertEquals(listOf("again", "partial", "full"), runs.map { it.runId })
        val full = runs.single { it.runId == "full" }
        assertEquals(2, full.functions)
        assertEquals(5, full.testCases)
        assertEquals(6, full.coveredBranches)
        assertEquals(6, full.totalBranches)
        assertEquals(1.0, full.coverage)
        assertEquals(listOf("Orders", "Strings"), service.projects())
    }

    @Test
    fun filtersByProjectCoverageAndUncoveredBranches() {
        assertEquals(setOf("full", "again"), service.runs(RunFilter(project = "Orders")).map { it.runId }.toSet())
        assertEquals(listOf("full"), service.runs(RunFilter(minCoverage = 0.9)).map { it.runId })
        assertEquals(listOf("partial"), service.runs(RunFilter(maxCoverage = 0.5)).map { it.runId })
        assertEquals(setOf("again", "partial"), service.runs(RunFilter(onlyWithUncovered = true)).map { it.runId }.toSet())
        assertEquals(listOf("again"), service.runs(RunFilter(project = "Orders", onlyWithUncovered = true)).map { it.runId })
    }

    @Test
    fun statisticsSummariseTheSelection() {
        val all = service.statistics(topComplex = 2)

        assertEquals(3, all.runs)
        assertEquals(2, all.projects)
        assertEquals((1.0 + 0.25 + 0.75) / 3, all.averageCoverage!!, 1e-9)
        assertEquals(8, all.totalTestCases)
        assertEquals(4, all.uncoveredBranches)
        assertEquals(listOf(9, 7), all.mostComplex.map { it.complexity })
        assertEquals("Calc.discount", all.mostComplex.first().function)

        val strings = service.statistics(RunFilter(project = "Strings"))
        assertEquals(1, strings.runs)
        assertEquals(0.25, strings.averageCoverage!!, 1e-9)
    }

    @Test
    fun detailsContainFunctionsBranchesAndCases() {
        val details = service.details("partial")

        assertNotNull(details)
        assertEquals("src/Strings.kt", details!!.sourcePath)
        assertEquals(1, details.functions.size)
        assertEquals(0.25, details.functions.single().coverage)
        assertEquals(4, details.branches.size)
        assertEquals(3, details.branches.count { HistoryService.isUncovered(it.state) })
        assertEquals("Calc.normalize", details.branches.first().function)
        assertEquals(1, details.testCases.size)
        assertEquals("x = 1", details.testCases.single().inputs)
        assertNull(service.details("missing"))
    }

    @Test
    fun notesAreStoredTrimmedAndRejectedWhenBlank() {
        val note = service.addNote("full", "  Проверено вручную  ")

        assertEquals("Проверено вручную", note.text)
        assertEquals(Instant.parse("2026-07-10T12:00:00Z"), note.createdAt)
        assertEquals(listOf("Проверено вручную"), service.notes("full").map { it.text })
        assertTrue(service.notes("partial").isEmpty())
        assertThrows<IllegalArgumentException> { service.addNote("full", "   ") }
        assertThrows<IllegalArgumentException> { service.addNote("missing", "text") }
    }

    @Test
    fun deletingRunRemovesItsNotesAndChildRows() {
        service.addNote("full", "Первая заметка")
        service.addNote("full", "Вторая заметка")

        assertTrue(service.deleteRun("full"))
        assertFalse(service.deleteRun("full"))

        assertEquals(listOf("again", "partial"), service.runs().map { it.runId })
        DriverManager.getConnection("jdbc:sqlite:$dbFile").use { connection ->
            connection.createStatement().use { statement ->
                listOf(
                    "SELECT COUNT(*) FROM note WHERE run_id = 'full'",
                    "SELECT COUNT(*) FROM function_info WHERE run_id = 'full'",
                    "SELECT COUNT(*) FROM note"
                ).forEach { sql ->
                    statement.executeQuery(sql).use { rows ->
                        rows.next()
                        assertEquals(0, rows.getInt(1), sql)
                    }
                }
            }
        }
    }

    @Test
    fun noteTableRejectsBlankTextAtDatabaseLevel() {
        DriverManager.getConnection("jdbc:sqlite:$dbFile").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            val blank = runCatching {
                connection.createStatement().use { it.execute("INSERT INTO note (run_id, created_at, text) VALUES ('full', '2026-07-01T00:00:00Z', '  ')") }
            }
            val orphan = runCatching {
                connection.createStatement().use { it.execute("INSERT INTO note (run_id, created_at, text) VALUES ('none', '2026-07-01T00:00:00Z', 'x')") }
            }
            assertTrue(blank.isFailure)
            assertTrue(orphan.isFailure)
        }
    }
}
