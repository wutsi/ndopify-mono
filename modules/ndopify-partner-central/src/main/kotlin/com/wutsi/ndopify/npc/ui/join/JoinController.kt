package com.wutsi.ndopify.npc.ui.join

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.ui.login.LoginController
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
import org.springframework.web.client.HttpClientErrorException

/**
 * The "Rejoindre Ndopify" sign-up wizard, on a single page (`rejoindre.html`) like `login.html`, with five steps —
 * [STEPS]: identite → experience → secteur → paiement → revue — then the confirmation.
 * - `GET /join?step=…` shows a step; `POST /join/{step}` validates it, stores its answers in the
 *   session's [PendingJoin] and redirects to the next step (or back to the review when `returnTo=review`).
 * - `POST /join/review` creates the agent (`POST /v1/agents`) from the session's answers, then shows the
 *   confirmation (`?step=confirmation`) while a [JoinedAgent] is in the session.
 *
 * The URL is never trusted: a step is only shown once every step before it is complete, otherwise the first
 * incomplete one is. Answers only reach the session once validated, so the review submits the session, never the page.
 */
@Controller
@RequestMapping("/join")
class JoinController(
    private val agentClient: NdAgentClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(JoinController::class.java)

        const val VIEW = "join"
        const val STEP_IDENTITY = "identity"
        const val STEP_EXPERIENCE = "experience"
        const val STEP_AREA = "area"
        const val STEP_PAYMENT = "payment"
        const val STEP_REVIEW = "review"
        const val STEP_CONFIRMATION = "confirmation"
        const val RETURN_TO_REVIEW = "review"

        val STEPS = listOf(STEP_IDENTITY, STEP_EXPERIENCE, STEP_AREA, STEP_PAYMENT, STEP_REVIEW)

        private val EXPERIENCE_LABELS = mapOf(
            ExperienceLevel.NOVICE to "Moins d'un an",
            ExperienceLevel.JUNIOR to "1 à 3 ans",
            ExperienceLevel.INTERMEDIATE to "3 à 5 ans",
            ExperienceLevel.SENIOR to "Plus de 5 ans",
        )

        private val AGENT_TYPE_LABELS = mapOf(
            AgentType.INDEPENDANT to "Indépendant",
            AgentType.REAL_ESTATE_AGENT to "Rattaché à une agence immobilière",
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
        val joined = joined(session)
        if (requestedStep == STEP_CONFIRMATION && joined != null) {
            model.addAttribute("step", STEP_CONFIRMATION)
            model.addAttribute("maskedEmail", LoginController.maskEmail(joined.email))
            return VIEW
        }

        val pending = pending(session)
        val requested = STEPS.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = STEPS.indexOf(pending.firstIncompleteStep())
        return step(STEPS[minOf(requested, allowed)], pending, returnTo, emptyMap(), model)
    }

    @PostMapping("/$STEP_IDENTITY")
    fun identity(
        @ModelAttribute form: IdentityForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val answers = pending(session).copy(
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            email = form.email.trim().lowercase(),
            whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
        )
        val errors = errors(
            "firstName" to JoinRules.firstName(answers.firstName),
            "lastName" to JoinRules.lastName(answers.lastName),
            "email" to JoinRules.email(answers.email),
            "whatsappNumber" to JoinRules.whatsappNumber(answers.whatsappNumber),
        )
        return save(STEP_IDENTITY, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_EXPERIENCE")
    fun experience(
        @ModelAttribute form: ExperienceForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_EXPERIENCE, pending)) return redirect(pending.firstIncompleteStep())

        val answers = pending.copy(experienceLevel = form.experienceLevel, agentType = form.agentType)
        val errors = errors(
            "experienceLevel" to JoinRules.experienceLevel(answers.experienceLevel),
            "agentType" to JoinRules.agentType(answers.agentType),
        )
        return save(STEP_EXPERIENCE, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AREA")
    fun sector(
        @ModelAttribute form: SectorForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AREA, pending)) return redirect(pending.firstIncompleteStep())

        val answers = pending.copy(cityId = form.cityId, neighborhoodIds = form.neighborhoodIds.distinct())
        val errors = errors(
            "cityId" to JoinRules.cityId(answers.cityId),
            "neighborhoodIds" to JoinRules.neighborhoodIds(answers.neighborhoodIds),
        ).ifEmpty { sectorErrors(answers.cityId!!, answers.neighborhoodIds) }
        return save(STEP_AREA, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_PAYMENT")
    fun payment(
        @ModelAttribute form: PaymentForm,
        @RequestParam(required = false) returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val pending = pending(session)
        if (!isAllowed(STEP_PAYMENT, pending)) return redirect(pending.firstIncompleteStep())

        val number = if (form.sameAsWhatsapp) pending.whatsappNumber else JoinRules.normalizePhone(form.mobileMoneyNumber)
        val answers = pending.copy(mobileMoneyNumber = number)
        val errors = errors("mobileMoneyNumber" to JoinRules.mobileMoneyNumber(answers.mobileMoneyNumber))
        return save(STEP_PAYMENT, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val pending = pending(session)
        if (pending.firstIncompleteStep() != STEP_REVIEW) return redirect(pending.firstIncompleteStep())

        try {
            agentClient.create(pending.toCreateAgentRequest())
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            val code = errorCode(ex)
            model.addAttribute("existingAccount", code == ErrorCode.AGENT_ALREADY_EXISTS)
            model.addAttribute("error", toMessage(code))
            return step(STEP_REVIEW, pending, null, emptyMap(), model)
        }

        session.removeAttribute(PendingJoin.SESSION_ATTRIBUTE)
        session.setAttribute(JoinedAgent.SESSION_ATTRIBUTE, JoinedAgent(email = pending.email.orEmpty()))
        return redirect(STEP_CONFIRMATION)
    }

    /** Stores valid answers and moves on, or shows the step again with its errors (the session is left untouched). */
    private fun save(
        step: String,
        answers: PendingJoin,
        errors: Map<String, String>,
        returnTo: String?,
        session: HttpSession,
        model: Model,
    ): String {
        if (errors.isNotEmpty()) {
            return step(step, answers, returnTo, errors, model)
        }

        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, answers)
        session.removeAttribute(JoinedAgent.SESSION_ATTRIBUTE)
        val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, answers.firstIncompleteStep(), compareBy { STEPS.indexOf(it) }))
    }

    private fun step(
        step: String,
        answers: PendingJoin,
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
                val neighborhoodIds = answers.neighborhoodIds.take(JoinRules.MAX_NEIGHBORHOODS)
                val locations = locations(listOfNotNull(answers.cityId) + neighborhoodIds)
                model.addAttribute("city", answers.cityId?.let { locations[it] })
                model.addAttribute("neighborhoods", neighborhoodIds.mapNotNull { locations[it] })
            }
        }
        if (step == STEP_PAYMENT) {
            // A foreign WhatsApp number can't be a Cameroonian Mobile Money number: no "same as WhatsApp" shortcut.
            model.addAttribute("canUseWhatsapp", JoinRules.mobileMoneyNumber(answers.whatsappNumber) == null)
        }
        if (step == STEP_REVIEW) {
            model.addAttribute("experienceLabel", EXPERIENCE_LABELS[answers.experienceLevel])
            model.addAttribute("agentTypeLabel", AGENT_TYPE_LABELS[answers.agentType])
        }
        return VIEW
    }

    /** The city must be one of the tenant's cities, and every neighbourhood one of its neighbourhoods. */
    private fun sectorErrors(cityId: Long, neighborhoodIds: List<Long>): Map<String, String> {
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

    private fun locations(ids: List<Long>): Map<Long, Location> {
        if (ids.isEmpty()) return emptyMap()
        return locationClient.search(SearchLocationRequest(ids = ids.distinct(), limit = ids.size))
            .locations
            .associateBy { location -> location.id }
    }

    private fun isAllowed(step: String, pending: PendingJoin): Boolean =
        STEPS.indexOf(step) <= STEPS.indexOf(pending.firstIncompleteStep())

    private fun redirect(step: String): String =
        if (step == STEP_IDENTITY) "redirect:/join" else "redirect:/join?step=$step"

    private fun errors(vararg errors: Pair<String, String?>): Map<String, String> =
        errors.mapNotNull { (field, message) -> message?.let { field to it } }.toMap()

    private fun pending(session: HttpSession): PendingJoin =
        session.getAttribute(PendingJoin.SESSION_ATTRIBUTE) as PendingJoin? ?: PendingJoin()

    private fun joined(session: HttpSession): JoinedAgent? =
        session.getAttribute(JoinedAgent.SESSION_ATTRIBUTE) as JoinedAgent?

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun toMessage(code: String?): String = when (code) {
        ErrorCode.AGENT_ALREADY_EXISTS -> "Un compte utilise déjà cette adresse email."
        else -> "Envoi impossible pour le moment. Réessayez dans quelques instants."
    }
}
