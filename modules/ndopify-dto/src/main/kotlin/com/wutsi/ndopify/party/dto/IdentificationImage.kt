package com.wutsi.ndopify.party.dto

import com.wutsi.ndopify.refdata.dto.StorageType
import java.util.Date
import java.util.UUID

data class IdentificationImage(
    val id: String = UUID.randomUUID().toString(),
    val imageType: IdentificationImageType = IdentificationImageType.UNKNOWN,
    val storageType: StorageType = StorageType.UNKNOWN,
    val path: String? = null,
    val mimeType: String? = null,
    val uploaded: Boolean = false,
    val createdAt: Date = Date(),
    val uploadedAt: Date? = null,
)
