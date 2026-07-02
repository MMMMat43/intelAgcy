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
}
