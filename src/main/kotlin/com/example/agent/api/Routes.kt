package com.example.agent.api

import com.example.agent.api.dto.AnalyzeRequest
import com.example.agent.api.dto.ErrorResponse
import com.example.agent.api.dto.GenerateTestsRequest
import com.example.agent.api.dto.GenerateTestsResponse
import com.example.agent.api.dto.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson { }
    }
}

private fun PipelineService.GenerateTestsResult.toResponse() = GenerateTestsResponse(
    functionsCount = functionsCount,
    testCasesCount = testCasesCount,
    generatedTestFilePath = generatedTestFilePath,
    executedTestCases = executedTestCases,
    skippedFunctions = skippedFunctions,
    warnings = warnings
)

fun Application.configureRouting(pipelineService: PipelineService = PipelineService()) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        post("/analyze") {
            val request = call.receive<AnalyzeRequest>()
            try {
                call.respond(pipelineService.analyze(request.sourcePath))
            } catch (e: PipelineService.InvalidSourceException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid source path"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = e.message ?: "Internal error"))
            }
        }

        post("/generate-tests") {
            val request = call.receive<GenerateTestsRequest>()
            try {
                call.respond(pipelineService.generateTests(request.sourcePath, request.outputDir).toResponse())
            } catch (e: PipelineService.InvalidSourceException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid source path"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = e.message ?: "Internal error"))
            }
        }

        post("/generate-tests-upload") {
            var uploadedFile: Path? = null
            try {
                val uploadsDir = resolveUploadsDir()
                var outputDir: String? = null

                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "outputDir") {
                                outputDir = part.value.takeIf { it.isNotBlank() }
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "file" || part.name == null) {
                                val safeName = sanitizeUploadedFileName(part.originalFileName)
                                val uploadScratchDir = Files.createTempDirectory(uploadsDir, "upload-")
                                val target = uploadScratchDir.resolve(safeName)
                                part.streamProvider().use { input ->
                                    Files.newOutputStream(target).use { output -> input.copyTo(output) }
                                }
                                uploadedFile = target
                            }
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val filePath = uploadedFile
                if (filePath == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "No 'file' part found in multipart request"))
                    return@post
                }

                val effectiveOutputDir = outputDir ?: resolveDefaultUploadOutputDir()
                call.respond(pipelineService.generateTests(filePath.toString(), effectiveOutputDir).toResponse())
            } catch (e: PipelineService.InvalidSourceException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid source path"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = e.message ?: "Internal error"))
            } finally {
                uploadedFile?.parent?.let { runCatching { it.toFile().deleteRecursively() } }
            }
        }
    }
}

private fun sanitizeUploadedFileName(originalFileName: String?): String {
    val baseName = (originalFileName ?: "Uploaded")
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .removeSuffix(".kt")
        .ifBlank { "Uploaded" }
        .replace(Regex("[^A-Za-z0-9_]"), "_")
    return "$baseName.kt"
}

private fun resolveUploadsDir(): Path {
    val dockerUploadsDir = Path.of("/data/uploads")
    if (Files.isDirectory(dockerUploadsDir) || runCatching { Files.createDirectories(dockerUploadsDir) }.isSuccess) {
        return dockerUploadsDir
    }
    return Files.createTempDirectory("agent-uploads-")
}

private fun resolveDefaultUploadOutputDir(): String {
    val base = Path.of("/data/output")
    val root = if (Files.isDirectory(base) || runCatching { Files.createDirectories(base) }.isSuccess) {
        base
    } else {
        Files.createTempDirectory("agent-output-")
    }
    return root.resolve(UUID.randomUUID().toString()).toString()
}
