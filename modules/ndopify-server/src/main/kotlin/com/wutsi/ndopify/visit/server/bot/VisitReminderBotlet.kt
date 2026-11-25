package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Reminds the agent over Telegram of a confirmed visit 24 h ahead: a `⏰ Rappel de visite` line with the day in the
 * tenant's `timeZone` (`aujourd'hui` / `demain`) and the time, the listing's property line and its type, `Agent : <B>`
 * on a co-brokerage visit. No video-call note: agents know how to hold a visit by video. The buttons open the visit on
 * Partner Central, then contact the customer — or B on a co-brokerage visit — on WhatsApp.
 *
 * It never carries the visit's 4-digit code nor the exact address: only the customer's copy has the code.
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitReminderBotlet(
    private val tenantService: TenantService,
    private val labels: VisitTelegramLabels,
    private val sender: ListingTelegramSender,
    private val clock: Clock,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val organizer = labels.organizer(visit, listing)
        sender.send(
            chatId,
            listing,
            labels.body(
                "⏰ Rappel de visite",
                "${labels.dayWord(visit, tenant, clock)} à ${formatter.time(visit.scheduledAt!!)}",
                listing,
                visit,
                organizer,
                formatter,
            ),
            labels.buttons(visit, listing, customer, tenant),
        )
    }
}
