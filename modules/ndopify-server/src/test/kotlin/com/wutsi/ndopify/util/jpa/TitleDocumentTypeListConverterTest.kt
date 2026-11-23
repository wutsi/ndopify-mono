package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleDocumentTypeListConverterTest {
    private val converter = TitleDocumentTypeListConverter()

    @Test
    fun convertToDatabaseColumn() {
        assertEquals(
            "LAND_TITLE,SURVEY_PLAN",
            converter.convertToDatabaseColumn(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN)),
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
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            converter.convertToEntityAttribute("LAND_TITLE,SURVEY_PLAN"),
        )
    }

    @Test
    fun convertToEntityAttributeEmpty() {
        assertEquals(emptyList(), converter.convertToEntityAttribute(""))
    }

    // A mandate created before the column existed, or by a fixture that omits it, has a NULL column.
    @Test
    fun convertToEntityAttributeNull() {
        assertEquals(emptyList(), converter.convertToEntityAttribute(null))
    }
}
