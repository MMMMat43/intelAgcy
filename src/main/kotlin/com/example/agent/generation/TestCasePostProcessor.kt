package com.example.agent.generation

import com.example.agent.model.TestCase

class TestCasePostProcessor {

    private data class Key(
        val className: String,
        val functionName: String,
        val signature: String,
        val type: com.example.agent.model.ScenarioType,
        val inputData: Map<String, String?>
    )

    fun deduplicateAndNormalize(cases: List<TestCase>): List<TestCase> {
        val seen = LinkedHashMap<Key, TestCase>()

        for (case in cases) {
            val normalized = case.copy(description = normalizeWhitespace(case.description))
            val key = Key(normalized.className, normalized.functionName, normalized.signature, normalized.type, normalized.inputData)
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
