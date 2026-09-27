package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType

data class Agent(
    val id: Long = -1,
    val userId: Long? = null,

    val firstName: String = "",
    val lastName: String = "",
    val agentType: AgentType = AgentType.UNKNOWN,
    val biography: String? = null,
    val agencyName: String? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val photoUrl: String? = null,
    val agencyLogoUrl: String? = null,

    val mobileMoneyNumber: String? = null,
    val mobileMoneyGateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,

    val mobileMoneyKycStatus: KycStatus = KycStatus.UNKNOWN,
    val identityKycStatus: KycStatus = KycStatus.UNKNOWN,
    val status: AgentStatus = AgentStatus.UNKNOWN,
)
