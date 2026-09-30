package com.example.agent.coverage

import com.example.agent.analysis.AnalyzedFunction
import com.example.agent.analysis.collectAll
import com.example.agent.execution.TypeConversion
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

data class BoundaryHints(
    val values: Map<String, List<String?>>,
    val constants: List<Double>
)

internal class ConstantEvaluator(private val constants: MutableMap<String, Any>) {

    fun define(name: String, value: Any) {
        constants[name] = value
    }

    fun evaluate(expression: KtExpression?): Any? = when (expression) {
        null -> null
        is KtParenthesizedExpression -> evaluate(expression.expression)
        is KtConstantExpression -> parseConstant(expression.text)
        is KtStringTemplateExpression -> evaluateString(expression)
        is KtNameReferenceExpression -> constants[expression.getReferencedName()]
        is KtDotQualifiedExpression -> (expression.selectorExpression as? KtNameReferenceExpression)
            ?.let { constants[it.getReferencedName()] }
        is KtPrefixExpression -> evaluatePrefix(expression)
        is KtBinaryExpression -> evaluateBinary(expression)
        else -> null
    }

    private fun evaluateString(expression: KtStringTemplateExpression): String? {
        val builder = StringBuilder()
        for (entry in expression.entries) {
            if (entry is KtLiteralStringTemplateEntry) builder.append(entry.text) else return null
        }
        return builder.toString()
    }

    private fun evaluatePrefix(expression: KtPrefixExpression): Any? {
        val operand = evaluate(expression.baseExpression)
        return when (expression.operationToken) {
            KtTokens.MINUS -> (operand as? Double)?.let { -it }
            KtTokens.PLUS -> operand as? Double
            KtTokens.EXCL -> (operand as? Boolean)?.not()
            else -> null
        }
    }

    private fun evaluateBinary(expression: KtBinaryExpression): Any? {
        val left = evaluate(expression.left)
        val right = evaluate(expression.right)
        val token = expression.operationToken
        if (left is Double && right is Double) {
            return when (token) {
                KtTokens.PLUS -> left + right
                KtTokens.MINUS -> left - right
                KtTokens.MUL -> left * right
                KtTokens.DIV -> if (right == 0.0) null else left / right
                KtTokens.PERC -> if (right == 0.0) null else left % right
                else -> null
            }
        }
        if (left is String && right is String && token == KtTokens.PLUS) return left + right
        return null
    }

    private fun parseConstant(text: String): Any? {
        if (text == "true") return true
        if (text == "false") return false
        if (text == "null") return null
        if (text.startsWith("'")) return null
        val cleaned = text.replace("_", "")
        if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            return cleaned.substring(2).removeSuffix("L").toLongOrNull(16)?.toDouble()
        }
        if (cleaned.startsWith("0b") || cleaned.startsWith("0B")) {
            return cleaned.substring(2).removeSuffix("L").toLongOrNull(2)?.toDouble()
        }
        return cleaned.removeSuffix("L").removeSuffix("l").removeSuffix("f").removeSuffix("F").toDoubleOrNull()
    }
}

class BoundaryConditionExtractor {

    fun extract(analyzed: AnalyzedFunction): BoundaryHints {
        val info = analyzed.info
        val function = analyzed.psi
        val body = function.bodyExpression ?: return BoundaryHints(emptyMap(), emptyList())

        val parameterTypes = info.parameters.associate { it.name to TypeConversion.normalize(it.type) }
        val evaluator = ConstantEvaluator(fileConstants(analyzed))
        val locals = LinkedHashMap<String, Set<String>>()

        body.collectAll(KtForExpression::class.java).forEach { loop ->
            val name = loop.loopParameter?.name ?: return@forEach
            val range = loop.loopRange ?: return@forEach
            locals[name] = dependencies(range, parameterTypes.keys, locals)
        }

        body.collectAll(KtProperty::class.java).forEach { property ->
            val name = property.name ?: return@forEach
            val initializer = property.initializer ?: return@forEach
            locals[name] = dependencies(initializer, parameterTypes.keys, locals)
            if (!property.isVar) {
                evaluator.evaluate(initializer)?.let { evaluator.define(name, it) }
            }
        }

        val values = LinkedHashMap<String, LinkedHashSet<String?>>()
        val constants = LinkedHashSet<Double>()

        fun add(parameter: String, value: String?) {
            values.getOrPut(parameter) { LinkedHashSet() } += value
        }

        body.collectAll(KtBinaryExpression::class.java).forEach { expression ->
            val token = expression.operationToken
            if (token !in COMPARISONS) return@forEach

            val left = expression.left
            val right = expression.right
            if (isNullLiteral(right) || isNullLiteral(left)) {
                val variable = if (isNullLiteral(right)) left else right
                directParameter(variable, parameterTypes.keys)?.let { add(it, null) }
                return@forEach
            }

            val leftConstant = evaluator.evaluate(left)
            val rightConstant = evaluator.evaluate(right)
            val (variable, constant) = when {
                rightConstant != null && leftConstant == null -> left to rightConstant
                leftConstant != null && rightConstant == null -> right to leftConstant
                else -> return@forEach
            }
            if (variable == null) return@forEach

            when (constant) {
                is Double -> {
                    constants += constant
                    val direct = directParameter(variable, parameterTypes.keys)
                    val dependent = if (direct != null) setOf(direct) else dependencies(variable, parameterTypes.keys, locals)
                    dependent.forEach { parameter ->
                        val type = parameterTypes[parameter] ?: return@forEach
                        if (NumericFormat.isNumeric(type)) {
                            numericValues(type, constant).forEach { add(parameter, it) }
                        }
                    }
                    lengthParameter(variable, parameterTypes)?.let { parameter ->
                        val size = constant.toInt()
                        if (size in 1..1000) {
                            listOf(size - 1, size, size + 1).filter { it >= 0 }.forEach { add(parameter, "a".repeat(it)) }
                        }
                    }
                }
                is String -> {
                    directParameter(variable, parameterTypes.keys)?.let { parameter ->
                        if (parameterTypes[parameter] == "String") {
                            add(parameter, constant)
                            add(parameter, constant + "x")
                        }
                    }
                }
            }
        }

        body.collectAll(KtDotQualifiedExpression::class.java).forEach { qualified ->
            val parameter = directParameter(qualified.receiverExpression, parameterTypes.keys) ?: return@forEach
            if (parameterTypes[parameter] != "String") return@forEach
            val call = qualified.selectorExpression as? KtCallExpression ?: return@forEach
            when (call.calleeExpression?.text) {
                "isEmpty", "isNotEmpty" -> {
                    add(parameter, "")
                    add(parameter, "x")
                }
                "isBlank", "isNotBlank" -> {
                    add(parameter, "")
                    add(parameter, " ")
                    add(parameter, "x")
                }
                "equals", "startsWith", "endsWith", "contains" -> {
                    val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()
                    val literal = evaluator.evaluate(argument) as? String
                    if (literal != null) {
                        add(parameter, literal)
                        add(parameter, literal + "x")
                        add(parameter, "x" + literal)
                    }
                }
            }
        }

        body.collectAll(KtDotQualifiedExpression::class.java).forEach { qualified ->
            val call = qualified.selectorExpression as? KtCallExpression ?: return@forEach
            val samples = CHAR_PREDICATE_SAMPLES[call.calleeExpression?.text] ?: return@forEach
            val owners = dependencies(qualified.receiverExpression, parameterTypes.keys, locals)
                .ifEmpty { parameterTypes.filterValues { it == "String" }.keys }
            owners.filter { parameterTypes[it] == "String" }.forEach { parameter ->
                samples.forEach { add(parameter, it) }
            }
        }

        body.collectAll(KtWhenExpression::class.java).forEach { whenExpression ->
            val parameter = directParameter(whenExpression.subjectExpression, parameterTypes.keys) ?: return@forEach
            val type = parameterTypes[parameter] ?: return@forEach
            whenExpression.entries.forEach { entry ->
                entry.conditions.forEach { condition ->
                    when (condition) {
                        is KtWhenConditionWithExpression -> when (val constant = evaluator.evaluate(condition.expression)) {
                            is Double -> if (NumericFormat.isNumeric(type)) {
                                constants += constant
                                numericValues(type, constant).forEach { add(parameter, it) }
                            }
                            is String -> if (type == "String") {
                                add(parameter, constant)
                                add(parameter, constant + "x")
                            }
                        }
                        is KtWhenConditionInRange -> {
                            val range = condition.rangeExpression as? KtBinaryExpression
                            if (range != null && NumericFormat.isNumeric(type)) {
                                listOf(range.left, range.right).forEach { bound ->
                                    (evaluator.evaluate(bound) as? Double)?.let { constant ->
                                        constants += constant
                                        numericValues(type, constant).forEach { add(parameter, it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return BoundaryHints(values.mapValues { it.value.toList() }, constants.toList())
    }

    private fun numericValues(type: String, constant: Double): List<String> {
        val deltas = if (NumericFormat.isInteger(type)) listOf(-1.0, 0.0, 1.0) else listOf(-1.0, -0.01, 0.0, 0.01, 1.0)
        return deltas.mapNotNull { NumericFormat.format(type, constant + it) }
    }

    private fun fileConstants(analyzed: AnalyzedFunction): MutableMap<String, Any> {
        val constants = LinkedHashMap<String, Any>()
        val evaluator = ConstantEvaluator(constants)
        val properties = analyzed.source.ktFile.collectAll(KtProperty::class.java).filter { !it.isLocal && !it.isVar }
        repeat(2) {
            properties.forEach { property ->
                val name = property.name ?: return@forEach
                evaluator.evaluate(property.initializer)?.let { constants[name] = it }
            }
        }
        return constants
    }

    private fun isNullLiteral(expression: KtExpression?): Boolean =
        expression is KtConstantExpression && expression.text == "null"

    private fun unwrap(expression: KtExpression?): KtExpression? {
        var current = expression
        while (current is KtParenthesizedExpression) current = current.expression
        return current
    }

    private fun directParameter(expression: KtExpression?, parameters: Set<String>): String? {
        val unwrapped = unwrap(expression) as? KtNameReferenceExpression ?: return null
        return unwrapped.getReferencedName().takeIf { it in parameters }
    }

    private fun lengthParameter(expression: KtExpression, types: Map<String, String>): String? {
        val qualified = unwrap(expression) as? KtDotQualifiedExpression ?: return null
        val parameter = directParameter(qualified.receiverExpression, types.keys) ?: return null
        val selector = qualified.selectorExpression as? KtNameReferenceExpression ?: return null
        return parameter.takeIf { types[it] == "String" && selector.getReferencedName() == "length" }
    }

    private fun dependencies(
        expression: KtExpression,
        parameters: Set<String>,
        locals: Map<String, Set<String>>
    ): Set<String> {
        val result = LinkedHashSet<String>()
        expression.collectAll(KtNameReferenceExpression::class.java).forEach { reference ->
            val name = reference.getReferencedName()
            when {
                name in parameters -> result += name
                locals.containsKey(name) -> result += locals.getValue(name)
            }
        }
        return result
    }

    companion object {
        private val CHAR_PREDICATE_SAMPLES: Map<String, List<String>> = mapOf(
            "isDigit" to listOf("1", "a1", "a"),
            "isLetter" to listOf("a", "1a", "1"),
            "isLetterOrDigit" to listOf("a", "1", "-"),
            "isUpperCase" to listOf("A", "aA", "a"),
            "isLowerCase" to listOf("a", "Aa", "A"),
            "isWhitespace" to listOf(" ", "a b", "a")
        )

        private val COMPARISONS: Set<IElementType> = setOf(
            KtTokens.LT,
            KtTokens.LTEQ,
            KtTokens.GT,
            KtTokens.GTEQ,
            KtTokens.EQEQ,
            KtTokens.EXCLEQ,
            KtTokens.EQEQEQ,
            KtTokens.EXCLEQEQEQ
        )
    }
}
