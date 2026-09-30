package com.example.agent.analysis

import com.example.agent.model.BranchInfo
import com.example.agent.model.CodeStructure
import com.example.agent.model.ExceptionInfo
import com.example.agent.model.FunctionInfo
import com.example.agent.model.FunctionKind
import com.example.agent.model.LoopInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.source.KotlinSourceFile
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhileExpression

data class ParsedSource(val file: KotlinSourceFile, val ktFile: KtFile)

data class AnalyzedFunction(
    val info: FunctionInfo,
    val psi: KtNamedFunction,
    val source: ParsedSource
)

data class AnalysisResult(
    val structure: CodeStructure,
    val functions: List<AnalyzedFunction>,
    val parsedSources: List<ParsedSource>,
    val warnings: List<String> = emptyList()
)

const val BYTE_ORDER_MARK = '\uFEFF'

private data class Owner(val className: String, val kind: String, val skipReason: String?)

private val SUPPORTED_TYPES = setOf("Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char", "String")

fun isSupportedParameterType(type: String): Boolean = type in SUPPORTED_TYPES

class KotlinCodeAnalyzer {

    fun analyze(sourcePath: String, files: List<KotlinSourceFile>): CodeStructure =
        analyzeDetailed(sourcePath, files).structure

    fun analyzeDetailed(sourcePath: String, files: List<KotlinSourceFile>): AnalysisResult {
        val warnings = mutableListOf<String>()
        val parsed = files.map { it.copy(content = it.content.trimStart(BYTE_ORDER_MARK)) }.mapNotNull { file ->
            val ktFile = KotlinPsi.parse(file.fileName, file.content)
            val errors = ktFile.collectAll(PsiErrorElement::class.java)
            if (errors.isNotEmpty()) {
                val first = errors.first()
                val line = lineOf(ktFile.text, first.textRange.startOffset)
                warnings += "Файл ${file.fileName} не разобран: синтаксическая ошибка в строке $line (${first.errorDescription})"
                null
            } else {
                ParsedSource(file, ktFile)
            }
        }

        val functions = parsed.flatMap { collect(it) }

        val packageName = parsed
            .map { it.ktFile.packageFqName.asString() }
            .firstOrNull { it.isNotBlank() } ?: ""

        val structure = CodeStructure(
            sourcePath = sourcePath,
            language = "kotlin",
            functions = functions.map { it.info },
            packageName = packageName
        )
        return AnalysisResult(structure, functions, parsed, warnings)
    }

    private fun collect(source: ParsedSource): List<AnalyzedFunction> {
        val ktFile = source.ktFile
        val packageName = ktFile.packageFqName.asString()
        val fileClassName = fileClassName(ktFile, source.file.fileName)
        val result = mutableListOf<AnalyzedFunction>()

        ktFile.declarations.forEach { declaration ->
            when (declaration) {
                is KtNamedFunction -> result += analyzeFunction(
                    declaration,
                    source,
                    packageName,
                    Owner(fileClassName, FunctionKind.TOP_LEVEL, null)
                )
                is KtClassOrObject -> collectClass(declaration, source, packageName, null, result)
            }
        }
        return result
    }

    private fun collectClass(
        declaration: KtClassOrObject,
        source: ParsedSource,
        packageName: String,
        outer: Owner?,
        out: MutableList<AnalyzedFunction>
    ) {
        val name = declaration.name ?: return
        val isCompanion = declaration is KtObjectDeclaration && declaration.isCompanion()

        val owner = when {
            outer == null -> Owner(
                className = name,
                kind = if (declaration is KtObjectDeclaration) FunctionKind.OBJECT_MEMBER else FunctionKind.MEMBER,
                skipReason = classSkipReason(declaration)
            )
            isCompanion && !outer.className.contains('.') ->
                Owner(outer.className, FunctionKind.COMPANION_MEMBER, outer.skipReason)
            else -> Owner("${outer.className}.$name", FunctionKind.MEMBER, "nested class")
        }

        declaration.declarations.forEach { member ->
            when (member) {
                is KtNamedFunction -> out += analyzeFunction(member, source, packageName, owner)
                is KtClassOrObject -> collectClass(member, source, packageName, owner, out)
            }
        }
    }

    private fun classSkipReason(declaration: KtClassOrObject): String? {
        if (declaration is KtObjectDeclaration) {
            return if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) "private object" else null
        }
        if (declaration !is KtClass) return null
        return when {
            declaration.isInterface() -> "interface"
            declaration.isEnum() -> "enum class"
            declaration.isAnnotation() -> "annotation class"
            declaration.isInner() -> "inner class"
            declaration.isSealed() || declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) -> "abstract or sealed class"
            declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) -> "private class"
            !hasDefaultConstructor(declaration) -> "class requires constructor arguments"
            else -> null
        }
    }

    private fun hasDefaultConstructor(declaration: KtClass): Boolean {
        if (declaration.hasExplicitPrimaryConstructor()) {
            return declaration.primaryConstructorParameters.all { it.hasDefaultValue() }
        }
        val secondary = declaration.secondaryConstructors
        return secondary.isEmpty() || secondary.any { it.valueParameters.all { p -> p.hasDefaultValue() } }
    }

    private fun analyzeFunction(
        function: KtNamedFunction,
        source: ParsedSource,
        packageName: String,
        owner: Owner
    ): AnalyzedFunction {
        val text = source.ktFile.text
        val body = function.bodyExpression

        val parameters = function.valueParameters.map { parameter ->
            val reference = parameter.typeReference
            val nullable = reference?.typeElement is KtNullableType
            val baseType = reference?.text?.trim()?.removeSuffix("?")?.trim()?.removePrefix("kotlin.") ?: "Unknown"
            ParameterInfo(
                name = parameter.name ?: "_",
                type = baseType,
                nullable = nullable,
                hasDefault = parameter.hasDefaultValue()
            )
        }

        val branches = mutableListOf<BranchInfo>()
        val loops = mutableListOf<LoopInfo>()
        val exceptions = mutableListOf<ExceptionInfo>()
        var complexity = 1

        if (body != null) {
            body.collectAll(KtIfExpression::class.java).forEach {
                branches += BranchInfo("if", it.condition?.text ?: "", lineOf(text, it.textRange.startOffset))
                complexity++
            }
            body.collectAll(KtWhenEntry::class.java).forEach {
                val condition = if (it.isElse) "else" else it.conditions.joinToString(", ") { c -> c.text }
                branches += BranchInfo("when", condition, lineOf(text, it.textRange.startOffset))
                if (!it.isElse) complexity++
            }
            body.collectAll(KtBinaryExpression::class.java).forEach {
                when (it.operationToken) {
                    KtTokens.ELVIS -> {
                        branches += BranchInfo("elvis", it.text, lineOf(text, it.textRange.startOffset))
                        complexity++
                    }
                    KtTokens.ANDAND, KtTokens.OROR -> complexity++
                }
            }
            body.collectAll(KtForExpression::class.java).forEach {
                loops += LoopInfo("for", it.loopRange?.text ?: "", lineOf(text, it.textRange.startOffset))
                complexity++
            }
            body.collectAll(KtWhileExpression::class.java).forEach {
                loops += LoopInfo("while", it.condition?.text ?: "", lineOf(text, it.textRange.startOffset))
                complexity++
            }
            body.collectAll(KtDoWhileExpression::class.java).forEach {
                loops += LoopInfo("do-while", it.condition?.text ?: "", lineOf(text, it.textRange.startOffset))
                complexity++
            }
            body.collectAll(KtThrowExpression::class.java).forEach {
                val thrown = it.thrownExpression
                val type = if (thrown is KtCallExpression) thrown.calleeExpression?.text ?: thrown.text else thrown?.text ?: ""
                exceptions += ExceptionInfo(type, "thrown", lineOf(text, it.textRange.startOffset))
            }
            body.collectAll(KtCatchClause::class.java).forEach {
                exceptions += ExceptionInfo(
                    it.catchParameter?.typeReference?.text ?: "Throwable",
                    "caught",
                    lineOf(text, it.textRange.startOffset)
                )
                complexity++
            }
        }

        val returnType = function.typeReference?.text?.trim()
            ?: if (function.hasBlockBody()) "Unit" else "Unknown"

        val info = FunctionInfo(
            name = function.name ?: "anonymous",
            className = owner.className,
            parameters = parameters,
            returnType = returnType,
            branches = branches,
            loops = loops,
            exceptions = exceptions,
            cyclomaticComplexity = complexity,
            kind = owner.kind,
            skipReason = skipReason(function, parameters, owner),
            packageName = packageName
        )
        return AnalyzedFunction(info, function, source)
    }

    private fun skipReason(function: KtNamedFunction, parameters: List<ParameterInfo>, owner: Owner): String? {
        return when {
            owner.skipReason != null -> owner.skipReason
            !function.hasBody() -> "function has no body"
            function.hasModifier(KtTokens.SUSPEND_KEYWORD) -> "suspend function"
            function.receiverTypeReference != null -> "extension function"
            function.typeParameters.isNotEmpty() -> "generic function"
            function.hasModifier(KtTokens.PRIVATE_KEYWORD) -> "private function"
            function.hasModifier(KtTokens.PROTECTED_KEYWORD) -> "protected function"
            function.hasModifier(KtTokens.INTERNAL_KEYWORD) -> "internal function"
            function.valueParameters.any { it.isVarArg } -> "vararg parameter"
            owner.kind == FunctionKind.TOP_LEVEL && function.name == "main" -> "entry point"
            else -> parameters.firstOrNull { !isSupportedParameterType(it.type) }
                ?.let { "unsupported parameter type: ${it.type}" }
        }
    }

    private fun fileClassName(ktFile: KtFile, fileName: String): String {
        val explicit = ktFile.fileAnnotationList?.annotationEntries
            ?.firstOrNull { it.shortName?.asString() == "JvmName" }
            ?.valueArguments?.firstOrNull()?.getArgumentExpression()?.text?.trim('"')
        if (!explicit.isNullOrBlank()) return explicit
        val base = fileName.removeSuffix(".kt").replace(Regex("[^A-Za-z0-9_]"), "_")
        return base.replaceFirstChar { it.uppercaseChar() } + "Kt"
    }
}
