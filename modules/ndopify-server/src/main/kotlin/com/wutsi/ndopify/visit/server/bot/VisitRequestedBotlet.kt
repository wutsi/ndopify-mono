package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer asked to visit a listing: a `📅` line naming the customer and the
 * property, the requested slot with the visit type — the agent must know whether to go to the property or plan a
 * WhatsApp video call — the note when present, and a `Voir la visite` button to the visit's page on Partner Central,
 * plus a `Contacter sur WhatsApp` button to the customer's `wa.me` link when it has a WhatsApp number (never the
 * number in the text).
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitRequestedBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val url = tenant.partnerCentralUrl?.removeSuffix("/")
        sender.send(
            chatId,
            listing,
            text(visit, customer, listing, formatter),
            listOfNotNull(
                url?.let { TelegramButton("Voir la visite", url = "$it/visits/${visit.id}") },
                customer.whatsappNumber?.takeIf { it.isNotBlank() }?.let {
                    TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(it))
                },
            ),
        )
    }

    private fun text(
        visit: VisitEntity,
        customer: CustomerEntity,
        listing: ListingEntity,
        formatter: TenantFormatter,
    ): String {
        val lines = mutableListOf(
            "📅 ${customer.party.fullName()} demande une visite : " + labels.titleAndPrice(listing, formatter),
            "${formatter.date(visit.requestedAt)} à ${formatter.time(visit.requestedAt)} · ${typeLabel(visit.type)}",
        )
        visit.note?.ifBlank { null }?.let { lines.add("Note : $it") }
        return lines.joinToString("\n")
    }

    // UNKNOWN never reaches a botlet: the request endpoint rejects it
    private fun typeLabel(type: VisitType): String =
        if (type == VisitType.VIDEO) "Visite vidéo" else "Sur place"
}
