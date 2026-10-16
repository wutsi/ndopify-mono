package com.wutsi.ndopify.party.dto

/**
 * Lifecycle of a KYC case: PENDING (the agent uploads the documents) → SUBMITTED (sent for review, documents
 * frozen) → IN_PROGRESS (picked up by the offline verification) → VERIFIED / REJECTED / REQUIRES_MANUAL_REVIEW.
 */
enum class KycStatus {
    UNKNOWN,
    PENDING,
    SUBMITTED,
    IN_PROGRESS,
    VERIFIED,
    REJECTED,
    REQUIRES_MANUAL_REVIEW,
    CANCELLED,
}
