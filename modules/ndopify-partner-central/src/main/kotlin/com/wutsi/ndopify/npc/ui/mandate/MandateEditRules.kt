package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules

/**
 * Validation of the edit page ([MandateEditForm]) and the update it sends. ndopify-server doesn't validate a mandate,
 * so this is the only check it gets: the find-property wizard's rules ([FindPropertyRules]), with the edit page's
 * messages (`initMandateEdit` in the prototype's partner-central.js). A landlord describes a property that exists:
 * "Indifférent" isn't an answer for it.
 */
object MandateEditRules {
    private const val MAX_AREA_DIGITS = 7
    private const val SUMMARY_LENGTH = 40

    private val ANY = FindPropertyRules.ANY

    /** The edit page's groups, and the fields each holds: a group with an error opens. */
    val GROUPS: Map<String, List<String>> = linkedMapOf(
        "sector" to listOf("neighborhoodIds"),
        "property" to listOf("propertyCategory", "propertyType", "rooms", "furnitureType", "parkings", "area"),
        "amenities" to emptyList(),
        "budget" to listOf("priceAmount", "movingPeriod"),
        "comments" to listOf("comments"),
    )

    fun errors(form: MandateEditForm, owner: Boolean, formatter: TenantFormatter): Map<String, String> {
        val type = type(form)
        val area = area(form.area)
        return listOfNotNull(
            FindPropertyRules.neighborhoodIds(form.neighborhoodIds)?.let { "neighborhoodIds" to it },
            FindPropertyRules.propertyCategory(form.propertyCategory)?.let { "propertyCategory" to it },
            typeError(form, owner)?.let { "propertyType" to it },
            ("rooms" to "Indiquez le nombre de chambres.")
                .takeIf { FindPropertyRules.needsRooms(type) && FindPropertyRules.rooms(form.rooms) != null },
            ("furnitureType" to "Indiquez si le bien est meublé.")
                .takeIf {
                    FindPropertyRules.needsFurniture(type) &&
                        (FindPropertyRules.furnitureType(form.furnitureType) != null || (owner && form.furnitureType == ANY))
                },
            ("parkings" to "Choisissez un nombre de places proposé.")
                .takeIf {
                    FindPropertyRules.asksParkings(type) &&
                        (FindPropertyRules.parkings(form.parkings) != null || (owner && form.parkings == ANY))
                },
            ("area" to "Indiquez la surface en m², ou laissez le champ vide.")
                .takeIf { area.isFailure || FindPropertyRules.area(area.getOrNull()) != null },
            priceError(form.priceAmount, owner, formatter)?.let { "priceAmount" to it },
            ("movingPeriod" to if (owner) "Indiquez quand le bien est disponible." else "Indiquez la date d'emménagement.")
                .takeIf { FindPropertyRules.movingPeriod(form.movingPeriod) != null },
            FindPropertyRules.comments(form.comments?.trim())?.let { "comments" to it },
        ).toMap()
    }

    /**
     * The whole search, as ndopify-server's update replaces it: the form's answers, "Indifférent" and the follow-up
     * questions that don't apply to the type as null; the rest (city, listing type, currency, lease terms) the
     * mandate's.
     */
    fun request(form: MandateEditForm, mandate: Mandate): UpdateMandateRequest {
        val type = type(form)
        val amenities = form.amenities.toSet()
        return UpdateMandateRequest(
            propertyCategory = form.propertyCategory ?: mandate.propertyCategory,
            listingType = mandate.listingType,
            cityId = mandate.cityId,
            neighbourhoodIds = form.neighborhoodIds.distinct(),
            propertyType = type?.let { PropertyType.valueOf(it) },
            furnitureType = form.furnitureType
                ?.takeIf { FindPropertyRules.needsFurniture(type) && it != ANY }
                ?.let { FurnitureType.valueOf(it) },
            rooms = form.rooms?.takeIf { FindPropertyRules.needsRooms(type) },
            parkings = form.parkings?.ifEmpty { null }?.takeIf { FindPropertyRules.asksParkings(type) && it != ANY },
            area = area(form.area).getOrNull(),
            waterMeter = "waterMeter" in amenities,
            dedicatedWaterMeter = "dedicatedWaterMeter" in amenities,
            waterTank = "waterTank" in amenities,
            waterWell = "waterWell" in amenities,
            electricityMeter = "electricityMeter" in amenities,
            dedicatedElectricityMeter = "dedicatedElectricityMeter" in amenities,
            generatorSet = "generatorSet" in amenities,
            solarPanel = "solarPanel" in amenities,
            roadPaved = "roadPaved" in amenities,
            trackAccessibleByCar = "trackAccessibleByCar" in amenities,
            trackInaccessibleByCar = "trackInaccessibleByCar" in amenities,
            securedFenced = "securedFenced" in amenities,
            securityGuard = "securityGuard" in amenities,
            videoSurveillance = "videoSurveillance" in amenities,
            price = mandate.price.copy(amount = FindPropertyRules.parseAmount(form.priceAmount) ?: mandate.price.amount),
            securityDeposit = mandate.securityDeposit,
            advanceRent = mandate.advanceRent,
            leaseTerm = mandate.leaseTerm,
            noticePeriod = mandate.noticePeriod,
            movingPeriod = form.movingPeriod ?: MovingPeriod.UNKNOWN,
            comments = form.comments?.trim()?.ifEmpty { null },
            version = form.version,
        )
    }

    /** Each folded group's current value, as the prototype's `SUMMARIES`; [neighborhoods] are the chosen names. */
    fun summaries(form: MandateEditForm, neighborhoods: List<String>, formatter: TenantFormatter): Map<String, String> {
        val type = type(form)
        val rooms = form.rooms?.takeIf { FindPropertyRules.needsRooms(type) && FindPropertyRules.rooms(it) == null }
        val area = area(form.area).getOrNull()
        val amenities = form.amenities.distinct().size
        val price = FindPropertyRules.parseAmount(form.priceAmount)?.takeIf { it > 0 }
        val comments = form.comments?.trim().orEmpty()
        return mapOf(
            "sector" to neighborhoods.joinToString(", ").ifEmpty { "Toute la ville" },
            "property" to listOfNotNull(
                (
                    MandateLabels.PROPERTY_TYPE_LABELS.entries.firstOrNull { it.key.name == form.propertyType }?.value
                        ?: "Indifférent".takeIf { form.propertyType == ANY }
                        ?: form.propertyCategory?.let { MandateLabels.category(it) }
                    ),
                rooms?.let { if (it == "1") "1 chambre" else "$it chambres" },
                area?.let { "$it m²" },
            ).joinToString(" · "),
            "amenities" to when (amenities) {
                0 -> "Aucun critère"
                1 -> "1 critère"
                else -> "$amenities critères"
            },
            "budget" to listOfNotNull(
                price?.let { formatter.money(it) + " / mois" } ?: "Montant à indiquer",
                MandateLabels.MOVE_IN_LABELS[form.movingPeriod],
            ).joinToString(" · "),
            "comments" to when {
                comments.isEmpty() -> "Aucune"
                comments.length > SUMMARY_LENGTH -> comments.take(SUMMARY_LENGTH) + "…"
                else -> comments
            },
        )
    }

    /** The type's name; null for "Indifférent" or no answer. */
    private fun type(form: MandateEditForm): String? = form.propertyType?.takeIf { it.isNotEmpty() && it != ANY }

    private fun typeError(form: MandateEditForm, owner: Boolean): String? {
        val type = type(form)
        return when {
            type == null && owner -> "Choisissez un type de bien proposé."
            type != null && PropertyType.entries.none { it != PropertyType.UNKNOWN && it.name == type } ->
                "Choisissez un type de bien proposé."
            else -> FindPropertyRules.propertyType(form.propertyCategory, type)
        }
    }

    private fun priceError(value: String?, owner: Boolean, formatter: TenantFormatter): String? {
        val amount = FindPropertyRules.parseAmount(value)
        val currency = formatter.currency()
        return if (amount == null || amount <= 0) {
            if (owner) "Indiquez le loyer, en $currency par mois." else "Indiquez le budget, en $currency par mois."
        } else {
            FindPropertyRules.priceAmount(amount, formatter)
        }
    }

    /** Blank → null (optional); digits only (spaces and dots as separators) → the surface; else a failure. */
    private fun area(value: String?): Result<Int?> {
        val text = value.orEmpty().filterNot { it.isWhitespace() || it == '.' || it == ' ' }
        return when {
            text.isEmpty() -> Result.success(null)
            text.all { it.isDigit() } && text.length <= MAX_AREA_DIGITS -> Result.success(text.toInt())
            else -> Result.failure(IllegalArgumentException(value))
        }
    }
}
