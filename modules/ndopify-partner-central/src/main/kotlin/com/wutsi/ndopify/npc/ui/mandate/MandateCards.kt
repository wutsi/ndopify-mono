package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.refdata.dto.Location

/** Builds the cards of the Espace agent's *Mes mandats* (see [MandateCardView]), worded as the prototype's index.html. */
object MandateCards {
    private const val FACTS = 3

    /** [contract] is the mandate's, null when it has none yet or it can't be loaded (no date in the next step). */
    fun of(mandate: Mandate, contract: ContractSummary?, locations: Map<Long, Location>, now: Long, formatter: TenantFormatter): MandateCardView {
        val facts = MandateLabels.criteria(mandate, locations, formatter).take(FACTS)
        val city = locations[mandate.cityId]?.name
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
            nextStep = MandateRules.nextStep(mandate, contract, now, formatter),
        )
    }

    /** The neighbourhoods only (the city is the agent's), or the city's name for a mandate on the whole city. */
    private fun sector(mandate: Mandate, value: String, city: String?): String =
        if (mandate.neighborhoodIds.isEmpty() && city != null) city else value.substringBefore(" — ")
}
