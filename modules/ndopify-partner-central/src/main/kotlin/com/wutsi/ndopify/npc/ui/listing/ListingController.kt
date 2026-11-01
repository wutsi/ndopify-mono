package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.mandate.MandateController
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * The listing of one of the agent's approved LISTING mandates (`listing.html`): its checklist, the visit of the
 * property section by section, and "Envoyer pour vérification" once every required section is done.
 *
 * ndopify-server doesn't check who reads or writes a listing, so this page does: a listing that isn't the signed-in
 * agent's (`Listing.agentId`) is the locked view, and an action on it is [CHANGED], never sent.
 *
 * The listing is created from the mandate page (`POST /mandates/{id}/listing`), pre-filled by ndopify-server from the
 * landlord's answers; a mandate that can't have one goes back to its page with the mandate page's own outcomes.
 *
 * Published, it has its *Propositions*: the clients it was sent to ([proposals]). A match of another agent's search
 * protects that agent's mandate: only the agent's own mandates are looked up, and their customers only, so another
 * agent's client is never asked of ndopify-server, let alone shown.
 */
@Controller
class ListingController(
    private val currentAgent: CurrentAgent,
    private val listingClient: NdListingClient,
    private val mandateClient: NdMandateClient,
    private val matchClient: NdMatchClient,
    private val customerClient: NdCustomerClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingController::class.java)

        const val VIEW = "listing"
        const val HOME = "redirect:/"

        const val OUTCOME = "outcome"
        const val SENT = "sent"

        // Sent without JS (the button is only inactive) before the checklist was done
        const val INCOMPLETE = "incomplete"
        const val CHANGED = "changed"
        const val FAILED = "error"

        // The listing couldn't be loaded (5xx, network): not the locked view, it may be there
        const val UNAVAILABLE = "unavailable"

        // 403: no longer the agent's; 404: gone; 409: changed since the page was shown
        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    private val sector = SectorValidator(locationClient, tenantContext)
    private val access = ListingAccess(listingClient)

    /** [id] is a String so that a hand-edited value is the locked view instead of failing the page. */
    @GetMapping("/listings/{id}")
    fun show(@PathVariable id: String, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        (model.getAttribute(OUTCOME) as? String)?.let { outcome(it, model) }
        val listing = id.toLongOrNull()?.let { load(it, agent, model) } ?: return locked(model)
        val images = lookup("images of listing ${listing.id}") { listingClient.images(listing.id) }
        val view = ListingViews.of(listing, images, locations(listing), formatter(), tenantContext.getTenant().publicPortalUrl)
        model.addAttribute("listing", view.copy(proposals = proposals(listing, agent)))
        return VIEW
    }

    /**
     * Checked here too: without JS the inactive button still posts. ndopify-server moves it to UNDER_VERIFICATION,
     * and checks the checklist again (409 LISTING_INCOMPLETE, ex: a photo refused meanwhile). A hand-edited [id] goes
     * back to the Espace agent.
     */
    @PostMapping("/listings/{id}/verify")
    fun send(@PathVariable("id") value: String, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val id = value.toLongOrNull() ?: return HOME

        val outcome =
            try {
                val listing = listingClient.get(id)
                when {
                    listing.agentId != agent.id || !ListingRules.editable(listing) -> CHANGED
                    !ListingRules.complete(listing) -> INCOMPLETE
                    else -> {
                        listingClient.verify(id)
                        SENT
                    }
                }
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Listing $id not sent: ${ex.statusCode}")
                when {
                    errorCode(ex) == ErrorCode.LISTING_INCOMPLETE -> INCOMPLETE
                    ex.statusCode.value() in CHANGED_STATUSES -> CHANGED
                    else -> FAILED
                }
            } catch (ex: RestClientException) {
                LOGGER.error("Listing $id not sent", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, outcome)
        return "redirect:/listings/$id"
    }

    /**
     * Creates the listing of an approved LISTING mandate of the agent, and opens it. One that exists already
     * (ndopify-server's 409 LISTING_ALREADY_EXISTS, ex: created from another tab) is opened instead. A hand-edited [id] goes
     * back to the Espace agent.
     */
    @PostMapping("/mandates/{id}/listing")
    fun create(@PathVariable("id") value: String, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val id = value.toLongOrNull() ?: return HOME

        val outcome =
            try {
                val mandate = mandateClient.get(id)
                if (!listable(mandate, agent)) {
                    MandateController.CHANGED
                } else {
                    val listingId = listingClient.create(id)
                    // No id in ndopify-server's answer: nothing to open
                    if (listingId > 0) return "redirect:/listings/$listingId"
                    LOGGER.error("No listing id returned for mandate $id")
                    MandateController.FAILED
                }
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("No listing created for mandate $id: ${ex.statusCode}")
                if (errorCode(ex) == ErrorCode.LISTING_ALREADY_EXISTS) {
                    lookup("listing of mandate $id") { listingClient.findIdByMandate(id, agent.id) }
                        ?.let { listingId -> return "redirect:/listings/$listingId" }
                }
                if (ex.statusCode.value() in CHANGED_STATUSES) MandateController.CHANGED else MandateController.FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("No listing created for mandate $id", ex)
                MandateController.FAILED
            }
        redirect.addFlashAttribute(MandateController.OUTCOME, outcome)
        return "redirect:/mandates/$id"
    }

    // Only a landlord's signed mandate gets a listing, and only its agent creates it
    private fun listable(mandate: Mandate, agent: Agent): Boolean =
        mandate.agentId == agent.id &&
            mandate.status == MandateStatus.APPROVED &&
            mandate.mandateType == MandateType.LISTING

    private fun outcome(outcome: String, model: Model) {
        val saved = outcome.removePrefix(ListingSectionController.SAVED_PREFIX)
            .takeIf { outcome.startsWith(ListingSectionController.SAVED_PREFIX) }
            ?.let { ListingRules.section(it) }
        val text = saved?.let { ListingRules.SAVED.getValue(it) } ?: words(outcome) ?: return
        model.addAttribute("outcomeText", text)
        model.addAttribute(
            "outcomeStyle",
            when (outcome) {
                INCOMPLETE, CHANGED -> "warning"
                FAILED -> "error"
                else -> "success"
            },
        )
    }

    private fun words(outcome: String): String? = when (outcome) {
        SENT -> "Annonce envoyée. Nous vous préviendrons dès sa publication."
        INCOMPLETE -> "Annonce incomplète : vérifiez chaque section avant de l'envoyer."
        CHANGED -> "Cette annonce a changé entre-temps : vérifiez son état avant de recommencer."
        FAILED -> "Action impossible pour le moment. Réessayez dans quelques instants."
        else -> null
    }

    private fun locked(model: Model): String {
        if (model.getAttribute(UNAVAILABLE) != true) model.addAttribute("locked", true)
        return VIEW
    }

    /** The agent's listing; null when it can't be had, with the view to show instead ([UNAVAILABLE] or locked). */
    private fun load(id: Long, agent: Agent, model: Model): Listing? =
        when (val result = access.load(id, agent)) {
            is ListingAccess.Found -> result.listing
            ListingAccess.Locked -> null
            ListingAccess.Unavailable -> {
                model.addAttribute(UNAVAILABLE, true)
                null
            }
        }

    /**
     * A published listing's proposals: its sent matches, the agent's own mandates among theirs, and those mandates'
     * customers, one search each. Null before it's published, or when the matches or mandates can't be had: no
     * section, rather than another agent's client shown as the agent's or the other way round. Customers failing
     * only leave the names out.
     */
    private fun proposals(listing: Listing, agent: Agent): ProposalsView? {
        if (listing.status != ListingStatus.PUBLISHED) return null
        val matches = lookup("matches of listing ${listing.id}") {
            matchClient.searchByListings(listOf(listing.id), MatchRules.SENT)
        } ?: return null
        val mandateIds = matches.map { it.mandateId }.distinct()
        val mandates = lookup("mandates of the matches of listing ${listing.id}") {
            mandateClient.searchByIds(mandateIds, agent.id)
        }?.filter { it.agentId == agent.id } ?: return null
        val customers = lookup("customers of the matches of listing ${listing.id}") {
            customerClient.search(mandates.map { it.customerId }.distinct())
        } ?: emptyList()
        return ListingViews.proposals(matches, mandates, customers, agent.id, formatter())
    }

    private fun locations(listing: Listing) =
        lookup("location names of listing ${listing.id}") {
            sector.locations(listOfNotNull(listing.cityId, listing.neighborhoodId))
        } ?: emptyMap()

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

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
}
