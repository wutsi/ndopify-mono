package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.client.ndopify.NdAgentClient
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdMandateClient
import com.wutsi.ndopify.web.client.ndopify.NdMatchClient
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtAuthentication
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
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock

/**
 * The link of the "proposition" e-mail, `/match/{id}?token=<public-portal JWT>`, and the customer's answer.
 *
 * With a `token`, it is kept in the access-token cookie and the visitor is redirected to the same address without it
 * (302; any other parameter is kept): the token must not stay in the address bar, the browser history, a shared link
 * or the `Referer` of the page's outbound links. It is not decoded here: [com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter]
 * signs the visitor in on the next request, and only for a `public-portal` token. The token is never logged.
 *
 * Without a `token`, signed out means the cookie is missing, expired, or not a `public-portal` token: `match-link`
 * with `reason = "expired"`. Signed in, the match is read (or answered) on ndopify-server, which checks the token and
 * the match (the claims are not trusted here); the page and the two answers handle its errors the same way:
 * - 401: it rejected the token (ex: signed with another secret): the cookie is cleared, `expired`;
 * - 403: the token is another customer's, or the match isn't PROPOSED/ACCEPTED/REJECTED: `not-yours`, with
 *   nothing about the match;
 * - 404 on the match: left to [com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler], the app's 404 page.
 *
 * The page ([MatchPages]) also reads the mandate (the search), its agent and the listing; an agent that is gone (404)
 * is shown as "votre agent", and a listing the customer can't
 * read anymore (403/404: no longer published) is not an error, the page shows it as unavailable.
 *
 * Answers are final, as ndopify-server's (a match leaves PROPOSED once): the page has no "change my mind" control,
 * and a second answer (409 `MATCH_NOT_PROPOSED`, ex: a double submit or another tab) just redirects to the page,
 * which shows the answer already given. A posted answer redirects with `?answer=` for the page's flash.
 */
@Controller
class MatchController(
    private val matchClient: NdMatchClient,
    private val mandateClient: NdMandateClient,
    private val agentClient: NdAgentClient,
    private val listingClient: NdListingClient,
    private val locationClient: NdLocationClient,
    private val accessTokenService: AccessTokenService,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchController::class.java)

        const val VIEW = "match"
        const val VIEW_LINK = "match-link"

        const val REASON_EXPIRED = "expired"
        const val REASON_NOT_YOURS = "not-yours"

        /** Three base64url segments: all a link's token must look like to be kept (its signature is ndopify-server's to check). */
        private val JWT_SHAPE = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

        /** `RejectMatchRequest.reason`'s size on ndopify-server: the form allows 500, a longer post is cut. */
        private const val MAX_COMMENT = 1000
    }

    @GetMapping("/match/{id}")
    fun index(
        @PathVariable id: Long,
        @RequestParam(name = "token", required = false) token: String?,
        @RequestParam(name = "answer", required = false) answer: String?,
        request: HttpServletRequest,
        model: Model,
    ): Any {
        if (!token.isNullOrBlank()) {
            if (!JWT_SHAPE.matches(token)) {
                // Not a token: setting it as a cookie value could even fail (`;`, space, `"`, `,`)
                return link(REASON_EXPIRED, model)
            }
            accessTokenService.set(token)
            return redirect(withoutToken(request))
        }

        return signedIn("GET /match/$id", model) {
            val match = matchClient.get(id)
            val mandate = mandateClient.get(match.mandateId)
            val agent = mandate.agentId?.let { agent(it) } ?: Agent()
            val listing = publishedListing(match.listingId)
            val images = listing?.let { listingClient.images(it.id) } ?: emptyList<ListingImage>()
            val locations = locationClient.search(
                (listOfNotNull(listing?.cityId, listing?.neighborhoodId, mandate.cityId) + mandate.neighborhoodIds).distinct(),
            )
            val tenant = model.getAttribute("tenant") as Tenant
            val formatter = TenantFormatter(tenant)
            val page = MatchPages.of(match, mandate, agent, listing, images, locations, tenant, formatter, formatter.today(clock), answer)
            model.addAttribute("page", page)
            VIEW
        }
    }

    @PostMapping("/match/{id}/accept")
    fun accept(@PathVariable id: Long, model: Model): Any =
        signedIn("POST /match/$id/accept", model) {
            answered(id, "accepted") { matchClient.accept(id) }
        }

    /** A missing (or UNKNOWN) reason is `OTHER`, a blank comment none. */
    @PostMapping("/match/{id}/reject")
    fun reject(@PathVariable id: Long, @ModelAttribute form: RejectForm, model: Model): Any =
        signedIn("POST /match/$id/reject", model) {
            val request = RejectMatchRequest(
                reasonCode = form.reason?.takeIf { it != MatchRejectionReason.UNKNOWN } ?: MatchRejectionReason.OTHER,
                reason = form.comment?.trim()?.ifEmpty { null }?.take(MAX_COMMENT),
            )
            answered(id, "rejected") { matchClient.reject(id, request) }
        }

    /** Runs [action] for a signed-in visitor, answering ndopify-server's 401 / 403 with the `match-link` page. */
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

    /** Posts the answer, then back to the page: with the flash, or without it when it was already answered (409). */
    private fun answered(id: Long, answer: String, post: () -> Unit): RedirectView =
        try {
            post()
            redirect("/match/$id?answer=$answer")
        } catch (ex: HttpClientErrorException.Conflict) {
            LOGGER.info("Match #$id already answered")
            redirect("/match/$id")
        }

    /** The agent, or an empty one ("votre agent") when ndopify-server no longer has it: the page doesn't need it. */
    private fun agent(id: Long): Agent =
        try {
            agentClient.get(id)
        } catch (ex: HttpClientErrorException.NotFound) {
            Agent()
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

    private fun redirect(url: String): RedirectView = RedirectView(url).apply {
        setStatusCode(HttpStatus.FOUND)
        setExposeModelAttributes(false)
    }

    private fun link(reason: String, model: Model): String {
        model.addAttribute("reason", reason)
        return VIEW_LINK
    }

    // The request's own path and parameters, minus `token`
    private fun withoutToken(request: HttpServletRequest): String {
        val builder = UriComponentsBuilder.newInstance().path(request.requestURI)
        request.parameterMap
            .filterKeys { name -> name != "token" }
            .forEach { (name, values) -> values.forEach { value -> builder.queryParam(name, value) } }
        return builder.build().encode().toUriString()
    }
}
