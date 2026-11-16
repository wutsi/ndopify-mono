package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.refdata.dto.Money

/**
 * The terms the agent confirms when sending a mandate's contract. A null value keeps the contract's current one (or
 * the tenant default for a new contract).
 */
data class RequestMandateApprovalRequest(
    val durationMonths: Int? = null,
    val visitFee: Money? = null,
)
