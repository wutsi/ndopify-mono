package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneId

/**
 * Reminds the agent over Telegram of a confirmed visit 24 h ahead: the mirror of `VisitReminderMailet`'s agent copy,
 * with the type, the date and time in the tenant's `timeZone`, the property's city and neighbourhood (never its exact
 * address), a WhatsApp button to contact the customer, the WhatsApp call note of a `VIDEO` visit and a `Voir la visite`
 * button.
 * It never carries the visit's 4-digit code: only the customer's copy does.
 *
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitReminderBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val client: TelegramClient,
    private val clock: Clock,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val url = tenant.partnerCentralUrl?.removeSuffix("/")
        client.sendMessage(
            chatId,
            text(visit, customer, listing, tenant, formatter),
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
        tenant: TenantEntity,
        formatter: TenantFormatter,
    ): String {
        val lines = mutableListOf<String>()
        lines.add("Rappel de la visite avec ${customer.party.fullName()}")
        lines.add("")
        lines.add(
            "La visite du bien ${listingTitle(listing)} avec ${customer.party.fullName()} " +
                "a lieu ${dayWord(visit, tenant)}, ${formatter.date(visit.scheduledAt!!)} à " +
                "${formatter.time(visit.scheduledAt)}.",
        )
        lines.add("")
        lines.add("Type: ${typeLabel(visit.type)}")
        placeOf(listing)?.let { lines.add("Lieu: $it") }
        if (visit.type == VisitType.VIDEO) {
            lines.add("")
            lines.add(
                "Depuis le bien, appelez ${customer.party.fullName()} en vidéo sur WhatsApp. " +
                    "Le client vous lira son code de visite pendant l'appel.",
            )
        }
        return lines.joinToString("\n")
    }

    /** The reminder's day, in the tenant's calendar: the job only reminds within the next 24 h. */
    private fun dayWord(visit: VisitEntity, tenant: TenantEntity): String {
        val zone = ZoneId.of(tenant.timeZone)
        val today = clock.instant().atZone(zone).toLocalDate()
        val day = visit.scheduledAt!!.toInstant().atZone(zone).toLocalDate()
        return if (day == today) "aujourd'hui" else "demain"
    }

    private fun listingTitle(listing: ListingEntity): String =
        listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"

    /** "Bonapriso, Douala": the neighbourhood and the city, never the exact address. */
    private fun placeOf(listing: ListingEntity): String? =
        listOfNotNull(
            listing.neighborhoodId?.let { locationService.findByIdOrNull(it)?.name?.ifBlank { null } },
            locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null },
        ).joinToString(", ").ifBlank { null }

    // UNKNOWN never reaches a botlet: the schedule endpoint rejects it
    private fun typeLabel(type: VisitType): String =
        if (type == VisitType.VIDEO) "Visite vidéo" else "Sur place"
}
