package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import org.springframework.stereotype.Service

/**
 * Tells the agent over Telegram that its customer cancelled a scheduled visit: the mirror of
 * [VisitCancelledMailet.sendToAgent], so that the agent frees the slot instead of waiting on it.
 *
 * The message carries the slot in the tenant's time zone, the listing's title, city and neighbourhood, the customer's
 * name and the reason, with a `Voir la visite` button to the visit's page on Partner Central. It never carries the
 * customer's check-in code, exact address nor public-portal token: those are the customer's alone.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitCancelledBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val sender: ListingTelegramSender,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        sender.send(
            chatId,
            listing,
            text(visit, customer, listing, formatter),
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
                listOf(TelegramButton("Voir la visite", url = "$url/visits/${visit.id}"))
            } ?: emptyList(),
        )
    }

    private fun text(
        visit: VisitEntity,
        customer: CustomerEntity,
        listing: ListingEntity,
        formatter: TenantFormatter,
    ): String {
        val lines = mutableListOf<String>()
        lines.add(VisitCancelledMailet.LABEL_BY_CUSTOMER.removeSuffix("."))
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }?.let { lines.add("Ville: $it") }
        listing.neighborhoodId
            ?.let { locationService.findByIdOrNull(it)?.name }
            ?.ifBlank { null }
            ?.let { lines.add("Quartier: $it") }
        visit.scheduledAt?.let { lines.add("Date: ${formatter.date(it)} à ${formatter.time(it)}") }
        lines.add("Client: ${customer.party.fullName()}")
        visit.cancellationReason?.ifBlank { null }?.let { lines.add("Motif: $it") }
        return lines.joinToString("\n")
    }
}
