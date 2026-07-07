package com.example.agent.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeConversionTest {

    @Test
    fun `converts int, String and boolean values correctly`() {
        assertEquals(ConversionResult.Converted(42), TypeConversion.convert("int", "42"))
        assertEquals(ConversionResult.Converted("hello"), TypeConversion.convert("String", "hello"))
        assertEquals(ConversionResult.Converted(true), TypeConversion.convert("boolean", "true"))
    }

    @Test
    fun `null for a reference type converts to null, null for a primitive is unsupported`() {
        assertEquals(ConversionResult.Converted(null), TypeConversion.convert("String", null))
        assertTrue(TypeConversion.convert("int", null) is ConversionResult.Unsupported)
    }

    @Test
    fun `unsupported type is reported without throwing`() {
        val result = TypeConversion.convert("CustomType", "anything")
        assertTrue(result is ConversionResult.Unsupported)
    }

    @Test
    fun `reflectionClassFor returns primitive class for lowercase type names`() {
        assertEquals(Int::class.javaPrimitiveType, TypeConversion.reflectionClassFor("int"))
        assertEquals(java.lang.Integer::class.java, TypeConversion.reflectionClassFor("Integer"))
        assertEquals(String::class.java, TypeConversion.reflectionClassFor("String"))
        assertEquals(null, TypeConversion.reflectionClassFor("CustomType"))
    }
}
