package com.wutsi.ndopify.mandate.dto

import com.wutsi.koki.listing.dto.ListingType
import org.springframework.web.bind.annotation.BindParam

data class SearchMandateRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("agent-id") val agentIds: List<Long> = emptyList(),
    @param:BindParam("customer-id") val customerIds: List<Long> = emptyList(),
    @param:BindParam("city-id") val cityIds: List<Long> = emptyList(),
    @param:BindParam("neighborhood-id") val neighborhoodIds: List<Long> = emptyList(),
    @param:BindParam("mandate-type") val mandateType: MandateType? = null,
    @param:BindParam("listing-type") val listingType: ListingType? = null,
    @param:BindParam("status") val statuses: List<MandateStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
