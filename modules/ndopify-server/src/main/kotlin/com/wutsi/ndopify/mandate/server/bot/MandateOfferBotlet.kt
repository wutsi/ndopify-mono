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
 * Offers a mandate to an agent over Telegram: the same wording as `MandateOfferMailet`, with an `Accepter` /
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
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(assignment, city, formatter),
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

    private fun text(
        assignment: AssignmentEntity,
        city: String,
        formatter: TenantFormatter,
    ): String {
        val mandate = assignment.mandate
        val listing = mandate.mandateType == MandateType.LISTING
        val lines = mutableListOf<String>()
        lines.add(
            "Un nouveau mandat vous est proposé" + (if (city.isEmpty()) "" else " à $city")
        )
        lines.add("")
        lines.add(
            if (listing) {
                "Un propriétaire vous propose son mandat en exclusivité."
            } else {
                "Un client recherche un bien, et nous vous proposons son mandat en exclusivité."
            }
        )
        lines.add("")
        lines.add("${if (listing) "Bien" else "Recherche"}: " + criteria(mandate))
        mandate.rooms?.ifBlank { null }?.let { lines.add("Pièces: $it") }
        lines.add("Ville: $city")
        MandateMailLabels.neighbourhoods(mandate, locationService)?.let { lines.add("Quartier(s): $it") }
        if (listing) {
            mandate.area?.let { lines.add("Surface: $it m²") }
            lines.add("${MandateMailLabels.priceLabel(mandate)}: ${MandateMailLabels.budget(mandate, formatter)}")
            mandate.availableFrom?.let { lines.add("Disponible à partir du: ${formatter.date(it)}") }
        } else {
            lines.add("Budget: ${MandateMailLabels.budget(mandate, formatter)}")
        }
        lines.add("")
        lines.add(
            "Vous avez jusqu'au ${formatter.date(assignment.expiresAt)} à ${formatter.time(assignment.expiresAt)} " +
                "pour accepter ce mandat. Passé ce délai, il sera proposé à un autre agent."
        )
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
