package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.service.VisitFeedbackLabels
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Tells the agent over Telegram that its customer answered the visit feedback form: a `⭐ Avis de visite` line with the
 * slot, the listing's property line and its type, `Agent : <B>` on a co-brokerage visit, then the answer — with
 * `· Raison : …` when the property doesn't interest it, and the comment in guillemets.
 *
 * The buttons open the visit on Partner Central, then contact the customer — or B on a co-brokerage visit — on
 * WhatsApp. The reason is capitalised here, for Telegram only: the shared [VisitFeedbackLabels] keep their lowercase
 * wording for the e-mail. It never carries the visit's code, exact address or the customer's `public-portal` token.
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitFeedbackBotlet(
    private val tenantService: TenantService,
    private val labels: VisitTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val organizer = labels.organizer(visit, listing)
        val slot = labels.slot(visit.scheduledAt ?: visit.requestedAt, formatter)
        sender.send(
            chatId,
            listing,
            text(visit, slot, listing, organizer, formatter),
            labels.buttons(visit, listing, customer, tenant),
        )
    }

    private fun text(
        visit: VisitEntity,
        slot: String,
        listing: ListingEntity,
        organizer: AgentEntity?,
        formatter: TenantFormatter,
    ): String {
        val body = labels.body("⭐ Avis de visite", slot, listing, visit, organizer, formatter)
        val lines = mutableListOf<String>()
        if (visit.interest == VisitInterest.NOT_INTERESTED) {
            val reason = VisitFeedbackLabels.reason(visit.feedbackReasonCode)?.capitalise()
            lines.add(listOfNotNull(VisitFeedbackLabels.interest(visit.interest!!), reason?.let { "Raison : $it" })
                .joinToString(" · "))
            visit.feedbackComment?.ifBlank { null }?.let { lines.add("Commentaire : « $it »") }
        } else {
            lines.add(VisitFeedbackLabels.interest(visit.interest!!))
        }
        return (listOf(body) + lines).joinToString("\n")
    }

    // "Trop cher": French capitalisation of the shared labels' lowercase reason, Telegram only
    private fun String.capitalise(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
}
