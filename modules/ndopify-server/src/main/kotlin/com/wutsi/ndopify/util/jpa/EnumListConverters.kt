package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.refdata.dto.AuthType
import jakarta.persistence.Converter

// The lists of enums of the entities, stored as comma-separated names (see EnumListConverter). Set on the field with
// @Convert: add one here for each new list of enums stored in an entity.

// mandate. A NULL column is no document, not a null answer: the entity's field is a list.
@Converter
class TitleDocumentTypeListConverter : EnumListConverter<TitleDocumentType>(TitleDocumentType::class.java) {
    override fun convertToEntityAttribute(dbData: String?): List<TitleDocumentType> =
        super.convertToEntityAttribute(dbData) ?: emptyList()
}

// refdata
@Converter
class AuthTypeListConverter : EnumListConverter<AuthType>(AuthType::class.java)
