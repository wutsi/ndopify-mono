package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_MOBILE_CHANGE")
data class MobileChangeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val tenantId: Long = -1,
    val verifyByUserId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "agent_id")
    val agent: AgentEntity = AgentEntity(),

    val oldMobileNumber: String? = null,
    val newMobileNumber: String = "",
    val newGateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,

    val holderName: String? = null,
    val holderNameScore: Double? = null,
    val countryCodeScore: Double? = null,
    val status: KycStatus = KycStatus.PENDING,
    val errorCode: String? = null,
    val failureReason: String? = null,
    val retries: Int? = null,

    val createdAt: Date = Date(),
    val verifiedAt: Date? = null,
)
