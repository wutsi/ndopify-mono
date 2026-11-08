package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.ui.listing.ListingPages
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the [VisitPage] of a visit from ndopify-server's DTOs; the listing's part is [ListingPages]'s, so never its
 * `agentRemarks`.
 *
 * The exact address and its map are never shown, even when the server sends them: only the listing's city,
 * neighbourhood and landmark, for both visit types. The code comes only from the server, to its customer, and is never
 * invented
 * here. The changed-slot line appears only for a CONFIRMED visit whose scheduled time differs from the requested one.
 * A visit can be cancelled while REQUESTED or CONFIRMED only.
 */
object VisitPages {
    private const val NO_AGENT_NAME = "votre agent"

    /** Each status's badge label and tone, as `visite.js` sets them; UNKNOWN falls back to "Visite"/"neutral". */
    private val STATUS: Map<VisitStatus, Pair<String, String>> = mapOf(
        VisitStatus.REQUESTED to ("Demandée" to "info"),
        VisitStatus.CONFIRMED to ("Confirmée" to "success"),
        VisitStatus.CHECKED_IN to ("En cours" to "info"),
        VisitStatus.COMPLETED to ("Terminée" to "success"),
        VisitStatus.CANCELLED to ("Annulée" to "neutral"),
        VisitStatus.EXPIRED to ("Expirée" to "neutral"),
        VisitStatus.NO_SHOW to ("Non effectuée" to "neutral"),
    )

    private val TYPES: Map<VisitType, String> = mapOf(
        VisitType.ON_SITE to "Sur place",
        VisitType.VIDEO to "Visite vidéo",
    )

    fun of(
        visit: Visit,
        listing: Listing?,
        images: List<ListingImage>,
        locations: List<Location>,
        agent: Agent?,
        customer: Customer?,
        tenant: Tenant,
        formatter: TenantFormatter,
        today: LocalDate,
        cancel: String?,
    ): VisitPage {
        val (statusLabel, statusTone) = STATUS[visit.status] ?: ("Visite" to "neutral")
        val typeLabel = TYPES[visit.type] ?: "Visite"
        // The code is sent to the customer from CONFIRMED and kept after. The exact address and its map never reach
        // the page: the listing's city, neighbourhood and landmark do.
        val fromConfirmed = visit.status != VisitStatus.REQUESTED && visit.status != VisitStatus.UNKNOWN
        val code = if (fromConfirmed) visit.code?.ifBlank { null } else null

        return VisitPage(
            id = visit.id,
            status = visit.status,
            statusLabel = statusLabel,
            statusTone = statusTone,
            type = visit.type,
            typeLabel = typeLabel,
            requestedSlot = VisitSlots.label(typeLabel, visit.requestedAt, tenant, formatter),
            scheduledSlot = visit.scheduledAt?.let { VisitSlots.label(typeLabel, it, tenant, formatter) },
            changedSlot = changedSlot(visit, tenant, formatter),
            note = visit.note?.ifBlank { null },
            code = code,
            videoNumber = customer?.whatsappNumber?.ifBlank { null },
            checkedInAt = visit.checkedInAt?.let { formatter.time(it) },
            cancelledAt = visit.cancelledAt?.let { "${formatter.date(it)} à ${formatter.time(it)}" },
            cancelledBy = visit.cancelledBy,
            cancellationReason = visit.cancellationReason?.ifBlank { null },
            noShowParty = visit.noShowParty,
            noShowReason = visit.noShowReason,
            canCancel = visit.status == VisitStatus.REQUESTED || visit.status == VisitStatus.CONFIRMED,
            flash = flash(visit, cancel),
            listing = listing?.let { ListingPages.of(it, images, locations, tenant, formatter, today) },
            agent = agent?.let { agent(it) },
            matchUrl = "/match/${visit.matchId}",
        )
    }

    /**
     * "Vous aviez demandé samedi 7 novembre 2026 à 10:30 : l'agent a proposé un autre créneau." — only for a CONFIRMED
     * visit whose scheduled time differs from the requested one; the date is read in the tenant's time zone.
     */
    private fun changedSlot(visit: Visit, tenant: Tenant, formatter: TenantFormatter): String? {
        if (visit.status != VisitStatus.CONFIRMED) {
            return null
        }
        val scheduledAt = visit.scheduledAt ?: return null
        if (scheduledAt == visit.requestedAt) {
            return null
        }
        val localDate = visit.requestedAt.toInstant().atZone(ZoneId.of(tenant.timeZone)).toLocalDate()
        return "Vous aviez demandé ${formatter.date(localDate, "EEEE d MMMM yyyy")} à ${formatter.time(visit.requestedAt)} : l'agent a proposé un autre créneau."
    }

    /** The outcome of the cancellation just posted; "done" only when the visit really is CANCELLED by its customer. */
    private fun flash(visit: Visit, cancel: String?): String? = when {
        cancel == "conflict" -> "Cette visite ne peut plus être annulée."
        cancel == "done" && visit.status == VisitStatus.CANCELLED && visit.cancelledBy == VisitParty.CUSTOMER ->
            "Votre visite est annulée. L'agent est prévenu."
        else -> null
    }

    private fun agent(agent: Agent): VisitAgent {
        val name = "${agent.party.firstName} ${agent.party.lastName}".trim().ifEmpty { NO_AGENT_NAME }
        val initials = listOf(agent.party.firstName, agent.party.lastName)
            .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
        return VisitAgent(
            name = name,
            initials = initials,
            photoUrl = agent.party.photoUrl?.ifBlank { null },
            whatsappUrl = agent.whatsappNumber?.filter { it.isDigit() }?.ifEmpty { null }?.let { "https://wa.me/$it" },
        )
    }
}
