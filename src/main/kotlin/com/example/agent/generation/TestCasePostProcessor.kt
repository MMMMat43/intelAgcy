package com.example.agent.generation

import com.example.agent.model.TestCase

/**
 * Post-processes a list of [TestCase]s produced by heuristic and/or LLM
 * generators: removes duplicates and normalizes textual descriptions.
 */
class TestCasePostProcessor {

    /**
     * Removes duplicate test cases (same [TestCase.functionName], [TestCase.type]
     * and [TestCase.inputData]) keeping the first occurrence, and normalizes
     * whitespace in [TestCase.description].
     */
    fun deduplicateAndNormalize(cases: List<TestCase>): List<TestCase> {
        val seen = LinkedHashMap<Triple<String, com.example.agent.model.ScenarioType, Map<String, String?>>, TestCase>()

        for (case in cases) {
            val normalized = case.copy(description = normalizeWhitespace(case.description))
            val key = Triple(normalized.functionName, normalized.type, normalized.inputData)
            if (!seen.containsKey(key)) {
                seen[key] = normalized
            }
        }

        return seen.values.toList()
    }

    private fun normalizeWhitespace(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }
}
