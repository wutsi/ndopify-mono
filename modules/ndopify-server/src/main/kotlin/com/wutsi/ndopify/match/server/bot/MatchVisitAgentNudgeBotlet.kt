package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Nudges an agent over Telegram that its customer accepted a match more than 48 h ago and still has no visit: a `⏰`
 * line asking it to call the customer, the listing's one property line, then the 48 h fact. The buttons contact the
 * customer on WhatsApp (when it has a number) and open the match on its mandate's page, where the agent books the visit.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MatchVisitAgentNudgeBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(match.tenantId!!)
        val whatsapp = customer.whatsappNumber?.trim()?.ifBlank { null }?.let { number ->
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(number))
        }
        val mandateButton = tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
            TelegramButton("Voir le mandat", url = "$url/mandates/${match.mandate.id}#match-${match.id}")
        }
        sender.send(
            chatId,
            match.listing,
            text(match, customer, TenantFormatter(tenant)),
            listOfNotNull(whatsapp, mandateButton),
        )
    }

    private fun text(match: MatchEntity, customer: CustomerEntity, formatter: TenantFormatter): String =
        listOf(
            "⏰ Appelez ${customer.party.fullName()} pour planifier la visite",
            labels.titleAndPrice(match.listing, formatter),
            "Proposition acceptée il y a plus de 48 h.",
        ).joinToString("\n")
}
