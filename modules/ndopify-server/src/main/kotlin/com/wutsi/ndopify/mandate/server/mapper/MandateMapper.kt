package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.refdata.dto.Money
import org.springframework.stereotype.Service

@Service
class MandateMapper {
    fun toMandate(entity: MandateEntity): Mandate {
        return Mandate(
            id = entity.id ?: -1,
            customerId = entity.customerId,
            agentId = entity.agentId,
            status = entity.status,
            mandateType = entity.mandateType,
            listingType = entity.listingType,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            propertyTypes = entity.propertyTypes,
            furnitureType = entity.furnitureType,
            rooms = entity.rooms,
            parkings = entity.parkings,
            minArea = entity.minArea,
            maxArea = entity.maxArea,
            waterAccessType = entity.waterAccessType,
            waterTank = entity.waterTank,
            waterWell = entity.waterWell,
            electricityAccessType = entity.electricityAccessType,
            generatorSet = entity.generatorSet,
            solarPanel = entity.solarPanel,
            roadAccessType = entity.roadAccessType,
            securedFenced = entity.securedFenced,
            securityGuard = entity.securityGuard,
            videoSurveillance = entity.videoSurveillance,
            price = toPrice(entity),
            securityDeposit = entity.securityDeposit,
            advanceRent = entity.advanceRent,
            leaseTerm = entity.leaseTerm,
            noticePeriod = entity.noticePeriod,
            preferredContactPeriod = entity.preferredContactPeriod,
            comments = entity.comments,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toMandateSummary(entity: MandateEntity): MandateSummary {
        return MandateSummary(
            id = entity.id ?: -1,
            customerId = entity.customerId,
            agentId = entity.agentId,
            status = entity.status,
            mandateType = entity.mandateType,
            listingType = entity.listingType,
            propertyTypes = entity.propertyTypes,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            rooms = entity.rooms,
            price = toPrice(entity),
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    private fun toPrice(entity: MandateEntity): Money {
        return Money(
            amount = entity.price,
            currency = entity.currencyCode,
            periodicity = entity.pricePeriodicityType,
        )
    }
}
