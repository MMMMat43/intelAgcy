package com.example.agent.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LocalFileSourceLoaderTest {

    private val loader = LocalFileSourceLoader()

    @Test
    fun `load returns single file when location is a kotlin file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("Sample.kt").toFile()
        file.writeText("class Sample")

        val result = loader.load(file.path)

        assertEquals(1, result.size)
        assertEquals(file.path, result[0].path)
        assertEquals("Sample.kt", result[0].fileName)
        assertEquals("class Sample", result[0].content)
    }

    @Test
    fun `load returns empty list for java and other non-kotlin files`(@TempDir tempDir: Path) {
        val java = tempDir.resolve("Sample.java").toFile().apply { writeText("public class Sample {}") }
        val text = tempDir.resolve("readme.txt").toFile().apply { writeText("not kotlin") }

        assertTrue(loader.load(java.path).isEmpty())
        assertTrue(loader.load(text.path).isEmpty())
    }

    @Test
    fun `load recursively finds kotlin files in nested directories`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        File(root, "Top.kt").writeText("class Top")

        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "Nested.kt").writeText("class Nested")

        val deeplyNested = File(nested, "deeper").apply { mkdirs() }
        File(deeplyNested, "Deep.kt").writeText("class Deep")

        val result = loader.load(root.path)

        assertEquals(3, result.size)
        assertEquals(setOf("Top.kt", "Nested.kt", "Deep.kt"), result.map { it.fileName }.toSet())
    }

    @Test
    fun `load filters out other files and ignored directories`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        File(root, "Keep.kt").writeText("class Keep")
        File(root, "Skip.java").writeText("class Skip {}")
        File(root, "notes.txt").writeText("ignore me")

        listOf(".git", "build", "target", "out", ".gradle").forEach { name ->
            val dir = File(root, name).apply { mkdirs() }
            File(dir, "Ignored.kt").writeText("class Ignored")
        }

        val result = loader.load(root.path)

        assertEquals(1, result.size)
        assertEquals("Keep.kt", result[0].fileName)
    }

    @Test
    fun `load throws for non-existent path`() {
        assertTrue(runCatching { loader.load("this/path/does/not/exist.kt") }.isFailure)
    }
}
