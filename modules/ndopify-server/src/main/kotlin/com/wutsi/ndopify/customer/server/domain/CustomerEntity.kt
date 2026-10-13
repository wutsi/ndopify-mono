package com.wutsi.ndopify.customer.server.domain

import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_CUSTOMER")
data class CustomerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @OneToOne
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    val whatsappNumber: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
