package com.example.agent.codegen

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
 * The `@Test` annotation is referenced via KotlinPoet's [ClassName] rather
 * than `org.junit.jupiter.api.Test::class`, because JUnit5 is only a
 * `testImplementation` dependency of this project and this generator lives
 * in the main source set — it must not require JUnit5 on the main
 * compile classpath.
 *
 * Each [TestCase] becomes one `@Test`-annotated method named
 * `test_<functionName>_<scenarioType>_<index>` (index is per function+type,
 * 1-based) with a KDoc comment containing the scenario description, and a
 * body that documents the input data and steps as comments plus a `TODO`
 * assertion placeholder (no attempt is made to call the real production
 * method, since its class is not necessarily available/compilable in this
 * generation context).
 */
class JUnit5TestCodeGenerator {

    private val junitTestAnnotation = ClassName("org.junit.jupiter.api", "Test")

    fun generate(testSuite: TestSuiteResult, packageName: String): FileSpec {
        val fileBuilder = FileSpec.builder(packageName, "GeneratedTests")

        testSuite.testCases
            .groupBy { it.className }
            .forEach { (className, cases) ->
                fileBuilder.addType(buildTestClass(className, cases))
            }

        return fileBuilder.build()
    }

    private fun buildTestClass(className: String, cases: List<TestCase>): TypeSpec {
        val classBuilder = TypeSpec.classBuilder("${className}Test")

        val perFunctionTypeCounters = mutableMapOf<Pair<String, ScenarioType>, Int>()

        cases.forEach { testCase ->
            val key = testCase.functionName to testCase.type
            val nextIndex = (perFunctionTypeCounters[key] ?: 0) + 1
            perFunctionTypeCounters[key] = nextIndex

            classBuilder.addFunction(buildTestMethod(testCase, nextIndex))
        }

        return classBuilder.build()
    }

    private fun buildTestMethod(testCase: TestCase, index: Int): FunSpec {
        val methodName = "test_${testCase.functionName}_${testCase.type.name.lowercase()}_$index"

        val codeBuilder = CodeBlock.builder()
        codeBuilder.addStatement("// inputData: %L", testCase.inputData.toString())
        testCase.steps.forEach { step ->
            codeBuilder.addStatement("// step: %L", step)
        }
        codeBuilder.addStatement("// TODO: assert expected = %L", testCase.expectedResult.toString())

        return FunSpec.builder(methodName)
            .addAnnotation(junitTestAnnotation)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", testCase.description)
            .addCode(codeBuilder.build())
            .build()
    }
}
