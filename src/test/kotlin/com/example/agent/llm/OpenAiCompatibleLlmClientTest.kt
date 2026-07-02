package com.example.agent.llm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiCompatibleLlmClientTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful response returns Success with completion text`() {
        val responseJson = """
            {
              "choices": [
                { "message": { "role": "assistant", "content": "Generated scenario text" } }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val config = LlmConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "test-key", model = "test-model")
        val client = OpenAiCompatibleLlmClient(config)

        val result = client.complete("some prompt")

        assertTrue(result is LlmResult.Success)
        assertEquals("Generated scenario text", (result as LlmResult.Success).text)

        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("Bearer test-key", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `HTTP 500 response returns Failure`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val config = LlmConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "test-key", model = "test-model")
        val client = OpenAiCompatibleLlmClient(config)

        val result = client.complete("some prompt")

        assertTrue(result is LlmResult.Failure)
    }

    @Test
    fun `missing api key returns Failure without any network call`() {
        val config = LlmConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = null, model = "test-model")
        val client = OpenAiCompatibleLlmClient(config)

        val result = client.complete("some prompt")

        assertTrue(result is LlmResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank api key returns Failure without any network call`() {
        val config = LlmConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "   ", model = "test-model")
        val client = OpenAiCompatibleLlmClient(config)

        val result = client.complete("some prompt")

        assertTrue(result is LlmResult.Failure)
        assertEquals(0, server.requestCount)
    }
}
