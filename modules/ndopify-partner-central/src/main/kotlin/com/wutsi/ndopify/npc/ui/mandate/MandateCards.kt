package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.listing.ListingStatsView
import com.wutsi.ndopify.npc.ui.listing.ListingViews
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantFormatter

/** Builds the cards of the Espace agent's *Mes mandats* (see [MandateCardView]), worded as the prototype's index.html. */
object MandateCards {
    private const val FACTS = 3

    /**
     * [listings] are the agent's by mandate id, null when they can't be loaded: a landlord's signed mandate then has
     * no listing line. [mandateStats] are the approved searches' by mandate id, null when they can't be loaded: no
     * activity line. [stats] are, by listing id, a published listing's activity, null when they can't be loaded: no
     * activity line.
     */
    fun of(
        mandate: Mandate,
        listings: Map<Long, ListingSummary>?,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
        mandateStats: Map<Long, MandateStats>? = null,
        stats: Map<Long, ListingStats>? = null,
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
}
