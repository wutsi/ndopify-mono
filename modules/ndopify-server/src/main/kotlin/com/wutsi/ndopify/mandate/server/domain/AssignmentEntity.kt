package com.wutsi.ndopify.mandate.server.domain

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_ASSIGNMENT")
data class AssignmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "mandate_id")
    val mandate: MandateEntity = MandateEntity(),

    val agentId: Long = -1L,
    val status: AssignmentStatus = AssignmentStatus.UNKNOWN,
    val score: Double = 0.0,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val expiresAt: Date = Date(),

    // Optimistic locking: of 2 concurrent transitions of the same offer (double-click accept/decline, accept vs
    // expire), the 2nd write fails instead of silently overwriting the 1st
    @Version
    val version: Long? = null,
)
