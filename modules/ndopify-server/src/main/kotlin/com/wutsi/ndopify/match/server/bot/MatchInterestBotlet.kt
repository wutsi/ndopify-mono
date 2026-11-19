package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer asked for a listing on its public page: the mirror of
 * `MatchInterestMailet`, with a `Contacter sur WhatsApp` button (when the customer has a number) and a `Voir le mandat`
 * one to the search's page on Partner Central, where it organises the visit.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MatchInterestBotlet(
    private val tenantService: TenantService,
    private val sender: ListingTelegramSender,
) {
    fun send(agent: AgentEntity, customer: CustomerEntity, mandate: MandateEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val whatsapp = customer.whatsappNumber?.takeIf { it.isNotBlank() }?.let { number ->
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(number))
        }
        val mandateButton = tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
            TelegramButton("Voir le mandat", url = "$url/mandates/${mandate.id}")
        }
        sender.send(
            chatId,
            listing,
            text(customer, listing),
            listOfNotNull(whatsapp, mandateButton),
        )
    }

    private fun text(customer: CustomerEntity, listing: ListingEntity): String {
        val lines = mutableListOf<String>()
        lines.add("${customer.party.fullName()} s'intéresse à un logement")
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        lines.add("")
        lines.add("Contactez-le pour organiser la visite.")
        return lines.joinToString("\n")
    }
}
