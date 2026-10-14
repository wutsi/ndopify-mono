package com.wutsi.ndopify.security.server.domain

import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

/** A user's membership of a tenant: its party there, and (via [UserApplicationEntity]) its applications and roles. */
@Entity
@Table(name = "T_MEMBERSHIP")
data class MembershipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity = UserEntity(),

    // Nullable: party-less members (e.g. admins) have no business-identity profile in the tenant.
    @OneToOne
    @JoinColumn(name = "party_id")
    val party: PartyEntity? = null,

    val deleted: Boolean = false,
    val createdAt: Date = Date(),
)
