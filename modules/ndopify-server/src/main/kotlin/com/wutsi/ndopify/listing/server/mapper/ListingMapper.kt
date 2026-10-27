package com.wutsi.ndopify.listing.server.mapper

import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import org.springframework.stereotype.Service

@Service
class ListingMapper {
    fun toListing(entity: ListingEntity, sections: List<ListingSectionSummary>): Listing {
        return Listing(
            id = entity.id ?: -1,
            version = entity.version ?: 0,
            mandateId = entity.mandate.id ?: -1,
            agentId = entity.agent.id ?: -1,
            propertyCategory = entity.propertyCategory,
            listingType = entity.listingType,
            status = entity.status,
            cityId = entity.cityId,
            neighborhoodId = entity.neighborhoodId,
            landmark = entity.landmark,
            accessDirections = entity.accessDirections,
            longitude = entity.longitude,
            latitude = entity.latitude,
            propertyType = entity.propertyType,
            furnitureType = entity.furnitureType,
            rooms = entity.rooms,
            bathrooms = entity.bathrooms,
            livingRooms = entity.livingRooms,
            kitchens = entity.kitchens,
            parkings = entity.parkings,
            area = entity.area,
            levels = entity.levels,
            floorLevel = entity.floorLevel,
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
            price = entity.price,
            currencyCode = entity.currencyCode,
            pricePeriodicityType = entity.pricePeriodicityType,
            securityDeposit = entity.securityDeposit,
            advanceRent = entity.advanceRent,
            leaseTerm = entity.leaseTerm,
            noticePeriod = entity.noticePeriod,
            availableFrom = entity.availableFrom,
            agentRemarks = entity.agentRemarks,
            sections = sections,
            title = entity.title,
            summary = entity.summary,
            description = entity.description,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            publishedAt = entity.publishedAt,
            closedAt = entity.closedAt,
            errorCodes = entity.errorCodes,
            errorMessage = entity.errorMessage,
            retries = entity.retries,
            verifiedAt = entity.verifiedAt,
            heroImageId = entity.heroImageId,
        )
    }

    fun toListingSummary(entity: ListingEntity): ListingSummary {
        return ListingSummary(
            id = entity.id ?: -1,
            mandateId = entity.mandate.id ?: -1,
            agentId = entity.agent.id ?: -1,
            propertyCategory = entity.propertyCategory,
            listingType = entity.listingType,
            status = entity.status,
            cityId = entity.cityId,
            neighborhoodId = entity.neighborhoodId,
            propertyType = entity.propertyType,
            furnitureType = entity.furnitureType,
            rooms = entity.rooms,
            bathrooms = entity.bathrooms,
            area = entity.area,
            price = entity.price,
            currencyCode = entity.currencyCode,
            pricePeriodicityType = entity.pricePeriodicityType,
            title = entity.title,
            summary = entity.summary,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            publishedAt = entity.publishedAt,
            closedAt = entity.closedAt,
            heroImageId = entity.heroImageId,
        )
    }

    fun toListingImage(entity: ListingImageEntity): ListingImage {
        return ListingImage(
            id = entity.id ?: -1,
            listingId = entity.listingId,
            url = entity.url,
            mimeType = entity.mimeType,
            status = entity.status,
            errorMessage = entity.errorMessage,
            title = entity.title,
            description = entity.description,
            width = entity.width,
            height = entity.height,
            quality = entity.quality,
        )
    }
}
