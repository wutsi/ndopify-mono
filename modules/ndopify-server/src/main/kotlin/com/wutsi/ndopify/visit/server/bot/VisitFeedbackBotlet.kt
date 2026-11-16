package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.service.VisitFeedbackLabels
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer answered the visit feedback form: the mirror of
 * `VisitFeedbackMailet` — the listing, the date and time in the tenant's `timeZone`, the customer, the answer (with the
 * reason and the comment when the property doesn't interest it) and a `Voir la visite` button to the visit's page on
 * Partner Central — and never the visit's code, exact address or the customer's `public-portal` token.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitFeedbackBotlet(
    private val tenantService: TenantService,
    private val client: TelegramClient,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(visit, customer, agent, listing, formatter),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir la visite", url = "$url/visits/${visit.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(
        visit: VisitEntity,
        customer: CustomerEntity,
        agent: AgentEntity,
        listing: ListingEntity,
        formatter: TenantFormatter,
    ): String {
        val at = visit.scheduledAt ?: visit.requestedAt
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} a donné son avis sur la visite.")
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        lines.add("Date: ${formatter.date(at)} à ${formatter.time(at)}")
        lines.add("Client: ${customer.party.fullName()}")
        lines.add("Avis: ${VisitFeedbackLabels.interest(visit.interest!!)}")
        if (visit.interest == VisitInterest.NOT_INTERESTED) {
            VisitFeedbackLabels.reason(visit.feedbackReasonCode)?.let { lines.add("Raison: $it") }
            visit.feedbackComment?.ifBlank { null }?.let { lines.add("Commentaire: $it") }
        }
        return lines.joinToString("\n")
    }
}
