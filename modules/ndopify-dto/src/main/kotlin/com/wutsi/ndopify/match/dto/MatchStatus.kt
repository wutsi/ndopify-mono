package com.wutsi.ndopify.match.dto

enum class MatchStatus {
    UNKNOWN,
    ACTIVE,
    SHORTLISTED, // Kept by the agent, to propose later
    DISMISSED,
    PROPOSED,
    ACCEPTED,
    REJECTED,
}
