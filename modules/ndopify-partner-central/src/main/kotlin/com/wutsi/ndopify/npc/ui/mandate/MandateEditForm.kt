package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.findproperty.PendingMandate

/**
 * The edit page's form: the whole search but its city (the mandate would leave the agent's area). Answers are kept
 * as posted, so the page shows them again with its errors. "Indifférent" is [FindPropertyRules.ANY], a client's
 * answer only; amenities are [PendingMandate.AMENITIES] names.
 */
data class MandateEditForm(
    val neighborhoodIds: List<Long> = emptyList(),
    val propertyCategory: PropertyCategory? = null,
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val area: String? = null,
    val amenities: List<String> = emptyList(),
    val priceAmount: String? = null,
    val movingPeriod: MovingPeriod? = null,
    val comments: String? = null,
    // The version of the mandate the page was loaded with: ndopify-server refuses the save if it changed since
    val version: Long? = null,
) {
    companion object {
        /** The mandate's answers; for a client, what it left open (null) is "Indifférent". */
        fun of(mandate: Mandate): MandateEditForm {
            val any = if (mandate.mandateType == MandateType.LISTING) null else FindPropertyRules.ANY
            return MandateEditForm(
                neighborhoodIds = mandate.neighborhoodIds,
                propertyCategory = mandate.propertyCategory,
                propertyType = mandate.propertyType?.name ?: any,
                rooms = mandate.rooms,
                furnitureType = mandate.furnitureType?.name ?: any,
                parkings = mandate.parkings ?: any,
                area = mandate.area?.toString(),
                amenities = Amenity.of(mandate).map { it.key },
                priceAmount = MandateLabels.formatNumber(mandate.price.amount),
                movingPeriod = mandate.movingPeriod,
                comments = mandate.comments,
                version = mandate.version,
            )
        }
    }

    fun has(amenity: String): Boolean = amenity in amenities
}
