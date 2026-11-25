package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneId
import java.util.Date

/**
 * The three lines the visit Telegram messages share: `<object> : <date/time>`, then the property line
 * `<title> - <price> - Sur place|Vidéo`, then `Agent : <organizer>` on a co-brokerage visit only. The event's own lines
 * (the note, the reason, the feedback) follow in the botlet.
 *
 * A visit is co-brokerage when its agent is not the listing's agent: the visit's agent is the organizer A, the
 * listing's agent is the co-agent B. The WhatsApp button then opens B on A's messages, and A on B's — never a third
 * party's number.
 */
@Service
class VisitTelegramLabels(private val labels: ListingTelegramLabels) {
    fun body(
        label: String,
        whenLabel: String,
        listing: ListingEntity,
        visit: VisitEntity,
        organizer: AgentEntity?,
        formatter: TenantFormatter,
    ): String = listOfNotNull(
        "$label : $whenLabel",
        property(visit, listing, formatter),
        organizer?.let { "Agent : ${it.party.fullName()}" },
    ).joinToString("\n")

    fun property(visit: VisitEntity, listing: ListingEntity, formatter: TenantFormatter): String =
        "${labels.titleAndPrice(listing, formatter)} - ${typeLabel(visit.type)}"

    /** B, the listing's agent, when the visit is a co-brokerage one (its agent is another agent); null otherwise. */
    fun organizer(visit: VisitEntity, listing: ListingEntity): AgentEntity? =
        listing.agent.takeIf { visit.agentId != it.id }

    /**
     * The number the WhatsApp button opens: the customer's, or the listing agent's on a co-brokerage visit. Never a
     * fallback from one to the other — a party without a number gets no button.
     */
    fun whatsappNumber(visit: VisitEntity, listing: ListingEntity, customer: CustomerEntity): String? {
        val organizer = organizer(visit, listing)
        val number = if (organizer == null) customer.whatsappNumber else organizer.whatsappNumber
        return number?.trim()?.ifBlank { null }
    }

    // Ex: "26/11/2026 à 10:30", in the tenant's date and time formats
    fun slot(date: Date, formatter: TenantFormatter): String = "${formatter.date(date)} à ${formatter.time(date)}"

    /** The reminder's day, in the tenant's calendar: the job only reminds within the next 24 h. */
    fun dayWord(visit: VisitEntity, tenant: TenantEntity, clock: Clock): String {
        val zone = ZoneId.of(tenant.timeZone)
        val today = clock.instant().atZone(zone).toLocalDate()
        val day = visit.scheduledAt!!.toInstant().atZone(zone).toLocalDate()
        return if (day == today) "aujourd'hui" else "demain"
    }

    // "Annulée par le client" / "Annulée par l'agent": who cancelled, without naming the customer
    fun cancelledBy(party: VisitParty?): String = when (party) {
        VisitParty.CUSTOMER -> "Annulée par le client"
        VisitParty.AGENT -> "Annulée par l'agent"
        else -> "Annulée"
    }

    // UNKNOWN never reaches a botlet: the request and schedule endpoints reject it
    fun typeLabel(type: VisitType): String = if (type == VisitType.VIDEO) "Vidéo" else "Sur place"

    /**
     * The visit messages' buttons, in their one order: `Voir la visite` to the visit's page on Partner Central, then
     * `Contacter sur WhatsApp`. Each is left out when the tenant has no `partnerCentralUrl` or the party no number.
     */
    fun buttons(
        visit: VisitEntity,
        listing: ListingEntity,
        customer: CustomerEntity,
        tenant: TenantEntity,
    ): List<TelegramButton> = listOfNotNull(
        tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
            TelegramButton("Voir la visite", url = "$url/visits/${visit.id}")
        },
        whatsappNumber(visit, listing, customer)?.let { number ->
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(number))
        },
    )
}
