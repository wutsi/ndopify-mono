package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.party.dto.Party
import java.util.Date

data class AgentSummary(
    val id: Long = -1,
    val party: Party = Party(),
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
