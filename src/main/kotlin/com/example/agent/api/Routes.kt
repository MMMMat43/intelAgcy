package com.example.agent.api

import com.example.agent.api.dto.AnalyzeRequest
import com.example.agent.api.dto.ErrorResponse
import com.example.agent.api.dto.GenerateTestsRequest
import com.example.agent.api.dto.GenerateTestsResponse
import com.example.agent.api.dto.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

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
    }
}
