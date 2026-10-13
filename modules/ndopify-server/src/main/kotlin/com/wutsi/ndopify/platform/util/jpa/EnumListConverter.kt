package com.wutsi.ndopify.platform.util.jpa

import com.wutsi.koki.listing.dto.PropertyType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class PropertyTypeListConverter : AttributeConverter<List<PropertyType>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<PropertyType>?): String? {
        return attribute?.joinToString(",") { it.name }
    }

    override fun convertToEntityAttribute(dbData: String?): List<PropertyType>? {
        return dbData?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull {
                try {
                    PropertyType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
