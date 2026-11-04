package com.wutsi.ndopify.visit.dto

/** Why a confirmed visit didn't happen: the customer's absence, its refusal of the code, or the agent's absence. */
enum class VisitNoShowReason {
    CUSTOMER_ABSENT,
    CODE_REFUSED,
    AGENT_ABSENT,
}
