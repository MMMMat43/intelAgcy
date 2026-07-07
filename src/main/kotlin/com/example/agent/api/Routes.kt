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

/**
 * Wires up the REST API exposed by the intelligent test agent: `/health`,
 * `/analyze` and `/generate-tests`, backed by [PipelineService].
 *
 * These endpoints are the integration surface consumed by an n8n workflow
 * (see `integration/n8n/workflow.json`), or any other HTTP client.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson { }
    }
}

fun Application.configureRouting(pipelineService: PipelineService = PipelineService()) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        post("/analyze") {
            val request = call.receive<AnalyzeRequest>()
            try {
                val structure = pipelineService.analyze(request.sourcePath)
                call.respond(structure)
            } catch (e: PipelineService.InvalidSourceException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid source path"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = e.message ?: "Internal error"))
            }
        }

        post("/generate-tests") {
            val request = call.receive<GenerateTestsRequest>()
            try {
                val result = pipelineService.generateTests(request.sourcePath, request.outputDir)
                call.respond(
                    GenerateTestsResponse(
                        functionsCount = result.functionsCount,
                        testCasesCount = result.testCasesCount,
                        generatedTestFilePath = result.generatedTestFilePath
                    )
                )
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
                                // The compiler (see InMemoryJavaCompiler) infers the
                                // expected public class name from the file's name, so
                                // the temp file MUST keep the original uploaded file
                                // name (e.g. "SampleCalculator.java") rather than a
                                // random name - otherwise compilation silently fails
                                // to match the public class and real assertions can
                                // never be generated, only TODO placeholders.
                                val safeName = sanitizeUploadedFileName(part.originalFileName)
                                // Each upload gets its own subdirectory so concurrent
                                // uploads with the same original file name never
                                // collide with each other.
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

                // Если outputDir не передан явно, каждая загрузка получает
                // свою собственную поддиректорию (по UUID), чтобы
                // параллельные демонстрационные запуски (например, через
                // n8n Form Trigger) не затирали результаты друг друга.
                val effectiveOutputDir = outputDir ?: resolveDefaultUploadOutputDir()

                val result = pipelineService.generateTests(filePath.toString(), effectiveOutputDir)
                call.respond(
                    GenerateTestsResponse(
                        functionsCount = result.functionsCount,
                        testCasesCount = result.testCasesCount,
                        generatedTestFilePath = result.generatedTestFilePath
                    )
                )
            } catch (e: PipelineService.InvalidSourceException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid source path"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = e.message ?: "Internal error"))
            } finally {
                // Delete the whole per-upload scratch directory (not just the
                // file itself), since the file now lives inside its own
                // dedicated temp subdirectory (see sanitizeUploadedFileName usage above).
                uploadedFile?.parent?.let { runCatching { it.toFile().deleteRecursively() } }
            }
        }
    }
}

/**
 * Directory used to stash uploaded `.java` files before analysis. Mounted
 * as a Docker volume at `/data/uploads` (see `docker-compose.yml`) so
 * uploads are visible on the host for debugging; falls back to the system
 * temp directory when that path does not exist (e.g. when running outside
 * Docker via `.\run.ps1 serve`).
 */
/**
 * Preserves the original uploaded file name (needed so the Java compiler's
 * "file name must match the public class name" rule is satisfied - see the
 * comment at the call site), while defending against path traversal
 * (`../`), absolute paths, and empty/missing names, and normalizing the
 * extension to `.java` regardless of what the client sent.
 */
private fun sanitizeUploadedFileName(originalFileName: String?): String {
    val baseName = (originalFileName ?: "Uploaded")
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .removeSuffix(".java")
        .ifBlank { "Uploaded" }
        .replace(Regex("[^A-Za-z0-9_$]"), "_")
    return "$baseName.java"
}

private fun resolveUploadsDir(): Path {
    val dockerUploadsDir = Path.of("/data/uploads")
    if (Files.isDirectory(dockerUploadsDir) || runCatching { Files.createDirectories(dockerUploadsDir) }.isSuccess) {
        return dockerUploadsDir
    }
    return Files.createTempDirectory("agent-uploads-")
}

/**
 * Default output directory for uploads that do not explicitly specify one:
 * a fresh, unique subdirectory per upload under `/data/output` (or the
 * system temp directory outside Docker), so concurrent demo runs never
 * overwrite each other's generated artifacts.
 */
private fun resolveDefaultUploadOutputDir(): String {
    val base = Path.of("/data/output")
    val root = if (Files.isDirectory(base) || runCatching { Files.createDirectories(base) }.isSuccess) {
        base
    } else {
        Files.createTempDirectory("agent-output-")
    }
    return root.resolve(UUID.randomUUID().toString()).toString()
}
