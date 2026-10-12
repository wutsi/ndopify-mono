package com.wutsi.ndopify.util.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class LongListConverter : AttributeConverter<List<Long>, String> {
    private val delimiter = ","

    override fun convertToDatabaseColumn(attribute: List<Long>?): String? {
        return attribute?.joinToString(delimiter)?.ifEmpty { null }
    }

    override fun convertToEntityAttribute(dbData: String?): List<Long>? {
        if (dbData.isNullOrEmpty()) return emptyList()
        return dbData.split(delimiter).mapNotNull { it.trim().toLongOrNull() }
    }
}
