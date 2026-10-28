package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.contract.dto.ContractSummary
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
            contractId = entity.contractId,
            propertyCategory = entity.propertyCategory,
            status = entity.status,
            mandateType = entity.mandateType,
            listingType = entity.listingType,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            propertyType = entity.propertyType,
            furnitureType = entity.furnitureType,
            rooms = entity.rooms,
            parkings = entity.parkings,
            area = entity.area,
            waterMeter = entity.waterMeter,
            dedicatedWaterMeter = entity.dedicatedWaterMeter,
            waterTank = entity.waterTank,
            waterWell = entity.waterWell,
            electricityMeter = entity.electricityMeter,
            dedicatedElectricityMeter = entity.dedicatedElectricityMeter,
            generatorSet = entity.generatorSet,
            solarPanel = entity.solarPanel,
            roadPaved = entity.roadPaved,
            trackAccessibleByCar = entity.trackAccessibleByCar,
            trackInaccessibleByCar = entity.trackInaccessibleByCar,
            securedFenced = entity.securedFenced,
            securityGuard = entity.securityGuard,
            videoSurveillance = entity.videoSurveillance,
            price = toPrice(entity),
            securityDeposit = entity.securityDeposit,
            advanceRent = entity.advanceRent,
            leaseTerm = entity.leaseTerm,
            noticePeriod = entity.noticePeriod,
            movingPeriod = entity.movingPeriod,
            availableFrom = entity.availableFrom,
            preferredContactPeriod = entity.preferredContactPeriod,
            comments = entity.comments,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            approvedAt = entity.approvedAt,
            closedAt = entity.closedAt,
            age = entity.age,
            finalPrice = entity.finalPrice,
            cancellationCode = entity.cancellationCode,
            cancellationReason = entity.cancellationReason,
            version = entity.version ?: 0,
        )
    }

    /** [contract] is the mandate's, loaded with the others of the search. */
    fun toMandateSummary(entity: MandateEntity, contract: ContractSummary?): MandateSummary {
        return MandateSummary(
            id = entity.id ?: -1,
            customerId = entity.customerId,
            agentId = entity.agentId,
            status = entity.status,
            mandateType = entity.mandateType,
            listingType = entity.listingType,
            propertyCategory = entity.propertyCategory,
            propertyType = entity.propertyType,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            furnitureType = entity.furnitureType,
            rooms = entity.rooms,
            parkings = entity.parkings,
            area = entity.area,
            waterMeter = entity.waterMeter,
            dedicatedWaterMeter = entity.dedicatedWaterMeter,
            waterTank = entity.waterTank,
            waterWell = entity.waterWell,
            electricityMeter = entity.electricityMeter,
            dedicatedElectricityMeter = entity.dedicatedElectricityMeter,
            generatorSet = entity.generatorSet,
            solarPanel = entity.solarPanel,
            roadPaved = entity.roadPaved,
            trackAccessibleByCar = entity.trackAccessibleByCar,
            trackInaccessibleByCar = entity.trackInaccessibleByCar,
            securedFenced = entity.securedFenced,
            securityGuard = entity.securityGuard,
            videoSurveillance = entity.videoSurveillance,
            price = toPrice(entity),
            movingPeriod = entity.movingPeriod,
            availableFrom = entity.availableFrom,
            contract = contract,
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
