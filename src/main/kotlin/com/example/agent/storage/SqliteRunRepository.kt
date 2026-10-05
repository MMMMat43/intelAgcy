package com.example.agent.storage

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

class SqliteRunRepository(private val databasePath: String) : RunRepository {

    private val lock = Any()

    init {
        connect().use { connection ->
            schemaStatements().forEach { statement ->
                connection.createStatement().use { it.execute(statement) }
            }
        }
    }

    private fun connect(): Connection {
        val connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        return connection
    }

    override fun save(record: RunRecord): RunRecord = synchronized(lock) {
        connect().use { connection ->
            connection.autoCommit = false
            try {
                insertRun(connection, record)
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
        record
    }

    override fun findById(id: String): RunRecord? = synchronized(lock) {
        connect().use { connection -> readRuns(connection, "WHERE r.id = ?", id).firstOrNull() }
    }

    override fun findAll(): List<RunRecord> = synchronized(lock) {
        connect().use { connection -> readRuns(connection, "", null).sortedByDescending { it.startedAt } }
    }

    override fun count(): Int = synchronized(lock) {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM run").use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }
        }
    }

    override fun deleteById(id: String): Boolean = synchronized(lock) {
        connect().use { connection ->
            connection.prepareStatement("DELETE FROM run WHERE id = ?").use {
                it.setString(1, id)
                it.executeUpdate() > 0
            }
        }
    }

    override fun addNote(runId: String, text: String, createdAt: Instant): RunNote = synchronized(lock) {
        require(text.isNotBlank()) { "Note text must not be blank" }
        connect().use { connection ->
            connection.prepareStatement(
                "INSERT INTO note (run_id, created_at, text) VALUES (?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { statement ->
                statement.setString(1, runId)
                statement.setString(2, createdAt.toString())
                statement.setString(3, text)
                statement.executeUpdate()
                val id = statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
                RunNote(id, runId, createdAt, text)
            }
        }
    }

    override fun notes(runId: String): List<RunNote> = synchronized(lock) {
        connect().use { connection ->
            connection.prepareStatement("SELECT id, run_id, created_at, text FROM note WHERE run_id = ? ORDER BY created_at, id").use {
                it.setString(1, runId)
                it.executeQuery().use { rows ->
                    val result = mutableListOf<RunNote>()
                    while (rows.next()) {
                        result += RunNote(rows.getLong(1), rows.getString(2), Instant.parse(rows.getString(3)), rows.getString(4))
                    }
                    result
                }
            }
        }
    }

    private fun insertRun(connection: Connection, record: RunRecord) {
        val projectId = projectId(connection, projectNameOf(record), record.startedAt)
        connection.prepareStatement(
            "INSERT OR REPLACE INTO run (id, project_id, source_path, started_at, duration_ms, status, coverage_measured, message) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, record.id)
            statement.setLong(2, projectId)
            statement.setString(3, record.sourcePath)
            statement.setString(4, record.startedAt.toString())
            statement.setLong(5, record.durationMillis)
            statement.setString(6, record.status.name)
            statement.setInt(7, if (record.coverageMeasured) 1 else 0)
            statement.setString(8, record.message)
            statement.executeUpdate()
        }

        record.functions.forEach { function ->
            val fileId = function.fileName?.let { sourceFileId(connection, projectId, it, function.packageName) }
            val functionId = insertFunction(connection, record.id, fileId, function)
            function.branches.forEach { insertBranch(connection, functionId, it) }
            function.cases.forEach { insertCase(connection, functionId, it) }
        }

        record.outputDir?.let { dir ->
            mapOf(
                "ANALYSIS" to "$dir/analysis.json",
                "TEST_CASES" to "$dir/test-cases.json",
                "COVERAGE" to "$dir/coverage.json",
                "TESTS_SOURCE" to "$dir/generated-tests"
            ).forEach { (kind, path) ->
                connection.prepareStatement("INSERT OR REPLACE INTO artifact (run_id, kind, path) VALUES (?, ?, ?)").use {
                    it.setString(1, record.id)
                    it.setString(2, kind)
                    it.setString(3, path)
                    it.executeUpdate()
                }
            }
        }
    }

    private fun projectNameOf(record: RunRecord): String {
        record.projectName?.takeIf { it.isNotBlank() }?.let { return it }
        val normalized = record.sourcePath.trimEnd('/', '\\')
        val last = normalized.substringAfterLast('/').substringAfterLast('\\')
        return last.ifBlank { normalized.ifBlank { "unnamed" } }
    }

    private fun projectId(connection: Connection, name: String, createdAt: Instant): Long {
        connection.prepareStatement("INSERT OR IGNORE INTO project (name, created_at) VALUES (?, ?)").use {
            it.setString(1, name)
            it.setString(2, createdAt.toString())
            it.executeUpdate()
        }
        return connection.prepareStatement("SELECT id FROM project WHERE name = ?").use {
            it.setString(1, name)
            it.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
        }
    }

    private fun sourceFileId(connection: Connection, projectId: Long, fileName: String, packageName: String): Long {
        connection.prepareStatement("INSERT OR IGNORE INTO source_file (project_id, file_name, package_name) VALUES (?, ?, ?)").use {
            it.setLong(1, projectId)
            it.setString(2, fileName)
            it.setString(3, packageName)
            it.executeUpdate()
        }
        return connection.prepareStatement("SELECT id FROM source_file WHERE project_id = ? AND file_name = ?").use {
            it.setLong(1, projectId)
            it.setString(2, fileName)
            it.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
        }
    }

    private fun insertFunction(connection: Connection, runId: String, fileId: Long?, function: FunctionRunRecord): Long {
        connection.prepareStatement(
            "INSERT INTO function_info (run_id, source_file_id, class_name, function_name, signature, complexity, total_branches, covered_branches) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { statement ->
            statement.setString(1, runId)
            if (fileId == null) statement.setNull(2, java.sql.Types.INTEGER) else statement.setLong(2, fileId)
            statement.setString(3, function.className)
            statement.setString(4, function.functionName)
            statement.setString(5, function.signature)
            if (function.complexity == null) statement.setNull(6, java.sql.Types.INTEGER) else statement.setInt(6, function.complexity)
            statement.setInt(7, function.totalBranches)
            statement.setInt(8, function.coveredBranches)
            statement.executeUpdate()
            return statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }
    }

    private fun insertBranch(connection: Connection, functionId: Long, branch: BranchRecord) {
        connection.prepareStatement("INSERT INTO branch (function_id, code, label, state) VALUES (?, ?, ?, ?)").use {
            it.setLong(1, functionId)
            it.setString(2, branch.code)
            it.setString(3, branch.label)
            it.setString(4, branch.state.name)
            it.executeUpdate()
        }
    }

    private fun insertCase(connection: Connection, functionId: Long, case: TestCaseRecord) {
        val origin = if (case.origin in KNOWN_ORIGINS) case.origin else "SEARCH"
        val caseId = connection.prepareStatement(
            "INSERT INTO test_case (function_id, code, scenario_type_id, origin_id, description, expected_result) " +
                "VALUES (?, ?, (SELECT id FROM scenario_type WHERE name = ?), (SELECT id FROM case_origin WHERE name = ?), ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { statement ->
            statement.setLong(1, functionId)
            statement.setString(2, case.code)
            statement.setString(3, case.scenarioType)
            statement.setString(4, origin)
            statement.setString(5, case.description)
            statement.setString(6, case.expectedResult)
            statement.executeUpdate()
            statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }
        case.inputs.forEach { (name, value) ->
            connection.prepareStatement("INSERT INTO test_input (test_case_id, param_name, param_value) VALUES (?, ?, ?)").use {
                it.setLong(1, caseId)
                it.setString(2, name)
                it.setString(3, value)
                it.executeUpdate()
            }
        }
    }

    private fun readRuns(connection: Connection, where: String, argument: String?): List<RunRecord> {
        val runs = mutableListOf<RunRecord>()
        connection.prepareStatement(
            "SELECT r.id, r.source_path, r.started_at, r.duration_ms, r.status, r.coverage_measured, r.message, p.name " +
                "FROM run r JOIN project p ON p.id = r.project_id $where"
        ).use { statement ->
            if (argument != null) statement.setString(1, argument)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val id = rows.getString(1)
                    val functions = readFunctions(connection, id)
                    runs += RunRecord(
                        id = id,
                        sourcePath = rows.getString(2),
                        startedAt = Instant.parse(rows.getString(3)),
                        durationMillis = rows.getLong(4),
                        status = RunStatus.valueOf(rows.getString(5)),
                        functionsCount = functions.size,
                        testCasesCount = functions.sumOf { it.cases.size },
                        executedTestCases = functions.sumOf { it.cases.size },
                        totalBranches = functions.sumOf { it.totalBranches },
                        coveredBranches = functions.sumOf { it.coveredBranches },
                        coverageMeasured = rows.getInt(6) == 1,
                        outputDir = readOutputDir(connection, id),
                        message = rows.getString(7),
                        functions = functions,
                        projectName = rows.getString(8)
                    )
                }
            }
        }
        return runs
    }

    private fun readOutputDir(connection: Connection, runId: String): String? =
        connection.prepareStatement("SELECT path FROM artifact WHERE run_id = ? AND kind = 'ANALYSIS'").use {
            it.setString(1, runId)
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1).removeSuffix("/analysis.json") else null }
        }

    private fun readFunctions(connection: Connection, runId: String): List<FunctionRunRecord> {
        val result = mutableListOf<FunctionRunRecord>()
        connection.prepareStatement(
            "SELECT f.id, f.class_name, f.function_name, f.signature, f.complexity, f.total_branches, f.covered_branches, s.file_name, s.package_name " +
                "FROM function_info f LEFT JOIN source_file s ON s.id = f.source_file_id WHERE f.run_id = ? ORDER BY f.id"
        ).use { statement ->
            statement.setString(1, runId)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val functionId = rows.getLong(1)
                    val cases = readCases(connection, functionId)
                    val complexity = rows.getInt(5).takeUnless { rows.wasNull() }
                    result += FunctionRunRecord(
                        className = rows.getString(2),
                        functionName = rows.getString(3),
                        totalBranches = rows.getInt(6),
                        coveredBranches = rows.getInt(7),
                        testCases = cases.size,
                        signature = rows.getString(4),
                        complexity = complexity,
                        fileName = rows.getString(8),
                        packageName = rows.getString(9).orEmpty(),
                        branches = readBranches(connection, functionId),
                        cases = cases
                    )
                }
            }
        }
        return result
    }

    private fun readBranches(connection: Connection, functionId: Long): List<BranchRecord> {
        val result = mutableListOf<BranchRecord>()
        connection.prepareStatement("SELECT code, label, state FROM branch WHERE function_id = ? ORDER BY id").use {
            it.setLong(1, functionId)
            it.executeQuery().use { rows ->
                while (rows.next()) result += BranchRecord(rows.getString(1), rows.getString(2), BranchState.valueOf(rows.getString(3)))
            }
        }
        return result
    }

    private fun readCases(connection: Connection, functionId: Long): List<TestCaseRecord> {
        val result = mutableListOf<TestCaseRecord>()
        connection.prepareStatement(
            "SELECT c.id, c.code, t.name, o.name, c.description, c.expected_result FROM test_case c " +
                "JOIN scenario_type t ON t.id = c.scenario_type_id JOIN case_origin o ON o.id = c.origin_id " +
                "WHERE c.function_id = ? ORDER BY c.id"
        ).use { statement ->
            statement.setLong(1, functionId)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val caseId = rows.getLong(1)
                    result += TestCaseRecord(
                        code = rows.getString(2),
                        scenarioType = rows.getString(3),
                        origin = rows.getString(4),
                        description = rows.getString(5),
                        expectedResult = rows.getString(6),
                        inputs = readInputs(connection, caseId)
                    )
                }
            }
        }
        return result
    }

    private fun readInputs(connection: Connection, caseId: Long): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        connection.prepareStatement("SELECT param_name, param_value FROM test_input WHERE test_case_id = ? ORDER BY id").use {
            it.setLong(1, caseId)
            it.executeQuery().use { rows -> while (rows.next()) result[rows.getString(1)] = rows.getString(2) }
        }
        return result
    }

    private fun schemaStatements(): List<String> {
        val text = SqliteRunRepository::class.java.getResourceAsStream("/db/schema.sql")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return text.lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        private val KNOWN_ORIGINS = setOf("HEURISTIC", "BOUNDARY", "SEARCH")
    }
}
