package com.example.agent.source

import java.io.File

/**
 * Единичный Java-исходный файл, загруженный из локальной файловой системы
 * или из клонированного git-репозитория.
 */
data class JavaSourceFile(
    val path: String,
    val content: String
)

/**
 * Абстракция загрузки Java-исходников для последующего анализа.
 */
interface SourceLoader {
    fun load(location: String): List<JavaSourceFile>
}

/**
 * Имена директорий, которые всегда игнорируются при рекурсивном обходе
 * (служебные/build-директории, не содержащие "полезных" исходников).
 */
private val IGNORED_DIRECTORY_NAMES = setOf(".git", "build", "target", "out")

/**
 * Загружает Java-исходники с локальной файловой системы: либо единичный
 * `*.java`-файл, либо директорию, рекурсивно обходимую с фильтрацией
 * по расширению `.java` и игнорированием служебных директорий.
 */
class LocalFileSourceLoader : SourceLoader {

    override fun load(location: String): List<JavaSourceFile> {
        val root = File(location)
        require(root.exists()) { "Path does not exist: $location" }

        return when {
            root.isFile -> loadSingleFile(root)
            root.isDirectory -> loadDirectory(root)
            else -> emptyList()
        }
    }

    private fun loadSingleFile(file: File): List<JavaSourceFile> {
        if (!isJavaFile(file)) {
            return emptyList()
        }
        return listOf(toJavaSourceFile(file))
    }

    private fun loadDirectory(directory: File): List<JavaSourceFile> {
        return directory.walkTopDown()
            .onEnter { dir -> dir.name !in IGNORED_DIRECTORY_NAMES }
            .filter { it.isFile && isJavaFile(it) }
            .map { toJavaSourceFile(it) }
            .toList()
    }

    private fun isJavaFile(file: File): Boolean = file.extension == "java"

    private fun toJavaSourceFile(file: File): JavaSourceFile =
        JavaSourceFile(path = file.path, content = file.readText())
}
