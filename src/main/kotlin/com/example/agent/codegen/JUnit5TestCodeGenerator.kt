package com.example.agent.codegen

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates compilable Kotlin/JUnit5 test source code from a [TestSuiteResult].
 *
 * Design decision: all test classes derived from the same [TestSuiteResult]
 * are emitted into a single [FileSpec] (one file, potentially multiple
 * top-level test classes — one class per distinct [TestCase.className]).
 * This keeps the generator simple and keeps [com.example.agent.storage.ArtifactStorage]
 * free of the need to manage multiple files per test suite.
 *
 * The generated file is placed in [packageName] (the REAL package of the
 * analyzed class, taken from [com.example.agent.model.CodeStructure.packageName]),
 * so that the generated test can reference the analyzed class directly,
 * without an explicit `import`.
 *
 * ### Oracle-based real test generation
 * When [functions] and [outcomes] are supplied (see [com.example.agent.api.PipelineService]),
 * each [TestCase] whose scenario was actually executed in-memory against the
 * real compiled class (via [com.example.agent.execution.TestCaseExecutor])
 * gets a REAL, working test body instead of a TODO placeholder:
 * - [ExecutionOutcome.ReturnedValue]: instantiates the class (unless the
 *   method is static), calls the method with the same literal arguments used
 *   during execution, and asserts the observed return value via
 *   `Assertions.assertEquals(...)` (or `Assertions.assertDoesNotThrow { ... }`
 *   for `void` methods, where an equality check would be meaningless).
 * - [ExecutionOutcome.ThrewException]: asserts the observed exception type
 *   via `Assertions.assertThrows(ExceptionClass::class.java) { ... }`.
 * - [ExecutionOutcome.CouldNotExecute], or any case where the return value or
 *   argument cannot be expressed as a simple Kotlin literal (e.g. a custom
 *   object return type), or no [functions]/[outcomes] were supplied at all —
 *   falls back to the original comment/TODO style, which is always safe to
 *   generate and always compiles.
 */
class JUnit5TestCodeGenerator {

    private val junitTestAnnotation = ClassName("org.junit.jupiter.api", "Test")
    private val assertionsClass = ClassName("org.junit.jupiter.api", "Assertions")

    fun generate(
        testSuite: TestSuiteResult,
        packageName: String,
        functions: List<FunctionInfo> = emptyList(),
        outcomes: Map<String, ExecutionOutcome> = emptyMap()
    ): FileSpec {
        val fileBuilder = FileSpec.builder(packageName, "GeneratedTests")

        // Lookup by (className, functionName). If a class declares several
        // overloads with the same name, the first one found is used — an
        // acceptable simplification for this generator, since overloaded
        // methods with identical names but different signatures are rare in
        // the kind of small, generated-test-friendly code this project
        // targets.
        val functionLookup = functions.associateBy { it.className to it.name }

        testSuite.testCases
            .groupBy { it.className }
            .forEach { (className, cases) ->
                fileBuilder.addType(buildTestClass(className, packageName, cases, functionLookup, outcomes))
            }

        return fileBuilder.build()
    }

    private fun buildTestClass(
        className: String,
        packageName: String,
        cases: List<TestCase>,
        functionLookup: Map<Pair<String, String>, FunctionInfo>,
        outcomes: Map<String, ExecutionOutcome>
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder("${className}Test")

        val perFunctionTypeCounters = mutableMapOf<Pair<String, ScenarioType>, Int>()

        cases.forEach { testCase ->
            val key = testCase.functionName to testCase.type
            val nextIndex = (perFunctionTypeCounters[key] ?: 0) + 1
            perFunctionTypeCounters[key] = nextIndex

            val function = functionLookup[testCase.className to testCase.functionName]
            val outcome = outcomes[testCase.id]

            classBuilder.addFunction(buildTestMethod(testCase, nextIndex, packageName, function, outcome))
        }

        return classBuilder.build()
    }

    private fun buildTestMethod(
        testCase: TestCase,
        index: Int,
        packageName: String,
        function: FunctionInfo?,
        outcome: ExecutionOutcome?
    ): FunSpec {
        val methodName = "test_${testCase.functionName}_${testCase.type.name.lowercase()}_$index"

        val realCode = if (function != null && outcome != null) {
            buildRealCodeBlock(testCase, packageName, function, outcome)
        } else {
            null
        }

        val code = realCode ?: buildFallbackCodeBlock(testCase)

        return FunSpec.builder(methodName)
            .addAnnotation(junitTestAnnotation)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", testCase.description)
            .addCode(code)
            .build()
    }

    /**
     * Attempts to build a real, working test body from an actually-observed
     * [ExecutionOutcome]. Returns `null` (triggering the TODO fallback) if
     * any argument or the observed return value cannot be expressed as a
     * simple Kotlin literal.
     */
    private fun buildRealCodeBlock(
        testCase: TestCase,
        packageName: String,
        function: FunctionInfo,
        outcome: ExecutionOutcome
    ): CodeBlock? {
        if (outcome is ExecutionOutcome.CouldNotExecute) {
            return null
        }

        val argumentLiterals = mutableListOf<CodeBlock>()
        for (parameter in function.parameters) {
            val literal = argumentLiteralFor(parameter.type, testCase.inputData[parameter.name]) ?: return null
            argumentLiterals.add(literal)
        }

        val classNameRef = ClassName(packageName, function.className)
        val argumentsBlock = CodeBlock.of(argumentLiterals.joinToString(", ") { "%L" }, *argumentLiterals.toTypedArray())

        val builder = CodeBlock.builder()
        val receiver: CodeBlock = if (function.isStatic) {
            CodeBlock.of("%T", classNameRef)
        } else {
            builder.addStatement("val instance = %T()", classNameRef)
            CodeBlock.of("instance")
        }

        val callExpression = CodeBlock.of("%L.%L(%L)", receiver, function.name, argumentsBlock)

        return when (outcome) {
            is ExecutionOutcome.ReturnedValue -> {
                if (function.returnType.trim() == "void") {
                    builder.addStatement("%T.assertDoesNotThrow { %L }", assertionsClass, callExpression)
                } else {
                    val expectedLiteral = literalForRuntimeValue(outcome.value) ?: return null
                    builder.addStatement("%T.assertEquals(%L, %L)", assertionsClass, expectedLiteral, callExpression)
                }
                builder.build()
            }
            is ExecutionOutcome.ThrewException -> {
                val exceptionClassName = try {
                    ClassName.bestGuess(outcome.exceptionClassName)
                } catch (e: IllegalArgumentException) {
                    return null
                }
                builder.addStatement(
                    "%T.assertThrows(%T::class.java) { %L }",
                    assertionsClass,
                    exceptionClassName,
                    callExpression
                )
                builder.build()
            }
            is ExecutionOutcome.CouldNotExecute -> null
        }
    }

    private fun buildFallbackCodeBlock(testCase: TestCase): CodeBlock {
        val codeBuilder = CodeBlock.builder()
        codeBuilder.addStatement("// inputData: %L", testCase.inputData.toString())
        testCase.steps.forEach { step ->
            codeBuilder.addStatement("// step: %L", step)
        }
        codeBuilder.addStatement("// TODO: assert expected = %L", testCase.expectedResult.toString())
        return codeBuilder.build()
    }

    /**
     * Renders a declared-parameter-type + raw string value pair (as stored
     * in [TestCase.inputData]) as a Kotlin literal expression, matching the
     * exact type expected by the method signature. Returns `null` if the
     * type is unsupported or the value cannot be parsed as that type.
     */
    private fun argumentLiteralFor(type: String, rawValue: String?): CodeBlock? {
        val normalizedType = type.trim()
        if (rawValue == null) {
            return if (normalizedType in PRIMITIVE_TYPES) null else CodeBlock.of("null")
        }
        return try {
            when (normalizedType) {
                "int", "Integer" -> CodeBlock.of("(%L)", rawValue.toInt())
                "long", "Long" -> CodeBlock.of("(%LL)", rawValue.toLong())
                "short", "Short" -> CodeBlock.of("(%L).toShort()", rawValue.toShort())
                "byte", "Byte" -> CodeBlock.of("(%L).toByte()", rawValue.toByte())
                "double", "Double" -> CodeBlock.of("(%L)", rawValue.toDouble())
                "float", "Float" -> CodeBlock.of("(%Lf)", rawValue.toFloat())
                "boolean", "Boolean" -> CodeBlock.of("%L", rawValue.toBoolean())
                "String", "java.lang.String" -> CodeBlock.of("%S", rawValue)
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Renders an actually-observed runtime return [value] (from
     * [ExecutionOutcome.ReturnedValue]) as a Kotlin literal expression.
     * Returns `null` for types with no simple literal form (e.g. custom
     * objects), triggering the TODO fallback for that test case.
     */
    private fun literalForRuntimeValue(value: Any?): CodeBlock? {
        return when (value) {
            null -> CodeBlock.of("null")
            is Int -> CodeBlock.of("(%L)", value)
            is Long -> CodeBlock.of("(%LL)", value)
            is Short -> CodeBlock.of("(%L).toShort()", value)
            is Byte -> CodeBlock.of("(%L).toByte()", value)
            is Double -> CodeBlock.of("(%L)", value)
            is Float -> CodeBlock.of("(%Lf)", value)
            is Boolean -> CodeBlock.of("%L", value)
            is String -> CodeBlock.of("%S", value)
            else -> null
        }
    }

    companion object {
        private val PRIMITIVE_TYPES = setOf("int", "long", "short", "byte", "double", "float", "boolean")
    }
}
