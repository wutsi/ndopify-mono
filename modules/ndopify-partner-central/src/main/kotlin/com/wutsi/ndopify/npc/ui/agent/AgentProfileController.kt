package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Another agent's profile (`agent.html`), opened from one of its listings once co-brokerage ships. The viewed agent
 * comes only from the `id` parameter; the signed-in agent is the viewer, used for the page's header. An unknown or
 * missing id renders the *Agent introuvable* panel with a 404. A failed listing read leaves the profile usable.
 */
@Controller
class AgentProfileController(
    private val currentAgent: CurrentAgent,
    private val agentClient: NdAgentClient,
    private val listingClient: NdListingClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val parallel: Parallel,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AgentProfileController::class.java)

        const val VIEW = "agent"
        private const val BACK = "← Retour à l'espace agent"
        private const val BACK_TO_LISTING = "← Retour"
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping("/agent")
    fun show(
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) back: String?,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val viewer = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(viewer, model)
        val backUrl = AgentProfileRules.back(back)
        model.addAttribute("backUrl", backUrl ?: "/")
        model.addAttribute("backLabel", if (backUrl == null) BACK else BACK_TO_LISTING)

        val agentId = id?.toLongOrNull()
        if (agentId == null) return missing(model, response)

        // On the request thread, before any task: this initialises TenantContext's cache and the formatter.
        val tenant = tenantContext.getTenant()
        val formatter = TenantFormatter(tenant)
        val requestedPage = page?.toIntOrNull() ?: 1

        // Wave 1: the independent reads.
        val agentCall = parallel.async { agentClient.get(agentId) }
        val listingsCall = parallel.async { listings(agentId, requestedPage) }
        val agent = join(agentCall)
        val listings = join(listingsCall)
        if (agent == null) return missing(model, response)

        // Wave 2: the visible listings' images and every location name the page shows.
        val visible = listings?.listings.orEmpty()
        val imageCalls = visible.associate { listing -> listing.id to parallel.async { images(listing.id) } }
        val locationIds = listOfNotNull(agent.cityId) + agent.neighborhoodIds + visible.flatMap { listing ->
            listOfNotNull(listing.cityId.takeIf { it > 0 }, listing.neighborhoodId)
        }
        val locationsCall = parallel.async { locations(locationIds) }
        val images = imageCalls.mapValues { (_, call) -> join(call) }
        val locations = join(locationsCall)

        model.addAttribute("profile", AgentProfileViews.of(agent, listings, images, locations, formatter, backUrl))
        model.addAttribute("missing", false)
        return VIEW
    }

    /**
     * The page of [requested] (clamped to the last one), with the whole set's page count; null when the read fails.
     * A clamp that moved the page costs a second read.
     */
    private fun listings(agentId: Long, requested: Int): AgentListingsPage? =
        try {
            val requestedPage = requested.coerceAtLeast(1)
            val first = listingClient.searchByAgent(
                agentId,
                AgentProfileRules.PAGE_SIZE,
                (requestedPage - 1) * AgentProfileRules.PAGE_SIZE,
            )
            val page = AgentProfileRules.page(first.total, requestedPage)
            val listings = if (page == requestedPage) {
                first.listings
            } else {
                listingClient.searchByAgent(
                    agentId,
                    AgentProfileRules.PAGE_SIZE,
                    (page - 1) * AgentProfileRules.PAGE_SIZE,
                ).listings
            }
            AgentListingsPage(listings, page, AgentProfileRules.pages(first.total))
        } catch (ex: RestClientException) {
            LOGGER.warn("No listings for agent $agentId: ${ex.message}")
            null
        }

    /** The listing's images; empty when they can't be had (the card keeps its placeholder). */
    private fun images(listingId: Long): List<ListingImage> =
        try {
            listingClient.images(listingId)
        } catch (ex: RestClientException) {
            LOGGER.warn("No images for listing $listingId: ${ex.message}")
            emptyList()
        }

    /** One lookup for the agent's and the visible listings' names; empty when they can't be had. */
    private fun locations(ids: List<Long>): Map<Long, Location> =
        try {
            sector.locations(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("No location names for the agent profile: ${ex.message}")
            emptyMap()
        }

    private fun missing(model: Model, response: HttpServletResponse): String {
        response.status = HttpServletResponse.SC_NOT_FOUND
        model.addAttribute("profile", null)
        model.addAttribute("missing", true)
        return VIEW
    }

    /** A task's result, or its own error (not the [CompletionException] that wraps it). */
    private fun <T> join(call: CompletableFuture<T>): T =
        try {
            call.join()
        } catch (ex: CompletionException) {
            throw ex.cause ?: ex
        }
}
