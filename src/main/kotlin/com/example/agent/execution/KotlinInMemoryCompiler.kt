package com.example.agent.execution

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

data class SourceUnit(val fileName: String, val content: String)

sealed class CompilationResult {
    data class Success(val classLoader: URLClassLoader, val tempDir: Path) : CompilationResult()
    data class Failure(val diagnostics: List<String>) : CompilationResult()
}

class KotlinInMemoryCompiler {

    fun compile(units: List<SourceUnit>): CompilationResult {
        if (units.isEmpty()) {
            return CompilationResult.Failure(listOf("No source files to compile"))
        }

        val tempDir = Files.createTempDirectory("agent-kotlinc-")
        return try {
            val sourceRoot = tempDir.resolve("src")
            val outputRoot = tempDir.resolve("classes")
            Files.createDirectories(outputRoot)

            val sourcePaths = units.mapIndexed { index, unit ->
                val directory = sourceRoot.resolve(index.toString())
                Files.createDirectories(directory)
                val target = directory.resolve(unit.fileName)
                Files.writeString(target, unit.content)
                target.toString()
            }

            val arguments = mutableListOf(
                "-d", outputRoot.toString(),
                "-no-stdlib",
                "-no-reflect",
                "-classpath", stdlibPath(),
                "-jvm-target", "17",
                "-nowarn"
            )
            arguments += sourcePaths

            val messages = ByteArrayOutputStream()
            val exitCode = synchronized(COMPILER_LOCK) {
                K2JVMCompiler().exec(PrintStream(messages, true, "UTF-8"), *arguments.toTypedArray())
            }

            if (exitCode != ExitCode.OK) {
                val diagnostics = messages.toString("UTF-8").lines().filter { it.isNotBlank() }
                tempDir.toFile().deleteRecursively()
                CompilationResult.Failure(diagnostics.ifEmpty { listOf("Compilation failed with exit code $exitCode") })
            } else {
                val classLoader = URLClassLoader(
                    arrayOf(outputRoot.toUri().toURL()),
                    KotlinInMemoryCompiler::class.java.classLoader
                )
                CompilationResult.Success(classLoader, tempDir)
            }
        } catch (t: Throwable) {
            tempDir.toFile().deleteRecursively()
            CompilationResult.Failure(listOf("Compiler failure: ${t.javaClass.simpleName}: ${t.message}"))
        }
    }

    private fun stdlibPath(): String =
        File(Unit::class.java.protectionDomain.codeSource.location.toURI()).path

    companion object {
        private val COMPILER_LOCK = Any()
    }
}

fun CompilationResult.Success.dispose() {
    runCatching { classLoader.close() }
    runCatching { tempDir.toFile().deleteRecursively() }
}
