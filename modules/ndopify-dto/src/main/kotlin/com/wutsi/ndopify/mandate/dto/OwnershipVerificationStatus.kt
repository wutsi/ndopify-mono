package com.wutsi.ndopify.mandate.dto

/**
 * Whether a LISTING mandate's owner is verified (#297): every document read on the mandate already passed
 * `DocumentChecks` when it is `EXTRACTED`. `UNKNOWN` is the DTO's default and is never returned by the endpoint.
 */
enum class OwnershipVerificationStatus {
    UNKNOWN,
    PENDING,
    VERIFIED,
    FAILED,
}
