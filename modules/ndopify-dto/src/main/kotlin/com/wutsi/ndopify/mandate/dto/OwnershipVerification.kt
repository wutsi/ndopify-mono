package com.wutsi.ndopify.mandate.dto

/** Whether the owner of a LISTING mandate is verified, and why not; status and codes only, never a document. */
data class OwnershipVerification(
    val status: OwnershipVerificationStatus = OwnershipVerificationStatus.UNKNOWN,
    val reasons: List<OwnershipVerificationReason> = emptyList(),
)
