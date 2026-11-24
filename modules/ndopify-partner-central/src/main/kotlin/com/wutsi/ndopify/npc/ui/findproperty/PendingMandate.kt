package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
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
 *
 * A building bought as a whole keeps its two counts — `levels` (étages) and `units` — and their "Indifférent"
 * answers ([levelsAny] / [unitsAny], posted as empty fields and sent as null). Its amenities step leaves the wizard
 * ([steps]), so its amenity answers are neither kept nor sent.
 *
 * `financingType` and `titleDocuments` are the buyer's brief for a purchase: a rental never sends them, and every
 * purchase keeps the documents it declares, whatever its type. The price per m² the budget step shows is derived on
 * the page and is never an answer here.
 */
data class PendingMandate(
    val propertyCategory: PropertyCategory? = null,
    val listingType: ListingType? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val area: Int? = null,
    val levels: Int? = null,
    val units: Int? = null,
    val levelsAny: Boolean = false,
    val unitsAny: Boolean = false,
    val titleDocuments: List<TitleDocumentType> = emptyList(),
    val amenities: Set<String> = emptySet(),
    val priceAmount: Long? = null,
    val movingPeriod: MovingPeriod? = null,
    val financingType: FinancingType? = null,
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

    fun isSale(): Boolean = FindPropertyRules.isSale(listingType)

    private fun type() = FindPropertyRules.parsePropertyType(propertyType)

    /** A building bought as a whole: six steps, the amenities one left out. */
    fun buildingSale(): Boolean = FindPropertyRules.buildingSale(listingType, type())

    /** The branch's steps, in order: a building sale has no amenities step. */
    fun steps(): List<String> = FindPropertyController.steps(this)

    fun isComplete(step: String): Boolean = when (step) {
        FindPropertyController.STEP_NEED ->
            FindPropertyRules.listingType(listingType) == null && FindPropertyRules.propertyCategory(propertyCategory) == null

        FindPropertyController.STEP_AREA ->
            FindPropertyRules.cityId(cityId) == null && FindPropertyRules.neighborhoodIds(neighborhoodIds) == null

        FindPropertyController.STEP_PROPERTY ->
            FindPropertyRules.propertyType(propertyCategory, propertyType, listingType) == null &&
                (!FindPropertyRules.needsRooms(propertyCategory, type(), listingType) || FindPropertyRules.rooms(rooms) == null) &&
                (!FindPropertyRules.needsFurniture(propertyCategory, type(), listingType) || FindPropertyRules.furnitureType(furnitureType) == null) &&
                FindPropertyRules.area(area, isSale() && type() == PropertyType.LAND) == null &&
                (!buildingSale() || countsAnswered())

        // Every criterion is optional; a building sale skips the step.
        FindPropertyController.STEP_AMENITIES -> true

        FindPropertyController.STEP_BUDGET ->
            FindPropertyRules.isPriceAmount(priceAmount, listingType) &&
                FindPropertyRules.movingPeriod(movingPeriod, listingType) == null &&
                FindPropertyRules.financingType(financingType, listingType) == null

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

    /** Both counts are required for a building sale: a whole number within its bounds, or "Indifférent". */
    private fun countsAnswered(): Boolean =
        FindPropertyRules.levels(levels?.toString(), levelsAny) == null &&
            FindPropertyRules.units(units?.toString(), unitsAny) == null

    /** The first step that still needs answers, or the review step when they're all done. */
    fun firstIncompleteStep(): String =
        steps().dropLast(1).firstOrNull { step -> !isComplete(step) }
            ?: FindPropertyController.STEP_REVIEW

    /** The answers as a TENANT mandate. Questions that don't apply to the chosen type or project are sent as null. */
    fun toCreateMandateRequest(currency: String): CreateMandateRequest {
        val type = type()
        val sale = isSale()
        val amenitiesNeeded = FindPropertyRules.needsAmenities(propertyCategory, type, listingType)
        return CreateMandateRequest(
            mandateType = MandateType.SEARCH,
            // The wizard's project; the old hard-coded rental stays the default, never UNKNOWN.
            listingType = listingType ?: ListingType.RENTAL,
            // A rental never sends one; every purchase keeps the documents it declares.
            financingType = financingType.takeIf { sale },
            titleDocuments = titleDocuments
                .filter { sale && it != TitleDocumentType.UNKNOWN }
                .distinct(),
            propertyCategory = propertyCategory ?: PropertyCategory.UNKNOWN,
            cityId = cityId ?: -1,
            neighbourhoodIds = neighborhoodIds,
            propertyType = type,
            furnitureType = FurnitureType.entries
                .firstOrNull { it.name == furnitureType }
                ?.takeIf { FindPropertyRules.needsFurniture(propertyCategory, type, listingType) },
            rooms = rooms?.takeIf { FindPropertyRules.needsRooms(propertyCategory, type, listingType) },
            parkings = parkings?.takeIf {
                FindPropertyRules.asksParkings(propertyCategory, type, listingType) && it != FindPropertyRules.ANY
            },
            area = area,
            // A building sale's two counts; another type sends none, and "Indifférent" is null.
            levels = levels.takeIf { buildingSale() },
            units = units.takeIf { buildingSale() },
            waterMeter = has("waterMeter") && amenitiesNeeded,
            dedicatedWaterMeter = has("dedicatedWaterMeter") && amenitiesNeeded,
            waterTank = has("waterTank") && amenitiesNeeded,
            waterWell = has("waterWell") && amenitiesNeeded,
            electricityMeter = has("electricityMeter") && amenitiesNeeded,
            dedicatedElectricityMeter = has("dedicatedElectricityMeter") && amenitiesNeeded,
            generatorSet = has("generatorSet") && amenitiesNeeded,
            solarPanel = has("solarPanel") && amenitiesNeeded,
            roadPaved = has("roadPaved") && amenitiesNeeded,
            trackAccessibleByCar = has("trackAccessibleByCar") && amenitiesNeeded,
            trackInaccessibleByCar = has("trackInaccessibleByCar") && amenitiesNeeded,
            securedFenced = has("securedFenced") && amenitiesNeeded,
            securityGuard = has("securityGuard") && amenitiesNeeded,
            videoSurveillance = has("videoSurveillance") && amenitiesNeeded,
            price = Money(
                amount = priceAmount ?: 0,
                currency = currency,
                periodicity = if (sale) PeriodicityType.NONE else PeriodicityType.MONTHLY,
            ),
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
