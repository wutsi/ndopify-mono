package com.wutsi.ndopify.party.server.service.identification

import com.fasterxml.jackson.annotation.JsonFormat
import com.wutsi.ndopify.party.dto.IdentificationType
import java.util.Date

data class IdentificationInfo(
    val type: IdentificationType = IdentificationType.UNKNOWN,
    val number: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val countryCode: String = "",
    val valid: Boolean = false,
    val invalidityReason: String? = null,

    @get:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val expiryDate: Date? = null,
)
