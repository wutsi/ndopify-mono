package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.mandate.MandateController
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
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
 */
@Controller
class ListingController(
    private val currentAgent: CurrentAgent,
    private val listingClient: NdListingClient,
    private val mandateClient: NdMandateClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingController::class.java)

        const val VIEW = "listing"
        const val SIGN_OUT = "redirect:/logout"

        const val OUTCOME = "outcome"
        const val SENT = "sent"

        // Sent without JS (the button is only inactive) before the checklist was done
        const val INCOMPLETE = "incomplete"
        const val CHANGED = "changed"
        const val FAILED = "error"

        // 403: no longer the agent's; 404: gone; 409: changed since the page was shown
        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    /** [id] is a String so that a hand-edited value is the locked view instead of failing the page. */
    @GetMapping("/listings/{id}")
    fun show(@PathVariable id: String, model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT
        ProfileRules.header(agent, model)

        val listing = id.toLongOrNull()?.let { load(it, agent) } ?: return locked(model)
        val images = lookup("images of listing ${listing.id}") { listingClient.images(listing.id) }
        val view = ListingViews.of(listing, images, locations(listing), formatter())
        model.addAttribute("listing", view)
        (model.getAttribute(OUTCOME) as? String)?.let { outcome(it, model) }
        return VIEW
    }

    /** Checked here too: without JS the inactive button still posts. ndopify-server moves it to UNDER_VERIFICATION. */
    @PostMapping("/listings/{id}/verify")
    fun send(@PathVariable id: Long, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: return SIGN_OUT

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
                if (ex.statusCode.value() in CHANGED_STATUSES) CHANGED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Listing $id not sent", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, outcome)
        return "redirect:/listings/$id"
    }

    /**
     * Opens the listing of an approved LISTING mandate of the agent, creating it the first time. One created meanwhile
     * (ndopify-server's 409 LISTING_ALREADY_EXISTS, ex: from another tab) is opened instead.
     */
    @PostMapping("/mandates/{id}/listing")
    fun create(@PathVariable id: Long, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: return SIGN_OUT

        val outcome =
            try {
                val mandate = mandateClient.get(id)
                if (!listable(mandate, agent)) {
                    MandateController.CHANGED
                } else {
                    val listingId = mandate.listingId ?: listingClient.create(id)
                    return "redirect:/listings/$listingId"
                }
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("No listing created for mandate $id: ${ex.statusCode}")
                if (errorCode(ex) == ErrorCode.LISTING_ALREADY_EXISTS) {
                    lookup("listing of mandate $id") { mandateClient.get(id).listingId }
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
        val text = when (outcome) {
            SENT -> "Annonce envoyée. Nous vous préviendrons dès sa publication."
            INCOMPLETE -> "Annonce incomplète : vérifiez chaque section avant de l'envoyer."
            CHANGED -> "Cette annonce a changé entre-temps : vérifiez son état avant de recommencer."
            FAILED -> "Action impossible pour le moment. Réessayez dans quelques instants."
            else -> return
        }
        model.addAttribute("outcomeText", text)
        model.addAttribute(
            "outcomeStyle",
            when (outcome) {
                SENT -> "success"
                FAILED -> "error"
                else -> "warning"
            },
        )
    }

    private fun locked(model: Model): String {
        model.addAttribute("locked", true)
        return VIEW
    }

    /** The agent's listing; null when it can't be had or is another agent's. */
    private fun load(id: Long, agent: Agent): Listing? =
        try {
            listingClient.get(id).takeIf { it.agentId == agent.id }
        } catch (ex: RestClientException) {
            LOGGER.warn("No listing $id: ${ex.message}")
            null
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
