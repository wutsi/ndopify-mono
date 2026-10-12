package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.refdata.dto.AuthType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class AuthTypeListConverter : AttributeConverter<List<AuthType>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<AuthType>?): String? {
        return attribute?.joinToString(",") { it.name }
    }

    override fun convertToEntityAttribute(dbData: String?): List<AuthType>? {
        return dbData?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull {
                try {
                    AuthType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}
