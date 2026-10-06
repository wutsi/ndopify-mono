package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_IDENTIFICATION")
data class IdentificationEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    @OneToMany(mappedBy = "identification")
    val images: List<IdentificationImageEntity> = emptyList(),

    val type: IdentificationType = IdentificationType.UNKNOWN,
    val issuingCountryCode: String? = "",

    val number: String? = null,
    val numberSuffix: String? = null, // Last 4 digits of the number (masked for privacy)
    val issuedAt: Date? = null,
    val expiresAt: Date? = null,
    val status: IdentificationStatus = IdentificationStatus.UNKNOWN,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
