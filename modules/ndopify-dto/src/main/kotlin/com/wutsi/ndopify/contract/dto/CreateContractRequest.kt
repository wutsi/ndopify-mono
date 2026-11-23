package com.wutsi.ndopify.contract.dto

import com.wutsi.ndopify.refdata.dto.Money
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class CreateContractRequest(
    val type: ContractType = ContractType.UNKNOWN,

    @get:NotEmpty @get:Valid val parties: List<CreateContractParty> = emptyList(),

    // How long the contract is valid, from its signature by the last party
    val durationMonths: Int = 0,

    // Null = none; the currency is the tenant's, or blank to mean it
    val visitFee: Money? = null,

    // A sale contract's commission in % of the sale price (1..20); ignored by the other types
    val commissionRate: Int? = null,
)

data class CreateContractParty(
    val partyId: Long = -1,
    @get:NotEmpty val legalName: String = "",
    @get:NotEmpty val legalEmail: String = "",
    val role: ContractPartyRole = ContractPartyRole.UNKNOWN,

    // False for a party named in the contract that doesn't sign it: it's not asked to, and can't
    val signatureRequired: Boolean = true,
)
