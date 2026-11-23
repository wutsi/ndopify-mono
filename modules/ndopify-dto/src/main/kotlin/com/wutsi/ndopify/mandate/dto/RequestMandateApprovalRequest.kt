package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.refdata.dto.Money

/**
 * The terms the agent confirms when sending a mandate's contract — its duration, the visitors' fee and, for a sale
 * mandate, its commission in % of the sale price. A null value keeps the contract's current one (or the tenant
 * default for a new contract).
 */
data class RequestMandateApprovalRequest(
    val durationMonths: Int? = null,
    val visitFee: Money? = null,
    val commissionRate: Int? = null,
)
