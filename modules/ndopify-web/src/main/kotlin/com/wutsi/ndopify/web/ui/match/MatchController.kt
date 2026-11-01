package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.web.client.ndopify.NdMatchClient
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtAuthentication
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

/**
 * The link of the "proposition" e-mail, `/match/{id}?token=<public-portal JWT>`.
 *
 * With a `token`, it is kept in the access-token cookie and the visitor is redirected to the same address without it
 * (302; any other parameter is kept): the token must not stay in the address bar, the browser history, a shared link
 * or the `Referer` of the page's outbound links. It is not decoded here: [com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter]
 * signs the visitor in on the next request, and only for a `public-portal` token. The token is never logged.
 *
 * Without a `token`, signed out means the cookie is missing, expired, or not a `public-portal` token: `match-link`
 * with `reason = "expired"`. Signed in, the match is read from ndopify-server, which checks the token and the match
 * (the claims are not trusted here):
 * - 401: it rejected the token (ex: signed with another secret): the cookie is cleared, `expired`;
 * - 403: the token is another customer's, or the match isn't PROPOSED/ACCEPTED/REJECTED: `not-yours`, with
 *   nothing about the match;
 * - 404: left to [com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler], the app's 404 page.
 *
 * Accepting and rejecting are not handled here yet.
 */
@Controller
class MatchController(
    private val matchClient: NdMatchClient,
    private val accessTokenService: AccessTokenService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchController::class.java)

        const val VIEW = "match"
        const val VIEW_LINK = "match-link"

        const val REASON_EXPIRED = "expired"
        const val REASON_NOT_YOURS = "not-yours"
    }

    @GetMapping("/match/{id}")
    fun index(
        @PathVariable id: Long,
        @RequestParam(name = "token", required = false) token: String?,
        request: HttpServletRequest,
        model: Model,
    ): Any {
        if (!token.isNullOrBlank()) {
            accessTokenService.set(token)
            return RedirectView(withoutToken(request)).apply {
                setStatusCode(HttpStatus.FOUND)
                setExposeModelAttributes(false)
            }
        }

        if (SecurityContextHolder.getContext().authentication !is JwtAuthentication) {
            return link(REASON_EXPIRED, model)
        }

        return try {
            model.addAttribute("match", matchClient.get(id))
            VIEW
        } catch (ex: HttpClientErrorException.Unauthorized) {
            LOGGER.warn("GET /match/$id: token rejected by ndopify-server")
            accessTokenService.remove()
            link(REASON_EXPIRED, model)
        } catch (ex: HttpClientErrorException.Forbidden) {
            LOGGER.warn("GET /match/$id: 403 from ndopify-server")
            link(REASON_NOT_YOURS, model)
        }
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
