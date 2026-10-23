package com.wutsi.ndopify.contract.dto

import jakarta.validation.constraints.NotEmpty

data class SignContractRequest(
    val partyId: String = "",
    val ipAddress: String? = null,
    val userAgent: String? = null,
    // SHA-256 (lowercase hex) of the content's bytes the signer was shown: must be the contract's
    @get:NotEmpty val contentHash: String = "",
)
