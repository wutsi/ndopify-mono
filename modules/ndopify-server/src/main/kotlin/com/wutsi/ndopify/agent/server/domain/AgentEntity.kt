package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import com.wutsi.ndopify.util.jpa.LongListConverter
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
@Table(name = "T_AGENT")
data class AgentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val tenantId: Long = 0,

    val userId: Long? = null,
    val agentType: AgentType = AgentType.UNKNOWN,
    val firstName: String = "",
    val lastName: String = "",
    val biography: String? = null,
    val agencyName: String? = null,
    val cityId: Long? = null,

    @Convert(LongListConverter::class)
    val neighborhoodIds: List<Long> = emptyList(),

    val photoUrl: String? = null,
    val agencyLogoUrl: String? = null,

    val mobileMoneyNumber: String? = null,
    val mobileMoneyGateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,

    @ManyToOne
    @JoinColumn(name = "mobile_change_id")
    val mobileChange: MobileChangeEntity? = null,

    val mobileMoneyKycStatus: KycStatus = KycStatus.UNKNOWN,
    val identityKycStatus: KycStatus = KycStatus.UNKNOWN,
    val status: AgentStatus = AgentStatus.UNKNOWN,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
