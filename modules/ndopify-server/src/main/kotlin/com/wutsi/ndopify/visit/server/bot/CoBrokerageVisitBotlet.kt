package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Tells the listing agent (B) over Telegram about a co-brokerage visit organized by the search agent (A): the same four
 * events as `CoBrokerageVisitMailet`, each in the shared visit format — `<object> : <date/time>`, the listing's property
 * line and its type, then `Agent : <A>` — followed by the event's own line (the cancellation's actor, the no-show).
 *
 * [agent] is A, the organizer; [listing]'s agent is the recipient B, so the `Agent :` line names A. The buttons open the
 * visit on Partner Central and contact A on WhatsApp, each left out when the tenant has no `partnerCentralUrl` or A no
 * number. The text never carries A's customer: no name, number, note, code, address nor feedback — the customer is only
 * ever implied.
 *
 * [ListingTelegramSender] sends the message with the listing's photo as its caption when it fits, else as the text
 * alone. [sendX]'s chat id comes from #135's link (`AgentTelegramService.chatId`), never B's e-mail. Telegram refusing
 * a message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]), so the notifier falls back to the e-mail.
 */
@Service
class CoBrokerageVisitBotlet(
    private val tenantService: TenantService,
    private val labels: VisitTelegramLabels,
    private val sender: ListingTelegramSender,
    private val clock: Clock,
) {
    companion object {
        // The Telegram wording of a no-show: one line for both e-mail reasons, without the code-refusal nuance
        private const val NO_SHOW = "Le client ne s'est pas présenté"
    }

    fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId, "✅ Visite confirmée", { _, formatter ->
            labels.slot(visit.scheduledAt!!, formatter)
        }, null)

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId, "⏰ Rappel de visite", { tenant, formatter ->
            "${labels.dayWord(visit, tenant, clock)} à ${formatter.time(visit.scheduledAt!!)}"
        }, null)

    fun sendCancelled(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId, "❌ Visite annulée", { _, formatter ->
            labels.slot(visit.scheduledAt!!, formatter)
        }, labels.cancelledBy(visit.cancelledBy))

    fun sendNoShow(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId, "⚠️ Visite non honorée", { _, formatter ->
            labels.slot(visit.scheduledAt!!, formatter)
        }, NO_SHOW)

    private fun send(
        visit: VisitEntity,
        listing: ListingEntity,
        agent: AgentEntity,
        chatId: Long,
        label: String,
        whenLabel: (TenantEntity, TenantFormatter) -> String,
        eventLine: String?,
    ) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val text = labels.body(label, whenLabel(tenant, formatter), listing, visit, agent, formatter) +
            (eventLine?.let { "\n$it" } ?: "")
        sender.send(chatId, listing, text, buttons(visit, agent, tenant))
    }

    // A's number, never the customer's: B acts on the visit from the chat
    private fun buttons(visit: VisitEntity, agent: AgentEntity, tenant: TenantEntity): List<TelegramButton> =
        listOfNotNull(
            tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
                TelegramButton("Voir la visite", url = "$url/visits/${visit.id}")
            },
            agent.whatsappNumber?.trim()?.ifBlank { null }?.let { number ->
                TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(number))
            },
        )
}
