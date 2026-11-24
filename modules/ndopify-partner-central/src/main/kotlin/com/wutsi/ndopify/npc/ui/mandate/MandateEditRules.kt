package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.offerproperty.OfferPropertyRules
import com.wutsi.ndopify.web.core.service.TenantFormatter

/**
 * Validation of the edit page ([MandateEditForm]) and the update it sends. ndopify-server doesn't validate a mandate,
 * so this is the only check it gets: the find-property wizard's rules ([FindPropertyRules]), with the edit page's
 * messages (`initMandateEdit` in the prototype's partner-central.js). A landlord describes a property that exists:
 * "Indifférent" isn't an answer for it.
 *
 * The page asks the questions [FindPropertyRules] gives the chosen type (#304). A question it doesn't show keeps the
 * mandate's stored answer: only a shown answer is saved, so a hidden field never wipes what the agent already said.
 */
object MandateEditRules {
    private const val MAX_AREA_DIGITS = 7
    private const val SUMMARY_LENGTH = 40

    private val ANY = FindPropertyRules.ANY

    /** The edit page's groups, and the fields each holds: a group with an error opens. */
    val GROUPS: Map<String, List<String>> = linkedMapOf(
        "sector" to listOf("neighborhoodIds"),
        "property" to listOf("propertyCategory", "propertyType", "rooms", "levels", "units", "furnitureType", "parkings", "area"),
        "amenities" to emptyList(),
        "budget" to listOf("priceAmount", "movingPeriod"),
        "comments" to listOf("comments"),
    )

    fun errors(form: MandateEditForm, mandate: Mandate, formatter: TenantFormatter): Map<String, String> {
        val type = type(form)
        val area = area(form.area)
        val owner = mandate.mandateType == MandateType.LISTING
        val sale = FindPropertyRules.isSale(mandate.listingType)
        // A building sold as a whole is asked its étages and its unités, and no room nor parking.
        val building = FindPropertyRules.buildingSale(mandate.listingType, type)
        val levelsError = if (building) countError(form.levels, "d'étages", FindPropertyRules.MAX_LEVELS) else null
        val unitsError = if (building) countError(form.units, "d'unités", FindPropertyRules.MAX_UNITS) else null
        return listOfNotNull(
            FindPropertyRules.neighborhoodIds(form.neighborhoodIds)?.let { "neighborhoodIds" to it },
            FindPropertyRules.propertyCategory(form.propertyCategory)?.let { "propertyCategory" to it },
            typeError(form, mandate)?.let { "propertyType" to it },
            ("rooms" to "Indiquez le nombre de chambres.")
                .takeIf {
                    !building &&
                        FindPropertyRules.needsRooms(form.propertyCategory, type, mandate.listingType) &&
                        FindPropertyRules.rooms(form.rooms) != null
                },
            levelsError?.let { "levels" to it },
            unitsError?.let { "units" to it },
            ("furnitureType" to "Indiquez si le bien est meublé.")
                .takeIf {
                    FindPropertyRules.needsFurniture(form.propertyCategory, type, mandate.listingType) &&
                        (FindPropertyRules.furnitureType(form.furnitureType) != null || (owner && form.furnitureType == ANY))
                },
            ("parkings" to "Choisissez un nombre de places proposé.")
                .takeIf {
                    !building && FindPropertyRules.asksParkings(form.propertyCategory, type, mandate.listingType) &&
                        (FindPropertyRules.parkings(form.parkings) != null || (owner && form.parkings == ANY))
                },
            ("area" to "Indiquez la surface en m², ou laissez le champ vide.")
                .takeIf {
                    showsArea(form, mandate) &&
                        (area.isFailure || FindPropertyRules.area(area.getOrNull(), false) != null)
                },
            priceError(form.priceAmount, mandate, formatter)?.let { "priceAmount" to it },
            ("movingPeriod" to when {
                owner -> "Indiquez quand le bien est disponible."
                sale -> "Indiquez quand vous souhaitez acheter."
                else -> "Indiquez la date d'emménagement."
            })
                .takeIf { FindPropertyRules.movingPeriod(form.movingPeriod, null) != null },
            FindPropertyRules.comments(form.comments?.trim())?.let { "comments" to it },
        ).toMap()
    }

    /**
     * The whole search, as ndopify-server's update replaces it: the form's answers, "Indifférent" as null, and every
     * question the page doesn't show for the type as the mandate's stored answer (#304) — a hidden field must never
     * wipe what the mandate already says. The documents go to the mandate's own list (a listing's ownership, a search's
     * requirement); the rest (city, listing type, currency, lease terms) the mandate's.
     */
    fun request(form: MandateEditForm, mandate: Mandate): UpdateMandateRequest {
        val type = type(form)
        val building = FindPropertyRules.buildingSale(mandate.listingType, type)
        val documents = documents(form)
        val sale = FindPropertyRules.isSale(mandate.listingType)
        val owner = mandate.mandateType == MandateType.LISTING
        // A building sale has no amenities group on the page: its stored criteria travel, never the (empty) posted set.
        val amenities = if (building) Amenity.of(mandate).map { it.key }.toSet() else form.amenities.toSet()
        return UpdateMandateRequest(
            propertyCategory = form.propertyCategory ?: mandate.propertyCategory,
            listingType = mandate.listingType,
            // Not editable here: the wizard's purchase answers travel unchanged, or the update would clear them.
            financingType = mandate.financingType,
            titleDocuments = if (sale && !owner) documents else mandate.titleDocuments,
            cityId = mandate.cityId,
            neighbourhoodIds = form.neighborhoodIds.distinct(),
            propertyType = type,
            furnitureType = if (showsFurniture(form, mandate)) furniture(form.furnitureType) else mandate.furnitureType,
            rooms = if (showsRooms(form, mandate)) form.rooms else mandate.rooms,
            parkings = if (showsParkings(form, mandate)) form.parkings?.ifEmpty { null }?.takeIf { it != ANY } else mandate.parkings,
            area = if (showsArea(form, mandate)) area(form.area).getOrNull() else mandate.area,
            levels = if (building) count(form.levels) else mandate.levels,
            units = if (building) count(form.units) else mandate.units,
            ownershipDocuments = if (sale && owner) documents else mandate.ownershipDocuments,
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
            availableFrom = mandate.availableFrom,
            leaseTerm = mandate.leaseTerm,
            noticePeriod = mandate.noticePeriod,
            movingPeriod = form.movingPeriod ?: MovingPeriod.UNKNOWN,
            comments = form.comments?.trim()?.ifEmpty { null },
            version = form.version,
        )
    }

    /** Each folded group's current value, as the prototype's `SUMMARIES`; [neighborhoods] are the chosen names. */
    fun summaries(
        form: MandateEditForm,
        mandate: Mandate,
        neighborhoods: List<String>,
        formatter: TenantFormatter,
    ): Map<String, String> {
        val type = type(form)
        val building = FindPropertyRules.buildingSale(mandate.listingType, type)
        val rooms = form.rooms?.takeIf {
            !building && FindPropertyRules.needsRooms(form.propertyCategory, type, mandate.listingType) &&
                FindPropertyRules.rooms(it) == null
        }
        // A residential non-land type isn't asked its surface, so a hidden one isn't summarised either (#304).
        val area = area(form.area).getOrNull().takeIf { showsArea(form, mandate) }
        val levels = count(form.levels).takeIf { building && it != null && it > 0 }
        val units = count(form.units).takeIf { building && it != null && it > 0 }
        val amenities = form.amenities.distinct().size
        val price = FindPropertyRules.parseAmount(form.priceAmount)?.takeIf { it > 0 }
        val comments = form.comments?.trim().orEmpty()
        // A sale's amount is a one-off price or budget: no " / mois".
        val sale = FindPropertyRules.isSale(mandate.listingType)
        return mapOf(
            "sector" to neighborhoods.joinToString(", ").ifEmpty { "Toute la ville" },
            "property" to listOfNotNull(
                (
                    MandateLabels.PROPERTY_TYPE_LABELS[type]
                        ?: "Indifférent".takeIf { form.propertyType == ANY }
                        ?: form.propertyCategory?.let { MandateLabels.category(it) }
                    ),
                rooms?.let { if (it == "1") "1 chambre" else "$it chambres" },
                levels?.let { if (it == 1) "1 étage" else "$it étages" },
                units?.let { if (it == 1) "1 unité" else "$it unités" },
                area?.let { "$it m²" },
            ).joinToString(" · "),
            "amenities" to when (amenities) {
                0 -> "Aucun critère"
                1 -> "1 critère"
                else -> "$amenities critères"
            },
            "budget" to listOfNotNull(
                price?.let { formatter.compactMoney(it) + if (sale) "" else " / mois" } ?: "Montant à indiquer",
                MandateLabels.MOVE_IN_LABELS[form.movingPeriod],
            ).joinToString(" · "),
            "comments" to when {
                comments.isEmpty() -> "Aucune"
                comments.length > SUMMARY_LENGTH -> comments.take(SUMMARY_LENGTH) + "…"
                else -> comments
            },
        )
    }

    /** The moving-period group's legend: a landlord's availability, a buyer's purchase date, a renter's move. */
    fun movingLegend(mandate: Mandate): String = when {
        mandate.mandateType == MandateType.LISTING -> "Disponibilité du bien"
        FindPropertyRules.isSale(mandate.listingType) -> "Quand souhaitez-vous acheter ?"
        else -> "Date d'emménagement souhaitée"
    }

    /** Whether the page asks the rooms: a home that isn't a studio or a room, and not a building sold as a whole. */
    fun showsRooms(form: MandateEditForm, mandate: Mandate): Boolean =
        !FindPropertyRules.buildingSale(mandate.listingType, type(form)) &&
            FindPropertyRules.needsRooms(form.propertyCategory, type(form), mandate.listingType)

    /**
     * Whether the page asks the furniture: a residential type sale and rental alike (#304), and a stored rental
     * building, which had that question before rentals stopped offering it.
     */
    fun showsFurniture(form: MandateEditForm, mandate: Mandate): Boolean =
        FindPropertyRules.needsFurniture(form.propertyCategory, type(form), mandate.listingType) ||
            storedRentalBuilding(form, mandate, type(form))

    /** Whether the page asks the parking: a residential type, a room included, never a building sold as a whole. */
    fun showsParkings(form: MandateEditForm, mandate: Mandate): Boolean =
        !FindPropertyRules.buildingSale(mandate.listingType, type(form)) &&
            FindPropertyRules.asksParkings(form.propertyCategory, type(form), mandate.listingType)

    /**
     * Whether the page asks the surface: a commercial property or a plot of land bought; a stored rental building keeps
     * the surface its commercial usage already asked for. A search without a type (*Indifférent*) keeps it when it may
     * still match a plot or a commercial property, as *Trouver un bien* does.
     */
    fun showsArea(form: MandateEditForm, mandate: Mandate): Boolean =
        FindPropertyRules.needsArea(form.propertyCategory, type(form), mandate.listingType) ||
            storedRentalBuilding(form, mandate, type(form)) ||
            (
                mandate.mandateType == MandateType.SEARCH && type(form) == null &&
                    (form.propertyCategory == PropertyCategory.COMMERCIAL || FindPropertyRules.isSale(mandate.listingType))
                )

    /**
     * A sale's implied rate under the price, as the page shows it before its script recomputes it (#304): the price per
     * m² for a plot of land, per unit for a building sold as a whole; null for any other mandate or without a usable
     * price and surface/count. Display only: it is never sent nor stored.
     */
    fun rate(form: MandateEditForm, mandate: Mandate, formatter: TenantFormatter): String? {
        val type = type(form)
        val price = FindPropertyRules.parseAmount(form.priceAmount) ?: return null
        val area = area(form.area).getOrNull()
        val sqm = OfferPropertyRules.pricePerSqm(mandate.listingType, type, price, area)
        if (sqm != null && area != null) {
            return "Soit ≈ " + formatter.compactMoney(sqm) + "/m² pour " + MandateLabels.formatNumber(area) + " m²"
        }
        val units = count(form.units)
        val unit = OfferPropertyRules.pricePerUnit(mandate.listingType, type, price, units)
        if (unit != null && units != null) {
            return "Soit ≈ " + formatter.compactMoney(unit) + "/unité pour " + units +
                (if (units > 1) " unités" else " unité")
        }
        return null
    }

    /** The type; null for "Indifférent", no answer, or a value that isn't a type ([typeError] rejects it). */
    private fun type(form: MandateEditForm): PropertyType? = FindPropertyRules.parsePropertyType(form.propertyType)

    /**
     * A rental building stored before #304 keeps its type, while it is still the answer and in its saved usage: the
     * rules no longer offer it, but the mandate's questions (surface, furniture) stay those it had.
     */
    private fun storedRentalBuilding(form: MandateEditForm, mandate: Mandate, type: PropertyType?): Boolean =
        !FindPropertyRules.isSale(mandate.listingType) &&
            mandate.propertyType == PropertyType.BUILDING &&
            type == PropertyType.BUILDING &&
            form.propertyCategory == mandate.propertyCategory

    /** The posted document names as types: unknown names and UNKNOWN dropped, duplicates removed, in form order. */
    private fun documents(form: MandateEditForm): List<TitleDocumentType> =
        form.documents
            .mapNotNull { name -> TitleDocumentType.entries.firstOrNull { it != TitleDocumentType.UNKNOWN && it.name == name } }
            .distinct()

    /** A posted furniture answer: "Indifférent", blank, an unknown name or no answer → null. */
    private fun furniture(value: String?): FurnitureType? =
        FurnitureType.entries.firstOrNull { it != FurnitureType.UNKNOWN && it.name == value && it.name != ANY }

    private fun typeError(form: MandateEditForm, mandate: Mandate): String? {
        val answer = form.propertyType?.takeIf { it.isNotEmpty() && it != ANY }
        val owner = mandate.mandateType == MandateType.LISTING
        // A rental land stored before #275 keeps its answer: only unchanged, in its saved category.
        val storedLand = !FindPropertyRules.isSale(mandate.listingType) &&
            mandate.propertyType == PropertyType.LAND &&
            form.propertyType == PropertyType.LAND.name &&
            form.propertyCategory == mandate.propertyCategory
        return when {
            answer == null && owner -> "Choisissez un type de bien proposé."
            answer != null && type(form) == null -> "Choisissez un type de bien proposé."
            storedLand -> null
            // A rental building stored before #304 keeps its answer the same way.
            storedRentalBuilding(form, mandate, type(form)) -> null
            else -> FindPropertyRules.propertyType(form.propertyCategory, answer, mandate.listingType)
        }
    }

    private fun priceError(value: String?, mandate: Mandate, formatter: TenantFormatter): String? {
        val amount = FindPropertyRules.parseAmount(value)
        val currency = formatter.currency()
        val owner = mandate.mandateType == MandateType.LISTING
        val sale = FindPropertyRules.isSale(mandate.listingType)
        return when {
            amount == null || amount <= 0 -> when {
                sale && owner -> "Indiquez le prix, en $currency."
                sale -> "Indiquez le budget, en $currency."
                owner -> "Indiquez le loyer, en $currency par mois."
                else -> "Indiquez le budget, en $currency par mois."
            }
            // A seller's floor, worded as the offer wizard's (OfferPropertyRules): a sale price, not a budget.
            sale && owner -> if (amount < FindPropertyRules.MIN_SALE_PRICE) {
                "Indiquez un prix d'au moins ${formatter.compactMoney(FindPropertyRules.MIN_SALE_PRICE)}."
            } else {
                null
            }
            else -> FindPropertyRules.priceAmount(amount, formatter, mandate.listingType)
        }
    }

    /** A building's count: "3" → 3, whatever else → null (the error is the field's). */
    private fun count(value: String?): Int? =
        value?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()

    /** A building's count as its edit group words it: a blank field and an out-of-bounds value have their own message. */
    private fun countError(value: String?, noun: String, max: Int): String? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return "Indiquez le nombre $noun."
        val number = text.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        return if (number != null && number in 1..max) null else "Indiquez un nombre entier, de 1 à $max."
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
