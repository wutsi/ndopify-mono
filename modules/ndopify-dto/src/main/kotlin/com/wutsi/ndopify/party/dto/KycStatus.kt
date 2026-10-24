package com.wutsi.ndopify.party.dto

/**
 * Lifecycle of a KYC case: PENDING_VERIFICATION (the agent uploads the documents) → SUBMITTED (sent for review, documents
 * frozen) → UNDER_VERIFICATION (picked up by the offline verification) → VERIFIED / REJECTED / REQUIRES_MANUAL_VERIFICATION.
 */
enum class KycStatus {
    UNKNOWN,
    PENDING_VERIFICATION,
    SUBMITTED,
    UNDER_VERIFICATION,
    VERIFIED,
    REJECTED,
    REQUIRES_MANUAL_VERIFICATION,
    CANCELLED,
}
