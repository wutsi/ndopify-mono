package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.listing.ListingStatsView
import com.wutsi.ndopify.npc.ui.listing.ListingViews
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantFormatter

/** Builds the cards of the Espace agent's *Mes mandats* (see [MandateCardView]), worded as the prototype's index.html. */
object MandateCards {
    /** A triage page: five cards on the home page, the rest folded behind "Voir les N autres mandats". */
    const val PAGE_SIZE = 5

    private const val FACTS = 3

    // What the agent must act on first: Assigné (to send), En approbation (waiting for the client), then Actif.
    private val STATUS_RANK = mapOf(
        MandateStatus.ASSIGNED to 0,
        MandateStatus.PENDING_APPROVAL to 1,
        MandateStatus.APPROVED to 2,
    )

    /** The home page's triage order: by status rank, newest first within each rank. */
    fun order(mandates: List<Mandate>): List<Mandate> =
        mandates.sortedWith(
            compareBy<Mandate> { STATUS_RANK[it.status] ?: STATUS_RANK.size }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )

    /**
     * [listings] are the agent's by mandate id, null when they can't be loaded: a landlord's signed mandate then has
     * no listing line. [mandateStats] are the approved searches' by mandate id, null when they can't be loaded: no
     * activity line. [stats] are, by listing id, a published listing's activity, null when they can't be loaded: no
     * activity line. [customers] are the mandates' by id, null when they can't be loaded: the card then titles with
     * the party and type instead of the client's name.
     */
    fun of(
        mandate: Mandate,
        listings: Map<Long, ListingSummary>?,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
        mandateStats: Map<Long, MandateStats>? = null,
        stats: Map<Long, ListingStats>? = null,
        customers: Map<Long, CustomerSummary>? = null,
    ): MandateCardView {
        val facts = MandateLabels.criteria(mandate, locations, formatter).take(FACTS)
        val city = locations[mandate.cityId]?.name
        val listing = listings?.get(mandate.id)
        val listingStats = listing?.takeIf { it.status == ListingStatus.PUBLISHED && MandateRules.listed(mandate) }
            ?.let { stats?.get(it.id) }
            ?.let { ListingStatsView.of(it) }
        val searchStats = if (MatchRules.matched(mandate)) {
            SearchStatsView.of(mandate, mandateStats?.get(mandate.id))
        } else {
            null
        }
        return MandateCardView(
            id = mandate.id,
            title = MandateLabels.title(mandate),
            clientName = clientName(mandate, customers),
            owner = mandate.mandateType == MandateType.LISTING,
            tag = MandateRules.tag(mandate.status),
            facts = facts.mapIndexed { i, (term, value) ->
                when (i) {
                    0 -> term to sector(mandate, value, city)
                    1 -> term to MandateLabels.price(mandate.price, formatter, compact = true) // "250K FCFA / mois", short on a card
                    else -> term to value
                }
            },
            listing = listings?.let { ListingViews.line(mandate, listing, formatter) },
            stats = listingStats?.label,
            searchStats = searchStats,
        )
    }

    /** The neighbourhoods only (the city is the agent's), or the city's name for a mandate on the whole city. */
    private fun sector(mandate: Mandate, value: String, city: String?): String =
        if (mandate.neighborhoodIds.isEmpty() && city != null) city else value.substringBefore(" — ")

    /** "Sandrine Eboa"; null without a customer or a name (the card's title then keeps the party and type). */
    private fun clientName(mandate: Mandate, customers: Map<Long, CustomerSummary>?): String? =
        customers?.get(mandate.customerId)?.party?.let { party ->
            listOf(party.firstName, party.lastName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }?.ifEmpty { null }
}
