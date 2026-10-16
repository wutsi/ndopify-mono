package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyController.Companion.STEPS
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.npc.ui.login.LoginController
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.Tenant
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

/**
 * The public "Trouver un bien" wizard, on a single page (`find-property.html`) like `/join`, with seven steps —
 * [STEPS]: need → area → property → amenities → budget → contact → review — then the confirmation.
 * - `GET /find-property?step=…` shows a step; `POST /find-property/{step}` validates it, stores its answers in the
 *   session's [PendingMandate] and redirects to the next step (or back to the review when `returnTo=review`).
 * - `POST /find-property/review` creates a TENANT mandate (`POST /v1/mandates`) from the session's answers, then
 *   shows the confirmation (`?step=confirmation`) while a [SubmittedMandate] is in the session.
 *
 * The URL is never trusted: a step is only shown once every step before it is complete, otherwise the first
 * incomplete one is. Answers only reach the session once validated, so the review submits the session, never the
 * page. ndopify-server doesn't validate mandates yet: [FindPropertyRules] and the location checks here are all the
 * validation a mandate gets.
 */
@Controller
@RequestMapping("/find-property")
class FindPropertyController(
    private val mandateClient: NdMandateClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FindPropertyController::class.java)

        const val VIEW = "find-property"
        const val STEP_NEED = "need"
        const val STEP_AREA = "area"
        const val STEP_PROPERTY = "property"
        const val STEP_AMENITIES = "amenities"
        const val STEP_BUDGET = "budget"
        const val STEP_CONTACT = "contact"
        const val STEP_REVIEW = "review"
        const val STEP_CONFIRMATION = "confirmation"
        const val RETURN_TO_REVIEW = "review"

        val STEPS = listOf(STEP_NEED, STEP_AREA, STEP_PROPERTY, STEP_AMENITIES, STEP_BUDGET, STEP_CONTACT, STEP_REVIEW)

        // Labels of the prototype (find-property.js).
        private val CATEGORY_LABELS = mapOf(
            PropertyCategory.RESIDENTIAL to "usage résidentiel",
            PropertyCategory.COMMERCIAL to "usage commercial",
        )

        private val CONTACT_PERIOD_LABELS = mapOf(
            ContactPeriodType.MORNING to "le matin",
            ContactPeriodType.AFTERNOON to "l'après-midi",
            ContactPeriodType.EVENING to "le soir",
        )
    }

    /** The current tenant, for the header on every step: its logo, and "Quitter" back to its public portal. */
    @ModelAttribute("tenant")
    fun tenant(): Tenant = tenantContext.getTenant()

    @GetMapping
    fun show(
        @RequestParam(name = "step", required = false) requestedStep: String?,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val submitted = submitted(session)
        if (requestedStep == STEP_CONFIRMATION && submitted != null) {
            model.addAttribute("step", STEP_CONFIRMATION)
            model.addAttribute("maskedEmail", LoginController.maskEmail(submitted.email))
            model.addAttribute("whatsappNumber", submitted.whatsappNumber)
            model.addAttribute("contactPeriodLabel", CONTACT_PERIOD_LABELS[submitted.preferredContactPeriod])
            return VIEW
        }

        val pending = pending(session)
        val requested = STEPS.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = STEPS.indexOf(pending.firstIncompleteStep())
        return step(STEPS[minOf(requested, allowed)], pending, returnTo, emptyMap(), model)
    }

    @PostMapping("/$STEP_NEED")
    fun need(
        @ModelAttribute form: NeedForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val answers = pending(session).copy(propertyCategory = form.propertyCategory)
        val errors = errors("propertyCategory" to FindPropertyRules.propertyCategory(answers.propertyCategory))
        return save(STEP_NEED, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AREA")
    fun area(
        @ModelAttribute form: AreaForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AREA, pending)) return redirect(pending.firstIncompleteStep())

        val answers = pending.copy(cityId = form.cityId, neighborhoodIds = form.neighborhoodIds.distinct())
        val errors = errors(
            "cityId" to FindPropertyRules.cityId(answers.cityId),
            "neighborhoodIds" to FindPropertyRules.neighborhoodIds(answers.neighborhoodIds),
        ).ifEmpty { areaErrors(answers.cityId!!, answers.neighborhoodIds) }
        return save(STEP_AREA, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_PROPERTY")
    fun property(
        @ModelAttribute form: PropertyForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_PROPERTY, pending)) return redirect(pending.firstIncompleteStep())

        // Questions that don't apply to the posted type are dropped: without JS the page shows them all.
        val type = form.propertyType?.takeIf { it.isNotEmpty() }
        val areaText = form.area?.trim().orEmpty()
        val area = FindPropertyRules.parseAmount(areaText)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        val answers = pending.copy(
            propertyType = type,
            rooms = form.rooms?.takeIf { FindPropertyRules.needsRooms(type) },
            furnitureType = form.furnitureType?.takeIf { FindPropertyRules.needsFurniture(type) },
            parkings = form.parkings?.takeIf { it.isNotEmpty() && FindPropertyRules.asksParkings(type) },
            area = area,
        )
        val errors = errors(
            "propertyType" to FindPropertyRules.propertyType(answers.propertyCategory, type),
            "rooms" to if (FindPropertyRules.needsRooms(type)) FindPropertyRules.rooms(answers.rooms) else null,
            "furnitureType" to
                if (FindPropertyRules.needsFurniture(type)) FindPropertyRules.furnitureType(answers.furnitureType) else null,
            "parkings" to FindPropertyRules.parkings(answers.parkings),
            // Something typed that isn't a usable number is an error too, not a silent "no area".
            "area" to if (areaText.isNotEmpty() && area == null) {
                FindPropertyRules.area(0)
            } else {
                FindPropertyRules.area(
                area
            )
            },
        )
        return save(STEP_PROPERTY, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AMENITIES")
    fun amenities(
        @ModelAttribute form: AmenitiesForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AMENITIES, pending)) return redirect(pending.firstIncompleteStep())

        // Unknown names are dropped, not errors: there's nothing the visitor could fix.
        val answers = pending.copy(amenities = form.amenities.filter { it in PendingMandate.AMENITIES }.toSet())
        return save(STEP_AMENITIES, answers, emptyMap(), returnTo, session, model)
    }

    @PostMapping("/$STEP_BUDGET")
    fun budget(
        @ModelAttribute form: BudgetForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_BUDGET, pending)) return redirect(pending.firstIncompleteStep())

        val answers = pending.copy(
            priceAmount = FindPropertyRules.parseAmount(form.priceAmount),
            movingPeriod = form.movingPeriod,
        )
        val errors = errors(
            "priceAmount" to FindPropertyRules.priceAmount(answers.priceAmount),
            "movingPeriod" to FindPropertyRules.movingPeriod(answers.movingPeriod),
        )
        return save(STEP_BUDGET, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_CONTACT")
    fun contact(
        @ModelAttribute form: ContactForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_CONTACT, pending)) return redirect(pending.firstIncompleteStep())

        val answers = pending.copy(
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            email = form.email.trim().lowercase(),
            whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
            preferredContactPeriod = form.preferredContactPeriod,
            comments = form.comments.trim(),
        )
        val errors = errors(
            "firstName" to JoinRules.firstName(answers.firstName),
            "lastName" to JoinRules.lastName(answers.lastName),
            "email" to JoinRules.email(answers.email),
            "whatsappNumber" to JoinRules.whatsappNumber(answers.whatsappNumber),
            "preferredContactPeriod" to FindPropertyRules.preferredContactPeriod(answers.preferredContactPeriod),
            "comments" to FindPropertyRules.comments(answers.comments),
        )
        return save(STEP_CONTACT, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val pending = pending(session)
        if (pending.firstIncompleteStep() != STEP_REVIEW) return redirect(pending.firstIncompleteStep())

        try {
            mandateClient.create(pending.toCreateMandateRequest(tenantContext.getTenant().currencyCode))
        } catch (ex: RestClientException) {
            // 4xx, 5xx or unreachable: nothing the visitor can fix, except trying again.
            LOGGER.error("Unable to create the mandate", ex)
            model.addAttribute("error", "Envoi impossible pour le moment. Réessayez dans quelques instants.")
            return step(STEP_REVIEW, pending, null, emptyMap(), model)
        }

        session.removeAttribute(PendingMandate.SESSION_ATTRIBUTE)
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate(
                email = pending.email.orEmpty(),
                whatsappNumber = pending.whatsappNumber.orEmpty(),
                preferredContactPeriod = pending.preferredContactPeriod,
            ),
        )
        return redirect(STEP_CONFIRMATION)
    }

    /** Stores valid answers and moves on, or shows the step again with its errors (the session is left untouched). */
    private fun save(
        step: String,
        answers: PendingMandate,
        errors: Map<String, String>,
        returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        if (errors.isNotEmpty()) {
            return step(step, answers, returnTo, errors, model)
        }

        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, answers)
        session.removeAttribute(SubmittedMandate.SESSION_ATTRIBUTE)
        val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, answers.firstIncompleteStep(), compareBy { STEPS.indexOf(it) }))
    }

    private fun step(
        step: String,
        answers: PendingMandate,
        returnTo: String?,
        errors: Map<String, String>,
        model: Model,
    ): String {
        model.addAttribute("step", step)
        model.addAttribute("stepNumber", STEPS.indexOf(step) + 1)
        model.addAttribute("stepCount", STEPS.size)
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        // "Revenir à la revue" instead of "Suivant": only once every answer is in.
        model.addAttribute(
            "returning",
            returnTo == RETURN_TO_REVIEW && step != STEP_REVIEW && answers.firstIncompleteStep() == STEP_REVIEW,
        )

        when (step) {
            STEP_AREA, STEP_REVIEW -> {
                // Names of the chosen city and neighbourhoods: the selects' initial options, and the review.
                // Capped: a step shown again with its errors may carry whatever ids were posted.
                val neighborhoodIds = answers.neighborhoodIds.take(FindPropertyRules.MAX_NEIGHBORHOODS)
                val locations = namesOrEmpty(listOfNotNull(answers.cityId) + neighborhoodIds)
                model.addAttribute("city", answers.cityId?.let { locations[it] })
                model.addAttribute("neighborhoods", neighborhoodIds.mapNotNull { locations[it] })
            }
        }
        if (step == STEP_PROPERTY) {
            model.addAttribute(
                "propertyTypes",
                FindPropertyRules.CATEGORIES_BY_TYPE.filterValues { it.contains(answers.propertyCategory) }.keys.toList(),
            )
            model.addAttribute("propertyTypeLabels", MandateLabels.PROPERTY_TYPE_LABELS)
        }
        if (step == STEP_AMENITIES) {
            model.addAttribute("amenityLabels", MandateLabels.AMENITY_LABELS)
        }
        if (step == STEP_REVIEW) {
            model.addAttribute("categoryLabel", CATEGORY_LABELS[answers.propertyCategory]?.let { "Location, $it" })
            model.addAttribute("propertyLabel", propertyLabel(answers))
            model.addAttribute("priceLabel", answers.priceAmount?.let { MandateLabels.formatNumber(it) + " FCFA par mois" })
            model.addAttribute("movingPeriodLabel", MandateLabels.MOVE_IN_LABELS[answers.movingPeriod])
            model.addAttribute("contactPeriodLabel", CONTACT_PERIOD_LABELS[answers.preferredContactPeriod])
            model.addAttribute("amenityLabels", MandateLabels.AMENITY_LABELS.filterKeys { answers.has(it) }.values.toList())
        }
        return VIEW
    }

    /** "Appartement · 3 chambres · Meublé ou non · 80 m² minimum", as the prototype's describeProperty. */
    private fun propertyLabel(answers: PendingMandate): String {
        val type = answers.propertyType
        val parts = listOfNotNull(
            MandateLabels.PROPERTY_TYPE_LABELS.entries.firstOrNull { it.key.name == type }?.value ?: "Tout type de bien",
            MandateLabels.ROOM_LABELS[answers.rooms]?.takeIf { FindPropertyRules.needsRooms(type) },
            MandateLabels.FURNITURE_LABELS[answers.furnitureType]?.takeIf { FindPropertyRules.needsFurniture(type) },
            MandateLabels.PARKING_LABELS[answers.parkings]?.takeIf { FindPropertyRules.asksParkings(type) },
            answers.area?.let { MandateLabels.formatNumber(it) + " m² minimum" },
        )
        return parts.joinToString(" · ")
    }

    /** The city must be one of the tenant's cities, and every neighbourhood one of its neighbourhoods. */
    private fun areaErrors(cityId: Long, neighborhoodIds: List<Long>): Map<String, String> {
        val locations = locations(listOf(cityId) + neighborhoodIds)
        val city = locations[cityId]
        if (city == null || city.type != LocationType.CITY || city.country != tenantContext.getTenant().countryCode) {
            return mapOf("cityId" to "Cette ville n'est pas proposée. Choisissez-en une dans la liste.")
        }

        val valid = neighborhoodIds.all { id ->
            val neighborhood = locations[id]
            neighborhood != null && neighborhood.type == LocationType.NEIGHBORHOOD && neighborhood.parentId == cityId
        }
        return if (valid) {
            emptyMap()
        } else {
            mapOf("neighborhoodIds" to "Choisissez des quartiers de la ville sélectionnée.")
        }
    }

    /**
     * The names to show, best effort: when ndopify-server is down, the page (and the review's "Envoi impossible")
     * still renders, without them. Validation never uses this: it calls [locations] and fails if it fails.
     */
    private fun namesOrEmpty(ids: List<Long>): Map<Long, Location> =
        try {
            locations(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("Unable to load the locations $ids", ex)
            emptyMap()
        }

    private fun locations(ids: List<Long>): Map<Long, Location> {
        if (ids.isEmpty()) return emptyMap()
        return locationClient.search(SearchLocationRequest(ids = ids.distinct(), limit = ids.size))
            .locations
            .associateBy { location -> location.id }
    }

    private fun isAllowed(step: String, pending: PendingMandate): Boolean =
        STEPS.indexOf(step) <= STEPS.indexOf(pending.firstIncompleteStep())

    private fun redirect(step: String): String =
        if (step == STEP_NEED) "redirect:/find-property" else "redirect:/find-property?step=$step"

    private fun errors(vararg errors: Pair<String, String?>): Map<String, String> =
        errors.mapNotNull { (field, message) -> message?.let { field to it } }.toMap()

    private fun pending(session: HttpSession): PendingMandate =
        session.getAttribute(PendingMandate.SESSION_ATTRIBUTE) as PendingMandate? ?: PendingMandate()

    private fun submitted(session: HttpSession): SubmittedMandate? =
        session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE) as SubmittedMandate?
}
