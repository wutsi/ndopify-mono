package com.wutsi.ndopify.match.dto

import com.wutsi.ndopify.refdata.dto.Money

/** Closes the deal of an ACCEPTED match: both mandates, when both are the agent's. */
data class CloseMatchRequest(
    val finalPrice: Money = Money(),
)
