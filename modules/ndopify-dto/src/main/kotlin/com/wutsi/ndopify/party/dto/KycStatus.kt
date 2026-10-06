package com.wutsi.ndopify.party.dto

enum class KycStatus {
    UNKNOWN,
    PENDING,
    IN_PROGRESS,
    VERIFIED,
    REJECTED,
    REQUIRES_MANUAL_REVIEW,
    CANCELLED,
}
