package com.wutsi.ndopify.platform.identity.model

import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.IdentityType

data class IdentityInfo(
    val type: IdentityType = IdentityType.UNKNOWN,
    val number: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val countryCode: String = "",
    val expiryDate: String = "",
    val status: IdentityStatus = IdentityStatus.UNKNOWN,
    val invalidityReason: String? = null,
)
