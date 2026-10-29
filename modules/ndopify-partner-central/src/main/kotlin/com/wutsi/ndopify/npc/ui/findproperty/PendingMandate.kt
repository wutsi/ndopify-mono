package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.npc.ui.mandate.Amenity
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.io.Serializable

/**
 * The answers of a "Trouver un bien" request in progress, kept in the HTTP session until the review submits them.
 * Answers are only stored once their step validated them, so the session is the trusted copy: the review submits it
 * as is, never values from the page. Follow-up answers that no longer apply (the rooms of a studio, …) may linger
 * here after the visitor changed their mind; [toCreateMandateRequest] never sends them.
 *
 * `propertyType`, `furnitureType` and `parkings` are kept as text because they can be "Indifférent"
 * ([FindPropertyRules.ANY]), a UI answer the server doesn't know.
 */
data class PendingMandate(
    val propertyCategory: PropertyCategory? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val area: Int? = null,
    val amenities: Set<String> = emptySet(),
    val priceAmount: Long? = null,
    val movingPeriod: MovingPeriod? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val preferredContactPeriod: ContactPeriodType? = null,
    val comments: String? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-mandate"

        /** The boolean criteria of CreateMandateRequest, in form order. */
        val AMENITIES = Amenity.KEYS
    }

    fun has(amenity: String): Boolean = amenities.contains(amenity)

    private fun type() = FindPropertyRules.parsePropertyType(propertyType)

    fun isComplete(step: String): Boolean = when (step) {
        FindPropertyController.STEP_NEED -> FindPropertyRules.propertyCategory(propertyCategory) == null

        FindPropertyController.STEP_AREA ->
            FindPropertyRules.cityId(cityId) == null && FindPropertyRules.neighborhoodIds(neighborhoodIds) == null

        FindPropertyController.STEP_PROPERTY ->
            FindPropertyRules.propertyType(propertyCategory, propertyType) == null &&
                (!FindPropertyRules.needsRooms(propertyCategory, type()) || FindPropertyRules.rooms(rooms) == null) &&
                (!FindPropertyRules.needsFurniture(type()) || FindPropertyRules.furnitureType(furnitureType) == null) &&
                FindPropertyRules.area(area) == null

        // Every criterion is optional.
        FindPropertyController.STEP_AMENITIES -> true

        FindPropertyController.STEP_BUDGET ->
            FindPropertyRules.isPriceAmount(priceAmount) && FindPropertyRules.movingPeriod(movingPeriod) == null

        FindPropertyController.STEP_CONTACT -> listOf(
            JoinRules.firstName(firstName),
            JoinRules.lastName(lastName),
            JoinRules.email(email),
            JoinRules.whatsappNumber(whatsappNumber),
            FindPropertyRules.preferredContactPeriod(preferredContactPeriod),
            FindPropertyRules.comments(comments),
        ).all { it == null }

        else -> false
    }

    /** The first step that still needs answers, or the review step when they're all done. */
    fun firstIncompleteStep(): String =
        FindPropertyController.STEPS.dropLast(1).firstOrNull { step -> !isComplete(step) }
            ?: FindPropertyController.STEP_REVIEW

    /** The answers as a TENANT rental mandate. Questions that don't apply to the chosen type are sent as null. */
    fun toCreateMandateRequest(currency: String): CreateMandateRequest {
        val type = type()
        return CreateMandateRequest(
            mandateType = MandateType.SEARCH,
            // Only rentals for now: the visitor isn't asked.
            listingType = ListingType.RENTAL,
            propertyCategory = propertyCategory ?: PropertyCategory.UNKNOWN,
            cityId = cityId ?: -1,
            neighbourhoodIds = neighborhoodIds,
            propertyType = type,
            furnitureType = FurnitureType.entries
                .firstOrNull { it.name == furnitureType }
                ?.takeIf { FindPropertyRules.needsFurniture(type) },
            rooms = rooms?.takeIf { FindPropertyRules.needsRooms(propertyCategory, type) },
            parkings = parkings?.takeIf { FindPropertyRules.asksParkings(type) && it != FindPropertyRules.ANY },
            area = area,
            waterMeter = has("waterMeter"),
            dedicatedWaterMeter = has("dedicatedWaterMeter"),
            waterTank = has("waterTank"),
            waterWell = has("waterWell"),
            electricityMeter = has("electricityMeter"),
            dedicatedElectricityMeter = has("dedicatedElectricityMeter"),
            generatorSet = has("generatorSet"),
            solarPanel = has("solarPanel"),
            roadPaved = has("roadPaved"),
            trackAccessibleByCar = has("trackAccessibleByCar"),
            trackInaccessibleByCar = has("trackInaccessibleByCar"),
            securedFenced = has("securedFenced"),
            securityGuard = has("securityGuard"),
            videoSurveillance = has("videoSurveillance"),
            price = Money(amount = priceAmount ?: 0, currency = currency, periodicity = PeriodicityType.MONTHLY),
            movingPeriod = movingPeriod ?: MovingPeriod.UNKNOWN,
            firstName = firstName.orEmpty(),
            lastName = lastName.orEmpty(),
            email = email.orEmpty(),
            whatsappNumber = whatsappNumber.orEmpty(),
            preferredContactPeriod = preferredContactPeriod,
            comments = comments?.takeIf { it.isNotBlank() },
        )
    }
}
