package com.wutsi.ndopify.contract.server.event

/**
 * Published by `ContractService.expire`, inside its transaction, when a contract becomes EXPIRED: not signed by all its
 * parties before its signature deadline, or signed and past its end date. Carries ids rather than the entity, so
 * listeners reload what they need. No listener yet.
 */
data class ContractExpiredEvent(
    val contractId: String,
    val tenantId: Long,
)
