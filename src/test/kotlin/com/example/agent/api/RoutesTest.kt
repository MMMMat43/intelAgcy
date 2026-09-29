package com.example.agent.api

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RoutesTest {

    private val sampleSource = """
        class Sample {
            fun add(a: Int, b: Int): Int {
                return a + b
            }
        }
    """.trimIndent()

    @Test
    fun `GET health returns 200 and status ok`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun `POST analyze with valid kotlin file returns 200 and structure`(@TempDir tempDir: Path) = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val file = tempDir.resolve("Sample.kt")
        Files.writeString(file, sampleSource)

        val response = client.post("/analyze") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourcePath": "${file.toString().replace("\\", "\\\\")}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"functions\""))
        assertTrue(body.contains("\"add\""))
        assertTrue(body.contains("\"kotlin\""))
    }

    @Test
    fun `POST analyze with nonexistent path returns 400`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.post("/analyze") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourcePath": "/this/path/does/not/exist/Sample.kt"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"error\""))
    }

    @Test
    fun `POST generate-tests-upload with a real Kotlin file returns 200 and writes real assertions`(@TempDir tempDir: Path) = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val outputDir = tempDir.resolve("out").toString()

        val response = client.post("/generate-tests-upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("outputDir", outputDir)
                        append(
                            "file",
                            sampleSource.toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"Sample.kt\"")
                            }
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"functionsCount\""), body)
        assertTrue(Files.exists(Path.of(outputDir, "analysis.json")))

        val generated = Files.walk(Path.of(outputDir, "generated-tests"))
            .filter { it.toString().endsWith(".kt") }
            .toList()
            .joinToString("\n") { Files.readString(it) }
        assertTrue(generated.contains("assertEquals"), generated)
    }

    @Test
    fun `POST generate-tests-upload without a file part returns 400`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.post("/generate-tests-upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("outputDir", "/tmp/whatever")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"error\""))
    }
}
