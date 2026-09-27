package com.wutsi.ndopify.platform.identity.model

import com.wutsi.ndopify.refdata.dto.IdentityType
import java.io.File

data class IdKycMatchRequest(
    val holderName: String,
    val countryCode: String,
    val images: List<File> = emptyList(),
    val identityType: IdentityType = IdentityType.UNKNOWN,
)
