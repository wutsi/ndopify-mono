package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyController.Companion.STEPS
import com.wutsi.ndopify.npc.ui.join.JoinRules
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

/**
 * The public "Trouver un bien" wizard, on a single page (`find-property.html`) like `/join`, with seven steps —
 * [STEPS]: need → area → property → amenities → budget → contact → review — then the confirmation. A building bought
 * as a whole keeps six of them ([steps]): its property step asks its étages and its unités instead of a room and a
 * parking, and the amenities step leaves.
 * - `GET /find-property?step=…` shows a step; `POST /find-property/{step}` validates it, stores its answers in the
 *   session's [PendingMandate] and redirects to the next step (or back to the review when `returnTo=review`).
 * - `POST /find-property/review` creates a TENANT mandate (`POST /v1/mandates`) from the session's answers, then
 *   shows the confirmation (`?step=confirmation`) while a [SubmittedMandate] is in the session.
 *
 * The URL is never trusted: a step is only shown once every step before it is complete, otherwise the first
 * incomplete one is. Answers only reach the session once validated, so the review submits the session, never the
 * page. ndopify-server doesn't validate mandates yet: [FindPropertyRules] and the location checks here are all the
 * validation a mandate gets.
 *
 * No `step` is the entry point ([show]), and always starts a new run: the answers of a previous one (abandoned, or
 * already submitted) are dropped, never shown. Every in-run URL carries its step.
 */
@Controller
@RequestMapping("/find-property")
class FindPropertyController(
    private val mandateClient: NdMandateClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    private val sector = SectorValidator(locationClient, tenantContext)

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

        /**
         * The steps of the answers' branch: a building sale skips the amenities step (six steps), every other branch
         * keeps the seven. A step of the seven that isn't in them is never shown.
         */
        fun steps(answers: PendingMandate): List<String> =
            if (FindPropertyRules.buildingSale(
                    answers.listingType,
                    FindPropertyRules.parsePropertyType(answers.propertyType),
                )
            ) {
                STEPS - STEP_AMENITIES
            } else {
                STEPS
            }
    }

    /** The current tenant, for the header on every step: its logo, and "Quitter" back to its public portal. */
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
        val submitted = submitted(session)
        if (requestedStep == STEP_CONFIRMATION && submitted != null) {
            model.addAttribute("step", STEP_CONFIRMATION)
            model.addAttribute("maskedEmail", LoginController.maskEmail(submitted.email))
            model.addAttribute("whatsappNumber", submitted.whatsappNumber)
            model.addAttribute("contactPeriodLabel", MandateLabels.CONTACT_PERIOD_LABELS[submitted.preferredContactPeriod])
            model.addAttribute("listingType", submitted.listingType)
            model.addAttribute("projectLabel", FindPropertyRules.projectLabel(submitted.listingType))
            return VIEW
        }

        // The public site's links preselect the project (?type=SALE|RENTAL), for display only: the session keeps
        // what the visitor posts.
        val pending = if (requestedStep == null) {
            // The entry point: a new run, never the answers of a previous one (an abandoned run, or a submitted
            // request whose confirmation is still in the session).
            session.removeAttribute(PendingMandate.SESSION_ATTRIBUTE)
            session.removeAttribute(SubmittedMandate.SESSION_ATTRIBUTE)
            PendingMandate(listingType = FindPropertyRules.parseListingType(type))
        } else {
            pending(session)
        }
        val keys = steps(pending)
        // A step of another branch (a building sale's amenities) isn't shown: the wizard moves on to the branch's step.
        if (requestedStep in STEPS && requestedStep !in keys) return redirect(pending.firstIncompleteStep())

        val requested = keys.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = keys.indexOf(pending.firstIncompleteStep())
        return step(keys[minOf(requested, allowed)], pending, returnTo, emptyMap(), model)
    }

    @PostMapping("/$STEP_NEED")
    fun need(
        @ModelAttribute form: NeedForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val answers = pending(session).copy(
            listingType = form.listingType,
            propertyCategory = form.propertyCategory,
        )
        val errors = errors(
            "listingType" to FindPropertyRules.listingType(answers.listingType),
            "propertyCategory" to FindPropertyRules.propertyCategory(answers.propertyCategory),
        )
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
        ).ifEmpty { sector.errors(answers.cityId!!, answers.neighborhoodIds) }
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
        val typeAnswer = form.propertyType?.takeIf { it.isNotEmpty() }
        val type = FindPropertyRules.parsePropertyType(typeAnswer)
        val buildingSale = FindPropertyRules.buildingSale(pending.listingType, type)
        val areaText = form.area?.trim().orEmpty()
        val area = FindPropertyRules.parseAmount(areaText)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        // Documents are kept for a land purchase only; unknown names were rejected at binding.
        val documents = form.titleDocuments
            .filter { it in FindPropertyRules.titleDocumentTypes(pending.listingType, type) }
            .distinct()
        val answers = pending.copy(
            propertyType = typeAnswer,
            rooms = form.rooms?.takeIf { FindPropertyRules.needsRooms(pending.propertyCategory, type, pending.listingType) },
            furnitureType = form.furnitureType?.takeIf { FindPropertyRules.needsFurniture(type, pending.listingType) },
            parkings = form.parkings?.takeIf {
                it.isNotEmpty() && FindPropertyRules.asksParkings(type, pending.listingType)
            },
            area = area,
            // A building sale's counts: kept as typed (the page shows them again on error), sent as null when
            // "Indifférent" is ticked, and dropped for any other type; so is a stale amenity answer.
            levels = form.levels?.trim()?.toIntOrNull().takeIf { buildingSale && !form.levelsAny },
            units = form.units?.trim()?.toIntOrNull().takeIf { buildingSale && !form.unitsAny },
            levelsAny = buildingSale && form.levelsAny,
            unitsAny = buildingSale && form.unitsAny,
            amenities = pending.amenities.takeIf { !buildingSale } ?: emptySet(),
            titleDocuments = documents,
        )
        val requiredArea = FindPropertyRules.needsArea(pending.propertyCategory, type, pending.listingType)
        val errors = errors(
            "propertyType" to FindPropertyRules.propertyType(answers.propertyCategory, typeAnswer, pending.listingType),
            "rooms" to
                if (FindPropertyRules.needsRooms(answers.propertyCategory, type, pending.listingType)) {
                    FindPropertyRules.rooms(answers.rooms)
                } else {
                    null
                },
            "furnitureType" to
                if (FindPropertyRules.needsFurniture(type, pending.listingType)) {
                    FindPropertyRules.furnitureType(answers.furnitureType)
                } else {
                    null
                },
            "parkings" to FindPropertyRules.parkings(answers.parkings),
            "levels" to if (buildingSale) FindPropertyRules.levels(form.levels, form.levelsAny) else null,
            "units" to if (buildingSale) FindPropertyRules.units(form.units, form.unitsAny) else null,
            // Something typed that isn't a usable number is an error too, not a silent "no area".
            "area" to if (areaText.isNotEmpty() && area == null) {
                FindPropertyRules.area(0, requiredArea)
            } else {
                FindPropertyRules.area(area, requiredArea)
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

        // A building sale has no amenities step: a hand-typed one drops the answers, as every skip does.
        if (pending.buildingSale()) {
            session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, pending.copy(amenities = emptySet()))
            session.removeAttribute(SubmittedMandate.SESSION_ATTRIBUTE)
            val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else pending.firstIncompleteStep()
            return redirect(next)
        }

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
            financingType = form.financingType,
        )
        val errors = errors(
            "priceAmount" to FindPropertyRules.priceAmount(answers.priceAmount, formatter(), pending.listingType),
            "movingPeriod" to FindPropertyRules.movingPeriod(answers.movingPeriod, pending.listingType),
            "financingType" to FindPropertyRules.financingType(answers.financingType, pending.listingType),
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
                listingType = pending.listingType ?: ListingType.RENTAL,
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
        val keys = steps(answers)
        val next = if (returnTo == RETURN_TO_REVIEW) {
            STEP_REVIEW
        } else {
            keys.getOrNull(keys.indexOf(step) + 1) ?: answers.firstIncompleteStep()
        }
        return redirect(minOf(next, answers.firstIncompleteStep(), compareBy { keys.indexOf(it) }))
    }

    private fun step(
        step: String,
        answers: PendingMandate,
        returnTo: String?,
        errors: Map<String, String>,
        model: Model,
    ): String {
        val keys = steps(answers)
        model.addAttribute("step", step)
        model.addAttribute("stepNumber", keys.indexOf(step) + 1)
        model.addAttribute("stepCount", keys.size)
        model.addAttribute("projectLabel", FindPropertyRules.projectLabel(answers.listingType))
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("buildingSale", answers.buildingSale())
        // "Revenir à la revue" instead of "Suivant": only once every answer is in.
        model.addAttribute(
            "returning",
            returnTo == RETURN_TO_REVIEW && step != STEP_REVIEW && answers.firstIncompleteStep() == STEP_REVIEW,
        )

        when (step) {
            STEP_NEED -> {
                model.addAttribute("categoryLabels", MandateLabels.byName(MandateLabels.CATEGORY_LABELS))
                model.addAttribute("listingTypeLabels", MandateLabels.byName(MandateLabels.LISTING_TYPE_LABELS))
            }
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
                FindPropertyRules.propertyTypes(answers.propertyCategory!!, answers.listingType),
            )
            model.addAttribute("propertyTypeLabels", MandateLabels.PROPERTY_TYPE_LABELS)
            model.addAttribute("furnitureLabels", MandateLabels.byName(MandateLabels.FURNITURE_TYPE_LABELS))
            model.addAttribute("titleDocumentLabels", MandateLabels.byName(MandateLabels.TITLE_DOCUMENT_LABELS))
            model.addAttribute(
                "asksTitleDocuments",
                FindPropertyRules.titleDocumentTypes(
                    answers.listingType,
                    FindPropertyRules.parsePropertyType(answers.propertyType),
                ).isNotEmpty(),
            )
        }
        if (step == STEP_BUDGET) {
            model.addAttribute("currency", formatter().currency())
            model.addAttribute("movingPeriodLabels", MandateLabels.byName(MandateLabels.MOVE_IN_LABELS))
            model.addAttribute("financingLabels", MandateLabels.byName(MandateLabels.FINANCING_LABELS))
            model.addAttribute(
                "moveInLabel",
                when {
                    answers.isSale() -> "Quand souhaitez-vous acheter\u00a0?"
                    answers.propertyCategory == PropertyCategory.COMMERCIAL -> "Date d'installation souhaitée"
                    else -> "Date d'emménagement souhaitée"
                },
            )
        }
        if (step == STEP_AMENITIES) {
            model.addAttribute("amenityLabels", MandateLabels.AMENITY_LABELS)
        }
        if (step == STEP_REVIEW) {
            model.addAttribute("categoryLabel", MandateLabels.project(answers.propertyCategory, answers.listingType))
            model.addAttribute("propertyLabel", propertyLabel(answers))
            // A building sale's two counts, "Indifférent" when the visitor didn't answer.
            if (answers.buildingSale()) {
                model.addAttribute("levelsLabel", answers.levels?.toString() ?: "Indifférent")
                model.addAttribute("unitsLabel", answers.units?.toString() ?: "Indifférent")
            }
            model.addAttribute("priceTerm", if (answers.isSale()) "Budget total" else "Budget mensuel")
            model.addAttribute(
                "priceLabel",
                answers.priceAmount?.let { formatter().money(it) + if (answers.isSale()) "" else " par mois" },
            )
            model.addAttribute("moveInTerm", MandateLabels.moveInTerm(answers.listingType, answers.propertyCategory))
            model.addAttribute("movingPeriodLabel", MandateLabels.MOVE_IN_LABELS[answers.movingPeriod])
            model.addAttribute(
                "financingLabel",
                answers.financingType?.takeIf { answers.isSale() }?.let { MandateLabels.FINANCING_LABELS[it] },
            )
            model.addAttribute(
                "titleDocumentsLabel",
                answers.titleDocuments
                    .takeIf {
                        answers.isSale() && FindPropertyRules.parsePropertyType(answers.propertyType) == PropertyType.LAND
                    }
                    ?.let { MandateLabels.titleDocuments(it) },
            )
            model.addAttribute("contactPeriodLabel", MandateLabels.CONTACT_PERIOD_LABELS[answers.preferredContactPeriod])
            model.addAttribute("amenityLabels", MandateLabels.AMENITY_LABELS.filterKeys { answers.has(it) }.values.toList())
        }
        return VIEW
    }

    /** "Appartement · 3 chambres · Meublé ou non · 80 m² minimum", as the prototype's describeProperty. */
    private fun propertyLabel(answers: PendingMandate): String {
        val type = FindPropertyRules.parsePropertyType(answers.propertyType)
        val parts = listOfNotNull(
            MandateLabels.PROPERTY_TYPE_LABELS[type] ?: "Tout type de bien",
            MandateLabels.ROOM_LABELS[answers.rooms]?.takeIf {
                FindPropertyRules.needsRooms(answers.propertyCategory, type, answers.listingType)
            },
            MandateLabels.FURNITURE_LABELS[answers.furnitureType]?.takeIf {
                FindPropertyRules.needsFurniture(type, answers.listingType)
            },
            MandateLabels.PARKING_LABELS[answers.parkings]?.takeIf {
                FindPropertyRules.asksParkings(type, answers.listingType)
            },
            answers.area?.let { MandateLabels.formatNumber(it) + " m² minimum" },
        )
        return parts.joinToString(" · ")
    }

    /**
     * The names to show, best effort: when ndopify-server is down, the page (and the review's "Envoi impossible")
     * still renders, without them. Validation never uses this: it calls [SectorValidator.locations] and fails if it fails.
     */
    private fun namesOrEmpty(ids: List<Long>): Map<Long, Location> =
        try {
            sector.locations(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("Unable to load the locations $ids", ex)
            emptyMap()
        }

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    private fun isAllowed(step: String, pending: PendingMandate): Boolean {
        val keys = steps(pending)
        return keys.indexOf(step) <= keys.indexOf(pending.firstIncompleteStep())
    }

    /** Every step has its own URL: the bare `/find-property` is the entry point, which starts a new run. */
    private fun redirect(step: String): String = "redirect:/find-property?step=$step"

    private fun errors(vararg errors: Pair<String, String?>): Map<String, String> =
        errors.mapNotNull { (field, message) -> message?.let { field to it } }.toMap()

    private fun pending(session: HttpSession): PendingMandate =
        session.getAttribute(PendingMandate.SESSION_ATTRIBUTE) as PendingMandate? ?: PendingMandate()

    private fun submitted(session: HttpSession): SubmittedMandate? =
        session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE) as SubmittedMandate?
}
