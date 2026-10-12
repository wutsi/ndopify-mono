package com.wutsi.ndopify.party.dto

/**
 * Lifecycle of a KYC case: PENDING (the agent uploads the documents) → SUBMITTED (sent for review, documents
 * frozen) → IN_PROGRESS (picked up by the offline verification) → VERIFIED / REJECTED / REQUIRES_MANUAL_REVIEW.
 *
 * Persisted by ordinal (INT columns): inserting or reordering values changes what existing rows mean, so it needs
 * a migration renumbering them (see V4_1__party-kyc-status-submitted.sql).
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
