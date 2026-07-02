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
    fun `load returns single file when location is a java file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("Sample.java").toFile()
        file.writeText("public class Sample {}")

        val result = loader.load(file.path)

        assertEquals(1, result.size)
        assertEquals(file.path, result[0].path)
        assertEquals("public class Sample {}", result[0].content)
    }

    @Test
    fun `load returns empty list when single file is not java`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("readme.txt").toFile()
        file.writeText("not java")

        val result = loader.load(file.path)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `load recursively finds java files in nested directories`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        File(root, "Top.java").writeText("class Top {}")

        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "Nested.java").writeText("class Nested {}")

        val deeplyNested = File(nested, "deeper").apply { mkdirs() }
        File(deeplyNested, "Deep.java").writeText("class Deep {}")

        val result = loader.load(root.path)

        assertEquals(3, result.size)
        val names = result.map { File(it.path).name }.toSet()
        assertEquals(setOf("Top.java", "Nested.java", "Deep.java"), names)
    }

    @Test
    fun `load filters out non-java files and ignored directories`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        File(root, "Keep.java").writeText("class Keep {}")
        File(root, "notes.txt").writeText("ignore me")

        val gitDir = File(root, ".git").apply { mkdirs() }
        File(gitDir, "Ignored.java").writeText("class Ignored {}")

        val buildDir = File(root, "build").apply { mkdirs() }
        File(buildDir, "Generated.java").writeText("class Generated {}")

        val targetDir = File(root, "target").apply { mkdirs() }
        File(targetDir, "Compiled.java").writeText("class Compiled {}")

        val outDir = File(root, "out").apply { mkdirs() }
        File(outDir, "Output.java").writeText("class Output {}")

        val result = loader.load(root.path)

        assertEquals(1, result.size)
        assertEquals("Keep.java", File(result[0].path).name)
    }

    @Test
    fun `load throws for non-existent path`() {
        assertTrue(
            runCatching { loader.load("this/path/does/not/exist.java") }.isFailure
        )
    }
}
