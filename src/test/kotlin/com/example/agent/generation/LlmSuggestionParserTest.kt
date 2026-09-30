package com.example.agent.generation

import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.model.FunctionInfo
import com.example.agent.model.ParameterInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmSuggestionParserTest {

    private val function = FunctionInfo(
        name = "f",
        className = "C",
        parameters = listOf(
            ParameterInfo("count", "Int"),
            ParameterInfo("label", "String", nullable = true),
            ParameterInfo("flag", "Boolean")
        ),
        returnType = "Int",
        branches = emptyList(),
        loops = emptyList(),
        exceptions = emptyList(),
        cyclomaticComplexity = 1
    )

    @Test
    fun `parses a fenced json array`() {
        val text = "Here you go:\n```json\n[{\"count\": 5, \"label\": \"x\", \"flag\": true}]\n```\nHope it helps"

        val result = LlmSuggestionParser.parse(text, function)

        assertEquals(listOf(mapOf("count" to "5", "label" to "x", "flag" to "true")), result)
    }

    @Test
    fun `accepts null for nullable and rejects null for non nullable`() {
        val text = "[{\"count\": 1, \"label\": null, \"flag\": false}, {\"count\": null, \"label\": \"a\", \"flag\": false}]"

        val result = LlmSuggestionParser.parse(text, function)

        assertEquals(1, result.size)
        assertEquals(null, result.single()["label"])
    }

    @Test
    fun `ignores invalid entries and garbage`() {
        val text = "[{\"count\": \"abc\", \"label\": \"x\", \"flag\": true}, {\"count\": 2}, 7, \"text\", {\"count\": 3, \"label\": \"y\", \"flag\": false}]"

        val result = LlmSuggestionParser.parse(text, function)

        assertEquals(1, result.size)
        assertEquals("3", result.single()["count"])
    }

    @Test
    fun `returns nothing for non json answers and duplicates are removed`() {
        assertTrue(LlmSuggestionParser.parse("I cannot help with that", function).isEmpty())
        assertTrue(LlmSuggestionParser.parse("[not json at all", function).isEmpty())

        val duplicated = "[{\"count\": 1, \"label\": \"a\", \"flag\": true}, {\"count\": 1, \"label\": \"a\", \"flag\": true}]"
        assertEquals(1, LlmSuggestionParser.parse(duplicated, function).size)
    }

    @Test
    fun `suggester returns nothing when the client fails or there is nothing uncovered`() {
        val failing = object : LlmClient {
            override fun complete(prompt: String): LlmResult = LlmResult.Failure("boom")
        }
        val throwing = object : LlmClient {
            override fun complete(prompt: String): LlmResult = throw IllegalStateException("network")
        }
        val working = object : LlmClient {
            override fun complete(prompt: String): LlmResult = LlmResult.Success("[{\"count\": 1, \"label\": null, \"flag\": true}]")
        }

        assertTrue(LlmUncoveredBranchSuggester(failing).suggest(function, listOf("if (count > 1)")).isEmpty())
        assertTrue(LlmUncoveredBranchSuggester(throwing).suggest(function, listOf("if (count > 1)")).isEmpty())
        assertTrue(LlmUncoveredBranchSuggester(working).suggest(function, emptyList()).isEmpty())
        assertEquals(1, LlmUncoveredBranchSuggester(working).suggest(function, listOf("if (count > 1)")).size)
    }
}
