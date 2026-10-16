package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.party.dto.KycStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumConverterTest {
    private val converter = KycStatusConverter()
    private val optional = PropertyTypeConverter()

    @Test
    fun convertToDatabaseColumn() {
        assertEquals("VERIFIED", converter.convertToDatabaseColumn(KycStatus.VERIFIED))
    }

    @Test
    fun convertToDatabaseColumnNull() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun convertToEntityAttribute() {
        assertEquals(KycStatus.VERIFIED, converter.convertToEntityAttribute("VERIFIED"))
    }

    @Test
    fun convertToEntityAttributeNull() {
        assertEquals(KycStatus.UNKNOWN, converter.convertToEntityAttribute(null))
    }

    @Test
    fun convertToEntityAttributeNotInEnum() {
        assertEquals(KycStatus.UNKNOWN, converter.convertToEntityAttribute("GALAXY"))
    }

    @Test
    fun convertToEntityAttributeOptional() {
        assertEquals(PropertyType.VILLA, optional.convertToEntityAttribute("VILLA"))
        assertNull(optional.convertToEntityAttribute(null))
        assertNull(optional.convertToEntityAttribute("GALAXY"))
    }
}
