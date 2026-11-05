package com.wutsi.ndopify.web.ui.layout

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UriComponentsBuilder

/**
 * The links of the public-portal e-mails, `/match/{id}?token=<public-portal JWT>` and the like.
 *
 * The token must not stay in the address bar, the browser history, a shared link or the `Referer` of the page's
 * outbound links: [withoutToken] gives the same address without it, after [isToken] has vouched it is worth keeping.
 * The token is never logged.
 */
object PortalLink {
    /** Three base64url segments: all a link's token must look like before it is kept (its signature is ndopify-server's to check). */
    private val TOKEN_SHAPE = Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+){2}")

    /** True for three base64url segments: all a link's token must look like before it is kept. */
    fun isToken(value: String): Boolean = TOKEN_SHAPE.matches(value)

    /** The request's path and parameters, minus `token`. */
    fun withoutToken(request: HttpServletRequest): String {
        val builder = UriComponentsBuilder.newInstance().path(request.requestURI)
        request.parameterMap
            .filterKeys { name -> name != "token" }
            .forEach { (name, values) -> values.forEach { value -> builder.queryParam(name, value) } }
        return builder.build().encode().toUriString()
    }
}
