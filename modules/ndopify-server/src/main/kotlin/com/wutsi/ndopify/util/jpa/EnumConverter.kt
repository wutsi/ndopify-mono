package com.wutsi.ndopify.util.jpa

import jakarta.persistence.AttributeConverter

/**
 * Stores an enum by its name. A NULL column, or a name the enum no longer has, reads as [default], so a non-null
 * entity field never gets a null. Subclasses are `@Converter(autoApply = true)`: see [EnumConverters].
 */
abstract class EnumConverter<E : Enum<E>>(
    private val type: Class<E>,
    private val default: E?,
) : AttributeConverter<E?, String?> {
    override fun convertToDatabaseColumn(attribute: E?): String? = attribute?.name

    override fun convertToEntityAttribute(dbData: String?): E? = dbData?.let { type.constantOf(it) } ?: default
}

/** The constant of this enum named [name], or null if it has none (ex: a value since renamed) */
internal fun <E : Enum<E>> Class<E>.constantOf(name: String): E? = enumConstants.find { it.name == name }
