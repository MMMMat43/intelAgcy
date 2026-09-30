package com.example.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CliTest {

    @Test
    fun `parseSourceArgument returns path when --source flag is present`() {
        val result = parseSourceArgument(arrayOf("--source", "src/main/java"))
        assertEquals("src/main/java", result)
    }

    @Test
    fun `parseSourceArgument returns null when --source flag is missing`() {
        val result = parseSourceArgument(arrayOf())
        assertNull(result)
    }

    @Test
    fun `parseSourceArgument returns null when --source flag has no value`() {
        val result = parseSourceArgument(arrayOf("--source"))
        assertNull(result)
    }

    @Test
    fun `resolveHistoryPath prefers the flag over the environment`() {
        val result = resolveHistoryPath(arrayOf("--history", "a.db"), mapOf("AGENT_HISTORY_DB" to "b.db"))
        assertEquals("a.db", result)
    }

    @Test
    fun `resolveHistoryPath falls back to the environment`() {
        val result = resolveHistoryPath(arrayOf(), mapOf("AGENT_HISTORY_DB" to "b.db"))
        assertEquals("b.db", result)
    }

    @Test
    fun `resolveHistoryPath is disabled by default`() {
        assertNull(resolveHistoryPath(arrayOf(), emptyMap()))
        assertNull(resolveHistoryPath(arrayOf(), mapOf("AGENT_HISTORY_DB" to " ")))
    }
}
