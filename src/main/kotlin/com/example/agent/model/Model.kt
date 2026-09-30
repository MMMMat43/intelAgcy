package com.example.agent.model

data class ParameterInfo(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val hasDefault: Boolean = false
)

enum class ScenarioType { POSITIVE, NEGATIVE, BOUNDARY }

data class BranchInfo(
    val kind: String,
    val condition: String,
    val lineNumber: Int
)

data class LoopInfo(
    val kind: String,
    val condition: String,
    val lineNumber: Int
)

data class ExceptionInfo(
    val exceptionType: String,
    val context: String,
    val lineNumber: Int
)

object FunctionKind {
    const val MEMBER = "member"
    const val TOP_LEVEL = "top-level"
    const val OBJECT_MEMBER = "object-member"
    const val COMPANION_MEMBER = "companion-member"
}

data class FunctionInfo(
    val name: String,
    val className: String,
    val parameters: List<ParameterInfo>,
    val returnType: String,
    val branches: List<BranchInfo>,
    val loops: List<LoopInfo>,
    val exceptions: List<ExceptionInfo>,
    val cyclomaticComplexity: Int,
    val kind: String = FunctionKind.MEMBER,
    val skipReason: String? = null,
    val packageName: String = ""
)

fun FunctionInfo.isTestable(): Boolean = skipReason == null

fun FunctionInfo.signature(): String =
    parameters.joinToString(",") { it.type + if (it.nullable) "?" else "" }

fun FunctionInfo.key(): String = "$className.$name(${signature()})"

data class CodeStructure(
    val sourcePath: String,
    val language: String,
    val functions: List<FunctionInfo>,
    val packageName: String = ""
)

data class TestCase(
    val id: String,
    val functionName: String,
    val className: String,
    val type: ScenarioType,
    val description: String,
    val inputData: Map<String, String?>,
    val expectedResult: String?,
    val steps: List<String>,
    val signature: String = "",
    val origin: String = "HEURISTIC"
)

data class TestSuiteResult(
    val sourcePath: String,
    val testCases: List<TestCase>
)
