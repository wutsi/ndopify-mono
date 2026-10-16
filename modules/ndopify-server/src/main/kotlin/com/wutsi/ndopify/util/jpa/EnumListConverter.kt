package com.wutsi.ndopify.util.jpa

import jakarta.persistence.AttributeConverter

/**
 * Stores a list of enums as their comma-separated names. Blank items, and names the enum no longer has, are skipped
 * on read. Subclasses are `@Converter`s set on the field with `@Convert`: see [EnumListConverters].
 */
abstract class EnumListConverter<E : Enum<E>>(
    private val type: Class<E>,
) : AttributeConverter<List<E>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<E>?): String? = attribute?.joinToString(DELIMITER) { it.name }

    override fun convertToEntityAttribute(dbData: String?): List<E>? =
        dbData?.split(DELIMITER)?.mapNotNull { type.constantOf(it) }

    companion object {
        private const val DELIMITER = ","
    }
}
