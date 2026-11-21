package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer cancelled a scheduled visit: a `❌` line naming the customer and the
 * property, then the slot and the reason, so that the agent frees it instead of waiting on it.
 *
 * The message never carries the customer's check-in code, exact address nor public-portal token: those are the
 * customer's alone. [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never
 * the agent's e-mail. Telegram refusing the message throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
 */
@Service
class VisitCancelledBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        sender.send(
            chatId,
            listing,
            text(visit, customer, listing, formatter),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir la visite", url = "$url/visits/${visit.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(
        visit: VisitEntity,
        customer: CustomerEntity,
        listing: ListingEntity,
        formatter: TenantFormatter,
    ): String {
        val lines = mutableListOf(
            "❌ ${customer.party.fullName()} a annulé la visite : " + labels.titleAndPrice(listing, formatter),
        )
        val facts = listOfNotNull(
            visit.scheduledAt?.let { "${formatter.date(it)} à ${formatter.time(it)}" },
            visit.cancellationReason?.ifBlank { null }?.let { "Motif : $it" },
        )
        if (facts.isNotEmpty()) {
            lines.add(facts.joinToString(" · "))
        }
        return lines.joinToString("\n")
    }
}
