package com.wutsi.ndopify.party.server.service.kyc

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.wutsi.ndopify.party.dto.IdentificationType
import java.util.Date

data class KycInfo(
    val type: IdentificationType = IdentificationType.UNKNOWN,

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val number: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val firstName: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val lastName: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val countryCode: String = "",

    val valid: Boolean = false,
    val invalidityReason: String? = null,

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val confidence: Double = 0.0,

    @get:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val expiryDate: Date? = null,
)
