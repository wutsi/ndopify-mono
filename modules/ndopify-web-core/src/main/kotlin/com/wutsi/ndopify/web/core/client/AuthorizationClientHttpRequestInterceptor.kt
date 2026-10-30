package com.wutsi.ndopify.web.core.client

import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.AccessTokenService.Companion.ACCESS_TOKEN_REJECTED
import com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Forwards the user's access token to ndopify-server as `Authorization: Bearer <token>`.
 *
 * - The token is read from the [cookieName] cookie of the incoming web request (set by [AccessTokenService], whose
 *   cookie name this must be), so outside a request (startup, scheduled jobs) or before the token is issued no header
 *   is added.
 * - Nor when [JwtAuthenticationFilter] rejected it ([ACCESS_TOKEN_REJECTED], ex: expired): ndopify-server would
 *   answer 401 to every call, even the public ones of the sign-in.
 * - A header already set by the caller is left untouched.
 */
class AuthorizationClientHttpRequestInterceptor(
    private val cookieName: String,
    private val currentRequest: () -> HttpServletRequest? = {
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
    },
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        if (request.headers.getFirst(HttpHeaders.AUTHORIZATION) == null) {
            accessToken()?.let { token -> request.headers.setBearerAuth(token) }
        }
        return execution.execute(request, body)
    }

    private fun accessToken(): String? =
        currentRequest()
            ?.takeIf { request -> request.getAttribute(ACCESS_TOKEN_REJECTED) != true }
            ?.cookies
            ?.firstOrNull { cookie -> cookie.name == cookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
}
