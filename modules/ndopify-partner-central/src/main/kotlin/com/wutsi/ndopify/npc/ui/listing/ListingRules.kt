package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.npc.service.TenantFormatter
import java.time.LocalDate

/**
 * The rules of a listing's pages (the prototype's `LISTING_SECTIONS`, `LISTING_TAGS`, `LISTING_APPLIES` and
 * `LISTING_RULES` in partner-central.js): its checklist, in order, how its status reads, and the validation of each
 * section's form. The checklist's state (each section VERIFIED or not) is ndopify-server's: `Listing.sections`.
 * ndopify-server checks the required fields again, not the lengths, ranges and choices checked here.
 */
object ListingRules {
    /** Same as ndopify-server's `ListingSectionService.MIN_PHOTOS`. */
    const val MIN_PHOTOS = 5

    const val MAX_LANDMARK = 100
    const val MAX_ACCESS_DIRECTIONS = 500
    const val MAX_REMARKS = 1000
    const val MAX_AVAILABILITY_DAYS = 365L

    // The road access: one answer, of the amenities' names
    const val ROAD_PAVED = "roadPaved"
    const val TRACK_ACCESSIBLE_BY_CAR = "trackAccessibleByCar"
    const val TRACK_INACCESSIBLE_BY_CAR = "trackInaccessibleByCar"
    val ROAD_ACCESS = listOf(ROAD_PAVED, TRACK_ACCESSIBLE_BY_CAR, TRACK_INACCESSIBLE_BY_CAR)

    // The terms' choices (the prototype's <select>s), in months
    val SECURITY_DEPOSITS = 1..6
    val ADVANCE_RENTS = 1..12
    val LEASE_TERMS = listOf(6, 12, 24, 36)
    val NOTICE_PERIODS = 1..3

    /** The types of each category (the prototype's `data-categories`: LAND is commercial, BUILDING residential). */
    val TYPES: Map<PropertyCategory, List<PropertyType>> = linkedMapOf(
        PropertyCategory.RESIDENTIAL to listOf(
            PropertyType.APARTMENT,
            PropertyType.HOUSE,
            PropertyType.VILLA,
            PropertyType.DUPLEX,
            PropertyType.STUDIO,
            PropertyType.ROOM,
            PropertyType.BUILDING,
        ),
        PropertyCategory.COMMERCIAL to listOf(
            PropertyType.OFFICE,
            PropertyType.RETAIL,
            PropertyType.INDUSTRIAL,
            PropertyType.LAND,
        ),
    )

    /** The question each count field of *Le bien* belongs to (see [applies]); the area is always asked. */
    val COUNT_QUESTIONS: Map<String, String> = linkedMapOf(
        "rooms" to "rooms",
        "livingRooms" to "rooms",
        "bathrooms" to "bathrooms",
        "kitchens" to "kitchens",
        "parkings" to "parkings",
        "levels" to "levels",
        "floorLevel" to "floorLevel",
    )

    // Counted on site: needed when asked
    private val REQUIRED_COUNTS = setOf("rooms", "bathrooms", "area")

    private const val COUNT_REQUIRED = "Indiquez ce nombre, compté sur place."
    private const val COUNT_INVALID = "Indiquez un nombre entier."
    private const val CHOICE_INVALID = "Choisissez une valeur de la liste."
    private const val MAX_COUNT_DIGITS = 6
    private const val MAX_AMOUNT_DIGITS = 12

    /** The checklist, in order. */
    val SECTIONS: List<ListingSection> = listOf(
        ListingSection.LOCATION,
        ListingSection.GPS,
        ListingSection.PROPERTY,
        ListingSection.AMENITIES,
        ListingSection.TERMS,
        ListingSection.PHOTOS,
        ListingSection.REMARKS,
    )

    /** The sections saved with a form: the photos are uploaded instead. */
    val FORM_SECTIONS: List<ListingSection> = SECTIONS - ListingSection.PHOTOS

    val LABELS: Map<ListingSection, String> = linkedMapOf(
        ListingSection.LOCATION to "Emplacement",
        ListingSection.GPS to "Position GPS",
        ListingSection.PROPERTY to "Le bien",
        ListingSection.AMENITIES to "Eau, électricité, accès",
        ListingSection.TERMS to "Loyer et conditions",
        ListingSection.PHOTOS to "Photos",
        ListingSection.REMARKS to "Vos remarques",
    )

    /** The checklist page's message once a section is saved (the prototype's `SECTION_SAVED`). */
    val SAVED: Map<ListingSection, String> = linkedMapOf(
        ListingSection.LOCATION to "Emplacement enregistré.",
        ListingSection.GPS to "Position GPS enregistrée.",
        ListingSection.PROPERTY to "Caractéristiques du bien enregistrées.",
        ListingSection.AMENITIES to "Équipements enregistrés.",
        ListingSection.TERMS to "Loyer et conditions enregistrés.",
        ListingSection.REMARKS to "Remarques enregistrées.",
    )

    // Sent, and not published yet: the prototype's single "PENDING" step.
    private val SENT = setOf(ListingStatus.UNDER_VERIFICATION, ListingStatus.VERIFIED, ListingStatus.PUBLISHING)

    data class Tag(val label: String, val style: String)

    private val DRAFT_TAG = Tag("Brouillon", "info")
    private val SENT_TAG = Tag("En vérification", "warning")
    private val PUBLISHED_TAG = Tag("Publiée", "success")

    // Rented, sold, expired, cancelled: no wording in the prototype, which stops at the publication.
    private val CLOSED = setOf(ListingStatus.RENTED, ListingStatus.SOLD, ListingStatus.EXPIRED, ListingStatus.CANCELLED)
    private val CLOSED_TAG = Tag("Fermée", "neutral")

    // PENDING (and UNKNOWN, never shown: the page is then locked): no code sets it yet, neither closed nor sent.
    private val PENDING_TAG = Tag("En attente", "warning")

    /** The URL's name of a section: `/listings/{id}/location`. */
    fun slug(section: ListingSection): String = section.name.lowercase()

    /** The form section of a URL's name; null for the photos (their own page) or anything else. */
    fun section(slug: String): ListingSection? = FORM_SECTIONS.firstOrNull { slug(it) == slug }

    fun tag(status: ListingStatus): Tag = when {
        status == ListingStatus.DRAFT -> DRAFT_TAG
        sent(status) -> SENT_TAG
        status == ListingStatus.PUBLISHED -> PUBLISHED_TAG
        status in CLOSED -> CLOSED_TAG
        else -> PENDING_TAG
    }

    /** Being checked by Ndopify: sent, not published yet. */
    fun sent(status: ListingStatus): Boolean = status in SENT

    /** Saved by the agent on site (PHOTOS: enough of them, none refused), as ndopify-server's checklist says. */
    fun done(listing: Listing, section: ListingSection): Boolean =
        listing.sections.any { it.section == section && it.status == ListingSectionStatus.VERIFIED }

    /**
     * Needed to send the listing, as ndopify-server's checklist says (`ListingSectionSummary.required`: only REMARKS
     * isn't, today); a section it doesn't list is.
     */
    fun required(listing: Listing, section: ListingSection): Boolean =
        listing.sections.firstOrNull { it.section == section }?.required ?: true

    /** Every required section done: the listing can be sent. Without its checklist, it can't be told: no. */
    fun complete(listing: Listing): Boolean =
        listing.sections.isNotEmpty() && SECTIONS.filter { required(listing, it) }.all { done(listing, it) }

    /** Only a DRAFT changes: nothing does while it's checked, nor once published. */
    fun editable(listing: Listing): Boolean = listing.status == ListingStatus.DRAFT

    /**
     * Whether a question of *Le bien* (`furniture`, `rooms`, `bathrooms`, `kitchens`, `parkings`, `levels`,
     * `floorLevel`) is asked for a type: the prototype's `LISTING_APPLIES`. None is, before a type is chosen.
     */
    fun applies(question: String, category: PropertyCategory?, type: PropertyType?): Boolean {
        if (type == null) return false
        val residential = category == PropertyCategory.RESIDENTIAL
        return when (question) {
            "furniture" -> residential && type != PropertyType.BUILDING
            "rooms" -> residential && type !in setOf(PropertyType.ROOM, PropertyType.STUDIO, PropertyType.BUILDING)
            "bathrooms" -> type != PropertyType.LAND
            "kitchens" -> residential && type !in setOf(PropertyType.ROOM, PropertyType.BUILDING)
            "parkings" -> type != PropertyType.ROOM
            "levels" -> type in setOf(PropertyType.HOUSE, PropertyType.DUPLEX, PropertyType.BUILDING)
            "floorLevel" -> type in setOf(
                PropertyType.ROOM,
                PropertyType.STUDIO,
                PropertyType.APARTMENT,
                PropertyType.OFFICE,
                PropertyType.RETAIL,
            )
            else -> false
        }
    }

    fun typeFits(category: PropertyCategory?, type: PropertyType?): Boolean =
        category != null && type != null && type in TYPES[category].orEmpty()

    /**
     * The errors of a section's answers, by field: messages of the prototype's `LISTING_RULES`. The questions of
     * *Le bien* that don't apply to the type are ignored (without JS, the page shows them all). [today] is in the
     * tenant's time zone.
     */
    fun errors(
        section: ListingSection,
        form: ListingSectionForm,
        today: LocalDate,
        formatter: TenantFormatter,
    ): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        when (section) {
            ListingSection.LOCATION -> {
                if (form.neighborhoodId?.trim()?.toLongOrNull() == null) {
                    errors["neighborhoodId"] = "Choisissez le quartier du bien."
                }
                val landmark = text(form.landmark)
                if (landmark == null) {
                    errors["landmark"] = "Indiquez un point de repère connu : école, carrefour, commerce…"
                } else if (landmark.length > MAX_LANDMARK) {
                    errors["landmark"] = "Le point de repère ne doit pas dépasser $MAX_LANDMARK caractères."
                }
                if ((text(form.accessDirections)?.length ?: 0) > MAX_ACCESS_DIRECTIONS) {
                    errors["accessDirections"] = "Ces indications ne doivent pas dépasser $MAX_ACCESS_DIRECTIONS caractères."
                }
            }

            ListingSection.GPS ->
                if (coordinate(form.latitude, 90.0) == null || coordinate(form.longitude, 180.0) == null) {
                    errors["latitude"] = "Relevez la position GPS depuis le bien."
                }

            ListingSection.PROPERTY -> {
                val category = category(form.propertyCategory)
                val type = type(form.propertyType)
                if (category == null) errors["propertyCategory"] = "Choisissez l'usage du bien."
                if (type == null || (category != null && !typeFits(category, type))) {
                    errors["propertyType"] = "Choisissez le type de bien."
                }
                if (type != null) {
                    if (applies("furniture", category, type) && furniture(form.furnitureType) == null) {
                        errors["furnitureType"] = "Indiquez si le bien est meublé."
                    }
                    counts(form).forEach { (field, value) ->
                        val asked = field == "area" || applies(COUNT_QUESTIONS.getValue(field), category, type)
                        countError(field, value, asked)?.let { errors[field] = it }
                    }
                } else {
                    countError("area", form.area, true)?.let { errors["area"] = it }
                }
            }

            ListingSection.AMENITIES ->
                if (form.roadAccess !in ROAD_ACCESS) errors["roadAccess"] = "Indiquez l'accès routier au bien."

            ListingSection.TERMS -> {
                val price = text(form.price)
                if (price == null) {
                    errors["price"] = "Indiquez le loyer mensuel."
                } else {
                    val amount = amount(price)
                    if (amount == null) {
                        errors["price"] = "Indiquez le loyer en chiffres, en ${formatter.currency()} par mois."
                    } else if (amount <= 0) {
                        errors["price"] = "Indiquez le loyer mensuel."
                    }
                }
                if (choice(form.securityDeposit, SECURITY_DEPOSITS.toList()) == null) {
                    errors["securityDeposit"] = "Choisissez la caution demandée."
                }
                optionalChoiceError(form.advanceRent, ADVANCE_RENTS.toList())?.let { errors["advanceRent"] = it }
                optionalChoiceError(form.leaseTerm, LEASE_TERMS)?.let { errors["leaseTerm"] = it }
                optionalChoiceError(form.noticePeriod, NOTICE_PERIODS.toList())?.let { errors["noticePeriod"] = it }
                val date = date(form.availableFrom)
                if (date == null) {
                    errors["availableFrom"] = "Indiquez la date à laquelle le bien est libre."
                } else if (date.isAfter(today.plusDays(MAX_AVAILABILITY_DAYS))) {
                    errors["availableFrom"] = "Choisissez une date dans les 12 prochains mois."
                }
            }

            ListingSection.REMARKS ->
                if ((text(form.agentRemarks)?.length ?: 0) > MAX_REMARKS) {
                    errors["agentRemarks"] = "Vos remarques ne doivent pas dépasser $MAX_REMARKS caractères."
                }

            ListingSection.PHOTOS, ListingSection.UNKNOWN -> {}
        }
        return errors
    }

    /**
     * The request saving [section] from valid answers ([errors] is empty): only the section's fields, a question that
     * doesn't apply to the type sent as null, the road access as one of its three booleans.
     */
    fun request(section: ListingSection, form: ListingSectionForm): UpdateListingRequest {
        val request = UpdateListingRequest(version = form.version, section = section)
        return when (section) {
            ListingSection.LOCATION -> request.copy(
                neighborhoodId = form.neighborhoodId?.trim()?.toLongOrNull(),
                landmark = text(form.landmark),
                accessDirections = text(form.accessDirections),
            )

            ListingSection.GPS -> request.copy(
                latitude = coordinate(form.latitude, 90.0),
                longitude = coordinate(form.longitude, 180.0),
            )

            ListingSection.PROPERTY -> {
                val category = category(form.propertyCategory)
                val type = type(form.propertyType)
                fun count(field: String, value: String?): Int? =
                    if (applies(COUNT_QUESTIONS.getValue(field), category, type)) count(value) else null
                request.copy(
                    propertyCategory = category,
                    propertyType = type,
                    furnitureType = if (applies("furniture", category, type)) furniture(form.furnitureType) else null,
                    rooms = count("rooms", form.rooms),
                    livingRooms = count("livingRooms", form.livingRooms),
                    bathrooms = count("bathrooms", form.bathrooms),
                    kitchens = count("kitchens", form.kitchens),
                    parkings = count("parkings", form.parkings),
                    levels = count("levels", form.levels),
                    floorLevel = count("floorLevel", form.floorLevel),
                    area = count(form.area),
                )
            }

            ListingSection.AMENITIES -> request.copy(
                waterMeter = form.waterMeter,
                dedicatedWaterMeter = form.dedicatedWaterMeter,
                waterTank = form.waterTank,
                waterWell = form.waterWell,
                electricityMeter = form.electricityMeter,
                dedicatedElectricityMeter = form.dedicatedElectricityMeter,
                generatorSet = form.generatorSet,
                solarPanel = form.solarPanel,
                roadPaved = form.roadAccess == ROAD_PAVED,
                trackAccessibleByCar = form.roadAccess == TRACK_ACCESSIBLE_BY_CAR,
                trackInaccessibleByCar = form.roadAccess == TRACK_INACCESSIBLE_BY_CAR,
                securedFenced = form.securedFenced,
                securityGuard = form.securityGuard,
                videoSurveillance = form.videoSurveillance,
            )

            ListingSection.TERMS -> request.copy(
                price = text(form.price)?.let { amount(it) },
                securityDeposit = choice(form.securityDeposit, SECURITY_DEPOSITS.toList()),
                advanceRent = choice(form.advanceRent, ADVANCE_RENTS.toList()),
                leaseTerm = choice(form.leaseTerm, LEASE_TERMS),
                noticePeriod = choice(form.noticePeriod, NOTICE_PERIODS.toList()),
                availableFrom = date(form.availableFrom),
            )

            ListingSection.REMARKS -> request.copy(agentRemarks = text(form.agentRemarks))

            ListingSection.PHOTOS, ListingSection.UNKNOWN -> request
        }
    }

    private fun counts(form: ListingSectionForm): List<Pair<String, String?>> = listOf(
        "rooms" to form.rooms,
        "livingRooms" to form.livingRooms,
        "bathrooms" to form.bathrooms,
        "kitchens" to form.kitchens,
        "parkings" to form.parkings,
        "levels" to form.levels,
        "floorLevel" to form.floorLevel,
        "area" to form.area,
    )

    // Only for a question asked: required (rooms, bathrooms, area) or not, a whole number, the area more than 0
    private fun countError(field: String, value: String?, asked: Boolean): String? {
        if (!asked) return null
        val text = text(value) ?: return if (field in REQUIRED_COUNTS) COUNT_REQUIRED else null
        val count = count(text) ?: return COUNT_INVALID
        return if (field == "area" && count <= 0) "Indiquez la surface en m²." else null
    }

    private fun optionalChoiceError(value: String?, choices: List<Int>): String? =
        if (text(value) != null && choice(value, choices) == null) CHOICE_INVALID else null

    private fun text(value: String?): String? = value?.trim()?.ifEmpty { null }

    /** "3", never "-1", "1.5" or "1e3". */
    fun count(value: String?): Int? =
        text(value)?.takeIf { it.length <= MAX_COUNT_DIGITS && it.all { c -> c.isDigit() } }?.toInt()

    private fun choice(value: String?, choices: List<Int>): Int? = count(value)?.takeIf { it in choices }

    /** "150 000" or "150.000" → 150000; anything but digits and separators ("150k") → null, unlike a typo read as another amount. */
    fun amount(value: String): Long? =
        value.filterNot { it.isWhitespace() || it == '.' || it == '\u00a0' || it == '\u202f' }
            .takeIf { it.isNotEmpty() && it.length <= MAX_AMOUNT_DIGITS && it.all { c -> c.isDigit() } }
            ?.toLong()

    // "4.0335" or "4,0335", within ±[max]
    private fun coordinate(value: String?, max: Double): Double? =
        text(value)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -max..max }

    private fun date(value: String?): LocalDate? =
        text(value)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun category(value: String?): PropertyCategory? =
        PropertyCategory.entries.firstOrNull { it.name == value && it != PropertyCategory.UNKNOWN }

    private fun type(value: String?): PropertyType? =
        PropertyType.entries.firstOrNull { it.name == value && it != PropertyType.UNKNOWN }

    private fun furniture(value: String?): FurnitureType? =
        FurnitureType.entries.firstOrNull { it.name == value && it != FurnitureType.UNKNOWN }
}
