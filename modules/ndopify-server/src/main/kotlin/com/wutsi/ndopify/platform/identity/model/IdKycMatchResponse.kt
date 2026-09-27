package com.wutsi.ndopify.platform.identity.model

import com.wutsi.ndopify.refdata.dto.IdentityStatus

data class IdKycMatchResponse(
    val holderNameScore: Double = 0.0,
    val countryCodeScore: Double = 0.0,
    val documentTypeScore: Double = 0.0,
    val status: IdentityStatus = IdentityStatus.UNKNOWN,
    val holderName: String = ""
)
