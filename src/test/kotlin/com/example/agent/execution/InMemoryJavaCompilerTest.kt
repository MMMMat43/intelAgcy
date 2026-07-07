package com.example.agent.execution

import com.example.agent.source.JavaSourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InMemoryJavaCompilerTest {

    @Test
    fun `compiles a valid Java source and loads it via reflection`() {
        val source = """
            public class SimpleCalc {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val result = InMemoryJavaCompiler().compile(listOf(JavaSourceFile("SimpleCalc.java", source)))

        assertTrue(result is CompilationResult.Success, "Expected successful compilation, got: $result")
        val success = result as CompilationResult.Success
        try {
            val clazz = Class.forName("SimpleCalc", false, success.classLoader)
            val method = clazz.getMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val returned = method.invoke(instance, 2, 3)
            assertEquals(5, returned)
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `compiles both sample resources used across the project`() {
        val calculatorSource = readSampleResource("SampleCalculator.java")
        val stringUtilsSource = readSampleResource("SampleStringUtils.java")

        val result = InMemoryJavaCompiler().compile(
            listOf(
                JavaSourceFile("SampleCalculator.java", calculatorSource),
                JavaSourceFile("SampleStringUtils.java", stringUtilsSource)
            )
        )

        assertTrue(result is CompilationResult.Success, "Expected successful compilation, got: $result")
        val success = result as CompilationResult.Success
        try {
            val calculatorClass = Class.forName("SampleCalculator", false, success.classLoader)
            assertTrue(calculatorClass.getMethod("divide", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) != null)

            val stringUtilsClass = Class.forName("SampleStringUtils", false, success.classLoader)
            assertTrue(stringUtilsClass.getMethod("normalize", String::class.java) != null)
        } finally {
            success.classLoader.close()
            success.tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `syntactically invalid source results in Failure with diagnostics`() {
        val source = "public class Broken { this is not valid java"

        val result = InMemoryJavaCompiler().compile(listOf(JavaSourceFile("Broken.java", source)))

        assertTrue(result is CompilationResult.Failure, "Expected compilation failure, got: $result")
        val failure = result as CompilationResult.Failure
        assertTrue(failure.diagnostics.isNotEmpty())
    }

    private fun readSampleResource(name: String): String {
        val path: Path = Path.of("src", "test", "resources", name)
        return Files.readString(path)
    }
}
