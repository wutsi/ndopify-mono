package com.wutsi.ndopify.util.jpa

import kotlin.test.Test
import kotlin.test.assertEquals

class StringListConverterTest {
    private val converter = StringListConverter()

    @Test
    fun `to DB`() {
        val result = converter.convertToDatabaseColumn(listOf("A", "B", "C"))
        assertEquals("A,B,C", result)
    }

    @Test
    fun `to DB - Empty List`() {
        val result = converter.convertToDatabaseColumn(listOf())
        assertEquals(null, result)
    }

    @Test
    fun `to DB - Null List`() {
        val result = converter.convertToDatabaseColumn(null)
        assertEquals(null, result)
    }

    @Test
    fun `to Entity`() {
        val result = converter.convertToEntityAttribute("A,B,C")
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun `to Entity - Empty String`() {
        val result = converter.convertToEntityAttribute("")
        assertEquals(emptyList(), result)
    }

    @Test
    fun `to Entity - Null String`() {
        val result = converter.convertToEntityAttribute(null)
        assertEquals(emptyList(), result)
    }
}
