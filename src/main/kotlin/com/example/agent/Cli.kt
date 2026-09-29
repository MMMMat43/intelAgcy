package com.example.agent

import com.example.agent.api.PipelineService
import com.example.agent.storage.ArtifactStorage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Paths

private val jsonMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .enable(SerializationFeature.INDENT_OUTPUT)

fun main(args: Array<String>) {
    val source = parseSourceArgument(args)
    if (source == null) {
        println("Usage: --source <path> [--generate-tests] [--output <dir>]")
        return
    }

    val pipeline = PipelineService()
    val outputDir = parseOutputArgument(args)

    println("Source: $source")

    try {
        if (hasGenerateTestsFlag(args)) {
            val result = pipeline.generateTests(source, outputDir)
            println(jsonMapper.writeValueAsString(result.testSuiteResult))
            result.skippedFunctions.forEach { println("Skipped: $it") }
            result.warnings.forEach { println("Warning: $it") }
            if (outputDir != null) {
                println("Artifacts saved to: $outputDir")
            }
        } else {
            val structure = pipeline.analyze(source)
            println(jsonMapper.writeValueAsString(structure))
            if (outputDir != null) {
                ArtifactStorage(Paths.get(outputDir)).saveAnalysis(structure)
                println("Analysis artifact saved to: $outputDir")
            }
        }
    } catch (e: PipelineService.InvalidSourceException) {
        println("Error: ${e.message}")
    }
}

internal fun parseSourceArgument(args: Array<String>): String? {
    val index = args.indexOf("--source")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}

internal fun hasGenerateTestsFlag(args: Array<String>): Boolean {
    return args.contains("--generate-tests")
}

internal fun parseOutputArgument(args: Array<String>): String? {
    val index = args.indexOf("--output")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}
