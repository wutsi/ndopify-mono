package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * The closing wizard (`mandate/close.html`), on its own page reached from the mandate page's *Fermer le mandat* link
 * while the mandate is APPROVED. Three steps, one at a time (`?step=`): the deal's counterpart (an ACCEPTED match), the
 * agreed rent, then the confirmation.
 *
 * - `GET /mandates/{id}/close?step=…` shows a step; `POST /mandates/{id}/close/counterpart|price|confirm` validates it,
 *   stores its answer in the session's [PendingMandateClose] and redirects to the next step (or back to the mandate
 *   page with its `closed` outcome on confirm).
 * - The URL is never trusted: a step is only shown once every earlier one is answered (the counterpart one is skipped
 *   when there are no candidates). Opening the page with no `step` clears a stale draft. The confirm step re-reads the
 *   candidates, so a stored match that is no longer one sends the agent back with a conflict. The final POST sends the
 *   session's answers, never the form's.
 *
 * A 403/404/409 from the confirm is the mandate page's `changed` outcome, a 5xx its `error`, both redirect there.
 */
@Controller
@RequestMapping("/mandates/{id}/close")
class MandateCloseController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val matchClient: NdMatchClient,
    private val listingClient: NdListingClient,
    private val locationClient: NdLocationClient,
    private val customerClient: NdCustomerClient,
    private val tenantContext: TenantContext,
    private val dealCandidates: MandateDealCandidates,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateCloseController::class.java)

        const val VIEW = "mandate/close"

        /** The counterpart form's "outside" answer. */
        const val OUTSIDE = "outside"

        /** Flash attribute of a match that is no longer a candidate on the confirm step. */
        const val CONFLICT = "conflict"

        private val CHANGED_STATUSES = setOf(403, 404, 409)

        private const val COUNTERPART_ERROR = "Choisissez une contrepartie dans la liste, ou « hors de vos annonces »."
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    /** The counterpart candidates read, either the list or an unreadable one (a failure shows the step, never nothing). */
    private data class Candidates(
        val candidates: List<CloseCandidateView>,
        val unavailable: Boolean,
        /** The landlord's own listing (a LISTING mandate), for the confirm recap; null when there's none. */
        val listing: Listing? = null,
    )

    @GetMapping
    fun show(
        @PathVariable id: String,
        @RequestParam(required = false) step: String?,
        session: HttpSession,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val mandate = id.toLongOrNull()?.let { load(it, agent) } ?: return locked(model)

        // Opening the wizard with no step restarts it: a stale draft is cleared.
        if (step == null) {
            session.removeAttribute(PendingMandateClose.SESSION_ATTRIBUTE)
        }

        val formatter = formatter()
        val read = candidates(mandate, agent, formatter)
        val counterpartApplicable = read.candidates.isNotEmpty() || read.unavailable
        val draft = pending(session)

        // The confirm step re-reads the candidates: a stored match that is no longer one is cleared and picked again.
        // A failed read (unavailable) is not "no longer a candidate": the selection stays, the step shows the note.
        if (step == PendingMandateClose.STEP_CONFIRM && draft.matchId != null && !read.unavailable &&
            read.candidates.none { it.matchId == draft.matchId }
        ) {
            session.setAttribute(PendingMandateClose.SESSION_ATTRIBUTE, draft.copy(matchId = null))
            redirect.addFlashAttribute(CONFLICT, true)
            return redirectStep(mandate.id, draft.copy(matchId = null).firstIncompleteStep(counterpartApplicable))
        }

        val steps = MandateCloseViews.steps(counterpartApplicable, mandate.mandateType == MandateType.LISTING)
        val names = steps.map { it.name }
        val allowed = names.indexOf(draft.firstIncompleteStep(counterpartApplicable))

        // A step that doesn't apply (ex: counterpart with no candidates) is the first applicable one.
        if (step != null && step !in names) {
            return redirectStep(mandate.id, steps[allowed].name)
        }

        val requested = names.indexOf(step).coerceAtLeast(0)
        val shown = minOf(requested, allowed)
        if (shown != requested) {
            return redirectStep(mandate.id, steps[shown].name)
        }
        return render(mandate, read, steps, steps[shown].name, formFor(steps[shown].name, draft, mandate, read), emptyMap(), draft, model)
    }

    @PostMapping("/counterpart")
    fun counterpart(
        @PathVariable id: Long,
        @ModelAttribute form: CounterpartForm,
        session: HttpSession,
        model: Model,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = load(id, agent) ?: return locked(model)

        val read = candidates(mandate, agent, formatter())
        val counterpartApplicable = read.candidates.isNotEmpty() || read.unavailable
        val draft = pending(session)

        if (form.counterpart == OUTSIDE) {
            session.setAttribute(PendingMandateClose.SESSION_ATTRIBUTE, draft.copy(matchId = null, outside = true))
            return redirectStep(id, PendingMandateClose.STEP_PRICE)
        }
        val matchId = form.counterpart?.toLongOrNull()
        if (matchId != null && read.candidates.any { it.matchId == matchId }) {
            session.setAttribute(PendingMandateClose.SESSION_ATTRIBUTE, draft.copy(matchId = matchId, outside = false))
            return redirectStep(id, PendingMandateClose.STEP_PRICE)
        }
        val steps = MandateCloseViews.steps(counterpartApplicable, mandate.mandateType == MandateType.LISTING)
        return render(mandate, read, steps, PendingMandateClose.STEP_COUNTERPART, form, mapOf("counterpart" to COUNTERPART_ERROR), draft, model)
    }

    @PostMapping("/price")
    fun price(
        @PathVariable id: Long,
        @ModelAttribute form: PriceForm,
        session: HttpSession,
        model: Model,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = load(id, agent) ?: return locked(model)

        val read = candidates(mandate, agent, formatter())
        val counterpartApplicable = read.candidates.isNotEmpty() || read.unavailable
        val draft = pending(session)

        val amount = MandateCloseRules.amount(form.finalPrice)
        if (amount == null) {
            val steps = MandateCloseViews.steps(counterpartApplicable, mandate.mandateType == MandateType.LISTING)
            return render(
                mandate,
                read,
                steps,
                PendingMandateClose.STEP_PRICE,
                form,
                mapOf("finalPrice" to "Indiquez le loyer convenu, en ${formatter().currency()} par mois."),
                draft,
                model,
            )
        }
        session.setAttribute(PendingMandateClose.SESSION_ATTRIBUTE, draft.copy(priceAmount = amount))
        return redirectStep(id, PendingMandateClose.STEP_CONFIRM)
    }

    @PostMapping("/confirm")
    fun confirm(@PathVariable id: Long, session: HttpSession, redirect: RedirectAttributes): String {
        currentAgent.get() ?: throw SignInRequiredException()

        val draft = pending(session)
        val currency = tenantContext.getTenant().currencyCode
        val outcome =
            try {
                if (draft.matchId != null) {
                    matchClient.close(draft.matchId, MandateCloseRules.closeMatch(draft, currency))
                } else {
                    mandateClient.close(id, MandateCloseRules.closeMandate(draft, currency))
                }
                MandateController.CLOSED
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Mandate $id not closed: ${ex.statusCode}")
                if (ex.statusCode.value() in CHANGED_STATUSES) MandateController.CHANGED else MandateController.FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Mandate $id not closed", ex)
                MandateController.FAILED
            }
        session.removeAttribute(PendingMandateClose.SESSION_ATTRIBUTE)
        redirect.addFlashAttribute(MandateController.OUTCOME, outcome)
        return "redirect:/mandates/$id"
    }

    private fun render(
        mandate: Mandate,
        read: Candidates,
        steps: List<CloseStepView>,
        step: String,
        form: Any,
        errors: Map<String, String>,
        draft: PendingMandateClose,
        model: Model,
    ): String {
        val currency = formatter().currency()
        model.addAttribute(
            "close",
            MandateCloseViews.of(
                mandate,
                read.candidates,
                read.unavailable,
                step,
                steps,
                currency,
                if (step == PendingMandateClose.STEP_CONFIRM) recap(mandate, read, draft) else null,
            ),
        )
        model.addAttribute("form", form)
        model.addAttribute("errors", errors)
        model.addAttribute("currency", currency)
        return VIEW
    }

    /** The confirm step's recap, from the chosen counterpart (or the outside answer), the listing and the customer. */
    private fun recap(mandate: Mandate, read: Candidates, draft: PendingMandateClose): CloseRecapView {
        val owner = mandate.mandateType == MandateType.LISTING
        val selected = draft.matchId?.let { id -> read.candidates.firstOrNull { it.matchId == id } }
        val customer = if (owner) {
            null
        } else {
            lookup("customer ${mandate.customerId} of mandate ${mandate.id}") { customerClient.get(mandate.customerId) }?.party
        }
        val locations = read.listing?.let { locations(it) } ?: emptyMap()
        return MandateCloseViews.recap(
            mandate,
            selected,
            draft.outside,
            read.listing,
            customer,
            locations,
            draft.priceAmount ?: 0,
            formatter(),
        )
    }

    /** The current draft as the step's form, so the page re-fills the agent's answers. */
    private fun formFor(step: String, draft: PendingMandateClose, mandate: Mandate, read: Candidates): Any =
        if (step == PendingMandateClose.STEP_COUNTERPART) {
            CounterpartForm(
                counterpart = when {
                    draft.outside -> OUTSIDE
                    draft.matchId != null -> draft.matchId.toString()
                    else -> null
                },
            )
        } else {
            PriceForm(finalPrice = prefill(draft, mandate, read.candidates)?.toString())
        }

    /**
     * The price step's initial value: the agent's stored price first, else the selected match's listing price, else the
     * mandate's own price (an outside deal, or a counterpart step that was skipped). Null when none is known.
     */
    private fun prefill(
        draft: PendingMandateClose,
        mandate: Mandate,
        candidates: List<CloseCandidateView>,
    ): Long? = draft.priceAmount
        ?: draft.matchId?.let { id -> candidates.firstOrNull { it.matchId == id }?.priceAmount }
        ?: mandate.price.amount.takeIf { it > 0 }

    private fun candidates(mandate: Mandate, agent: Agent, formatter: TenantFormatter): Candidates {
        if (mandate.mandateType == MandateType.LISTING) {
            val listingId = lookup("listing of mandate ${mandate.id}") { listingClient.findIdByMandate(mandate.id, agent.id) }
            if (listingId == null) {
                return Candidates(emptyList(), false)
            }
            val listing = lookup("listing $listingId of mandate ${mandate.id}") { listingClient.get(listingId) }
            if (listing?.status != ListingStatus.PUBLISHED) {
                return Candidates(emptyList(), false, listing)
            }
            return try {
                val clients = dealCandidates.clients(listingId, listOf(MatchStatus.ACCEPTED), agent.id)
                    .filter { it.mandate != null }
                val price = listing?.price?.takeIf { it > 0 }
                Candidates(
                    clients.map { MandateCloseViews.candidate(it.match, it.mandate!!, it.customer, price, formatter) },
                    false,
                    listing,
                )
            } catch (ex: RestClientException) {
                LOGGER.warn("No candidates of listing $listingId: ${ex.message}")
                Candidates(emptyList(), true, listing)
            }
        }
        return try {
            val listings = dealCandidates.listings(mandate.id, listOf(MatchStatus.ACCEPTED))
                .filter { it.listing != null }
            val names = locations(listings.mapNotNull { it.listing })
            Candidates(
                listings.map { MandateCloseViews.candidate(it.match, it.listing!!, names, formatter) },
                false,
            )
        } catch (ex: RestClientException) {
            LOGGER.warn("No candidates of mandate ${mandate.id}: ${ex.message}")
            Candidates(emptyList(), true)
        }
    }

    /** The neighbourhood names of the search's candidates, best effort: without them the row shows the city's. */
    private fun locations(listings: List<ListingSummary>): Map<Long, Location> =
        lookup("location names of the candidates") {
            sector.locations(listings.flatMap { listOfNotNull(it.cityId, it.neighborhoodId) })
        } ?: emptyMap()

    /** The city and neighbourhood of the landlord's listing, for the recap's "Bien". */
    private fun locations(listing: Listing): Map<Long, Location> =
        lookup("location names of listing ${listing.id}") {
            sector.locations(listOfNotNull(listing.cityId.takeIf { it > 0 }, listing.neighborhoodId))
        } ?: emptyMap()

    /** The mandate, only while it is the signed-in agent's and APPROVED (closable); null → the locked view. */
    private fun load(id: Long, agent: Agent): Mandate? =
        try {
            mandateClient.get(id).takeIf { it.agentId == agent.id && it.status == MandateStatus.APPROVED }
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandate $id: ${ex.message}")
            null
        }

    private fun locked(model: Model): String {
        model.addAttribute("locked", true)
        return VIEW
    }

    private fun pending(session: HttpSession): PendingMandateClose =
        session.getAttribute(PendingMandateClose.SESSION_ATTRIBUTE) as PendingMandateClose? ?: PendingMandateClose()

    private fun redirectStep(id: Long, step: String): String = "redirect:/mandates/$id/close?step=$step"

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
