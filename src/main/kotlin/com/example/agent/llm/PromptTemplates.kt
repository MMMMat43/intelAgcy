package com.example.agent.llm

import com.example.agent.model.FunctionInfo

/**
 * Prompt templates used to ask an LLM for additional, semantically-informed
 * test scenario suggestions on top of purely heuristic generation.
 */
object PromptTemplates {

    /**
     * Builds a prompt describing a single analyzed function, asking the model
     * to suggest positive, negative and boundary test scenarios for it.
     */
    fun functionAnalysisPrompt(functionInfo: FunctionInfo): String {
        val parametersDescription = if (functionInfo.parameters.isEmpty()) {
            "no parameters"
        } else {
            functionInfo.parameters.joinToString(", ") { "${it.name}: ${it.type}${if (it.nullable) "?" else ""}" }
        }

        val branchesDescription = if (functionInfo.branches.isEmpty()) {
            "none"
        } else {
            functionInfo.branches.joinToString("; ") { "${it.kind} (${it.condition}) at line ${it.lineNumber}" }
        }

        val loopsDescription = if (functionInfo.loops.isEmpty()) {
            "none"
        } else {
            functionInfo.loops.joinToString("; ") { "${it.kind} (${it.condition}) at line ${it.lineNumber}" }
        }

        val exceptionsDescription = if (functionInfo.exceptions.isEmpty()) {
            "none"
        } else {
            functionInfo.exceptions.joinToString("; ") { "${it.exceptionType} (${it.context}) at line ${it.lineNumber}" }
        }

        return """
            Analyze the following Kotlin function and suggest test scenarios.

            Class: ${functionInfo.className}
            Method: ${functionInfo.name}
            Return type: ${functionInfo.returnType}
            Parameters: $parametersDescription
            Branches: $branchesDescription
            Loops: $loopsDescription
            Exceptions: $exceptionsDescription
            Cyclomatic complexity: ${functionInfo.cyclomaticComplexity}

            Suggest additional positive, negative, and boundary test scenarios that a
            purely structural analysis might miss, focusing on the business meaning of
            this method. For each scenario, briefly describe: scenario type (positive,
            negative, or boundary), a short description, and the expected outcome.
        """.trimIndent()
    }
}
