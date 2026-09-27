package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_IDENTITY_CHANGE")
data class IdentityChangeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val tenantId: Long = -1,

    @ManyToOne
    @JoinColumn(name = "agent_id")
    val agent: AgentEntity = AgentEntity(),

    val oldFirstName: String = "",
    val oldLastName: String = "",
    val newFirstName: String = "",
    val newLastName: String = "",

    val identityType: IdentityType = IdentityType.UNKNOWN,
    val documentPage1Url: String = "",
    val documentPage2Url: String? = null,

    val holderName: String? = null,
    val number: String? = null,
    val expiryDate: Date? = null,

    val status: KycStatus = KycStatus.PENDING,
    val failureReason: String? = null,
    val createdAt: Date = Date(),
    val verifiedAt: Date = Date(),
)
