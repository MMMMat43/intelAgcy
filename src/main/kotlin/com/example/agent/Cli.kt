package com.example.agent

import com.example.agent.api.ConsoleProgressListener
import com.example.agent.api.PipelineListener
import com.example.agent.api.PipelineService
import com.example.agent.api.RunRecordingListener
import com.example.agent.assembly.AgentAssembly
import com.example.agent.source.SourceLoaderFactory
import com.example.agent.storage.ArtifactStorage
import com.example.agent.storage.SqliteRunRepository
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
        println("Usage: --source <path> [--generate-tests] [--output <dir>] [--history <file.db>]")
        return
    }

    val listeners = mutableListOf<PipelineListener>(ConsoleProgressListener())
    resolveHistoryPath(args)?.let { listeners += RunRecordingListener(SqliteRunRepository(it)) }
    val pipeline = AgentAssembly.pipelineService(
        listeners = listeners,
        sourceLoaderFactory = SourceLoaderFactory(allowRemote = true)
    )
    val outputDir = parseOutputArgument(args)

    println("Source: $source")

    try {
        if (hasGenerateTestsFlag(args)) {
            val result = pipeline.generateTests(source, outputDir)
            println(jsonMapper.writeValueAsString(result.testSuiteResult))
            result.coverage?.takeIf { it.branchCoverage != null }?.let { coverage ->
                println(
                    "Branch coverage: ${coverage.coveredBranches}/${coverage.totalBranches} " +
                        "(${"%.1f".format((coverage.branchCoverage ?: 0.0) * 100)}%)"
                )
                coverage.functions.forEach { function ->
                    println(
                        "  ${function.className}.${function.functionName}: " +
                            "${function.coveredBranches}/${function.totalBranches}"
                    )
                }
            }
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

internal fun resolveHistoryPath(args: Array<String>, environment: Map<String, String> = System.getenv()): String? {
    val index = args.indexOf("--history")
    if (index != -1 && index + 1 < args.size) {
        return args[index + 1]
    }
    return environment["AGENT_HISTORY_DB"]?.takeIf { it.isNotBlank() }
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
