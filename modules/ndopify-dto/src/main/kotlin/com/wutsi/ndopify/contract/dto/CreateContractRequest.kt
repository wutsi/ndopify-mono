package com.wutsi.ndopify.contract.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.util.Date

data class CreateContractRequest(
    val type: ContractType = ContractType.UNKNOWN,

    @Deprecated("") @get:NotEmpty val contentUrl: String = "",
    @get:NotEmpty @get:Valid val parties: List<CreateContractParty> = emptyList(),

    val validFrom: Date = Date(),
    val validTo: Date = Date(),
)

data class CreateContractParty(
    val partyId: Long = -1,
    @get:NotEmpty val legalName: String = "",
    @get:NotEmpty val legalEmail: String = "",
    val role: ContractPartyRole = ContractPartyRole.UNKNOWN,
)
