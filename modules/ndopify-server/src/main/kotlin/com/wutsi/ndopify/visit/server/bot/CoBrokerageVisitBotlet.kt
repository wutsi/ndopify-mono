package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.CoBrokerageVisitLabels
import org.springframework.stereotype.Service

/**
 * Tells the listing agent (B) over Telegram about a co-brokerage visit organized by the search agent (A): the same
 * four messages as `CoBrokerageVisitMailet`, in French plain text, so that a linked B gets the message instead of the
 * e-mail.
 *
 * [listing]'s agent is the recipient B, [agent] is A, the organizer whose voice the message carries: the bot is the
 * sender, so the text closes with A's name instead of the e-mail's agent footer. [sendX]'s chat id comes from #135's
 * link (`AgentTelegramService.chatId`), never B's e-mail. The customer is only ever "mon client": this botlet never
 * reads it, and its text carries none of its data.
 *
 * Telegram refusing a message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]), so the notifier falls
 * back to the e-mail.
 */
@Service
class CoBrokerageVisitBotlet(
    private val tenantService: TenantService,
    private val client: TelegramClient,
) {
    fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { title, slot, type ->
            listOf(
                "Bonjour ${listing.agent.party.fullName()},",
                "",
                "J'ai organisé une visite de votre bien $title le $slot ($type).",
                "",
                onSite(visit.type),
            )
        }

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { title, slot, type ->
            listOf(
                "Bonjour ${listing.agent.party.fullName()},",
                "",
                "Rappel : la visite de votre bien $title a lieu le $slot ($type).",
                "",
                onSite(visit.type),
            )
        }

    fun sendCancelled(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { title, slot, type ->
            listOf(
                "Bonjour ${listing.agent.party.fullName()},",
                "",
                "La visite de votre bien $title ($type) prévue le $slot a été annulée.",
                "",
                CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy),
            )
        }

    fun sendNoShow(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long) =
        send(visit, listing, agent, chatId) { title, slot, type ->
            listOf(
                "Bonjour ${listing.agent.party.fullName()},",
                "",
                "La visite de votre bien $title ($type) prévue le $slot n'a pas eu lieu.",
                "",
                CoBrokerageVisitLabels.noShowReason(visit.noShowReason),
            )
        }

    // The event's block, then A's signature: the bot takes the e-mail's place as the sender
    private fun send(
        visit: VisitEntity,
        listing: ListingEntity,
        agent: AgentEntity,
        chatId: Long,
        body: (title: String, slot: String, type: String) -> List<String>,
    ) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val scheduledAt = visit.scheduledAt!!
        val title = listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"
        val slot = "${formatter.date(scheduledAt)} à ${formatter.time(scheduledAt)}"
        val lines = body(title, slot, CoBrokerageVisitLabels.typeLabel(visit.type))
            .toMutableList()
        lines.add("")
        lines.add("Visite organisée par ${agent.party.fullName()}.")

        client.sendMessage(
            chatId,
            lines.joinToString("\n"),
            tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
                listOf(TelegramButton("Voir l'annonce", url = "$url/listings/${listing.id}#visits-title"))
            } ?: emptyList(),
        )
    }

    private fun onSite(type: VisitType): String =
        if (type == VisitType.ON_SITE) {
            "Je vous y retrouve avec mon client."
        } else {
            "Je serai sur place pour vous ouvrir ; le client suivra en visio."
        }
}
