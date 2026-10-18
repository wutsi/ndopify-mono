package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycStatus
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_KYC_CASE")
data class KycCaseEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    @OneToMany(mappedBy = "case")
    val verifications: List<KycVerificationEntity> = emptyList(),

    @OneToMany(mappedBy = "kycCase")
    val images: List<KycImageEntity> = emptyList(),

    val identificationType: IdentificationType = IdentificationType.UNKNOWN,
    val score: Int? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val retries: Int? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),

    // Optimistic locking: a write based on a stale read fails (409) instead of silently overwriting the change
    @Version
    val version: Long? = null,
)
