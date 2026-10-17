package com.wutsi.ndopify.contract.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class CreateContractRequest(
    val type: ContractType = ContractType.UNKNOWN,

    @get:NotEmpty @get:Valid val parties: List<CreateContractParty> = emptyList(),

    // How long the contract is valid, from its signature by the last party
    val durationMonths: Int = 0,
)

data class CreateContractParty(
    val partyId: Long = -1,
    @get:NotEmpty val legalName: String = "",
    @get:NotEmpty val legalEmail: String = "",
    val role: ContractPartyRole = ContractPartyRole.UNKNOWN,
)
