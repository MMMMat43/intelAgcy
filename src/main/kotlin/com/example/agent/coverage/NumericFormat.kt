package com.example.agent.coverage

internal object NumericFormat {

    private val INTEGER_TYPES = setOf("Int", "Long", "Short", "Byte")
    private val FLOATING_TYPES = setOf("Double", "Float")

    fun isInteger(type: String): Boolean = type in INTEGER_TYPES

    fun isNumeric(type: String): Boolean = type in INTEGER_TYPES || type in FLOATING_TYPES

    fun format(type: String, value: Double): String? {
        if (value.isNaN() || value.isInfinite()) return null
        return when (type) {
            "Int" -> integerIn(value, Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
            "Long" -> integerIn(value, -9.0e18, 9.0e18)
            "Short" -> integerIn(value, Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
            "Byte" -> integerIn(value, Byte.MIN_VALUE.toDouble(), Byte.MAX_VALUE.toDouble())
            "Double" -> value.toString()
            "Float" -> if (Math.abs(value) <= Float.MAX_VALUE.toDouble()) value.toFloat().toString() else null
            else -> null
        }
    }

    private fun integerIn(value: Double, min: Double, max: Double): String? {
        val rounded = Math.rint(value)
        return if (rounded < min || rounded > max) null else rounded.toLong().toString()
    }
}
