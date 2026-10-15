package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.party.dto.KycStatus
import org.springframework.web.bind.annotation.BindParam

data class SearchAgentRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("party-id") val partyIds: List<Long> = emptyList(),
    @param:BindParam("city-id") val cityId: Long? = null,
    @param:BindParam("neighborhood-id") val neighborhoodIds: List<Long> = emptyList(),
    @param:BindParam("mobile-money-number") val mobileMoneyNumber: String? = null,
    @param:BindParam("kyc-status") val kycStatus: List<KycStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
