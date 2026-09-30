package com.example.agent.source

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SourceLoaderFactoryTest {

    @Test
    fun `local path selects the local loader`(@TempDir dir: Path) {
        val file = dir.resolve("A.kt").toFile().apply { writeText("class A") }

        assertTrue(SourceLoaderFactory(allowRemote = true).create(file.path) is LocalFileSourceLoader)
        assertTrue(SourceLoaderFactory(allowRemote = true).create(dir.toString()) is LocalFileSourceLoader)
    }

    @Test
    fun `repository urls select the git loader when remote access is allowed`() {
        val factory = SourceLoaderFactory(allowRemote = true)

        assertTrue(factory.create("https://example.org/team/project.git") is GitSourceLoader)
        assertTrue(factory.create("git@example.org:team/project.git") is GitSourceLoader)
        assertTrue(factory.create("ssh://git@example.org/team/project.git") is GitSourceLoader)
    }

    @Test
    fun `repository urls are never treated as remote unless explicitly allowed`() {
        val factory = SourceLoaderFactory()

        assertTrue(factory.create("https://example.org/team/project.git") is LocalFileSourceLoader)
    }

    @Test
    fun `option-like and blank locations are not remote`() {
        assertFalse(SourceLoaderFactory.isRemoteRepository("--upload-pack=evil"))
        assertFalse(SourceLoaderFactory.isRemoteRepository(""))
        assertFalse(SourceLoaderFactory.isRemoteRepository("  "))
        assertFalse(SourceLoaderFactory.isRemoteRepository("src/test/resources/SampleCalculator.kt"))
    }

    @Test
    fun `git loader rejects option-like locations before running git`() {
        assertThrows<IllegalArgumentException> { GitSourceLoader().load("--upload-pack=evil") }
    }
}
