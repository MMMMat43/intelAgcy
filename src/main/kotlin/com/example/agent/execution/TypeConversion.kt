package com.example.agent.execution

/**
 * Result of converting a [com.example.agent.model.TestCase.inputData] string
 * value into a real Java object for reflective invocation.
 */
sealed class ConversionResult {
    data class Converted(val value: Any?) : ConversionResult()
    data class Unsupported(val reason: String) : ConversionResult()
}

/**
 * Converts the string-encoded parameter values produced by
 * [com.example.agent.generation.HeuristicScenarioGenerator] into real typed
 * Java objects, and resolves the reflection-compatible [Class] for a given
 * declared parameter type.
 *
 * Supports exactly the set of types [com.example.agent.generation.HeuristicScenarioGenerator]
 * knows how to generate boundary/typical values for: int/Integer, long/Long,
 * short/Short, byte/Byte, double/Double, float/Float, boolean/Boolean, String.
 * Any other type is reported as [ConversionResult.Unsupported] rather than
 * throwing, so callers can gracefully skip real execution for that scenario.
 */
object TypeConversion {

    fun convert(type: String, rawValue: String?): ConversionResult {
        val normalizedType = type.trim()

        if (rawValue == null) {
            return if (isPrimitive(normalizedType)) {
                ConversionResult.Unsupported("cannot pass null for primitive type '$normalizedType'")
            } else {
                ConversionResult.Converted(null)
            }
        }

        return try {
            when (normalizedType) {
                "int", "Integer" -> ConversionResult.Converted(rawValue.toInt())
                "long", "Long" -> ConversionResult.Converted(rawValue.toLong())
                "short", "Short" -> ConversionResult.Converted(rawValue.toShort())
                "byte", "Byte" -> ConversionResult.Converted(rawValue.toByte())
                "double", "Double" -> ConversionResult.Converted(rawValue.toDouble())
                "float", "Float" -> ConversionResult.Converted(rawValue.toFloat())
                "boolean", "Boolean" -> ConversionResult.Converted(rawValue.toBoolean())
                "String", "java.lang.String" -> ConversionResult.Converted(rawValue)
                else -> ConversionResult.Unsupported("unsupported parameter type: '$normalizedType'")
            }
        } catch (e: NumberFormatException) {
            ConversionResult.Unsupported("could not parse '$rawValue' as $normalizedType: ${e.message}")
        }
    }

    /**
     * Returns the reflection-compatible [Class] for a declared parameter
     * type, or `null` if unsupported. For primitive-spelled types (`int`,
     * not `Integer`), returns the primitive [Class] (e.g. `Int::class.javaPrimitiveType`)
     * so that `Class.getMethod(name, *parameterTypes)` resolves correctly
     * against methods declared with primitive parameters.
     */
    fun reflectionClassFor(type: String): Class<*>? {
        return when (type.trim()) {
            "int" -> Int::class.javaPrimitiveType
            "Integer" -> java.lang.Integer::class.java
            "long" -> Long::class.javaPrimitiveType
            "Long" -> java.lang.Long::class.java
            "short" -> Short::class.javaPrimitiveType
            "Short" -> java.lang.Short::class.java
            "byte" -> Byte::class.javaPrimitiveType
            "Byte" -> java.lang.Byte::class.java
            "double" -> Double::class.javaPrimitiveType
            "Double" -> java.lang.Double::class.java
            "float" -> Float::class.javaPrimitiveType
            "Float" -> java.lang.Float::class.java
            "boolean" -> Boolean::class.javaPrimitiveType
            "Boolean" -> java.lang.Boolean::class.java
            "String", "java.lang.String" -> String::class.java
            else -> null
        }
    }

    private fun isPrimitive(type: String): Boolean {
        return type in setOf("int", "long", "short", "byte", "double", "float", "boolean")
    }
}
