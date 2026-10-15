package com.wutsi.ndopify.mandate.dto

enum class MandateStatus {
    UNKNOWN,
    PENDING_ASSIGNMENT,
    ASSIGNED,
    PENDING_APPROVAL,
    APPROVED,
    PUBLISHED,
    CLOSED,
    CANCELLED,
    MANUAL_ASSIGNMENT, // Without an agent for too long: assigned by a human. Persisted by ordinal: append only
}
