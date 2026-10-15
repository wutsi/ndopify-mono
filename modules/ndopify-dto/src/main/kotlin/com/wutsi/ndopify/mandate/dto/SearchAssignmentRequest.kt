package com.wutsi.ndopify.mandate.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchAssignmentRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("agent-id") val agentIds: List<Long> = emptyList(),
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
    @param:BindParam("status") val statuses: List<AssignmentStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
