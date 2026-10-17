package com.wutsi.ndopify.mandate.server.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_CONTRACT")
data class ContractEntity(
    @Id
    val id: String? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @OneToOne
    @JoinColumn(name = "mandate_id")
    val mandate: MandateEntity = MandateEntity(),

    val contentPath: String = "",
    val signed: Boolean = false,

    val validFrom: Date = Date(),
    val validTo: Date = Date(),
    val createdAt: Date = Date(),
    val signedAt: Date? = null,
)
