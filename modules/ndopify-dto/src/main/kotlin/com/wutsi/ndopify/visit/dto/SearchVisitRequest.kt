package com.wutsi.ndopify.visit.dto

import org.springframework.web.bind.annotation.BindParam
import java.time.Instant

data class SearchVisitRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
    @param:BindParam("match-id") val matchIds: List<Long> = emptyList(),
    @param:BindParam("agent-id") val agentIds: List<Long> = emptyList(),
    @param:BindParam("customer-id") val customerIds: List<Long> = emptyList(),
    @param:BindParam("status") val statuses: List<VisitStatus> = emptyList(),

    @param:BindParam("scheduled-from") val scheduledFrom: Instant? = null,
    @param:BindParam("scheduled-to") val scheduledTo: Instant? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
