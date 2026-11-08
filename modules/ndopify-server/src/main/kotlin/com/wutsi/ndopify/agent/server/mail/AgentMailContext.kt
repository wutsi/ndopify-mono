package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantFormatter

/**
 * The agent's signature in a customer e-mail, on behalf of the agent: the From display name — the address stays the
 * tenant's, so SPF / DKIM / DMARC keep passing — and the context of the shared footer
 * (`/mail/fragments/agent-footer.mjml`). No Reply-To: a customer reply must not reach the agent's mailbox.
 */
object AgentMailContext {
    /** The From display name; [Receipient.email] is blank, filled by `MailService` with the configured address. */
    fun from(agent: AgentEntity): Receipient = Receipient(displayName = agent.party.fullName())

    fun of(agent: AgentEntity, formatter: TenantFormatter): Map<String, Any?> = mapOf(
        "agent_full_name" to agent.party.fullName(),
        "agent_photo_url" to agent.party.photoUrl?.ifBlank { null },
        "agent_whatsapp" to agent.whatsappNumber?.takeIf { it.isNotBlank() }?.let { formatter.phone(it) },
        "agent_whatsapp_url" to agent.whatsappNumber
            ?.filter { it.isDigit() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { "https://wa.me/$it" },
    )
}
