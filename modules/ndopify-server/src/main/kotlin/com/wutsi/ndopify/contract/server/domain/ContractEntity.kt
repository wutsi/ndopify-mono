package com.wutsi.ndopify.contract.server.domain

import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_CONTRACT")
data class ContractEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @OneToMany(mappedBy = "contract")
    val contractParties: List<ContractPartyEntity> = listOf(),

    val number: String = "",
    val type: ContractType = ContractType.UNKNOWN,
    val status: ContractStatus = ContractStatus.UNKNOWN,
    val contentUrl: String? = null, // Set by POST /v1/contracts/{id}/content
    val durationMonths: Int = 0,
    val signatureDeadline: Date? = null, // Set by each request for signature
    val validFrom: Date? = null, // Set by the last signature, with validUntil
    val validUntil: Date? = null,
    val createdAt: Date = Date(),
    val signedAt: Date? = null,

    // Optimistic locking: a write based on a stale read fails (409) instead of silently overwriting the change
    @Version
    val version: Long? = null,
)
