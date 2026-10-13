package com.wutsi.ndopify.platform.util.jpa

import com.wutsi.koki.listing.dto.PropertyType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertyTypeListConverterTest {
    private val converter = PropertyTypeListConverter()

    @Test
    fun convertToDatabaseColumn() {
        assertEquals(
            "ROOM,APARTMENT",
            converter.convertToDatabaseColumn(listOf(PropertyType.ROOM, PropertyType.APARTMENT)),
        )
    }

    @Test
    fun convertToDatabaseColumnEmpty() {
        assertEquals("", converter.convertToDatabaseColumn(emptyList()))
    }

    @Test
    fun convertToDatabaseColumnNull() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun convertToEntityAttribute() {
        assertEquals(
            listOf(PropertyType.ROOM, PropertyType.APARTMENT),
            converter.convertToEntityAttribute("ROOM,APARTMENT"),
        )
    }

    @Test
    fun convertToEntityAttributeWithBlanks() {
        assertEquals(
            listOf(PropertyType.ROOM, PropertyType.APARTMENT),
            converter.convertToEntityAttribute("ROOM,,APARTMENT,"),
        )
    }

    @Test
    fun convertToEntityAttributeWithInvalidValue() {
        assertEquals(
            listOf(PropertyType.ROOM),
            converter.convertToEntityAttribute("ROOM,FOO"),
        )
    }

    @Test
    fun convertToEntityAttributeEmpty() {
        assertEquals(emptyList(), converter.convertToEntityAttribute(""))
    }

    @Test
    fun convertToEntityAttributeNull() {
        assertNull(converter.convertToEntityAttribute(null))
    }
}
