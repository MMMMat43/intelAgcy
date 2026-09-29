package com.example.agent.source

import java.io.File

data class KotlinSourceFile(
    val path: String,
    val content: String
) {
    val fileName: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')
}

interface SourceLoader {
    fun load(location: String): List<KotlinSourceFile>
}

private val IGNORED_DIRECTORY_NAMES = setOf(".git", "build", "target", "out", ".gradle")

class LocalFileSourceLoader : SourceLoader {

    override fun load(location: String): List<KotlinSourceFile> {
        val root = File(location)
        require(root.exists()) { "Path does not exist: $location" }

        return when {
            root.isFile -> loadSingleFile(root)
            root.isDirectory -> loadDirectory(root)
            else -> emptyList()
        }
    }

    private fun loadSingleFile(file: File): List<KotlinSourceFile> {
        if (!isKotlinFile(file)) {
            return emptyList()
        }
        return listOf(toSourceFile(file))
    }

    private fun loadDirectory(directory: File): List<KotlinSourceFile> {
        return directory.walkTopDown()
            .onEnter { dir -> dir.name !in IGNORED_DIRECTORY_NAMES }
            .filter { it.isFile && isKotlinFile(it) }
            .map { toSourceFile(it) }
            .toList()
    }

    private fun isKotlinFile(file: File): Boolean = file.extension == "kt"

    private fun toSourceFile(file: File): KotlinSourceFile =
        KotlinSourceFile(path = file.path, content = file.readText())
}
