package com.example.agent.llm

import com.example.agent.model.FunctionInfo

object PromptTemplates {

    fun uncoveredBranchesPrompt(function: FunctionInfo, uncoveredBranches: List<String>): String {
        val parameters = if (function.parameters.isEmpty()) {
            "no parameters"
        } else {
            function.parameters.joinToString(", ") { "${it.name}: ${it.type}${if (it.nullable) "?" else ""}" }
        }
        val branches = uncoveredBranches.joinToString("\n") { "- $it" }
        return """
            Kotlin function ${function.className}.${function.name}($parameters): ${function.returnType}.
            Automatic test generation could not reach these branches:
            $branches

            Suggest concrete argument values that would make execution enter those branches.
            Answer with a JSON array only, no explanations. Each element is an object with one key per
            parameter name (${function.parameters.joinToString(", ") { it.name }}) and a value of the
            parameter type. Use numbers for numeric types, true/false for Boolean, strings for String and Char,
            null only for nullable parameters. Give at most 10 elements.
        """.trimIndent()
    }
}
