package com.wutsi.ndopify.security.dto

import com.wutsi.ndopify.party.dto.Party
import java.util.Date

data class User(
    val id: Long = -1,
    val email: String = "",
    val party: Party? = null,
    val createdAt: Date = Date(),
)
