package com.wutsi.ndopify.party.dto

import java.util.Date
import java.util.UUID

data class IdentificationImage(
    val id: String = UUID.randomUUID().toString(),
    val imageType: IdentificationImageType = IdentificationImageType.UNKNOWN,
    val createdAt: Date = Date(),
    val uploadedAt: Date? = null,
)
