package com.example.agent.generation

import com.example.agent.model.ScenarioType
import com.example.agent.model.TestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestCasePostProcessorTest {

    private val postProcessor = TestCasePostProcessor()

    @Test
    fun `removes duplicate test cases with same function, type and input data`() {
        val case1 = TestCase(
            id = "id-1",
            functionName = "divide",
            className = "Calculator",
            type = ScenarioType.BOUNDARY,
            description = "  Zero   value   scenario  ",
            inputData = mapOf("value" to "0"),
            expectedResult = "ok",
            steps = listOf("step1")
        )
        val duplicate = case1.copy(id = "id-2", description = "Zero value scenario")
        val distinct = case1.copy(id = "id-3", inputData = mapOf("value" to "-1"))

        val result = postProcessor.deduplicateAndNormalize(listOf(case1, duplicate, distinct))

        assertEquals(2, result.size, "Expected duplicate to be removed, keeping only distinct entries")
        assertEquals("Zero value scenario", result[0].description, "Expected whitespace to be normalized")
    }
}
