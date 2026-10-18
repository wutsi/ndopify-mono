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
) {
    companion object {
        /** The mandate's answers; for a client, what it left open (null) is "Indifférent". */
        fun of(mandate: Mandate): MandateEditForm {
            val any = if (mandate.mandateType == MandateType.LISTING) null else FindPropertyRules.ANY
            val checked = mapOf(
                "waterMeter" to mandate.waterMeter,
                "dedicatedWaterMeter" to mandate.dedicatedWaterMeter,
                "waterTank" to mandate.waterTank,
                "waterWell" to mandate.waterWell,
                "electricityMeter" to mandate.electricityMeter,
                "dedicatedElectricityMeter" to mandate.dedicatedElectricityMeter,
                "generatorSet" to mandate.generatorSet,
                "solarPanel" to mandate.solarPanel,
                "roadPaved" to mandate.roadPaved,
                "trackAccessibleByCar" to mandate.trackAccessibleByCar,
                "trackInaccessibleByCar" to mandate.trackInaccessibleByCar,
                "securedFenced" to mandate.securedFenced,
                "securityGuard" to mandate.securityGuard,
                "videoSurveillance" to mandate.videoSurveillance,
            )
            return MandateEditForm(
                neighborhoodIds = mandate.neighborhoodIds,
                propertyCategory = mandate.propertyCategory,
                propertyType = mandate.propertyType?.name ?: any,
                rooms = mandate.rooms,
                furnitureType = mandate.furnitureType?.name ?: any,
                parkings = mandate.parkings ?: any,
                area = mandate.area?.toString(),
                amenities = PendingMandate.AMENITIES.filter { checked[it] == true },
                priceAmount = MandateLabels.formatNumber(mandate.price.amount),
                movingPeriod = mandate.movingPeriod,
                comments = mandate.comments,
            )
        }
    }

    fun has(amenity: String): Boolean = amenity in amenities
}
