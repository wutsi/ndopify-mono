package com.wutsi.ndopify.platform.util.jpa

import kotlin.test.Test
import kotlin.test.assertEquals

class LongListConverterTest {
    private val converter = LongListConverter()

    @Test
    fun `to DB`() {
        val result = converter.convertToDatabaseColumn(listOf(1L, 22L, 333L))
        assertEquals("1,22,333", result)
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
        val result = converter.convertToEntityAttribute("1,22,333")
        assertEquals(listOf(1L, 22L, 333L), result)
    }

    @Test
    fun `to Entity - Spaces are trimmed`() {
        val result = converter.convertToEntityAttribute(" 1 , 22,333 ")
        assertEquals(listOf(1L, 22L, 333L), result)
    }

    @Test
    fun `to Entity - Invalid values are ignored`() {
        val result = converter.convertToEntityAttribute("1,,abc,333")
        assertEquals(listOf(1L, 333L), result)
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

    @Test
    fun `round trip`() {
        val ids = listOf(100L, 200L, 300L)
        assertEquals(ids, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(ids)))
    }
}
