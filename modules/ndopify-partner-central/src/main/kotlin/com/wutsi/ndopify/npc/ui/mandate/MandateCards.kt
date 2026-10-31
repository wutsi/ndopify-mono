package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.npc.ui.listing.ListingViews
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantFormatter

/** Builds the cards of the Espace agent's *Mes mandats* (see [MandateCardView]), worded as the prototype's index.html. */
object MandateCards {
    private const val FACTS = 3

    /**
     * [contract] is the mandate's, null when it has none yet or it can't be loaded (no date in the next step).
     * [listings] are the agent's by mandate id, null when they can't be loaded: a landlord's signed mandate then has
     * no listing line, and the next step is the mandate's.
     */
    fun of(
        mandate: Mandate,
        contract: ContractSummary?,
        listings: Map<Long, ListingSummary>?,
        locations: Map<Long, Location>,
        now: Long,
        formatter: TenantFormatter,
    ): MandateCardView {
        val facts = MandateLabels.criteria(mandate, locations, formatter).take(FACTS)
        val city = locations[mandate.cityId]?.name
        val listing = listings?.get(mandate.id)
        return MandateCardView(
            id = mandate.id,
            title = MandateLabels.title(mandate),
            tag = MandateRules.tag(mandate.status),
            facts = facts.mapIndexed { i, (term, value) ->
                when (i) {
                    0 -> term to sector(mandate, value, city)
                    1 -> term to MandateLabels.price(mandate.price, formatter, compact = true) // "250K FCFA / mois", short on a card
                    else -> term to value
                }
            },
            listing = listings?.let { ListingViews.line(mandate, listing, formatter) },
            nextStep = listings?.takeIf { MandateRules.listed(mandate) }?.let { MandateRules.listingStep(listing) }
                ?: MandateRules.nextStep(mandate, contract, now, formatter),
        )
    }

    /** The neighbourhoods only (the city is the agent's), or the city's name for a mandate on the whole city. */
    private fun sector(mandate: Mandate, value: String, city: String?): String =
        if (mandate.neighborhoodIds.isEmpty() && city != null) city else value.substringBefore(" — ")
}
