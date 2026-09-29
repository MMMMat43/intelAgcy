package com.example.agent.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class KotlinInMemoryCompilerTest {

    private fun resource(name: String): SourceUnit =
        SourceUnit(name, File("src/test/resources/$name").readText())

    @Test
    fun `compiles SampleCalculator and exposes its methods through reflection`() {
        val result = KotlinInMemoryCompiler().compile(listOf(resource("SampleCalculator.kt")))

        val success = result as CompilationResult.Success
        try {
            val clazz = Class.forName("SampleCalculator", false, success.classLoader)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val method = clazz.getMethod("divide", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            assertEquals(5, method.invoke(instance, 10, 2))
        } finally {
            success.dispose()
        }
    }

    @Test
    fun `compiles all bundled Kotlin samples together`() {
        val result = KotlinInMemoryCompiler().compile(
            listOf(resource("SampleCalculator.kt"), resource("SampleStringUtils.kt"), resource("OrderProcessor.kt"))
        )

        val success = result as CompilationResult.Success
        success.dispose()
    }

    @Test
    fun `reports diagnostics for invalid code`() {
        val result = KotlinInMemoryCompiler().compile(listOf(SourceUnit("Bad.kt", "class Bad { fun f(): Int = \"text\" }")))

        val failure = result as CompilationResult.Failure
        assertTrue(failure.diagnostics.isNotEmpty())
    }

    @Test
    fun `fails gracefully when there are no sources`() {
        val result = KotlinInMemoryCompiler().compile(emptyList())

        assertTrue(result is CompilationResult.Failure)
    }
}
