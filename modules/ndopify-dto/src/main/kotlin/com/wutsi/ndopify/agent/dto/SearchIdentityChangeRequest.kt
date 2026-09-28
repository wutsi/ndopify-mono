package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus

data class SearchIdentityChangeRequest(
    val ids: List<Long> = emptyList(),
    val agentId: Long? = null,
    val status: KycStatus? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
