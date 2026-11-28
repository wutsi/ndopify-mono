package com.wutsi.ndopify.mandate.dto

/**
 * Whether the owner of a LISTING mandate is verified, and why not; status and codes only, never a document. The
 * verdict also carries [location], the city comparison (#376): `location.status` is a display hint and only
 * `MISMATCH` adds a reason.
 */
data class OwnershipVerification(
    val status: OwnershipVerificationStatus = OwnershipVerificationStatus.UNKNOWN,
    val reasons: List<OwnershipVerificationReason> = emptyList(),
    val location: OwnershipLocation = OwnershipLocation(),
)
