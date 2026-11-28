package com.wutsi.ndopify.mandate.dto

/**
 * Whether the owner of a LISTING mandate is verified, and why not; status and codes only, never a document. The
 * verdict also carries [location], the city comparison (#376): `location.status` is a display hint and only
 * `MISMATCH` adds a reason.
 *
 * [overridden] is `true` only when an admin's override decided the verdict (#377), never for a document check: a
 * consumer must not infer it from the documents, and an older client ignores it.
 */
data class OwnershipVerification(
    val status: OwnershipVerificationStatus = OwnershipVerificationStatus.UNKNOWN,
    val reasons: List<OwnershipVerificationReason> = emptyList(),
    val location: OwnershipLocation = OwnershipLocation(),
    val overridden: Boolean = false,
)
