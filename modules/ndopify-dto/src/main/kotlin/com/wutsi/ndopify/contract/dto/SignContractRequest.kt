package com.wutsi.ndopify.contract.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class SignContractRequest(
    val partyId: String = "",
    // IPv4 or IPv6 address, as T_CONTRACT_PARTY.ip_address VARCHAR(45)
    @get:Size(max = 45) val ipAddress: String? = null,
    @get:Size(max = 1024) val userAgent: String? = null,
    // SHA-256 (lowercase hex) of the content's bytes the signer was shown: must be the contract's
    @get:NotEmpty val contentHash: String = "",
)
