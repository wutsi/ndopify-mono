package com.wutsi.ndopify.party.dto

import java.util.Date

data class KycImage(
    val id: String = "",

    val caseId: String = "",
    val identificationType: IdentificationType = IdentificationType.UNKNOWN,
    val imageType: KycImageType = KycImageType.UNKNOWN,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
