package com.example.agent.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of an LLM completion call. Deliberately not tied to any specific
 * provider's JSON response shape, so callers only deal with plain text.
 */
sealed class LlmResult {
    data class Success(val text: String) : LlmResult()
    data class Failure(val reason: String) : LlmResult()
}

/** Domain-facing interface for calling an LLM to complete a prompt. */
interface LlmClient {
    fun complete(prompt: String): LlmResult
}

/**
 * [LlmClient] implementation that talks to an OpenAI-compatible
 * "/chat/completions" REST endpoint over HTTP using OkHttp.
 *
 * Behaviour:
 * - If [LlmConfig.apiKey] is null/blank, no network call is made and
 *   [LlmResult.Failure] is returned immediately (fallback mode).
 * - Any network error, timeout, or non-2xx HTTP response is converted into
 *   [LlmResult.Failure] instead of throwing, so callers can safely rely on
 *   heuristic-only generation when the LLM is unavailable.
 */
class OpenAiCompatibleLlmClient(
    private val config: LlmConfig,
    connectTimeoutSeconds: Long = 30,
    readTimeoutSeconds: Long = 30
) : LlmClient {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override fun complete(prompt: String): LlmResult {
        val apiKey = config.apiKey
        if (apiKey.isNullOrBlank()) {
            return LlmResult.Failure("No API key configured")
        }

        val requestBodyMap = mapOf(
            "model" to config.model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to prompt)
            )
        )

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBodyJson = try {
            objectMapper.writeValueAsString(requestBodyMap)
        } catch (e: Exception) {
            return LlmResult.Failure("Failed to serialize request body: ${e.message}")
        }

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LlmResult.Failure("LLM API returned HTTP ${response.code}: ${response.message}")
                } else {
                    val bodyString = response.body?.string()
                    if (bodyString.isNullOrBlank()) {
                        LlmResult.Failure("LLM API returned an empty response body")
                    } else {
                        parseCompletionText(bodyString)
                    }
                }
            }
        } catch (e: IOException) {
            LlmResult.Failure("Network error calling LLM API: ${e.message}")
        } catch (e: Exception) {
            LlmResult.Failure("Unexpected error calling LLM API: ${e.message}")
        }
    }

    private fun parseCompletionText(bodyString: String): LlmResult {
        return try {
            val root = objectMapper.readTree(bodyString)
            val content = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText(null)
            if (content.isNullOrBlank()) {
                LlmResult.Failure("Could not extract completion text from LLM response")
            } else {
                LlmResult.Success(content)
            }
        } catch (e: Exception) {
            LlmResult.Failure("Failed to parse LLM response: ${e.message}")
        }
    }
}
