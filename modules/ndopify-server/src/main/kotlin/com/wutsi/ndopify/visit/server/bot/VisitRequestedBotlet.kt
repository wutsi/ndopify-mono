package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer asked to visit a listing: the mirror of `VisitRequestedMailet`, plus
 * the requested visit type — the agent must know whether to go to the property or plan a WhatsApp video call — and a
 * `Voir la visite` button to the visit's page on Partner Central, where it schedules it.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitRequestedBotlet(
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
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} souhaite visiter un bien que vous lui avez proposé.")
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        lines.add("Date souhaitée: ${formatter.date(visit.requestedAt)} à ${formatter.time(visit.requestedAt)}")
        lines.add("Type: ${typeLabel(visit.type)}")
        formatter.phone(customer.whatsappNumber)?.let { lines.add("WhatsApp: $it") }
        visit.note?.ifBlank { null }?.let { lines.add("Note: $it") }
        lines.add("")
        lines.add("Confirmez la visite avec la date et l'heure qui vous conviennent.")
        return lines.joinToString("\n")
    }

    // UNKNOWN never reaches a botlet: the request endpoint rejects it
    private fun typeLabel(type: VisitType): String =
        if (type == VisitType.VIDEO) "Visite vidéo" else "Sur place"
}
