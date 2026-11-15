package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells an agent over Telegram that its mandate is approved: the same wording as `MandateApprovedMailet`, with the
 * city, the type, the neighbourhoods and the budget or price, and a `Voir le mandat` button. The customer's name, the
 * contract number and its validity dates stay on the mandate page.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MandateApprovedBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val client: TelegramClient,
) {
    fun send(mandate: MandateEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(mandate, agent, city, formatter),
            tenant.partnerCentralUrl?.let { url ->
                listOf(TelegramButton("Voir le mandat", url = "${url.removeSuffix("/")}/mandates/${mandate.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(mandate: MandateEntity, agent: AgentEntity, city: String, formatter: TenantFormatter): String {
        val listing = mandate.mandateType == MandateType.LISTING
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            "Votre client a signé le mandat${if (city.isEmpty()) "" else " à $city"}. " +
                "Le mandat est approuvé : vous pouvez commencer."
        )
        lines.add("")
        lines.add("${if (listing) "Bien" else "Recherche"}: " + criteria(mandate))
        if (city.isNotEmpty()) {
            lines.add("Ville: $city")
        }
        MandateMailLabels.neighbourhoods(mandate, locationService)?.let { lines.add("Quartier(s): $it") }
        if (listing) {
            lines.add("${MandateMailLabels.priceLabel(mandate)}: ${MandateMailLabels.budget(mandate, formatter)}")
        } else {
            lines.add("Budget: ${MandateMailLabels.budget(mandate, formatter)}")
        }
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
