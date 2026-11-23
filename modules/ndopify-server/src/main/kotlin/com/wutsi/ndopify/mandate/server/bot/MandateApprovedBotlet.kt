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
 * Tells an agent over Telegram that its mandate is approved: a `✅` line saying the customer signed, then the
 * mandate's property line — its criteria, its neighbourhoods and its budget or price — with a `Voir le mandat` button.
 * The customer's name, the contract number and its validity dates stay on the mandate page.
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
        val formatter = TenantFormatter(tenant)
        client.sendMessage(
            chatId,
            text(mandate, formatter),
            tenant.partnerCentralUrl?.let { url ->
                listOf(TelegramButton("Voir le mandat", url = "${url.removeSuffix("/")}/mandates/${mandate.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(mandate: MandateEntity, formatter: TenantFormatter): String {
        val budget = MandateMailLabels.budget(mandate, formatter)
        val property = "${if (mandate.mandateType == MandateType.LISTING) "Bien" else "Recherche"} : " +
            criteria(mandate) +
            (MandateMailLabels.neighbourhoods(mandate, locationService)?.let { " ($it)" } ?: "") +
            if (MandateMailLabels.sale(mandate)) {
                " — ${MandateMailLabels.amountLabel(mandate)} : $budget"
            } else {
                " — $budget"
            }
        return listOf(
            "✅ Le client a signé le mandat. Il va commencer à recevoir les offres.",
            property,
        ).joinToString("\n")
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
