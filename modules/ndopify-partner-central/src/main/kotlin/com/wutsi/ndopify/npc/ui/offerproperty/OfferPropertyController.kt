package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.findproperty.SubmittedMandate
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.npc.ui.listing.ListingViews
import com.wutsi.ndopify.npc.ui.listing.RentalTerms
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.login.LoginController
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.LocalDate

/**
 * The public "Proposer un bien" wizard, on a single page (`offer-property.html`), built like `/find-property`: seven
 * steps — [STEPS]: property → address → terms → features → amenities → contact → review — then the confirmation.
 * - `GET /offer-property?step=…` shows a step; `POST /offer-property/{step}` validates it ([OfferPropertyRules]),
 *   stores its answers in the session's [PendingListing] and redirects to the next step (or back to the review when
 *   `returnTo=review`).
 * - `POST /offer-property/review` creates a LISTING mandate (`POST /v1/mandates`) from the session's answers, then
 *   shows the confirmation (`?step=confirmation`) while a [SubmittedMandate] is under [SUBMITTED_ATTRIBUTE].
 *
 * The URL is never trusted: a step is only shown once every step before it is complete, otherwise the first
 * incomplete one is. Completeness depends on the day (the availability date), hence the [Clock].
 *
 * No `step` is the entry point ([show]), and always starts a new run: the answers of a previous one (abandoned, or
 * already submitted) are dropped, never shown. Every in-run URL carries its step.
 */
@Controller
@RequestMapping("/offer-property")
class OfferPropertyController(
    private val mandateClient: NdMandateClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    private val sector = SectorValidator(locationClient, tenantContext)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OfferPropertyController::class.java)

        const val VIEW = "offer-property"
        const val STEP_PROPERTY = OfferPropertyRules.STEP_PROPERTY
        const val STEP_ADDRESS = OfferPropertyRules.STEP_ADDRESS
        const val STEP_TERMS = OfferPropertyRules.STEP_TERMS
        const val STEP_FEATURES = OfferPropertyRules.STEP_FEATURES
        const val STEP_AMENITIES = OfferPropertyRules.STEP_AMENITIES
        const val STEP_CONTACT = OfferPropertyRules.STEP_CONTACT
        const val STEP_REVIEW = OfferPropertyRules.STEP_REVIEW
        const val STEP_CONFIRMATION = "confirmation"
        const val RETURN_TO_REVIEW = "review"
        const val SUBMITTED_ATTRIBUTE = "npc.submitted-listing"

        val STEPS = OfferPropertyRules.STEPS

        private const val HAS_PARKING = "YES"
        private const val HAS_NO_PARKING = "NO"
    }

    /** The current tenant, for the header on every step: its logo. */
    @ModelAttribute("tenant")
    fun tenant(): Tenant = tenantContext.getTenant()

    @GetMapping
    fun show(
        @RequestParam(name = "step", required = false) requestedStep: String?,
        @RequestParam(name = "type", required = false) type: String?,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val submitted = session.getAttribute(SUBMITTED_ATTRIBUTE) as SubmittedMandate?
        if (requestedStep == STEP_CONFIRMATION && submitted != null) {
            model.addAttribute("step", STEP_CONFIRMATION)
            model.addAttribute("maskedEmail", LoginController.maskEmail(submitted.email))
            model.addAttribute("whatsappNumber", submitted.whatsappNumber)
            model.addAttribute("contactPeriodLabel", MandateLabels.CONTACT_PERIOD_LABELS[submitted.preferredContactPeriod])
            model.addAttribute("listingType", submitted.listingType)
            model.addAttribute("projectLabel", OfferPropertyRules.projectLabel(submitted.listingType))
            model.addAttribute("exitUrl", exitUrl(submitted.listingType))
            return VIEW
        }

        // The public site's links preselect the project (?type=SALE|RENTAL), for display only: the session keeps
        // what the visitor posts.
        val pending = if (requestedStep == null) {
            // The entry point: a new run, never the answers of a previous one (an abandoned run, or a submitted
            // listing whose confirmation is still in the session).
            session.removeAttribute(PendingListing.SESSION_ATTRIBUTE)
            session.removeAttribute(SUBMITTED_ATTRIBUTE)
            PendingListing(listingType = FindPropertyRules.parseListingType(type))
        } else {
            pending(session)
        }
        val requested = STEPS.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = STEPS.indexOf(firstIncomplete(pending))
        return step(STEPS[minOf(requested, allowed)], pending, returnTo, emptyMap(), model)
    }

    @PostMapping("/$STEP_PROPERTY")
    fun property(
        @ModelAttribute form: PropertyForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val answers = pending(session).copy(
            listingType = form.listingType,
            propertyCategory = form.propertyCategory,
        )
        val errors = errors(
            "listingType" to OfferPropertyRules.listingType(answers.listingType),
            "propertyCategory" to OfferPropertyRules.propertyCategory(answers.propertyCategory),
        )
        return save(STEP_PROPERTY, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_ADDRESS")
    fun address(
        @ModelAttribute form: AddressForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_ADDRESS, pending)) return redirect(firstIncomplete(pending))

        val answers = pending.copy(cityId = form.cityId, neighborhoodId = form.neighborhoodId)
        val errors = errors(
            "cityId" to OfferPropertyRules.cityId(answers.cityId),
            "neighborhoodId" to OfferPropertyRules.neighborhoodId(answers.neighborhoodId),
        ).ifEmpty { addressErrors(answers.cityId!!, answers.neighborhoodId!!) }
        return save(STEP_ADDRESS, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_TERMS")
    fun terms(
        @ModelAttribute form: TermsForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_TERMS, pending)) return redirect(firstIncomplete(pending))

        val today = today()
        val dateText = form.availableFrom?.trim().orEmpty()
        val date = OfferPropertyRules.parseDate(dateText)
        // Something typed that isn't a date is out of the allowed range, not a silent "no date".
        val dateError = if (dateText.isNotEmpty() && date == null) {
            OfferPropertyRules.availableFrom(today.minusDays(1), today)
        } else {
            OfferPropertyRules.availableFrom(date, today)
        }
        val answers = pending.copy(
            priceAmount = FindPropertyRules.parseAmount(form.priceAmount),
            // A sale has no advance nor deposit: they are not asked, and any posted value is dropped.
            advanceRent = months(form.advanceRent, blank = null).takeIf { !pending.isSale() },
            securityDeposit = months(form.securityDeposit, blank = null).takeIf { !pending.isSale() },
            availableFrom = date,
        )
        val errors = errors(
            "priceAmount" to OfferPropertyRules.priceAmount(answers.priceAmount, formatter(), answers.listingType),
            "advanceRent" to OfferPropertyRules.advanceRent(answers.advanceRent).takeIf { !answers.isSale() },
            "securityDeposit" to OfferPropertyRules.securityDeposit(answers.securityDeposit)
                .takeIf { !answers.isSale() },
            "availableFrom" to dateError,
        )
        return save(STEP_TERMS, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_FEATURES")
    fun features(
        @ModelAttribute form: FeaturesForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_FEATURES, pending)) return redirect(firstIncomplete(pending))

        // Questions that don't apply to the posted type are dropped: without JS the page shows them all.
        val category = pending.propertyCategory
        val listingType = pending.listingType
        val typeAnswer = form.propertyType?.takeIf { it.isNotEmpty() }
        val type = FindPropertyRules.parsePropertyType(typeAnswer)
        val needsParking = OfferPropertyRules.needsParking(category, type, listingType)
        val hasParking = form.hasParking
        val places = form.parkings?.takeIf { it.isNotEmpty() }
        val areaText = form.area?.trim().orEmpty()
        val area = FindPropertyRules.parseAmount(areaText)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        val needsArea = OfferPropertyRules.needsArea(category, type, listingType)
        // Only a plot of land sold names its surface: a commercial premises keeps the optional wording.
        val areaRequired = pending.isSale() && type == PropertyType.LAND
        val documents = form.ownershipDocuments
            .filter { it in OfferPropertyRules.ownershipDocumentTypes(listingType, type) }
            .distinct()
        val answers = pending.copy(
            propertyType = typeAnswer,
            furnitureType = form.furnitureType?.takeIf {
                OfferPropertyRules.needsFurniture(category, type, listingType)
            },
            rooms = form.rooms?.takeIf { OfferPropertyRules.needsRooms(category, type, listingType) },
            parkings = when {
                !needsParking -> null
                hasParking == HAS_NO_PARKING -> OfferPropertyRules.NO_PARKING
                hasParking == HAS_PARKING -> places
                else -> null
            },
            area = area.takeIf { needsArea },
            ownershipDocuments = documents,
        )
        val errors = errors(
            "propertyType" to OfferPropertyRules.propertyType(category, typeAnswer, listingType),
            "furnitureType" to OfferPropertyRules.furnitureType(answers.furnitureType)
                .takeIf { OfferPropertyRules.needsFurniture(category, type, listingType) },
            "rooms" to OfferPropertyRules.rooms(answers.rooms)
                .takeIf { OfferPropertyRules.needsRooms(category, type, listingType) },
            // No answer is the "Parking ou garage" question's error; the places' own is under "Combien de places ?".
            "hasParking" to OfferPropertyRules.PARKING_ANSWER_REQUIRED
                .takeIf { needsParking && hasParking != HAS_PARKING && hasParking != HAS_NO_PARKING },
            "parkings" to when {
                !needsParking || (hasParking != HAS_PARKING && hasParking != HAS_NO_PARKING) -> null
                hasParking == HAS_PARKING && places == null -> OfferPropertyRules.PARKING_PLACES_REQUIRED
                else -> OfferPropertyRules.parkings(answers.parkings)
            },
            // Something typed that isn't a usable number is an error too, not a silent "no area".
            "area" to when {
                !needsArea -> null
                area == null && areaText.isEmpty() -> OfferPropertyRules.area(null, required = areaRequired)
                else -> OfferPropertyRules.area(area ?: 0, required = areaRequired)
            },
        )
        // The posted choice, so that "Oui" stays checked when only its places are missing (never on a redirect).
        if (errors.isNotEmpty()) model.addAttribute("hasParking", hasParking)
        return save(STEP_FEATURES, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AMENITIES")
    fun amenities(
        @ModelAttribute form: AmenitiesForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AMENITIES, pending)) return redirect(firstIncomplete(pending))

        // Unknown names are dropped, not errors: there's nothing the visitor could fix.
        val answers = pending.copy(
            roadAccess = form.roadAccess?.takeIf { it.isNotEmpty() },
            amenities = form.amenities.filter { it in OfferPropertyRules.AMENITIES }.toSet(),
        )
        val errors = errors("roadAccess" to OfferPropertyRules.roadAccess(answers.roadAccess))
        return save(STEP_AMENITIES, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_CONTACT")
    fun contact(
        @ModelAttribute form: ContactForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_CONTACT, pending)) return redirect(firstIncomplete(pending))

        val answers = pending.copy(
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            email = form.email.trim().lowercase(),
            whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
            preferredContactPeriod = form.preferredContactPeriod,
        )
        val errors = errors(
            "firstName" to JoinRules.firstName(answers.firstName),
            "lastName" to JoinRules.lastName(answers.lastName),
            "email" to JoinRules.email(answers.email),
            "whatsappNumber" to JoinRules.whatsappNumber(answers.whatsappNumber),
            "preferredContactPeriod" to OfferPropertyRules.preferredContactPeriod(answers.preferredContactPeriod),
        )
        return save(STEP_CONTACT, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val pending = pending(session)
        // toCreateMandateRequest fills placeholders for what's missing: only call it on a complete session.
        if (firstIncomplete(pending) != STEP_REVIEW) return redirect(firstIncomplete(pending))

        try {
            mandateClient.create(pending.toCreateMandateRequest(tenantContext.getTenant().currencyCode))
        } catch (ex: RestClientException) {
            // 4xx, 5xx or unreachable: nothing the visitor can fix, except trying again.
            LOGGER.error("Unable to create the listing", ex)
            model.addAttribute("error", "Envoi impossible pour le moment. Réessayez dans quelques instants.")
            return step(STEP_REVIEW, pending, null, emptyMap(), model)
        }

        session.removeAttribute(PendingListing.SESSION_ATTRIBUTE)
        session.setAttribute(
            SUBMITTED_ATTRIBUTE,
            SubmittedMandate(
                email = pending.email.orEmpty(),
                whatsappNumber = pending.whatsappNumber.orEmpty(),
                preferredContactPeriod = pending.preferredContactPeriod,
                listingType = pending.listingType ?: ListingType.RENTAL,
            ),
        )
        return redirect(STEP_CONFIRMATION)
    }

    /** Stores valid answers and moves on, or shows the step again with its errors (the session is left untouched). */
    private fun save(
        step: String,
        answers: PendingListing,
        errors: Map<String, String>,
        returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        if (errors.isNotEmpty()) {
            return step(step, answers, returnTo, errors, model)
        }

        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, answers)
        session.removeAttribute(SUBMITTED_ATTRIBUTE)
        val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, firstIncomplete(answers), compareBy { STEPS.indexOf(it) }))
    }

    private fun step(
        step: String,
        answers: PendingListing,
        returnTo: String?,
        errors: Map<String, String>,
        model: Model,
    ): String {
        model.addAttribute("step", step)
        model.addAttribute("stepNumber", STEPS.indexOf(step) + 1)
        model.addAttribute("stepCount", STEPS.size)
        model.addAttribute("projectLabel", OfferPropertyRules.projectLabel(answers.listingType))
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("exitUrl", exitUrl(answers))
        // "Revenir à la revue" instead of "Suivant": only once every answer is in.
        model.addAttribute(
            "returning",
            returnTo == RETURN_TO_REVIEW && step != STEP_REVIEW && firstIncomplete(answers) == STEP_REVIEW,
        )

        if (step == STEP_PROPERTY) {
            model.addAttribute("sale", answers.isSale())
            model.addAttribute("listingTypeLabels", MandateLabels.byName(MandateLabels.OWNER_LISTING_TYPE_LABELS))
            model.addAttribute("categoryLabels", MandateLabels.byName(MandateLabels.CATEGORY_LABELS))
            model.addAttribute("categoryDetailLabels", CATEGORY_DETAIL_LABELS)
        }
        if (step == STEP_ADDRESS || step == STEP_REVIEW) {
            // Names of the chosen city and neighbourhood: the selects' initial options, and the review.
            val locations = namesOrEmpty(listOfNotNull(answers.cityId, answers.neighborhoodId))
            model.addAttribute("city", answers.cityId?.let { locations[it] })
            model.addAttribute("neighborhood", answers.neighborhoodId?.let { locations[it] })
        }
        if (step == STEP_TERMS) {
            val today = today()
            model.addAttribute("sale", answers.isSale())
            model.addAttribute("currency", formatter().currency())
            model.addAttribute("availableMin", today.toString())
            model.addAttribute("availableMax", today.plusDays(OfferPropertyRules.MAX_AVAILABILITY_DAYS).toString())
            model.addAttribute("rentalMonths", RentalTerms.MONTHS)
        }
        if (step == STEP_FEATURES) {
            val type = FindPropertyRules.parsePropertyType(answers.propertyType)
            val sale = answers.isSale()
            val category = answers.propertyCategory
            val residential = category == PropertyCategory.RESIDENTIAL
            model.addAttribute("sale", sale)
            model.addAttribute(
                "propertyTypes",
                OfferPropertyRules.propertyTypes(answers.propertyCategory!!, answers.listingType),
            )
            model.addAttribute("propertyTypeLabels", PROPERTY_TYPE_LABELS)
            model.addAttribute("furnitureLabels", MandateLabels.byName(MandateLabels.FURNITURE_TYPE_LABELS))
            model.addAttribute("currency", formatter().currency())
            model.addAttribute("ownershipDocumentLabels", MandateLabels.byName(MandateLabels.TITLE_DOCUMENT_LABELS))
            model.addAttribute("ownershipDocumentNames", HashSet(answers.ownershipDocuments.map { it.name }))
            // A sale renders the follow-up questions until its type is known, then only the ones its type needs.
            model.addAttribute(
                "showsRooms",
                residential && (!sale || type == null || OfferPropertyRules.needsRooms(category, type, answers.listingType)),
            )
            model.addAttribute(
                "requiresRooms",
                residential && (!sale || (type != null && OfferPropertyRules.needsRooms(category, type, answers.listingType))),
            )
            model.addAttribute(
                "showsParking",
                residential && (!sale || type == null || OfferPropertyRules.needsParking(category, type, answers.listingType)),
            )
            model.addAttribute(
                "requiresParking",
                residential && (!sale || (type != null && OfferPropertyRules.needsParking(category, type, answers.listingType))),
            )
            // Every sale may declare its documents until its type is known; only a plot of land then keeps them.
            model.addAttribute("asksOwnershipDocuments", sale && (type == null || type == PropertyType.LAND))
            model.addAttribute("asksArea", !residential(category) || sale)
            model.addAttribute("landSale", sale && type == PropertyType.LAND)
        }
        if (step == STEP_AMENITIES) {
            // The owner's wording, as the listing's (road access: one radio, in the mandates' wording)
            model.addAttribute("amenityLabels", MandateLabels.AMENITY_LABELS + ListingViews.AMENITY_LABELS)
        }
        if (step == STEP_REVIEW) {
            model.addAttribute("categoryLabel", MandateLabels.ownerProject(answers.propertyCategory, answers.listingType))
            model.addAttribute("locationLabel", locationLabel(model))
            model.addAttribute("termsLines", termsLines(answers))
            model.addAttribute("propertyLabel", propertyLabel(answers))
            model.addAttribute("amenitiesLabel", amenitiesLabel(answers))
            model.addAttribute("contactLines", contactLines(answers))
            model.addAttribute(
                "ownershipDocumentsLabel",
                answers.ownershipDocuments
                    .takeIf { answers.isSale() && FindPropertyRules.parsePropertyType(answers.propertyType) == PropertyType.LAND }
                    ?.let { MandateLabels.ownershipDocuments(it) },
            )
        }
        return VIEW
    }

    /** "Bastos, Yaoundé", from the names [step] has just put in the model. */
    private fun locationLabel(model: Model): String? {
        val city = model.getAttribute("city") as Location?
        val neighborhood = model.getAttribute("neighborhood") as Location?
        return if (city == null || neighborhood == null) null else "${neighborhood.name}, ${city.name}"
    }

    /**
     * "150 000 FCFA par mois", "Avance de 3 mois", "Caution de 2 mois", "Disponible dès maintenant"; a sale's price
     * has no "/ mois" and no advance nor deposit line, and a sold plot adds its implied rate right after it.
     */
    private fun termsLines(answers: PendingListing): List<String> {
        val formatter = formatter()
        val availability = answers.availableFrom?.let {
            if (it == formatter.today(clock)) "Disponible dès maintenant" else "Disponible dès le " + formatter.date(it)
        }
        if (answers.isSale()) {
            val type = FindPropertyRules.parsePropertyType(answers.propertyType)
            val rate = OfferPropertyRules.pricePerSqm(answers.listingType, type, answers.priceAmount, answers.area)
            return listOfNotNull(
                answers.priceAmount?.let { formatter.money(it) },
                rate?.let {
                    "≈ " + formatter.money(it) + "/m² pour " + MandateLabels.formatNumber(answers.area!!) + " m²"
                },
                availability,
            )
        }
        return listOfNotNull(
            answers.priceAmount?.let { formatter.money(it) + " par mois" },
            answers.advanceRent?.let { "Avance de $it mois" } ?: "Sans avance de loyer",
            answers.securityDeposit?.let { "Caution de $it mois" } ?: "Sans caution",
            availability,
        )
    }

    /** "Appartement · Non meublé · 3 chambres · Sans parking", as the prototype's describeProperty. */
    private fun propertyLabel(answers: PendingListing): String {
        val category = answers.propertyCategory
        val type = FindPropertyRules.parsePropertyType(answers.propertyType)
        val parts = listOfNotNull(
            PROPERTY_TYPE_LABELS[type],
            MandateLabels.FURNITURE_LABELS[answers.furnitureType]
                ?.takeIf { OfferPropertyRules.needsFurniture(category, type, answers.listingType) },
            MandateLabels.ROOM_LABELS[answers.rooms]
                ?.takeIf { OfferPropertyRules.needsRooms(category, type, answers.listingType) },
            MandateLabels.PARKING_LABELS[answers.parkings]
                ?.takeIf { OfferPropertyRules.needsParking(category, type, answers.listingType) },
            answers.area?.takeIf { OfferPropertyRules.needsArea(category, type, answers.listingType) }
                ?.let { MandateLabels.formatNumber(it) + " m²" },
        )
        return parts.joinToString(" · ")
    }

    /** The road access first, then the checked amenities, in form order. */
    private fun amenitiesLabel(answers: PendingListing): String =
        (
            listOfNotNull(answers.roadAccess) +
                OfferPropertyRules.AMENITIES.filter { it in answers.amenities }
            )
            .mapNotNull { MandateLabels.AMENITY_LABELS[it] }
            .joinToString(", ")

    private fun contactLines(answers: PendingListing): List<String> =
        listOfNotNull(
            "${answers.firstName.orEmpty()} ${answers.lastName.orEmpty()}".trim().ifEmpty { null },
            answers.email,
            answers.whatsappNumber,
            MandateLabels.CONTACT_PERIOD_LABELS[answers.preferredContactPeriod]?.let { "Contact $it" },
        )

    /** The city must be one of the tenant's cities, and the neighbourhood one of that city's. */
    private fun addressErrors(cityId: Long, neighborhoodId: Long): Map<String, String> =
        sector.errors(cityId, listOf(neighborhoodId)).mapKeys { (key, _) ->
            if (key == "neighborhoodIds") "neighborhoodId" else key
        }.mapValues { (key, message) ->
            if (key == "neighborhoodId") "Choisissez un quartier de la ville sélectionnée." else message
        }

    /**
     * The names to show, best effort: when ndopify-server is down, the page (and the review's "Envoi impossible")
     * still renders, without them. Validation never uses this: it calls [locations] and fails if it fails.
     */
    private fun namesOrEmpty(ids: List<Long>): Map<Long, Location> =
        try {
            sector.locations(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("Unable to load the locations $ids", ex)
            emptyMap()
        }

    /** A number of months: [blank] when nothing was posted, 0 (invalid) for something that isn't a number. */
    private fun months(value: String?, blank: Int?): Int? {
        val text = value?.trim().orEmpty()
        return if (text.isEmpty()) blank else text.toIntOrNull() ?: 0
    }

    /** "Quitter" and the confirmation: the owners' page of the project on the tenant's public portal. */
    private fun exitUrl(answers: PendingListing): String? = exitUrl(answers.listingType)

    private fun exitUrl(listingType: ListingType?): String? =
        tenantContext.getTenant().publicPortalUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?.let { if (FindPropertyRules.isSale(listingType)) "$it/owner/sell" else "$it/owner" }

    private fun residential(category: PropertyCategory?) = category == PropertyCategory.RESIDENTIAL

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    private fun today(): LocalDate = formatter().today(clock)

    private fun firstIncomplete(pending: PendingListing): String = pending.firstIncompleteStep(today())

    private fun isAllowed(step: String, pending: PendingListing): Boolean =
        STEPS.indexOf(step) <= STEPS.indexOf(firstIncomplete(pending))

    /** Every step has its own URL: the bare `/offer-property` is the entry point, which starts a new run. */
    private fun redirect(step: String): String = "redirect:/offer-property?step=$step"

    private fun errors(vararg errors: Pair<String, String?>): Map<String, String> =
        errors.mapNotNull { (field, message) -> message?.let { field to it } }.toMap()

    private fun pending(session: HttpSession): PendingListing =
        session.getAttribute(PendingListing.SESSION_ATTRIBUTE) as PendingListing? ?: PendingListing()
}

// The category's detail text at step 1: a sale offers a plot to build on (prototype's choice__detail).
private val CATEGORY_DETAIL_LABELS: Map<String, String> = mapOf(
    "RENTAL_RESIDENTIAL" to "Pour y habiter\u00a0: chambre, studio, appartement, villa, immeuble…",
    "SALE_RESIDENTIAL" to "Pour y habiter\u00a0: appartement, villa, immeuble, terrain à bâtir…",
    "RENTAL_COMMERCIAL" to "Pour une activité\u00a0: bureau, boutique, entrepôt…",
    "SALE_COMMERCIAL" to "Pour une activité\u00a0: bureau, boutique, entrepôt…",
)

// "Villa, maison" is one answer, as in the prototype: it is sent as HOUSE. Land is only a sale's: it keeps its label
// even though the rental map no longer lists it.
private val PROPERTY_TYPE_LABELS: Map<PropertyType, String> =
    (OfferPropertyRules.CATEGORIES_BY_TYPE.keys + PropertyType.LAND).associateWith { type ->
        if (type == PropertyType.HOUSE) "Villa, maison" else MandateLabels.propertyType(type)
    }
