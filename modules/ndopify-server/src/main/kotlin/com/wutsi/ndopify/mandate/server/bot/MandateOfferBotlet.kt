package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Offers a mandate to an agent over Telegram: a `📋` line with the mandate's one property line (criteria,
 * neighbourhoods, compact price) and, for a purchase, the buyer's financing and documents, then the offer's expiry. The
 * offer stays short on purpose — the agent decides from the buttons.
 *
 * The keyboard has the two callback rows `Accepter` / `Refuser`, which answer the offer, then a `Voir détails` button to
 * the offer's card on Partner Central's home page when the tenant has a `partnerCentralUrl`. [send]'s recipient is the
 * agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail. Telegram refusing the
 * message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
 */
@Service
class MandateOfferBotlet(
    private val tenantService: TenantService,
    private val labels: MandateTelegramLabels,
    private val client: TelegramClient,
) {
    fun send(assignment: AssignmentEntity, agent: AgentEntity, chatId: Long) {
        val mandate = assignment.mandate
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val details = tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
            listOf(TelegramButton("Voir détails", url = "$url/#assignment-${assignment.id}"))
        }
        val rows = listOfNotNull(
            listOf(
                TelegramButton(
                    "Accepter",
                    TelegramOfferCallback.data(TelegramOfferCallback.Action.ACCEPT, assignment.id!!),
                ),
                TelegramButton(
                    "Refuser",
                    TelegramOfferCallback.data(TelegramOfferCallback.Action.DECLINE, assignment.id!!),
                ),
            ),
            details,
        )
        client.sendMessageRows(chatId, text(assignment, formatter), rows)
    }

    private fun text(assignment: AssignmentEntity, formatter: TenantFormatter): String {
        val mandate = assignment.mandate
        val expiresAt = assignment.expiresAt
        return listOf(
            "📋 Nouveau mandat : " + labels.line(mandate, formatter) + purchase(mandate),
            "Expire le ${formatter.date(expiresAt)} à ${formatter.time(expiresAt)}.",
        ).joinToString("\n")
    }

    // The purchase answers on the criteria line: financing first, then the documents the buyer requires.
    private fun purchase(mandate: MandateEntity): String =
        listOfNotNull(
            MandateMailLabels.financingType(mandate)?.let { " · Financement : $it" },
            MandateMailLabels.titleDocuments(mandate)?.let { " · Documents : $it" },
        ).joinToString("")
}
