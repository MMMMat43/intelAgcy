package com.example.agent.storage

import com.example.agent.api.PipelineService
import com.example.agent.api.RunRecordingListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant

class SqliteRunRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun dbFile(): String = tempDir.resolve("history.db").toString()

    private fun sampleRecord(id: String = "run-1") = RunRecord(
        id = id,
        sourcePath = "C:/src/Demo.kt",
        startedAt = Instant.parse("2026-07-01T10:00:00Z"),
        durationMillis = 1200,
        status = RunStatus.COMPLETED,
        functionsCount = 1,
        testCasesCount = 2,
        executedTestCases = 2,
        totalBranches = 3,
        coveredBranches = 2,
        coverageMeasured = true,
        outputDir = "out/demo",
        projectName = "Demo",
        functions = listOf(
            FunctionRunRecord(
                className = "Demo",
                functionName = "work",
                totalBranches = 3,
                coveredBranches = 2,
                testCases = 2,
                signature = "x: Int",
                complexity = 3,
                fileName = "Demo.kt",
                packageName = "demo",
                branches = listOf(
                    BranchRecord("Demo.work#1:if-then", "if (x > 0)", BranchState.COVERED),
                    BranchRecord("Demo.work#2:if-else", "else of if (x > 0)", BranchState.COVERED),
                    BranchRecord("Demo.work#3:loop", "for (i in 0..x)", BranchState.NOT_FOUND)
                ),
                cases = listOf(
                    TestCaseRecord("work-1", "POSITIVE", "HEURISTIC", "typical", "returns 1", mapOf("x" to "1")),
                    TestCaseRecord("work-2", "BOUNDARY", "BOUNDARY", "boundary", "returns 0", mapOf("x" to "0"))
                )
            )
        )
    )

    @Test
    fun savedRunIsReadBackWithAllNestedRows() {
        val repository = SqliteRunRepository(dbFile())
        repository.save(sampleRecord())

        val loaded = repository.findById("run-1")

        assertNotNull(loaded)
        assertEquals(RunStatus.COMPLETED, loaded!!.status)
        assertEquals("Demo", loaded.projectName)
        assertEquals(1, loaded.functions.size)
        val function = loaded.functions.single()
        assertEquals("work", function.functionName)
        assertEquals(3, function.complexity)
        assertEquals("Demo.kt", function.fileName)
        assertEquals(3, function.branches.size)
        assertEquals(BranchState.NOT_FOUND, function.branches.last().state)
        assertEquals(2, function.cases.size)
        assertEquals(mapOf("x" to "0"), function.cases.last().inputs)
        assertEquals("out/demo", loaded.outputDir)
    }

    @Test
    fun countAndFindAllReflectSavedRuns() {
        val repository = SqliteRunRepository(dbFile())
        repository.save(sampleRecord("a"))
        repository.save(sampleRecord("b").copy(startedAt = Instant.parse("2026-07-02T10:00:00Z")))

        assertEquals(2, repository.count())
        assertEquals(listOf("b", "a"), repository.findAll().map { it.id })
        assertNull(repository.findById("missing"))
    }

    @Test
    fun foreignKeysAreEnforcedAndDatabaseIsConsistent() {
        val file = dbFile()
        val repository = SqliteRunRepository(file)
        repository.save(sampleRecord())

        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    rows.next()
                    assertEquals("ok", rows.getString(1))
                }
                statement.executeQuery("PRAGMA foreign_key_check").use { rows -> assertTrue(!rows.next()) }
            }
            val failed = runCatching {
                connection.createStatement().use {
                    it.execute("INSERT INTO function_info (run_id, class_name, function_name) VALUES ('no-such-run', 'A', 'b')")
                }
            }
            assertTrue(failed.isFailure)
        }
    }

    @Test
    fun deletingRunCascadesToChildren() {
        val file = dbFile()
        val repository = SqliteRunRepository(file)
        repository.save(sampleRecord())

        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            connection.createStatement().use { it.executeUpdate("DELETE FROM run WHERE id = 'run-1'") }
            listOf("function_info", "branch", "test_case", "test_input", "artifact").forEach { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                        rows.next()
                        assertEquals(0, rows.getInt(1), table)
                    }
                }
            }
        }
    }

    @Test
    fun unknownOriginFallsBackToSearchAndSchemaRejectsNothingElse() {
        val repository = SqliteRunRepository(dbFile())
        val record = sampleRecord().let {
            it.copy(functions = it.functions.map { f ->
                f.copy(cases = f.cases.map { c -> c.copy(origin = "SOMETHING_ELSE") })
            })
        }
        repository.save(record)

        val origins = repository.findById("run-1")!!.functions.single().cases.map { it.origin }.toSet()
        assertEquals(setOf("SEARCH"), origins)
    }

    @Test
    fun pipelineRecordsRealRunIntoDatabase() {
        val source = Path.of("src/test/resources/SampleCalculator.kt")
        assertTrue(Files.exists(source))
        val repository = SqliteRunRepository(dbFile())
        val service = PipelineService(listeners = listOf(RunRecordingListener(repository)))

        val result = service.generateTests(source.toString(), tempDir.resolve("out").toString())

        assertEquals(1, repository.count())
        val saved = repository.findAll().single()
        assertEquals(RunStatus.COMPLETED, saved.status)
        assertTrue(saved.functions.isNotEmpty())
        assertEquals(result.response.functionsCount, saved.functions.size)
        assertTrue(saved.functions.sumOf { it.cases.size } > 0)
        assertTrue(saved.functions.all { it.branches.size == it.totalBranches })
        assertEquals(setOf("SampleCalculator"), saved.functions.map { it.className }.toSet())
    }

    @Test
    fun schemaResourceMatchesLabCopy() {
        val resource = SqliteRunRepository::class.java.getResourceAsStream("/db/schema.sql")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(resource.contains("CREATE TABLE IF NOT EXISTS function_info"))
        assertTrue(!resource.contains("llm", ignoreCase = true))
    }
}
