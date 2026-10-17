package com.wutsi.ndopify.contract.server.event

/**
 * Published by `ContractService.sign`, inside its transaction, when the last party signs: the contract is SIGNED.
 * Carries ids rather than the entity, so listeners reload what they need. Lets the contract domain stay unaware of
 * what the contract is for (ex: `MandateEventListener` approves the mandate it belongs to).
 */
data class ContractSignedEvent(
    val contractId: String,
    val tenantId: Long,
)
