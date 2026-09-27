package com.wutsi.ndopify.agent.dto

enum class AgentStatus {
    UNKNOWN,
    ACTIVE,
    RESTRICTED, // No Payout
    LIMITED, // No Payout, No Publishing
}
