package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.refdata.dto.StorageType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_KYC_IMAGE")
data class KycImageEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "case_id")
    val kycCase: KycCaseEntity = KycCaseEntity(),

    val identificationType: IdentificationType = IdentificationType.UNKNOWN,
    val imageType: KycImageType = KycImageType.UNKNOWN,
    val storageType: StorageType = StorageType.UNKNOWN,
    val path: String? = null,
    val mimeType: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
