package com.wutsi.ndopify.contract.server.event

/**
 * Published by `ContractService.requestSignature`, inside its transaction, when a DRAFT contract is sent to its parties
 * for signature. Carries ids rather than the entity, so listeners reload what they need. [tenantId] is the contract's
 * tenant, since listeners run on a thread that has none. Its listener (e-mailing the parties) must be
 * `@TransactionalEventListener(AFTER_COMMIT)`.
 */
data class ContractSignatureRequestedEvent(
    val contractId: String,
    val tenantId: Long,
)
