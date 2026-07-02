package com.example.agent.storage

import com.example.agent.model.CodeStructure
import com.example.agent.model.TestSuiteResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists pipeline artifacts (code analysis result, generated test cases,
 * and generated JUnit5 test source code) to a predictable directory
 * structure under [outputDir]:
 *
 * ```
 * <outputDir>/analysis.json
 * <outputDir>/test-cases.json
 * <outputDir>/generated-tests/<PackageAsPath>/GeneratedTests.kt
 * ```
 */
class ArtifactStorage(private val outputDir: Path) {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun saveAnalysis(structure: CodeStructure) {
        Files.createDirectories(outputDir)
        val target = outputDir.resolve("analysis.json")
        objectMapper.writeValue(target.toFile(), structure)
    }

    fun saveTestCases(testSuite: TestSuiteResult) {
        Files.createDirectories(outputDir)
        val target = outputDir.resolve("test-cases.json")
        objectMapper.writeValue(target.toFile(), testSuite)
    }

    fun saveGeneratedTestCode(fileSpec: FileSpec) {
        val generatedTestsDir = outputDir.resolve("generated-tests")
        Files.createDirectories(generatedTestsDir)
        fileSpec.writeTo(generatedTestsDir)
    }

    fun loadAnalysis(): CodeStructure {
        val target = outputDir.resolve("analysis.json")
        return objectMapper.readValue(target.toFile(), CodeStructure::class.java)
    }

    fun loadTestCases(): TestSuiteResult {
        val target = outputDir.resolve("test-cases.json")
        return objectMapper.readValue(target.toFile(), TestSuiteResult::class.java)
    }
}
