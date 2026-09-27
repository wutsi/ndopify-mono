package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.refdata.dto.AuthType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthTypeListConverterTest {
    private val converter = AuthTypeListConverter()

    @Test
    fun convertToDatabaseColumn() {
        assertEquals(
            "PASSWORD,GOOGLE_ONE_TAP",
            converter.convertToDatabaseColumn(listOf(AuthType.PASSWORD, AuthType.GOOGLE_ONE_TAP)),
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
            listOf(AuthType.PASSWORD, AuthType.GOOGLE_ONE_TAP),
            converter.convertToEntityAttribute("PASSWORD,GOOGLE_ONE_TAP"),
        )
    }

    @Test
    fun convertToEntityAttributeWithBlanks() {
        assertEquals(
            listOf(AuthType.PASSWORD, AuthType.GOOGLE_ONE_TAP),
            converter.convertToEntityAttribute("PASSWORD,,GOOGLE_ONE_TAP,"),
        )
    }

    @Test
    fun convertToEntityAttributeWithInvalidValue() {
        assertEquals(
            listOf(AuthType.PASSWORD),
            converter.convertToEntityAttribute("PASSWORD,FOO"),
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
