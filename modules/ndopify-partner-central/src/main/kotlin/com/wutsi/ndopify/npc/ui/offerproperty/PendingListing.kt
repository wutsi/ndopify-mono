package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.io.Serializable
import java.time.LocalDate

/**
 * The answers of a "Proposer un bien" offer in progress, kept in the HTTP session until the review submits them.
 * Answers are only stored once their step validated them, so the session is the trusted copy. Follow-up answers
 * that no longer apply (the rooms of a studio, …) may linger here after the visitor changed their mind;
 * [toCreateMandateRequest] never sends them. The availability date is only valid for a while, so completeness
 * depends on the day.
 */
data class PendingListing(
    val listingType: ListingType? = null,
    val propertyCategory: PropertyCategory? = null,
    val cityId: Long? = null,
    val neighborhoodId: Long? = null,
    val priceAmount: Long? = null,
    val advanceRent: Int? = null,
    val securityDeposit: Int? = null,
    val availableFrom: LocalDate? = null,
    val propertyType: String? = null,
    val furnitureType: String? = null,
    val rooms: String? = null,
    val parkings: String? = null,
    val area: Int? = null,
    val ownershipDocuments: List<TitleDocumentType> = emptyList(),
    val roadAccess: String? = null,
    val amenities: Set<String> = emptySet(),
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val preferredContactPeriod: ContactPeriodType? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-listing"
    }

    fun has(amenity: String): Boolean = amenities.contains(amenity)

    /** The wizard's project: a rental unless the owner chose to sell. */
    fun isSale(): Boolean = FindPropertyRules.isSale(listingType)

    fun isComplete(step: String, today: LocalDate): Boolean = when (step) {
        OfferPropertyRules.STEP_PROPERTY ->
            OfferPropertyRules.listingType(listingType) == null &&
                OfferPropertyRules.propertyCategory(propertyCategory) == null

        OfferPropertyRules.STEP_ADDRESS ->
            OfferPropertyRules.cityId(cityId) == null && OfferPropertyRules.neighborhoodId(neighborhoodId) == null

        OfferPropertyRules.STEP_TERMS ->
            OfferPropertyRules.isPriceAmount(priceAmount, listingType) &&
                (isSale() || OfferPropertyRules.advanceRent(advanceRent) == null) &&
                (isSale() || OfferPropertyRules.securityDeposit(securityDeposit) == null) &&
                OfferPropertyRules.availableFrom(availableFrom, today) == null

        OfferPropertyRules.STEP_FEATURES -> listOf(
            OfferPropertyRules.propertyType(propertyCategory, propertyType, listingType),
            OfferPropertyRules.furnitureType(furnitureType).takeIf { needsFurniture() },
            OfferPropertyRules.rooms(rooms).takeIf { needsRooms() },
            OfferPropertyRules.parkings(parkings).takeIf { needsParking() },
            OfferPropertyRules.area(area, OfferPropertyRules.needsArea(propertyCategory, type(), listingType))
                .takeIf { OfferPropertyRules.needsArea(propertyCategory, type(), listingType) },
        ).all { it == null }

        OfferPropertyRules.STEP_AMENITIES -> OfferPropertyRules.roadAccess(roadAccess) == null

        OfferPropertyRules.STEP_CONTACT -> listOf(
            JoinRules.firstName(firstName),
            JoinRules.lastName(lastName),
            JoinRules.email(email),
            JoinRules.whatsappNumber(whatsappNumber),
            OfferPropertyRules.preferredContactPeriod(preferredContactPeriod),
        ).all { it == null }

        else -> false
    }

    /** The first step that still needs answers, or the review step when they're all done. */
    fun firstIncompleteStep(today: LocalDate): String =
        OfferPropertyRules.STEPS.dropLast(1).firstOrNull { !isComplete(it, today) }
            ?: OfferPropertyRules.STEP_REVIEW

    private fun type() = FindPropertyRules.parsePropertyType(propertyType)

    private fun needsFurniture() = OfferPropertyRules.needsFurniture(propertyCategory, type(), listingType)

    private fun needsRooms() = OfferPropertyRules.needsRooms(propertyCategory, type(), listingType)

    private fun needsParking() = OfferPropertyRules.needsParking(propertyCategory, type(), listingType)

    /**
     * The answers as a LISTING mandate, for the chosen project. Questions that don't apply to the chosen type are sent
     * as null. A sale is a one-off price with no advance, deposit nor furnishing; only a land sale sends the documents
     * of ownership the seller declares. The price per m² is not an answer: it is never sent.
     */
    fun toCreateMandateRequest(currency: String): CreateMandateRequest =
        CreateMandateRequest(
            mandateType = MandateType.LISTING,
            listingType = listingType ?: ListingType.RENTAL,
            propertyCategory = propertyCategory ?: PropertyCategory.UNKNOWN,
            cityId = cityId ?: -1,
            neighbourhoodIds = listOfNotNull(neighborhoodId),
            propertyType = type(),
            furnitureType = FurnitureType.entries.firstOrNull { it.name == furnitureType }?.takeIf { needsFurniture() },
            rooms = rooms?.takeIf { needsRooms() },
            parkings = parkings?.takeIf { needsParking() },
            area = area?.takeIf { OfferPropertyRules.needsArea(propertyCategory, type(), listingType) },
            ownershipDocuments = ownershipDocuments.filter { isSale() && type() == PropertyType.LAND }.distinct(),
            waterMeter = has("waterMeter"),
            dedicatedWaterMeter = has("dedicatedWaterMeter"),
            waterTank = has("waterTank"),
            waterWell = has("waterWell"),
            electricityMeter = has("electricityMeter"),
            dedicatedElectricityMeter = has("dedicatedElectricityMeter"),
            generatorSet = has("generatorSet"),
            solarPanel = has("solarPanel"),
            roadPaved = roadAccess == "roadPaved",
            trackAccessibleByCar = roadAccess == "trackAccessibleByCar",
            trackInaccessibleByCar = roadAccess == "trackInaccessibleByCar",
            securedFenced = has("securedFenced"),
            securityGuard = has("securityGuard"),
            videoSurveillance = has("videoSurveillance"),
            price = Money(
                amount = priceAmount ?: 0,
                currency = currency,
                periodicity = if (isSale()) PeriodicityType.NONE else PeriodicityType.MONTHLY,
            ),
            advanceRent = advanceRent.takeIf { !isSale() },
            securityDeposit = securityDeposit.takeIf { !isSale() },
            availableFrom = availableFrom,
            firstName = firstName.orEmpty(),
            lastName = lastName.orEmpty(),
            email = email.orEmpty(),
            whatsappNumber = whatsappNumber.orEmpty(),
            preferredContactPeriod = preferredContactPeriod,
        )
}
