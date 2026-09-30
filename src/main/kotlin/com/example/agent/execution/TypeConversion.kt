package com.example.agent.execution

sealed class ConversionResult {
    data class Converted(val value: Any?) : ConversionResult()
    data class Unsupported(val reason: String) : ConversionResult()
}

object TypeConversion {

    private val PRIMITIVE_CAPABLE = setOf("Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char")

    fun convert(type: String, nullable: Boolean, rawValue: String?): ConversionResult {
        val normalizedType = normalize(type)

        if (rawValue == null) {
            return if (nullable) {
                ConversionResult.Converted(null)
            } else {
                ConversionResult.Unsupported("null is not allowed for non-nullable type '$normalizedType'")
            }
        }

        return try {
            when (normalizedType) {
                "Int" -> ConversionResult.Converted(rawValue.toInt())
                "Long" -> ConversionResult.Converted(rawValue.toLong())
                "Short" -> ConversionResult.Converted(rawValue.toShort())
                "Byte" -> ConversionResult.Converted(rawValue.toByte())
                "Double" -> ConversionResult.Converted(rawValue.toDouble())
                "Float" -> ConversionResult.Converted(rawValue.toFloat())
                "Boolean" -> when (rawValue) {
                    "true" -> ConversionResult.Converted(true)
                    "false" -> ConversionResult.Converted(false)
                    else -> ConversionResult.Unsupported("could not parse '$rawValue' as Boolean")
                }
                "Char" -> if (rawValue.length == 1) {
                    ConversionResult.Converted(rawValue[0])
                } else {
                    ConversionResult.Unsupported("could not parse '$rawValue' as Char")
                }
                "String" -> ConversionResult.Converted(rawValue)
                else -> ConversionResult.Unsupported("unsupported parameter type: '$normalizedType'")
            }
        } catch (e: NumberFormatException) {
            ConversionResult.Unsupported("could not parse '$rawValue' as $normalizedType: ${e.message}")
        }
    }

    fun reflectionClassFor(type: String, nullable: Boolean): Class<*>? {
        val normalizedType = normalize(type)
        val usePrimitive = !nullable && normalizedType in PRIMITIVE_CAPABLE
        return when (normalizedType) {
            "Int" -> if (usePrimitive) Int::class.javaPrimitiveType else Int::class.javaObjectType
            "Long" -> if (usePrimitive) Long::class.javaPrimitiveType else Long::class.javaObjectType
            "Short" -> if (usePrimitive) Short::class.javaPrimitiveType else Short::class.javaObjectType
            "Byte" -> if (usePrimitive) Byte::class.javaPrimitiveType else Byte::class.javaObjectType
            "Double" -> if (usePrimitive) Double::class.javaPrimitiveType else Double::class.javaObjectType
            "Float" -> if (usePrimitive) Float::class.javaPrimitiveType else Float::class.javaObjectType
            "Boolean" -> if (usePrimitive) Boolean::class.javaPrimitiveType else Boolean::class.javaObjectType
            "Char" -> if (usePrimitive) Char::class.javaPrimitiveType else Char::class.javaObjectType
            "String" -> String::class.java
            else -> null
        }
    }

    fun normalize(type: String): String =
        type.trim().removeSuffix("?").trim().removePrefix("kotlin.")
}
