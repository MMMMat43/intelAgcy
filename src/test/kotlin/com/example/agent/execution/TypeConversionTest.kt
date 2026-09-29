package com.example.agent.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeConversionTest {

    @Test
    fun `converts supported types`() {
        assertEquals(ConversionResult.Converted(42), TypeConversion.convert("Int", false, "42"))
        assertEquals(ConversionResult.Converted(7L), TypeConversion.convert("Long", false, "7"))
        assertEquals(ConversionResult.Converted(1.5), TypeConversion.convert("Double", false, "1.5"))
        assertEquals(ConversionResult.Converted(true), TypeConversion.convert("Boolean", false, "true"))
        assertEquals(ConversionResult.Converted('x'), TypeConversion.convert("Char", false, "x"))
        assertEquals(ConversionResult.Converted("text"), TypeConversion.convert("String", false, "text"))
    }

    @Test
    fun `null is accepted only for nullable types`() {
        assertEquals(ConversionResult.Converted(null), TypeConversion.convert("String", true, null))
        assertTrue(TypeConversion.convert("String", false, null) is ConversionResult.Unsupported)
    }

    @Test
    fun `unparsable or unsupported values are reported`() {
        assertTrue(TypeConversion.convert("Int", false, "abc") is ConversionResult.Unsupported)
        assertTrue(TypeConversion.convert("Boolean", false, "maybe") is ConversionResult.Unsupported)
        assertTrue(TypeConversion.convert("List<Int>", false, "1") is ConversionResult.Unsupported)
    }

    @Test
    fun `reflection class uses primitives for non nullable types only`() {
        assertEquals(Int::class.javaPrimitiveType, TypeConversion.reflectionClassFor("Int", false))
        assertEquals(Int::class.javaObjectType, TypeConversion.reflectionClassFor("Int", true))
        assertEquals(String::class.java, TypeConversion.reflectionClassFor("String", false))
        assertNull(TypeConversion.reflectionClassFor("List<Int>", false))
    }
}
