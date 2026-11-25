package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells an agent over Telegram that its mandate is approved: a `✅` line saying the customer signed, then the mandate's
 * one property line (`Recherche : …` or `Bien : …`, its criteria, neighbourhoods and compact price). The buttons contact
 * the customer on WhatsApp when it has a number, then open the mandate on Partner Central. The customer's name, the
 * contract number and its validity dates stay on the mandate page.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class MandateApprovedBotlet(
    private val tenantService: TenantService,
    private val labels: MandateTelegramLabels,
    private val client: TelegramClient,
) {
    fun send(mandate: MandateEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val whatsapp = customer.whatsappNumber?.trim()?.ifBlank { null }?.let { number ->
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(number))
        }
        val mandateButton = tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
            TelegramButton("Voir le mandat", url = "$url/mandates/${mandate.id}")
        }
        client.sendMessage(
            chatId,
            text(mandate, formatter),
            listOfNotNull(whatsapp, mandateButton),
        )
    }

    private fun text(mandate: MandateEntity, formatter: TenantFormatter): String {
        val property = "${if (mandate.mandateType == MandateType.LISTING) "Bien" else "Recherche"} : " +
            labels.line(mandate, formatter)
        return listOf("✅ Le client a signé le mandat.", property).joinToString("\n")
    }
}
