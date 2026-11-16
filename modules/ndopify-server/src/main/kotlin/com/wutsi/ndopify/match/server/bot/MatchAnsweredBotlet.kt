package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchAnsweredLabels
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the search agent over Telegram that its customer answered a proposed match: the mirror of
 * `MatchAnsweredMailet`, with the customer, the listing and its price, the mandate's criteria, a rejection's reason and
 * comment, and a `Voir le mandat` button to the mandate's page on Partner Central.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MatchAnsweredBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val client: TelegramClient,
) {
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(match.tenantId!!)
        val city = locationService.findByIdOrNull(match.mandate.cityId)?.name ?: ""
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(match, customer, agent, city, formatter),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir le mandat", url = "$url/mandates/${match.mandate.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(
        match: MatchEntity,
        customer: CustomerEntity,
        agent: AgentEntity,
        city: String,
        formatter: TenantFormatter,
    ): String {
        val mandate = match.mandate
        val listing = match.listing
        val accepted = match.status == MatchStatus.ACCEPTED
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            (if (accepted) "Bonne nouvelle : " else "") +
                "${customer.party.fullName()} a ${if (accepted) "accepté" else "refusé"} la proposition.",
        )
        MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)
            ?.let { lines.add("Motif: $it") }
        match.rejectionReason?.ifBlank { null }?.let { lines.add("Commentaire: $it") }
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        lines.add(
            "${MandateMailLabels.priceLabel(mandate)}: " +
                MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter),
        )
        lines.add("")
        lines.add("Recherche: " + criteria(mandate))
        lines.add("Ville: $city")
        MandateMailLabels.neighbourhoods(mandate, locationService)?.let { lines.add("Quartier(s): $it") }
        lines.add("Budget: ${MandateMailLabels.budget(mandate, formatter)}")
        lines.add("")
        lines.add("Cette réponse est définitive.")
        return lines.joinToString("\n")
    }

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
