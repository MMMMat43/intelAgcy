package com.example.agent.source

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Загружает Java-исходники из git-репозитория: клонирует HEAD ветку
 * репозитория во временную директорию через системный процесс `git clone`
 * и переиспользует [LocalFileSourceLoader] для получения файлов.
 *
 * Не реализует полноценный git-клиент (ветки/теги/история) — только
 * клонирование HEAD, достаточное для анализа текущего состояния кода.
 */
class GitSourceLoader(
    private val localFileSourceLoader: LocalFileSourceLoader = LocalFileSourceLoader(),
    private val cloneTimeoutSeconds: Long = 120
) : SourceLoader {

    override fun load(location: String): List<JavaSourceFile> {
        val targetDir = Files.createTempDirectory("intelligent-test-agent-git-").toFile()
        try {
            cloneRepository(location, targetDir)
            return localFileSourceLoader.load(targetDir.path)
        } finally {
            targetDir.deleteRecursively()
        }
    }

    private fun cloneRepository(repositoryUrl: String, targetDir: File) {
        val process = ProcessBuilder("git", "clone", "--depth", "1", repositoryUrl, targetDir.path)
            .redirectErrorStream(true)
            .start()

        val finished = process.waitFor(cloneTimeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("git clone timed out after $cloneTimeoutSeconds seconds for $repositoryUrl")
        }
        if (process.exitValue() != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw IllegalStateException("git clone failed for $repositoryUrl (exit ${process.exitValue()}): $output")
        }
    }
}
