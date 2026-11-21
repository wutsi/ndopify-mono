package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Nudges an agent over Telegram that its customer accepted a match more than 48 h ago and still has no visit: the
 * customer and the property in one `⏰` line, then the two things to do — call the customer on WhatsApp, then book the
 * visit on the mandate's page.
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
        val digits = customer.whatsappNumber.orEmpty().filter(Char::isDigit)
        val baseUrl = tenant.partnerCentralUrl?.removeSuffix("/")
        val buttons = listOfNotNull(
            digits.takeIf { it.isNotEmpty() }?.let { TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/$it") },
            baseUrl?.let {
                TelegramButton("Planifier la visite", url = "$it/mandates/${match.mandate.id}#match-${match.id}")
            },
        )
        sender.send(chatId, match.listing, text(match, customer, TenantFormatter(tenant)), buttons)
    }

    private fun text(match: MatchEntity, customer: CustomerEntity, formatter: TenantFormatter): String =
        listOf(
            "⏰ ${customer.party.fullName()} attend une visite : " + labels.titleAndPrice(match.listing, formatter),
            "Acceptée il y a plus de 48 h. Appelez-le, puis planifiez la visite.",
        ).joinToString("\n")
}
