package com.wutsi.ndopify.contract.dto

import jakarta.validation.constraints.NotEmpty

// The document of the contract: the URL of its content
data class SetContractRequest(
    @get:NotEmpty val url: String = "",
)
