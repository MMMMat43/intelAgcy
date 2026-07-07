package com.example.agent.analysis

import com.example.agent.source.JavaSourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaCodeAnalyzerTest {

    private val analyzer = JavaCodeAnalyzer()

    @Test
    fun `method without branches has cyclomatic complexity of 1 and empty lists`() {
        val source = """
            public class Simple {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("Simple.java", listOf(JavaSourceFile("Simple.java", source)))

        assertEquals(1, structure.functions.size)
        val function = structure.functions[0]
        assertEquals("add", function.name)
        assertEquals("Simple", function.className)
        assertEquals(listOf("a", "b"), function.parameters.map { it.name })
        assertEquals(1, function.cyclomaticComplexity)
        assertTrue(function.branches.isEmpty())
        assertTrue(function.loops.isEmpty())
        assertTrue(function.exceptions.isEmpty())
    }

    @Test
    fun `method with if-else, for loop and throw is analyzed correctly`() {
        // Decision points: 1 (base) + 1 (if) + 1 (for) = 3
        val source = """
            public class Validator {
                public int classify(int value) {
                    if (value < 0) {
                        throw new IllegalArgumentException("negative");
                    } else {
                        int sum = 0;
                        for (int i = 0; i < value; i++) {
                            sum += i;
                        }
                        return sum;
                    }
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("Validator.java", listOf(JavaSourceFile("Validator.java", source)))

        assertEquals(1, structure.functions.size)
        val function = structure.functions[0]

        assertEquals(1, function.branches.size)
        assertEquals("if", function.branches[0].kind)

        assertEquals(1, function.loops.size)
        assertEquals("for", function.loops[0].kind)

        assertEquals(1, function.exceptions.size)
        assertEquals("thrown", function.exceptions[0].context)
        assertEquals("IllegalArgumentException", function.exceptions[0].exceptionType)

        assertEquals(3, function.cyclomaticComplexity)
    }

    @Test
    fun `method with try-catch on multiple exception types collects caught exceptions`() {
        // Decision points: 1 (base) + 2 (catch blocks) = 3
        val source = """
            public class Reader {
                public void readFile(String path) {
                    try {
                        doRead(path);
                    } catch (java.io.IOException e) {
                        handle(e);
                    } catch (RuntimeException e) {
                        handle(e);
                    }
                }

                private void doRead(String path) {}
                private void handle(Exception e) {}
            }
        """.trimIndent()

        val structure = analyzer.analyze("Reader.java", listOf(JavaSourceFile("Reader.java", source)))

        val readFile = structure.functions.first { it.name == "readFile" }

        assertEquals(2, readFile.exceptions.size)
        assertTrue(readFile.exceptions.all { it.context == "caught" })
        assertEquals(
            setOf("java.io.IOException", "RuntimeException"),
            readFile.exceptions.map { it.exceptionType }.toSet()
        )

        assertEquals(3, readFile.cyclomaticComplexity)
    }

    @Test
    fun `method with logical operators in condition increases complexity`() {
        // Decision points: 1 (base) + 1 (if) + 1 (&&) = 3
        val source = """
            public class RangeChecker {
                public boolean inRange(int value, int min, int max) {
                    if (value >= min && value <= max) {
                        return true;
                    }
                    return false;
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("RangeChecker.java", listOf(JavaSourceFile("RangeChecker.java", source)))
        val function = structure.functions[0]

        assertEquals(3, function.cyclomaticComplexity)
    }

    @Test
    fun `package declaration is extracted into CodeStructure packageName`() {
        val source = """
            package com.example.demo;

            public class Simple {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("Simple.java", listOf(JavaSourceFile("Simple.java", source)))

        assertEquals("com.example.demo", structure.packageName)
    }

    @Test
    fun `missing package declaration results in empty packageName`() {
        val source = """
            public class Simple {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("Simple.java", listOf(JavaSourceFile("Simple.java", source)))

        assertEquals("", structure.packageName)
    }

    @Test
    fun `static method is flagged as isStatic true, instance method as false`() {
        val source = """
            public class Utils {
                public static int staticAdd(int a, int b) {
                    return a + b;
                }

                public int instanceAdd(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val structure = analyzer.analyze("Utils.java", listOf(JavaSourceFile("Utils.java", source)))

        val staticMethod = structure.functions.first { it.name == "staticAdd" }
        val instanceMethod = structure.functions.first { it.name == "instanceAdd" }

        assertTrue(staticMethod.isStatic)
        assertTrue(!instanceMethod.isStatic)
    }
}
