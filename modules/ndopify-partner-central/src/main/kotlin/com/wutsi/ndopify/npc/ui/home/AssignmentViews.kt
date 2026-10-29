package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location

/** Builds the offers' cards of the Espace agent (see [AssignmentView]), worded as the prototype's index.html. */
object AssignmentViews {
    private const val MINUTE = 60_000L

    /** Still open: PENDING and before its deadline (ndopify-server's expire job may not have run yet). */
    fun isOpen(assignment: AssignmentSummary, now: Long): Boolean =
        assignment.status == AssignmentStatus.PENDING && assignment.expiresAt.time > now

    fun of(assignment: AssignmentSummary, mandate: Mandate, locations: Map<Long, Location>, now: Long, formatter: TenantFormatter): AssignmentView {
        val owner = mandate.mandateType == MandateType.LISTING
        val neighborhoods = mandate.neighborhoodIds.mapNotNull { locations[it] }
        return AssignmentView(
            id = assignment.id,
            // The card shows neither the property type (it is in the details) nor the city (the agent works in one).
            title = MandateLabels.mandateType(mandate.mandateType),
            sectorTerm = if (mandate.neighborhoodIds.size > 1) "Secteurs" else "Secteur",
            sector = sector(mandate, neighborhoods),
            priceTerm = if (owner) "Loyer" else "Budget",
            price = MandateLabels.price(mandate.price, formatter, compact = true),
            movingTerm = if (owner) "Disponibilité" else "Emménagement",
            moving = MandateLabels.MOVE_IN_LABELS[mandate.movingPeriod],
            expiresAt = assignment.expiresAt.time,
            remaining = remaining(assignment.expiresAt.time - now),
            details = details(mandate, owner),
        )
    }

    /** The neighbourhoods, "Tous les quartiers" without any; null without their names (ndopify-server unreachable). */
    private fun sector(mandate: Mandate, neighborhoods: List<Location>): String? = when {
        mandate.neighborhoodIds.isEmpty() -> "Tous les quartiers"
        neighborhoods.isEmpty() -> null
        else -> neighborhoods.joinToString(", ") { it.name }
    }

    /** "1 h 05 min", "52 min", "moins d'1 min". */
    fun remaining(ms: Long): String {
        val minutes = ms / MINUTE
        return when {
            minutes < 1 -> "moins d'1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
        }
    }

    /** The questions the find-property wizard asked for this type of property, then the criteria by group. */
    private fun details(mandate: Mandate, owner: Boolean): List<Pair<String, String>> {
        val type = mandate.propertyType?.name
        return listOfNotNull(
            MandateLabels.category(mandate.propertyCategory)?.let { "Catégorie" to it },
            "Type de propriété" to MandateLabels.propertyType(mandate.propertyType),
            ("Meublé" to MandateLabels.furniture(mandate.furnitureType)).takeIf { FindPropertyRules.needsFurniture(type) },
            mandate.rooms?.let { "Chambres" to it }?.takeIf { FindPropertyRules.needsRooms(mandate.propertyCategory, type) },
            mandate.parkings?.let { "Parking" to it }?.takeIf { FindPropertyRules.asksParkings(type) },
            mandate.area?.let { (if (owner) "Surface" else "Surface souhaitée") to MandateLabels.formatNumber(it) + " m²" },
        ) + MandateLabels.amenityGroups(mandate)
    }
}
