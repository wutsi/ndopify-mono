package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateSummary

/**
 * The Espace agent's cards are built from the search's summaries (one call for all of them), with the labels of the
 * mandate page, which take a [Mandate]. A summary has all a card shows; what it lacks (terms, comments, version) is
 * left to its default, so a card must never show it.
 */
object MandateSummaries {
    fun toMandate(summary: MandateSummary): Mandate =
        Mandate(
            id = summary.id,
            customerId = summary.customerId,
            agentId = summary.agentId,
            contractId = summary.contract?.id,
            propertyCategory = summary.propertyCategory,
            status = summary.status,
            mandateType = summary.mandateType,
            listingType = summary.listingType,
            cityId = summary.cityId,
            neighborhoodIds = summary.neighborhoodIds,
            propertyType = summary.propertyType,
            furnitureType = summary.furnitureType,
            rooms = summary.rooms,
            parkings = summary.parkings,
            area = summary.area,
            waterMeter = summary.waterMeter,
            dedicatedWaterMeter = summary.dedicatedWaterMeter,
            waterTank = summary.waterTank,
            waterWell = summary.waterWell,
            electricityMeter = summary.electricityMeter,
            dedicatedElectricityMeter = summary.dedicatedElectricityMeter,
            generatorSet = summary.generatorSet,
            solarPanel = summary.solarPanel,
            roadPaved = summary.roadPaved,
            trackAccessibleByCar = summary.trackAccessibleByCar,
            trackInaccessibleByCar = summary.trackInaccessibleByCar,
            securedFenced = summary.securedFenced,
            securityGuard = summary.securityGuard,
            videoSurveillance = summary.videoSurveillance,
            price = summary.price,
            movingPeriod = summary.movingPeriod,
            availableFrom = summary.availableFrom,
            createdAt = summary.createdAt,
            modifiedAt = summary.modifiedAt,
        )
}
