package com.wutsi.ndopify.security.server.domain

import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_USER")
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val email: String = "",

    // Not @TenantId: this is the user's home tenant, looked up by email *before* TenantContext is known
    // (see AbstractAuthenticator). Null means the user isn't scoped to a single tenant (e.g. a super-admin
    // authenticating against a cross-tenant application like admin-console).
    val tenantId: Long? = null,

    // Nullable: party-less users (e.g. super-admins) have no business-identity profile. When set,
    // party.email is authoritative and kept in sync with this user's email by PartyService.update().
    @OneToOne
    @JoinColumn(name = "party_id")
    val party: PartyEntity? = null,

    val deleted: Boolean = false,
    val createdAt: Date = Date(),
    val deletedAt: Date? = null,
)
