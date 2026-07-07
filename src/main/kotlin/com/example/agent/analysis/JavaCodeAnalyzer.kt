package com.example.agent.analysis

import com.example.agent.model.BranchInfo
import com.example.agent.model.CodeStructure
import com.example.agent.model.ExceptionInfo
import com.example.agent.model.FunctionInfo
import com.example.agent.model.LoopInfo
import com.example.agent.model.ParameterInfo
import com.example.agent.source.JavaSourceFile
import com.github.javaparser.ParseProblemException
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.ConditionalExpr
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.DoStmt
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.stmt.ForStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.stmt.ThrowStmt
import com.github.javaparser.ast.stmt.WhileStmt

/**
 * Статический анализатор Java-исходного кода на базе `javaparser-core`.
 *
 * Строит AST каждого исходного файла и для каждого метода извлекает:
 * параметры, тип возврата, ветвления (if/switch/ternary), циклы
 * (for/while/do-while), обработку исключений (throw/catch) и
 * цикломатическую сложность.
 *
 * ### Формула цикломатической сложности
 * Используется общепринятый вариант подсчёта по количеству точек
 * принятия решений в теле метода:
 *
 * ```
 * M = 1 + (число if/else-if)
 *       + (число case-меток в switch, кроме default)
 *       + (число for/foreach)
 *       + (число while)
 *       + (число do-while)
 *       + (число catch-блоков)
 *       + (число тернарных операторов)
 *       + (число операторов && и ||)
 * ```
 *
 * Это эквивалентно классической формуле `M = E - N + 2P` для графа потока
 * управления одного метода (P = 1), но выражено через прямой подсчёт
 * узлов AST, что проще реализовать без явного построения CFG.
 * Метод без единого ветвления/цикла/catch имеет сложность `1`.
 */
class JavaCodeAnalyzer {

    /**
     * Извлекает имя пакета анализируемого проекта.
     *
     * УПРОЩЕНИЕ: если переданные файлы объявляют разные package (несколько
     * классов в разных пакетах в одном запросе), берётся package ПЕРВОГО
     * файла с непустым package в порядке списка [files]. Для типичного
     * сценария (анализ одного файла/одного класса) это ведёт себя ожидаемо;
     * для многофайловых мульти-package проектов это заведомое упрощение,
     * достаточное для того, чтобы сгенерированный тестовый код мог
     * обращаться к анализируемому классу без явного import (см.
     * [com.example.agent.codegen.JUnit5TestCodeGenerator]).
     */
    fun analyze(sourcePath: String, files: List<JavaSourceFile>): CodeStructure {
        val compilationUnits = files.mapNotNull { file ->
            try {
                StaticJavaParser.parse(file.content)
            } catch (e: ParseProblemException) {
                // Файл с синтаксическими ошибками пропускается: анализ остальных
                // файлов не должен прерываться из-за одного некорректного файла.
                null
            }
        }

        val functions = compilationUnits.flatMap { compilationUnit ->
            compilationUnit.findAll(MethodDeclaration::class.java).map { method -> analyzeMethod(method) }
        }

        val packageName = compilationUnits
            .firstNotNullOfOrNull { compilationUnit ->
                compilationUnit.packageDeclaration
                    .map { it.nameAsString }
                    .filter { it.isNotBlank() }
                    .orElse(null)
            } ?: ""

        return CodeStructure(
            sourcePath = sourcePath,
            language = "java",
            functions = functions,
            packageName = packageName
        )
    }

    private fun analyzeMethod(method: MethodDeclaration): FunctionInfo {
        val className = method.findAncestor(com.github.javaparser.ast.body.TypeDeclaration::class.java)
            .map { it.nameAsString }
            .orElse("Unknown")

        val parameters = method.parameters.map { param ->
            ParameterInfo(name = param.nameAsString, type = param.typeAsString)
        }

        val branches = collectBranches(method)
        val loops = collectLoops(method)
        val exceptions = collectExceptions(method)
        val complexity = calculateCyclomaticComplexity(method)

        return FunctionInfo(
            name = method.nameAsString,
            className = className,
            parameters = parameters,
            returnType = method.typeAsString,
            branches = branches,
            loops = loops,
            exceptions = exceptions,
            cyclomaticComplexity = complexity,
            isStatic = method.isStatic
        )
    }

    private fun collectBranches(method: MethodDeclaration): List<BranchInfo> {
        val branches = mutableListOf<BranchInfo>()

        method.findAll(IfStmt::class.java).forEach { ifStmt ->
            branches.add(
                BranchInfo(
                    kind = "if",
                    condition = ifStmt.condition.toString(),
                    lineNumber = ifStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(SwitchEntry::class.java).forEach { entry ->
            val labels = if (entry.labels.isEmpty) "default" else entry.labels.joinToString(", ") { it.toString() }
            branches.add(
                BranchInfo(
                    kind = "switch",
                    condition = labels,
                    lineNumber = entry.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(ConditionalExpr::class.java).forEach { ternary ->
            branches.add(
                BranchInfo(
                    kind = "ternary",
                    condition = ternary.condition.toString(),
                    lineNumber = ternary.begin.map { it.line }.orElse(-1)
                )
            )
        }

        return branches
    }

    private fun collectLoops(method: MethodDeclaration): List<LoopInfo> {
        val loops = mutableListOf<LoopInfo>()

        method.findAll(ForStmt::class.java).forEach { forStmt ->
            loops.add(
                LoopInfo(
                    kind = "for",
                    condition = forStmt.compare.map { it.toString() }.orElse(""),
                    lineNumber = forStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(ForEachStmt::class.java).forEach { forEachStmt ->
            loops.add(
                LoopInfo(
                    kind = "for",
                    condition = forEachStmt.iterable.toString(),
                    lineNumber = forEachStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(WhileStmt::class.java).forEach { whileStmt ->
            loops.add(
                LoopInfo(
                    kind = "while",
                    condition = whileStmt.condition.toString(),
                    lineNumber = whileStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(DoStmt::class.java).forEach { doStmt ->
            loops.add(
                LoopInfo(
                    kind = "do-while",
                    condition = doStmt.condition.toString(),
                    lineNumber = doStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        return loops
    }

    private fun collectExceptions(method: MethodDeclaration): List<ExceptionInfo> {
        val exceptions = mutableListOf<ExceptionInfo>()

        method.findAll(ThrowStmt::class.java).forEach { throwStmt ->
            val exceptionType = extractThrownTypeName(throwStmt)
            exceptions.add(
                ExceptionInfo(
                    exceptionType = exceptionType,
                    context = "thrown",
                    lineNumber = throwStmt.begin.map { it.line }.orElse(-1)
                )
            )
        }

        method.findAll(CatchClause::class.java).forEach { catchClause ->
            exceptions.add(
                ExceptionInfo(
                    exceptionType = catchClause.parameter.typeAsString,
                    context = "caught",
                    lineNumber = catchClause.begin.map { it.line }.orElse(-1)
                )
            )
        }

        return exceptions
    }

    private fun extractThrownTypeName(throwStmt: ThrowStmt): String {
        val expression = throwStmt.expression
        return if (expression.isObjectCreationExpr) {
            expression.asObjectCreationExpr().typeAsString
        } else {
            expression.toString()
        }
    }

    private fun calculateCyclomaticComplexity(method: MethodDeclaration): Int {
        var complexity = 1

        complexity += method.findAll(IfStmt::class.java).size
        complexity += method.findAll(SwitchEntry::class.java).count { !it.labels.isEmpty }
        complexity += method.findAll(ForStmt::class.java).size
        complexity += method.findAll(ForEachStmt::class.java).size
        complexity += method.findAll(WhileStmt::class.java).size
        complexity += method.findAll(DoStmt::class.java).size
        complexity += method.findAll(CatchClause::class.java).size
        complexity += method.findAll(ConditionalExpr::class.java).size
        complexity += method.findAll(BinaryExpr::class.java).count {
            it.operator == BinaryExpr.Operator.AND || it.operator == BinaryExpr.Operator.OR
        }

        return complexity
    }
}
