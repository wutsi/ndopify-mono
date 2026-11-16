package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.service.OriginListing
import com.wutsi.ndopify.mandate.server.service.OriginListingResolver
import com.wutsi.ndopify.refdata.dto.Money
import org.springframework.stereotype.Service

/**
 * The listing domain's [OriginListingResolver]: copies a listing's property, criteria and budget into the
 * [CreateMandateRequest] of the search mandate it seeds. The visitor's own fields (contact, moving period, rental terms)
 * are left at their defaults: the server takes those from the visitor, never from the listing.
 */
@Service
class ListingOriginListingResolver(
    private val dao: ListingRepository,
) : OriginListingResolver {
    override fun resolve(listingId: Long): OriginListing? {
        val listing = dao.findById(listingId).orElse(null) ?: return null
        return OriginListing(
            id = listing.id!!,
            agentId = listing.agent.id,
            published = listing.status == ListingStatus.PUBLISHED,
            criteria = criteria(listing),
        )
    }

    private fun criteria(listing: ListingEntity) = CreateMandateRequest(
        propertyCategory = listing.propertyCategory,
        mandateType = MandateType.SEARCH,
        listingType = listing.listingType,
        cityId = listing.cityId,
        neighbourhoodIds = listOfNotNull(listing.neighborhoodId),
        propertyType = listing.propertyType,
        furnitureType = listing.furnitureType,
        rooms = listing.rooms?.toString(),
        parkings = listing.parkings?.toString(),
        area = listing.area,
        waterMeter = listing.waterMeter,
        dedicatedWaterMeter = listing.dedicatedWaterMeter,
        waterTank = listing.waterTank,
        waterWell = listing.waterWell,
        electricityMeter = listing.electricityMeter,
        dedicatedElectricityMeter = listing.dedicatedElectricityMeter,
        generatorSet = listing.generatorSet,
        solarPanel = listing.solarPanel,
        roadPaved = listing.roadPaved,
        trackAccessibleByCar = listing.trackAccessibleByCar,
        trackInaccessibleByCar = listing.trackInaccessibleByCar,
        securedFenced = listing.securedFenced,
        securityGuard = listing.securityGuard,
        videoSurveillance = listing.videoSurveillance,
        price = Money(
            amount = listing.price,
            currency = listing.currencyCode,
            periodicity = listing.pricePeriodicityType,
        ),
    )
}
