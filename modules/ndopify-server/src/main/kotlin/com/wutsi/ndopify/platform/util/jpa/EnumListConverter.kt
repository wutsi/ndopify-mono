package com.wutsi.ndopify.platform.util.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.apache.poi.ss.formula.functions.T

@Converter
open class EnumListConverter<T : Enum<T>> : AttributeConverter<List<T>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<T>?): String? {
        return attribute?.joinToString(",") { it.name }
    }

    override fun convertToEntityAttribute(dbData: String?): List<T>? {
        return dbData?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull {
                try {
                    java.lang.Enum.valueOf(T::class.java as Class<T>, it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
