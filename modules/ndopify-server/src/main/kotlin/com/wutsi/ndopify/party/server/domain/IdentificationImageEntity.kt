package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.refdata.dto.StorageType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_IDENTIFICATION_IMAGE")
data class IdentificationImageEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "identification_id")
    val identification: IdentificationEntity = IdentificationEntity(),

    val imageType: IdentificationImageType = IdentificationImageType.UNKNOWN,
    val storageType: StorageType = StorageType.UNKNOWN,
    val path: String? = null,
    val mimeType: String? = null,

    val createdAt: Date = Date(),
    val uploadedAt: Date? = null,
)
