package com.example.agent.api

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
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

    @Test
    fun `GET health returns 200 and status ok`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""), "Expected status ok in response body")
    }

    @Test
    fun `POST analyze with valid java file returns 200 and structure`(@TempDir tempDir: Path) = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val javaFile = tempDir.resolve("Sample.java")
        Files.writeString(
            javaFile,
            """
            public class Sample {
                public int add(int a, int b) {
                    return a + b;
                }
            }
            """.trimIndent()
        )

        val response = client.post("/analyze") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourcePath": "${javaFile.toString().replace("\\", "\\\\")}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"functions\""), "Expected functions field in analysis response")
        assertTrue(body.contains("\"add\""), "Expected 'add' function name in analysis response")
    }

    @Test
    fun `POST analyze with nonexistent path returns 400`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.post("/analyze") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourcePath": "/this/path/does/not/exist/Sample.java"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"error\""), "Expected error field in response body")
    }

    @Test
    fun `POST generate-tests-upload with a real Java file returns 200 and a summary`(@TempDir tempDir: Path) = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val javaContent = """
            public class Sample {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val outputDir = tempDir.resolve("out").toString()

        val response = client.post("/generate-tests-upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("outputDir", outputDir)
                        append(
                            "file",
                            javaContent.toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"Sample.java\"")
                            }
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"functionsCount\""), "Expected functionsCount field in response: $body")
        assertTrue(Files.exists(Path.of(outputDir, "analysis.json")), "Expected analysis.json to be written to outputDir")
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
        assertTrue(response.bodyAsText().contains("\"error\""), "Expected error field in response body")
    }
}
