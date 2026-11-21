package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Offers a mandate to an agent over Telegram: a `📋` line with the mandate's property — its criteria, its
 * neighbourhoods and its budget or price — the listing's details, and the offer's expiry, with an `Accepter` /
 * `Refuser` keyboard so that the tap answers the offer.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MandateOfferBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val client: TelegramClient,
) {
    fun send(assignment: AssignmentEntity, agent: AgentEntity, chatId: Long) {
        val mandate = assignment.mandate
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(assignment, formatter),
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
        )
    }

    private fun text(assignment: AssignmentEntity, formatter: TenantFormatter): String {
        val mandate = assignment.mandate
        val lines = mutableListOf(
            "📋 Nouveau mandat : " + criteria(mandate) +
                (MandateMailLabels.neighbourhoods(mandate, locationService)?.let { " ($it)" } ?: "") +
                " — ${MandateMailLabels.budget(mandate, formatter)}",
        )
        if (mandate.mandateType == MandateType.LISTING) {
            details(mandate, formatter)?.let { lines.add(it) }
        }
        lines.add("Expire le ${formatter.date(assignment.expiresAt)} à ${formatter.time(assignment.expiresAt)}.")
        return lines.joinToString("\n")
    }

    // The listing's facts on one line: "4 pièces · 250 m² · disponible le 01/12/2026"
    private fun details(mandate: MandateEntity, formatter: TenantFormatter): String? =
        listOfNotNull(
            mandate.rooms?.trim()?.ifBlank { null }?.let { rooms -> "$rooms pièce" + if (rooms == "1") "" else "s" },
            mandate.area?.let { "$it m²" },
            mandate.availableFrom?.let { "disponible le ${formatter.date(it)}" },
        ).ifEmpty { null }?.joinToString(" · ")

    // Ex: "Location - Appartement", or "Location" without a property type
    private fun criteria(mandate: MandateEntity): String {
        val propertyType = MandateMailLabels.propertyType(mandate)
        return if (propertyType == null) {
            MandateMailLabels.listingType(mandate)
        } else {
            "${MandateMailLabels.listingType(mandate)} - $propertyType"
        }
    }
}
