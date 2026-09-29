package com.example.agent.analysis

import com.example.agent.model.FunctionKind
import com.example.agent.model.FunctionInfo
import com.example.agent.source.KotlinSourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinCodeAnalyzerTest {

    private fun analyze(code: String, fileName: String = "Sample.kt") =
        KotlinCodeAnalyzer().analyze("/tmp/$fileName", listOf(KotlinSourceFile("/tmp/$fileName", code.trimIndent())))

    private fun functionOf(code: String, name: String): FunctionInfo =
        analyze(code).functions.first { it.name == name }

    @Test
    fun `method without branching has complexity one and empty collections`() {
        val function = functionOf(
            """
            class Calc {
                fun add(a: Int, b: Int): Int = a + b
            }
            """,
            "add"
        )

        assertEquals(1, function.cyclomaticComplexity)
        assertTrue(function.branches.isEmpty())
        assertTrue(function.loops.isEmpty())
        assertTrue(function.exceptions.isEmpty())
        assertEquals("Calc", function.className)
        assertEquals(listOf("a", "b"), function.parameters.map { it.name })
        assertEquals("Int", function.parameters[0].type)
    }

    @Test
    fun `if for and throw produce complexity three and are extracted`() {
        val function = functionOf(
            """
            class Calc {
                fun run(n: Int): Int {
                    if (n < 0) {
                        throw IllegalArgumentException("negative")
                    }
                    var total = 0
                    for (i in 0 until n) {
                        total += i
                    }
                    return total
                }
            }
            """,
            "run"
        )

        assertEquals(3, function.cyclomaticComplexity)
        assertEquals(1, function.branches.size)
        assertEquals("if", function.branches[0].kind)
        assertEquals("n < 0", function.branches[0].condition)
        assertEquals(1, function.loops.size)
        assertEquals("for", function.loops[0].kind)
        assertEquals(1, function.exceptions.size)
        assertEquals("IllegalArgumentException", function.exceptions[0].exceptionType)
        assertEquals("thrown", function.exceptions[0].context)
    }

    @Test
    fun `catch blocks are recorded as caught exceptions`() {
        val function = functionOf(
            """
            class Calc {
                fun parse(text: String): Int {
                    return try {
                        text.toInt()
                    } catch (e: NumberFormatException) {
                        -1
                    } catch (e: IllegalStateException) {
                        -2
                    }
                }
            }
            """,
            "parse"
        )

        assertEquals(3, function.cyclomaticComplexity)
        assertEquals(listOf("NumberFormatException", "IllegalStateException"), function.exceptions.map { it.exceptionType })
        assertTrue(function.exceptions.all { it.context == "caught" })
    }

    @Test
    fun `logical operators and elvis add to complexity`() {
        val function = functionOf(
            """
            class Calc {
                fun check(a: Boolean, b: Boolean, c: String?): Int {
                    if (a && b) {
                        return 1
                    }
                    return c?.length ?: 0
                }
            }
            """,
            "check"
        )

        assertEquals(4, function.cyclomaticComplexity)
        assertTrue(function.branches.any { it.kind == "elvis" })
    }

    @Test
    fun `when branches count except else`() {
        val function = functionOf(
            """
            class Calc {
                fun kind(n: Int): String = when (n) {
                    0 -> "zero"
                    1 -> "one"
                    else -> "many"
                }
            }
            """,
            "kind"
        )

        assertEquals(3, function.cyclomaticComplexity)
        assertEquals(3, function.branches.count { it.kind == "when" })
    }

    @Test
    fun `package name and function kinds are detected`() {
        val structure = analyze(
            """
            package demo.pkg

            fun topLevel(x: Int): Int = x

            object Single {
                fun inObject(x: Int): Int = x
            }

            class Box {
                fun inClass(x: Int): Int = x

                companion object {
                    fun inCompanion(x: Int): Int = x
                }
            }
            """,
            "Demo.kt"
        )

        assertEquals("demo.pkg", structure.packageName)
        assertEquals("kotlin", structure.language)
        val byName = structure.functions.associateBy { it.name }
        assertEquals(FunctionKind.TOP_LEVEL, byName.getValue("topLevel").kind)
        assertEquals("DemoKt", byName.getValue("topLevel").className)
        assertEquals(FunctionKind.OBJECT_MEMBER, byName.getValue("inObject").kind)
        assertEquals("Single", byName.getValue("inObject").className)
        assertEquals(FunctionKind.MEMBER, byName.getValue("inClass").kind)
        assertEquals(FunctionKind.COMPANION_MEMBER, byName.getValue("inCompanion").kind)
        assertEquals("Box", byName.getValue("inCompanion").className)
        assertTrue(byName.values.all { it.packageName == "demo.pkg" })
    }

    @Test
    fun `nullable and default parameters are detected`() {
        val function = functionOf(
            """
            class Calc {
                fun greet(name: String?, times: Int = 1): String = name ?: "anon"
            }
            """,
            "greet"
        )

        assertTrue(function.parameters[0].nullable)
        assertEquals("String", function.parameters[0].type)
        assertFalse(function.parameters[1].nullable)
        assertTrue(function.parameters[1].hasDefault)
    }

    @Test
    fun `unsupported functions are skipped with a reason`() {
        val structure = analyze(
            """
            class Calc {
                suspend fun waiting(x: Int): Int = x
                fun <T> generic(x: T): T = x
                fun Int.extended(): Int = this
                private fun hidden(x: Int): Int = x
                fun custom(list: List<Int>): Int = list.size
                fun ok(x: Int): Int = x
            }
            """
        )
        val byName = structure.functions.associateBy { it.name }

        assertEquals("suspend function", byName.getValue("waiting").skipReason)
        assertEquals("generic function", byName.getValue("generic").skipReason)
        assertEquals("extension function", byName.getValue("extended").skipReason)
        assertEquals("private function", byName.getValue("hidden").skipReason)
        assertNotNull(byName.getValue("custom").skipReason)
        assertNull(byName.getValue("ok").skipReason)
    }

    @Test
    fun `file with syntax errors is ignored without failing`() {
        val structure = analyze("class Broken { fun oops( : }")

        assertTrue(structure.functions.isEmpty())
    }
}
