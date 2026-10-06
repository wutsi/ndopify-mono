package com.wutsi.ndopify.party.dto

enum class IdentificationStatus {
    UNKNOWN,
    PENDING_VERIFICATION,
    PROCESSING,
    VERIFIED,
    REQUIRES_MANUAL_REVIEW,
    REJECTED,
    EXPIRED,
    REVOKED,
}
