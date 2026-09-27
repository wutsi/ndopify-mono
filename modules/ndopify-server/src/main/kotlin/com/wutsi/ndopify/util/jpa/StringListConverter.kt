package com.wutsi.ndopify.util.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
    private val delimiter = ","

    override fun convertToDatabaseColumn(attribute: List<String>?): String? {
        return attribute?.joinToString(delimiter)?.ifEmpty { null }
    }

    override fun convertToEntityAttribute(dbData: String?): List<String>? {
        if (dbData.isNullOrEmpty()) return emptyList()
        return dbData.split(delimiter).map { it.trim() }
    }
}
