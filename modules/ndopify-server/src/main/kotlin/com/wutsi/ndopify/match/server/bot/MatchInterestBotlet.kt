package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer asked for a listing on its public page: the mirror of
 * `MatchInterestMailet`, plus a `Voir le mandat` button to the search's page on Partner Central, where it proposes the
 * visit.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MatchInterestBotlet(
    private val tenantService: TenantService,
    private val client: TelegramClient,
) {
    fun send(agent: AgentEntity, customer: CustomerEntity, mandate: MandateEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        client.sendMessage(
            chatId,
            text(agent, customer, listing),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir le mandat", url = "$url/mandates/${mandate.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(agent: AgentEntity, customer: CustomerEntity, listing: ListingEntity): String {
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} s'intéresse à un logement.")
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        lines.add("")
        lines.add("Ce logement a été ajouté à son mandat de recherche. Proposez-lui la visite depuis son mandat.")
        return lines.joinToString("\n")
    }
}
