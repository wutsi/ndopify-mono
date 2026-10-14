package com.wutsi.ndopify.security.server.event

/**
 * Published by `OtpService.create`, inside its transaction. [code] is the clear code: only its hash is persisted,
 * so the event is the one place it travels to the mailet. [toString] masks it so it never ends up in a log.
 * [tenantId] is the tenant resolved for the request, since the listener runs on a thread that has none.
 */
data class OtpCreatedEvent(
    val authFactorId: Long,
    val code: String,
    val tenantId: Long,
) {
    override fun toString() = "OtpCreatedEvent(authFactorId=$authFactorId, code=******, tenantId=$tenantId)"
}
