package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.refdata.dto.Location
import java.time.ZoneId

/** Builds the cards of the Espace agent's *Mes mandats* (see [MandateCardView]), worded as the prototype's index.html. */
object MandateCards {
    private const val FACTS = 3

    /** [contract] is the mandate's, null when it has none yet or it can't be loaded (no date in the next step). */
    fun of(mandate: Mandate, contract: ContractSummary?, locations: Map<Long, Location>, now: Long, zone: ZoneId): MandateCardView {
        val facts = MandateLabels.criteria(mandate, locations).take(FACTS)
        return MandateCardView(
            id = mandate.id,
            title = MandateLabels.title(mandate),
            tag = MandateRules.tag(mandate.status),
            // The card names the neighbourhoods only: the city is the agent's.
            facts = facts.mapIndexed { i, (term, value) -> if (i == 0) term to value.substringBefore(" — ") else term to value },
            nextStep = MandateRules.nextStep(mandate, contract, now, zone),
        )
    }
}
