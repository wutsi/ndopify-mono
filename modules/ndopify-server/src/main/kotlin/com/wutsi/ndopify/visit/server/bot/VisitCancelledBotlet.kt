package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer cancelled a scheduled visit: a `❌ Visite annulée` line with the slot
 * (the scheduled one, or the requested one for a visit cancelled before it was confirmed), the listing's property line
 * and its type, `Agent : <B>` on a co-brokerage visit, then `Annulée par le client · Motif : …` so that the agent frees
 * the slot instead of waiting on it.
 *
 * The message never carries the customer's check-in code, exact address nor public-portal token: those are the
 * customer's alone. The buttons open the visit on Partner Central, then contact the customer — or B on a co-brokerage
 * visit — on WhatsApp. [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never
 * the agent's e-mail. Telegram refusing the message throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
 */
@Service
class VisitCancelledBotlet(
    private val tenantService: TenantService,
    private val labels: VisitTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val organizer = labels.organizer(visit, listing)
        val slot = labels.slot(visit.scheduledAt ?: visit.requestedAt, formatter)
        val reason = visit.cancellationReason?.ifBlank { null }
        val cancellation = listOfNotNull(labels.cancelledBy(visit.cancelledBy), reason?.let { "Motif : $it" })
            .joinToString(" · ")
        val text = labels.body("❌ Visite annulée", slot, listing, visit, organizer, formatter) +
            "\n" + cancellation
        sender.send(chatId, listing, text, labels.buttons(visit, listing, customer, tenant))
    }
}
