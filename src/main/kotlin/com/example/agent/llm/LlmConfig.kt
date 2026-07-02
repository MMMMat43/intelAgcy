package com.example.agent.llm

/**
 * Configuration for calling an external, OpenAI-compatible LLM HTTP API.
 *
 * Values are read from environment variables via [fromEnv]:
 * - `LLM_API_BASE_URL` (default: "https://api.openai.com/v1")
 * - `LLM_API_KEY` (no default; when absent the client must fall back gracefully)
 * - `LLM_MODEL` (default: "gpt-4o-mini")
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String?,
    val model: String
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        fun fromEnv(): LlmConfig {
            val env = System.getenv()
            val baseUrl = env["LLM_API_BASE_URL"]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            val apiKey = env["LLM_API_KEY"]?.takeIf { it.isNotBlank() }
            val model = env["LLM_MODEL"]?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
            return LlmConfig(baseUrl = baseUrl, apiKey = apiKey, model = model)
        }
    }
}
