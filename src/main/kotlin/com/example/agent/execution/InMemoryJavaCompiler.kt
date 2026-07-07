package com.example.agent.execution

import com.example.agent.source.JavaSourceFile
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * Result of attempting to compile a set of [JavaSourceFile]s in memory.
 */
sealed class CompilationResult {
    /**
     * @param classLoader loads classes from [tempDir], where compiled `.class`
     *   files were written.
     * @param tempDir the temporary directory holding compiled output. Callers
     *   are responsible for deleting it once the classloader is no longer needed.
     */
    data class Success(val classLoader: URLClassLoader, val tempDir: Path) : CompilationResult()

    data class Failure(val diagnostics: List<String>) : CompilationResult()
}

/**
 * Compiles a list of [JavaSourceFile]s in memory using the JDK's own
 * `javax.tools.JavaCompiler`, so that the analyzed code can actually be
 * loaded and executed via reflection (see [TestCaseExecutor]).
 *
 * Requires a full JDK at runtime (not just a JRE): [ToolProvider.getSystemJavaCompiler]
 * returns `null` when only a JRE is available. This is handled gracefully by
 * returning [CompilationResult.Failure] instead of throwing.
 */
class InMemoryJavaCompiler {

    fun compile(files: List<JavaSourceFile>): CompilationResult {
        if (files.isEmpty()) {
            return CompilationResult.Failure(listOf("No source files to compile"))
        }

        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: return CompilationResult.Failure(
                listOf("No Java compiler available in this runtime (JRE instead of JDK?)")
            )

        val tempDir = Files.createTempDirectory("agent-compile-")
        val diagnostics = DiagnosticCollector<JavaFileObject>()

        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(tempDir.toFile()))

            val sourceFiles = files.map { InMemoryJavaSourceObject(it) }

            val success = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                null,
                null,
                sourceFiles
            ).call()

            if (!success) {
                val messages = diagnostics.diagnostics.map { it.toString() }
                return CompilationResult.Failure(messages.ifEmpty { listOf("Compilation failed with no diagnostics") })
            }

            val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), javaClass.classLoader)
            return CompilationResult.Success(classLoader, tempDir)
        } finally {
            fileManager.close()
        }
    }
}

/**
 * Adapts a [JavaSourceFile] (in-memory content, not necessarily backed by a
 * real file on disk under its "correct" name) into a [javax.tools.JavaFileObject]
 * that `javax.tools.JavaCompiler` can compile.
 */
private class InMemoryJavaSourceObject(private val source: JavaSourceFile) :
    javax.tools.SimpleJavaFileObject(
        java.net.URI.create(
            "string:///" + guessSimpleClassName(source) + JavaFileObject.Kind.SOURCE.extension
        ),
        JavaFileObject.Kind.SOURCE
    ) {

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source.content

    companion object {
        /**
         * Best-effort guess of the primary public class name from the file
         * path, falling back to a generic name. javac only uses this URI for
         * diagnostics/bookkeeping, not to determine the actual compiled class
         * name (that comes from the `package`/class declarations themselves).
         */
        private fun guessSimpleClassName(source: JavaSourceFile): String {
            val fileName = source.path.substringAfterLast('/').substringAfterLast('\\')
            return fileName.removeSuffix(".java").ifBlank { "Source${System.nanoTime()}" }
        }
    }
}
