package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.refdata.dto.Money

data class CloseMandateRequest(
    val finalPrice: Money = Money(),
)
