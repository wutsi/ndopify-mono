package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.npc.ui.visit.VisitSlots
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.util.Date

/**
 * Booking a visit for a property the customer accepted (`mandate/visit.html`), on its own page rather than a form on
 * the mandate page: the agent has called the customer on WhatsApp (the mandate page's contact block), and confirms here
 * the property, the day, the time and the type. The visit is created `CONFIRMED`, so the customer is e-mailed the date,
 * the address and its code at once; the mandate page's card then carries it in *Visites à venir*.
 *
 * `GET /mandates/{id}/visits` offers the mandate's ACCEPTED matches without an open visit. A mandate that isn't
 * APPROVED, or one the customer has no accepted match left in, redirects to its page; an unknown, another agent's or
 * unreadable one goes to `/`. `POST` validates the answers ([MandateVisitRules]), then books through
 * [NdVisitClient.createForMatch] and comes back to the mandate's contact block with the
 * [MandateController.VISIT_OUTCOME] flash: `scheduled`, `invalid-slot` for a 400 `VISIT_INVALID_SLOT`,
 * `whatsapp-missing` for a 409 `VISIT_WHATSAPP_MISSING`, `changed` for a 403 / 404 / 409 (the mandate or the match moved
 * on), `error` for a 5xx.
 *
 * The page has no script: every field posts, and the rules mirror ndopify-server's.
 */
@Controller
@RequestMapping("/mandates/{id}/visits")
class MandateVisitController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val matchClient: NdMatchClient,
    private val visitClient: NdVisitClient,
    private val listingClient: NdListingClient,
    private val customerClient: NdCustomerClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateVisitController::class.java)

        const val VIEW = "mandate/visit"

        // The location the page comes back to: the client's contact block, at the top of the mandate page
        const val BACK = "/mandates/%d#contact-title"

        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    @GetMapping
    fun show(@PathVariable id: String, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        val mandate = id.toLongOrNull()?.let { load(it) } ?: return "redirect:/"
        if (mandate.status != MandateStatus.APPROVED) {
            return "redirect:/mandates/${mandate.id}"
        }
        val matches = schedulable(agent, mandate)
        if (matches.isEmpty()) {
            return "redirect:/mandates/${mandate.id}"
        }
        return render(agent, mandate, matches, MandateVisitForm(), emptyMap(), model)
    }

    @PostMapping
    fun create(
        @PathVariable id: Long,
        @ModelAttribute form: MandateVisitForm,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        val mandate = load(id)
        val matches = mandate?.takeIf { it.status == MandateStatus.APPROVED }?.let { schedulable(agent, it) }
        if (mandate == null || matches == null || matches.isEmpty()) {
            redirect.addFlashAttribute(MandateController.VISIT_OUTCOME, MandateController.CHANGED)
            return back(id)
        }

        val errors = MandateVisitRules.errors(form, matches)
        if (errors.isNotEmpty()) {
            return render(agent, mandate, matches, form, errors, model)
        }

        val outcome =
            try {
                visitClient.createForMatch(
                    form.matchId!!.trim().toLong(),
                    MandateVisitRules.request(form, tenantContext.getTenant()),
                )
                MandateController.VISIT_SCHEDULED
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Visit of match ${form.matchId} not booked: ${ex.statusCode}")
                when {
                    errorCode(ex) == ErrorCode.VISIT_INVALID_SLOT -> MandateController.VISIT_INVALID_SLOT
                    errorCode(ex) == ErrorCode.VISIT_WHATSAPP_MISSING -> MandateController.VISIT_WHATSAPP_MISSING
                    ex.statusCode.value() in CHANGED_STATUSES -> MandateController.CHANGED
                    else -> MandateController.FAILED
                }
            } catch (ex: RestClientException) {
                LOGGER.error("Visit of match ${form.matchId} not booked", ex)
                MandateController.FAILED
            }
        redirect.addFlashAttribute(MandateController.VISIT_OUTCOME, outcome)
        return back(mandate.id)
    }

    private fun render(
        agent: Agent,
        mandate: Mandate,
        matches: List<Match>,
        form: MandateVisitForm,
        errors: Map<String, String>,
        model: Model,
    ): String {
        ProfileRules.header(agent, model)
        val tenant = tenantContext.getTenant()
        val listings = lookup("listings of the accepted matches of mandate ${mandate.id}") {
            listingClient.searchByIds(matches.map { it.listingId }.distinct()).associateBy { it.id }
        }
        model.addAttribute("mandate", mandate)
        model.addAttribute("properties", properties(matches, listings))
        model.addAttribute("clientName", clientName(mandate))
        model.addAttribute("days", VisitSlots.days(VisitSlots.localDate(Date(clock.millis()), tenant), TenantFormatter(tenant)))
        model.addAttribute("times", VisitSlots.times())
        model.addAttribute("types", MandateVisitRules.TYPES)
        model.addAttribute("form", form)
        model.addAttribute("errors", errors)
        return VIEW
    }

    /**
     * The mandate's ACCEPTED matches without an open visit: the ones the agent may book. The matches' search already
     * leaves out an ended mandate or a listing off the market; a failed visit read leaves them all, and ndopify-server
     * refuses the one that has a visit (409 `VISIT_ALREADY_EXISTS`, shown as `changed`).
     */
    private fun schedulable(agent: Agent, mandate: Mandate): List<Match> {
        val matches = lookup("matches of mandate ${mandate.id}") {
            matchClient.search(listOf(mandate.id), listOf(MatchStatus.ACCEPTED))
        } ?: return emptyList()
        if (matches.isEmpty()) {
            return emptyList()
        }
        val visited = lookup("open visits of the matches of mandate ${mandate.id}") {
            visitClient.searchByMatches(agent.id, VisitRules.OPEN, matches.map { it.id }).map { it.matchId }.toSet()
        }.orEmpty()
        return matches.filter { it.id !in visited }
    }

    /** The mandate, null when it can't be had (unknown, another agent's, or ndopify-server down). */
    private fun load(id: Long): Mandate? =
        lookup("mandate $id") { mandateClient.get(id) }

    private fun back(id: Long): String = "redirect:" + BACK.format(id)

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }

    /** The customer's name for the intro; null when it can't be read (the page then shows the mandate's number only). */
    private fun clientName(mandate: Mandate): String? {
        val party = lookup("customer ${mandate.customerId} of mandate ${mandate.id}") {
            customerClient.get(mandate.customerId)
        }?.party
            ?: return null
        return listOf(party.firstName, party.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    /** The picker's options, when the listings couldn't be read: every match named by its listing's id. */
    private fun properties(matches: List<Match>, listings: Map<Long, ListingSummary>?) =
        MandateVisitProperty.of(matches, listings.orEmpty())
}
