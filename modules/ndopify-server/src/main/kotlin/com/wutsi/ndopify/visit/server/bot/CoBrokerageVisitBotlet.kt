package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.CoBrokerageVisitLabels
import org.springframework.stereotype.Service

/**
 * Tells the listing agent (B) over Telegram about a co-brokerage visit organized by the search agent (A): the same
 * four events as `CoBrokerageVisitMailet`, each as an emoji line naming the property, the slot and the type or reason,
 * so that a linked B gets the message instead of the e-mail.
 *
 * [listing]'s agent is the recipient B, [agent] is A, the organizer whose voice the message carries: the text closes
 * with A's name instead of the e-mail's agent footer, on the line after the event. [ListingTelegramSender] sends the
 * message with the listing's photo as its caption when it fits, else as the text alone. [sendX]'s chat id comes from
 * #135's link (`AgentTelegramService.chatId`), never B's e-mail. The customer is only ever "mon client": this botlet
 * never reads it, and its text carries none of its data.
 *
 * Telegram refusing a message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]), so the notifier falls
 * back to the e-mail.
 */
@Service
class CoBrokerageVisitBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { line, slot, type ->
            listOf(
                "✅ Visite confirmée : $line, $slot",
                "$type — ${onSite(visit.type)}",
            )
        }

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { line, slot, type ->
            listOf(
                "⏰ Rappel : $line, $slot",
                "$type — ${onSite(visit.type)}",
            )
        }

    fun sendCancelled(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { line, slot, _ ->
            listOf(
                "❌ Visite annulée : $line, $slot",
                CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy),
            )
        }

    fun sendNoShow(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { line, slot, _ ->
            listOf(
                "⚠️ Visite non honorée : $line, $slot",
                CoBrokerageVisitLabels.noShowReason(visit.noShowReason),
            )
        }

    // The event's block, then A's signature: the bot takes the e-mail's place as the sender
    private fun send(
        visit: VisitEntity,
        listing: ListingEntity,
        agent: AgentEntity,
        chatId: Long,
        body: (line: String, slot: String, type: String) -> List<String>,
    ) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val scheduledAt = visit.scheduledAt!!
        val line = labels.titleAndPrice(listing, formatter)
        val slot = "${formatter.date(scheduledAt)} à ${formatter.time(scheduledAt)}"
        val lines = body(line, slot, CoBrokerageVisitLabels.typeLabel(visit.type))
            .toMutableList()
        lines.add("Visite organisée par ${agent.party.fullName()}.")

        sender.send(
            chatId,
            listing,
            lines.joinToString("\n"),
            tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
                listOf(TelegramButton("Voir l'annonce", url = "$url/listings/${listing.id}#visits-title"))
            } ?: emptyList(),
        )
    }

    private fun onSite(type: VisitType): String =
        if (type == VisitType.ON_SITE) {
            "je vous y retrouve avec mon client."
        } else {
            "je serai sur place pour vous ouvrir ; le client suivra en visio."
        }
}
