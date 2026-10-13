package com.wutsi.ndopify.customer.dto

import com.wutsi.ndopify.party.dto.Party
import java.util.Date

data class CustomerSummary(
    val id: Long? = null,
    val party: Party = Party(),
    val whatsappNumber: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
