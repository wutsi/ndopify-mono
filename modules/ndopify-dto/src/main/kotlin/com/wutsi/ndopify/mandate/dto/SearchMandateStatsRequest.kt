package com.wutsi.ndopify.mandate.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchMandateStatsRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
)
