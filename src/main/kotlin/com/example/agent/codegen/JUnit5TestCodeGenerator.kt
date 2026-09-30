package com.example.agent.codegen

import com.example.agent.execution.ExecutionOutcome
import com.example.agent.execution.TypeConversion
import com.example.agent.model.FunctionInfo
import com.example.agent.model.FunctionKind
import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import com.example.agent.model.TestSuiteResult
import com.example.agent.model.signature
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec

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

        testSuite.testCases
            .groupBy { it.className }
            .forEach { (className, cases) ->
                fileBuilder.addType(buildTestClass(className, cases, functions, outcomes))
            }

        return fileBuilder.build()
    }

    private fun buildTestClass(
        className: String,
        cases: List<TestCase>,
        functions: List<FunctionInfo>,
        outcomes: Map<String, ExecutionOutcome>
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder("${className.replace('.', '_')}Test")
        val counters = mutableMapOf<Pair<String, ScenarioType>, Int>()

        cases.forEach { testCase ->
            val key = testCase.functionName to testCase.type
            val nextIndex = (counters[key] ?: 0) + 1
            counters[key] = nextIndex

            val function = findFunction(functions, testCase)
            val outcome = outcomes[testCase.id]

            classBuilder.addFunction(buildTestMethod(testCase, nextIndex, function, outcome))
        }

        return classBuilder.build()
    }

    private fun findFunction(functions: List<FunctionInfo>, testCase: TestCase): FunctionInfo? {
        val candidates = functions.filter { it.className == testCase.className && it.name == testCase.functionName }
        if (testCase.signature.isNotEmpty()) {
            candidates.firstOrNull { it.signature() == testCase.signature }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun buildTestMethod(
        testCase: TestCase,
        index: Int,
        function: FunctionInfo?,
        outcome: ExecutionOutcome?
    ): FunSpec {
        val methodName = "test_${testCase.functionName}_${testCase.type.name.lowercase()}_$index"

        val realCode = if (function != null && outcome != null) {
            buildRealCodeBlock(testCase, function, outcome)
        } else {
            null
        }

        return FunSpec.builder(methodName)
            .addAnnotation(junitTestAnnotation)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", testCase.description)
            .addCode(realCode ?: buildFallbackCodeBlock(testCase))
            .build()
    }

    private fun buildRealCodeBlock(testCase: TestCase, function: FunctionInfo, outcome: ExecutionOutcome): CodeBlock? {
        if (outcome is ExecutionOutcome.CouldNotExecute) {
            return null
        }

        val argumentLiterals = mutableListOf<CodeBlock>()
        for (parameter in function.parameters) {
            val literal = argumentLiteralFor(parameter.type, parameter.nullable, testCase.inputData[parameter.name]) ?: return null
            argumentLiterals.add(literal)
        }
        val argumentsBlock = CodeBlock.of(argumentLiterals.joinToString(", ") { "%L" }, *argumentLiterals.toTypedArray())

        val classRef = ClassName(function.packageName, function.className)
        val builder = CodeBlock.builder()

        val callExpression: CodeBlock = when (function.kind) {
            FunctionKind.TOP_LEVEL ->
                CodeBlock.of("%M(%L)", MemberName(function.packageName, function.name), argumentsBlock)
            FunctionKind.OBJECT_MEMBER, FunctionKind.COMPANION_MEMBER ->
                CodeBlock.of("%T.%L(%L)", classRef, function.name, argumentsBlock)
            else -> {
                builder.addStatement("val instance = %T()", classRef)
                CodeBlock.of("instance.%L(%L)", function.name, argumentsBlock)
            }
        }

        return when (outcome) {
            is ExecutionOutcome.ReturnedValue -> {
                val returnType = function.returnType.trim()
                val statementOnly = returnType == "Unit" || (returnType == "Unknown" && outcome.value == null)
                if (statementOnly) {
                    builder.addStatement("%L", callExpression)
                } else {
                    val expected = literalForRuntimeValue(outcome.value) ?: return null
                    builder.addStatement("%T.assertEquals(%L, %L)", assertionsClass, expected, callExpression)
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

    private fun argumentLiteralFor(type: String, nullable: Boolean, rawValue: String?): CodeBlock? {
        if (rawValue == null) {
            return if (nullable) CodeBlock.of("null") else null
        }
        return try {
            when (TypeConversion.normalize(type)) {
                "Int" -> intLiteral(rawValue.toInt())
                "Long" -> longLiteral(rawValue.toLong())
                "Short" -> CodeBlock.of("(%L).toShort()", rawValue.toShort())
                "Byte" -> CodeBlock.of("(%L).toByte()", rawValue.toByte())
                "Double" -> doubleLiteral(rawValue.toDouble())
                "Float" -> floatLiteral(rawValue.toFloat())
                "Boolean" -> when (rawValue) {
                    "true" -> CodeBlock.of("true")
                    "false" -> CodeBlock.of("false")
                    else -> null
                }
                "Char" -> if (rawValue.length == 1) charLiteral(rawValue[0]) else null
                "String" -> stringLiteral(rawValue)
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun literalForRuntimeValue(value: Any?): CodeBlock? {
        return when (value) {
            null -> CodeBlock.of("null")
            is Int -> intLiteral(value)
            is Long -> longLiteral(value)
            is Short -> CodeBlock.of("(%L).toShort()", value)
            is Byte -> CodeBlock.of("(%L).toByte()", value)
            is Double -> doubleLiteral(value)
            is Float -> floatLiteral(value)
            is Boolean -> CodeBlock.of("%L", value)
            is Char -> charLiteral(value)
            is String -> stringLiteral(value)
            else -> null
        }
    }

    private fun intLiteral(value: Int): CodeBlock =
        if (value == Int.MIN_VALUE) CodeBlock.of("Int.MIN_VALUE") else CodeBlock.of("%L", value.toString())

    private fun longLiteral(value: Long): CodeBlock =
        if (value == Long.MIN_VALUE) CodeBlock.of("Long.MIN_VALUE") else CodeBlock.of("%LL", value.toString())

    private fun doubleLiteral(value: Double): CodeBlock = when {
        value.isNaN() -> CodeBlock.of("Double.NaN")
        value == Double.POSITIVE_INFINITY -> CodeBlock.of("Double.POSITIVE_INFINITY")
        value == Double.NEGATIVE_INFINITY -> CodeBlock.of("Double.NEGATIVE_INFINITY")
        value == Double.MAX_VALUE -> CodeBlock.of("Double.MAX_VALUE")
        value == -Double.MAX_VALUE -> CodeBlock.of("-Double.MAX_VALUE")
        value == Double.MIN_VALUE -> CodeBlock.of("Double.MIN_VALUE")
        else -> CodeBlock.of("%L", value.toString())
    }

    private fun floatLiteral(value: Float): CodeBlock = when {
        value.isNaN() -> CodeBlock.of("Float.NaN")
        value == Float.POSITIVE_INFINITY -> CodeBlock.of("Float.POSITIVE_INFINITY")
        value == Float.NEGATIVE_INFINITY -> CodeBlock.of("Float.NEGATIVE_INFINITY")
        value == Float.MAX_VALUE -> CodeBlock.of("Float.MAX_VALUE")
        value == -Float.MAX_VALUE -> CodeBlock.of("-Float.MAX_VALUE")
        value == Float.MIN_VALUE -> CodeBlock.of("Float.MIN_VALUE")
        else -> CodeBlock.of("%Lf", value.toString())
    }

    private fun charLiteral(value: Char): CodeBlock {
        val text = if (value.isLetterOrDigit() || value == ' ') {
            "'$value'"
        } else {
            "'\\u" + value.code.toString(16).padStart(4, '0') + "'"
        }
        return CodeBlock.of("%L", text)
    }

    private fun stringLiteral(value: String): CodeBlock =
        if (value.length >= 64 && value.all { it == value[0] }) {
            CodeBlock.of("%S.repeat(%L)", value[0].toString(), value.length.toString())
        } else {
            CodeBlock.of("%S", value)
        }
}
