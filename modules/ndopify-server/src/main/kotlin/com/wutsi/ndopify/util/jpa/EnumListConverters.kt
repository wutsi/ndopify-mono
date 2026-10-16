package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.refdata.dto.AuthType
import jakarta.persistence.Converter

// The lists of enums of the entities, stored as comma-separated names (see EnumListConverter). Set on the field with
// @Convert: add one here for each new list of enums stored in an entity.

// refdata
@Converter
class AuthTypeListConverter : EnumListConverter<AuthType>(AuthType::class.java)
