package com.wutsi.ndopify.visit.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneId

/**
 * Reminds the agent over Telegram of a confirmed visit 24 h ahead: a `⏰` line with the property, the day in the
 * tenant's `timeZone` and the customer, then the type — and, for a `VIDEO` visit, the WhatsApp call note — with a
 * `Voir la visite` button and a WhatsApp button to contact the customer.
 *
 * It never carries the visit's 4-digit code nor the exact address: only the customer's copy has the code.
 * [send]'s recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail.
 * Telegram refusing the message throws ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then
 * falls back to the e-mail.
 */
@Service
class VisitReminderBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
    private val clock: Clock,
) {
    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val url = tenant.partnerCentralUrl?.removeSuffix("/")
        sender.send(
            chatId,
            listing,
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
        val lines = mutableListOf(
            "⏰ Rappel : " + labels.titleAndPrice(listing, formatter) +
                ", ${dayWord(visit, tenant)} à ${formatter.time(visit.scheduledAt!!)} avec ${customer.party.fullName()}",
            typeLabel(visit.type),
        )
        if (visit.type == VisitType.VIDEO) {
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

    // UNKNOWN never reaches a botlet: the schedule endpoint rejects it
    private fun typeLabel(type: VisitType): String =
        if (type == VisitType.VIDEO) "Visite vidéo" else "Sur place"
}
