package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.util.jpa.StringListConverter
import jakarta.persistence.Convert
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
    var verifyByUserId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "agent_id")
    val agent: AgentEntity = AgentEntity(),

    val oldFirstName: String = "",
    val oldLastName: String = "",
    val newFirstName: String = "",
    val newLastName: String = "",

    val identityType: IdentityType = IdentityType.UNKNOWN,

    @Convert(converter = StringListConverter::class)
    val imageUrls: List<String> = emptyList(),

    var holderName: String? = null,
    var retries: Int? = null,

    var status: KycStatus = KycStatus.PENDING,
    var errorCode: String? = null,
    var failureReason: String? = null,
    val createdAt: Date = Date(),
    var verifiedAt: Date? = null,
)
