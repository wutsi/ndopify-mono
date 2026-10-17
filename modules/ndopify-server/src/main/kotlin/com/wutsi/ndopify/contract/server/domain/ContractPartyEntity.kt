package com.wutsi.ndopify.contract.server.domain

import com.wutsi.ndopify.contract.dto.ContractPartyRole
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_CONTRACT_PARTY")
data class ContractPartyEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "contract_id")
    val contract: ContractEntity = ContractEntity(),

    val role: ContractPartyRole = ContractPartyRole.UNKNOWN,
    val partyId: Long = -1,
    val legalName: String = "",
    val legalEmail: String = "",
    val signed: Boolean = false,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val signedAt: Date? = null,
)
