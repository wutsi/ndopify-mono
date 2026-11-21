package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchAnsweredLabels
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the search agent over Telegram that its customer answered a proposed match: the mirror of
 * `MatchAnsweredMailet`'s answer, as one `✅` / `❌` line naming the customer and the property, completed by a
 * rejection's reason and comment, with a `Voir le mandat` button to the mandate's page on Partner Central.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MatchAnsweredBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(match.tenantId!!)
        sender.send(
            chatId,
            match.listing,
            text(match, customer, TenantFormatter(tenant)),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir le mandat", url = "$url/mandates/${match.mandate.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(match: MatchEntity, customer: CustomerEntity, formatter: TenantFormatter): String {
        val accepted = match.status == MatchStatus.ACCEPTED
        val lines = mutableListOf(
            "${if (accepted) "✅" else "❌"} ${customer.party.fullName()} a " +
                "${if (accepted) "accepté" else "refusé"} la proposition : " +
                labels.titleAndPrice(match.listing, formatter),
        )
        val reason = MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)
        val comment = match.rejectionReason?.ifBlank { null }
        when {
            reason != null && comment != null -> lines.add("Motif : $reason · « $comment »")
            reason != null -> lines.add("Motif : $reason")
            comment != null -> lines.add("Commentaire : « $comment »")
        }
        return lines.joinToString("\n")
    }
}
