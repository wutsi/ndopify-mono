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
 * Tells the agent over Telegram that its customer asked to visit a listing: a `📅 Demande de visite` line with the
 * requested slot, the listing's property line and its type, `Agent : <B>` on a co-brokerage visit, then the customer's
 * note in guillemets. The buttons open the visit on Partner Central, then contact the customer — or B, the co-agent of
 * a co-brokerage visit — on WhatsApp.
 *
 * The text never carries the customer's number. [send]'s recipient is the agent's chat, from #135's link
 * (`AgentTelegramService.chatId`), never the agent's e-mail. Telegram refusing the message throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
 */
@Service
class VisitRequestedBotlet(
    private val tenantService: TenantService,
    private val labels: VisitTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val organizer = labels.organizer(visit, listing)
        sender.send(
            chatId,
            listing,
            text(visit, listing, organizer, formatter),
            labels.buttons(visit, listing, customer, tenant),
        )
    }

    private fun text(
        visit: VisitEntity,
        listing: ListingEntity,
        organizer: AgentEntity?,
        formatter: TenantFormatter,
    ): String = buildString {
        append(
            labels.body(
                "📅 Demande de visite",
                labels.slot(visit.requestedAt, formatter),
                listing,
                visit,
                organizer,
                formatter,
            ),
        )
        visit.note?.ifBlank { null }?.let { append("\nNote : « $it »") }
    }
}
