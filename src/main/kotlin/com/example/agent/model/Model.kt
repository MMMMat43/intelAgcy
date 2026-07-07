package com.example.agent.model

data class ParameterInfo(
    val name: String,
    val type: String
)

enum class ScenarioType { POSITIVE, NEGATIVE, BOUNDARY }

data class BranchInfo(
    val kind: String,      // "if", "switch", "ternary" и т.п.
    val condition: String, // текстовое представление условия
    val lineNumber: Int
)

data class LoopInfo(
    val kind: String,      // "for", "while", "do-while"
    val condition: String,
    val lineNumber: Int
)

data class ExceptionInfo(
    val exceptionType: String,
    val context: String,   // например, "thrown" или "caught"
    val lineNumber: Int
)

data class FunctionInfo(
    val name: String,
    val className: String,
    val parameters: List<ParameterInfo>,
    val returnType: String,
    val branches: List<BranchInfo>,
    val loops: List<LoopInfo>,
    val exceptions: List<ExceptionInfo>,
    val cyclomaticComplexity: Int,
    // Признак модификатора `static` у метода. По умолчанию false, чтобы не
    // ломать существующие места создания FunctionInfo без этого поля.
    val isStatic: Boolean = false
)

data class CodeStructure(
    val sourcePath: String,
    val language: String, // "java"
    val functions: List<FunctionInfo>,
    // Имя пакета анализируемого класса (пусто, если package не объявлен).
    // По умолчанию "", чтобы не ломать существующие места создания
    // CodeStructure без этого поля.
    val packageName: String = ""
)

data class TestCase(
    val id: String,
    val functionName: String,
    val className: String,
    val type: ScenarioType,
    val description: String,
    val inputData: Map<String, String?>, // имя параметра -> значение как строка (для сериализации/кодогенерации)
    val expectedResult: String?,
    val steps: List<String>
)

data class TestSuiteResult(
    val sourcePath: String,
    val testCases: List<TestCase>
)
