package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.web.client.ndopify.NdAgentClient
import com.wutsi.ndopify.web.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdVisitClient
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtAuthentication
import com.wutsi.ndopify.web.ui.layout.PortalLink
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.RedirectView
import java.time.Clock

/**
 * The link of the "visite" e-mail, `/visits/{id}?token=<public-portal JWT>`, and the customer's cancellation.
 *
 * The token handling mirrors [com.wutsi.ndopify.web.ui.match.MatchController]: with a `token`, it is kept in the
 * access-token cookie and the visitor is redirected to the same address without it (302; any other parameter is kept),
 * so the token never stays in the address bar, the history, a shared link or the `Referer`. A token of the wrong shape
 * is not kept (setting it as a cookie could even fail). The token is never logged.
 *
 * Signed in, the visit is read on ndopify-server, which checks the token and the visit (the claims are not trusted
 * here); the page and the cancellation handle its errors the same way:
 * - 401: it rejected the token (ex: signed with another secret): the cookie is cleared, `expired`;
 * - 403: the token is another customer's: `not-yours`, with nothing about the visit;
 * - 404 on the visit: left to [com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler], the app's 404 page.
 *
 * The page ([VisitPages]) also reads the listing (its images and locations) and, when the server still has them, the
 * agent and the customer. A listing the customer can't read anymore (403/404: no longer published) is not an error, its
 * images are skipped and the page shows the visit without it; a gone agent or customer is not an error either.
 */
@Controller
class VisitController(
    private val visitClient: NdVisitClient,
    private val listingClient: NdListingClient,
    private val locationClient: NdLocationClient,
    private val agentClient: NdAgentClient,
    private val customerClient: NdCustomerClient,
    private val accessTokenService: AccessTokenService,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitController::class.java)

        const val VIEW = "visit"
        const val VIEW_LINK = "visit-link"

        const val REASON_EXPIRED = "expired"
        const val REASON_NOT_YOURS = "not-yours"

        /** `CancelVisitRequest.reason`'s size on ndopify-server: the form allows 500, a longer post is cut. */
        private const val MAX_REASON = 500
    }

    @GetMapping("/visits/{id}")
    fun index(
        @PathVariable id: Long,
        @RequestParam(name = "token", required = false) token: String?,
        @RequestParam(name = "cancel", required = false) cancel: String?,
        request: HttpServletRequest,
        model: Model,
    ): Any {
        if (!token.isNullOrBlank()) {
            if (!PortalLink.isToken(token)) {
                // Not a token: setting it as a cookie value could even fail (`;`, space, `"`, `,`)
                return link(REASON_EXPIRED, model)
            }
            accessTokenService.set(token)
            return redirect(PortalLink.withoutToken(request))
        }

        return signedIn("GET /visits/$id", model) {
            val visit = visitClient.get(id)
            val listing = publishedListing(visit.listingId)
            val images = listing?.let { listingImages(it.id) } ?: emptyList<ListingImage>()
            val locationIds = listOfNotNull(listing?.cityId, listing?.neighborhoodId).distinct()
            val locations = if (locationIds.isEmpty()) emptyList() else locationClient.search(locationIds)
            val agent = visit.agentId.takeIf { it > 0 }?.let { agent(it) }
            val customer = customer(visit.customerId)
            val tenant = model.getAttribute("tenant") as Tenant
            val formatter = TenantFormatter(tenant)
            val page = VisitPages.of(visit, listing, images, locations, agent, customer, tenant, formatter, formatter.today(clock), cancel)
            model.addAttribute("page", page)
            VIEW
        }
    }

    /** Posts the cancellation; a blank reason is none, a long one is cut to what ndopify-server takes. */
    @PostMapping("/visits/{id}/cancel")
    fun cancel(@PathVariable id: Long, @ModelAttribute form: CancelVisitForm, model: Model): Any =
        signedIn("POST /visits/$id/cancel", model) {
            val request = CancelVisitRequest(form.reason?.trim()?.ifEmpty { null }?.take(MAX_REASON))
            try {
                visitClient.cancel(id, request)
                redirect("/visits/$id?cancel=done")
            } catch (ex: HttpClientErrorException.Conflict) {
                LOGGER.info("Visit #$id can't be cancelled anymore")
                redirect("/visits/$id?cancel=conflict")
            }
        }

    /** Runs [action] for a signed-in visitor, answering ndopify-server's 401 / 403 with the `visit-link` page. */
    private fun signedIn(call: String, model: Model, action: () -> Any): Any {
        if (SecurityContextHolder.getContext().authentication !is JwtAuthentication) {
            return link(REASON_EXPIRED, model)
        }
        return try {
            action()
        } catch (ex: HttpClientErrorException.Unauthorized) {
            LOGGER.warn("$call: token rejected by ndopify-server")
            accessTokenService.remove()
            link(REASON_EXPIRED, model)
        } catch (ex: HttpClientErrorException.Forbidden) {
            LOGGER.warn("$call: 403 from ndopify-server")
            link(REASON_NOT_YOURS, model)
        }
    }

    /** The listing when the customer can still see it, null when not readable (403/404) or not PUBLISHED. */
    private fun publishedListing(id: Long): Listing? =
        try {
            listingClient.get(id).takeIf { it.status == ListingStatus.PUBLISHED }
        } catch (ex: HttpClientErrorException.NotFound) {
            null
        } catch (ex: HttpClientErrorException.Forbidden) {
            null
        }

    /** The listing's images, empty when ndopify-server no longer has them: the page shows the visit without them. */
    private fun listingImages(id: Long): List<ListingImage> =
        try {
            listingClient.images(id)
        } catch (ex: HttpClientErrorException.NotFound) {
            emptyList()
        } catch (ex: HttpClientErrorException.Forbidden) {
            emptyList()
        }

    /** The agent, or an empty one ("votre agent") when ndopify-server no longer has it: the page doesn't need it. */
    private fun agent(id: Long): Agent =
        try {
            agentClient.get(id)
        } catch (ex: HttpClientErrorException.NotFound) {
            Agent()
        }

    /** The customer, null when not readable (403/404): the page shows the visit without their contact. */
    private fun customer(id: Long): Customer? =
        try {
            customerClient.get(id)
        } catch (ex: HttpClientErrorException.NotFound) {
            null
        } catch (ex: HttpClientErrorException.Forbidden) {
            null
        }

    private fun redirect(url: String): RedirectView = RedirectView(url).apply {
        setStatusCode(HttpStatus.FOUND)
        setExposeModelAttributes(false)
    }

    private fun link(reason: String, model: Model): String {
        model.addAttribute("reason", reason)
        return VIEW_LINK
    }
}
