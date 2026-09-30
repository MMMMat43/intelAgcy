package com.example.agent.generation

import com.example.agent.execution.ConversionResult
import com.example.agent.execution.TypeConversion
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmResult
import com.example.agent.llm.PromptTemplates
import com.example.agent.model.FunctionInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class LlmUncoveredBranchSuggester(
    private val client: LlmClient,
    private val maxSuggestions: Int = 20
) {

    fun suggest(function: FunctionInfo, uncoveredBranches: List<String>): List<Map<String, String?>> {
        if (uncoveredBranches.isEmpty()) return emptyList()
        val response = try {
            client.complete(PromptTemplates.uncoveredBranchesPrompt(function, uncoveredBranches))
        } catch (e: Exception) {
            return emptyList()
        }
        return when (response) {
            is LlmResult.Failure -> emptyList()
            is LlmResult.Success -> LlmSuggestionParser.parse(response.text, function).take(maxSuggestions)
        }
    }
}

object LlmSuggestionParser {

    private val mapper = ObjectMapper()

    fun parse(text: String, function: FunctionInfo): List<Map<String, String?>> {
        val json = extractJson(text) ?: return emptyList()
        val root = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val elements: List<JsonNode> = when {
            root.isArray -> root.toList()
            root.isObject -> listOf(root)
            else -> emptyList()
        }
        val seen = LinkedHashSet<Map<String, String?>>()
        elements.forEach { element ->
            if (element.isObject) toVector(element, function)?.let { seen += it }
        }
        return seen.toList()
    }

    private fun extractJson(text: String): String? {
        val withoutFences = text.replace(Regex("```[a-zA-Z]*"), "")
        val arrayStart = withoutFences.indexOf('[')
        val arrayEnd = withoutFences.lastIndexOf(']')
        if (arrayStart in 0 until arrayEnd) return withoutFences.substring(arrayStart, arrayEnd + 1)
        val objectStart = withoutFences.indexOf('{')
        val objectEnd = withoutFences.lastIndexOf('}')
        if (objectStart in 0 until objectEnd) return withoutFences.substring(objectStart, objectEnd + 1)
        return null
    }

    private fun toVector(node: JsonNode, function: FunctionInfo): Map<String, String?>? {
        val vector = LinkedHashMap<String, String?>()
        for (parameter in function.parameters) {
            val field = node.get(parameter.name) ?: return null
            val type = TypeConversion.normalize(parameter.type)
            val value: String? = when {
                field.isNull -> null
                field.isBoolean -> field.asBoolean().toString()
                field.isNumber -> if (type in INTEGER_TYPES && field.canConvertToLong()) field.asLong().toString() else field.asText()
                field.isTextual -> field.asText()
                else -> return null
            }
            if (TypeConversion.convert(parameter.type, parameter.nullable, value) !is ConversionResult.Converted) return null
            vector[parameter.name] = value
        }
        return vector
    }

    private val INTEGER_TYPES = setOf("Int", "Long", "Short", "Byte")
}
