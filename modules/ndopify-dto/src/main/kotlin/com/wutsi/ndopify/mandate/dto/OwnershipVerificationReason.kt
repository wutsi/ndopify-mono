package com.wutsi.ndopify.mandate.dto

/**
 * One reason a check failed: [code] is a plain string (`OWNERSHIP_ID_MISSING`...), so a new code needs no DTO
 * release; [details] carries the failed document's `problemCodes` for `*_UNUSABLE`, and is empty otherwise.
 */
data class OwnershipVerificationReason(
    val code: String = "",
    val details: List<String> = emptyList(),
)
