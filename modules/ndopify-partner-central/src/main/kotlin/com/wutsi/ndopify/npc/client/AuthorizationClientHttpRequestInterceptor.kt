package com.wutsi.ndopify.npc.client

import com.wutsi.ndopify.npc.service.security.AccessTokenService.Companion.ACCESS_TOKEN_COOKIE
import com.wutsi.ndopify.npc.ui.login.LoginController
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Forwards the signed-in agent's access token to ndopify-server as `Authorization: Bearer <token>`.
 *
 * - The token is read from the access-token cookie of the incoming web request (set by [LoginController]), so
 *   outside a request (startup, scheduled jobs) or before sign-in no header is added.
 * - A header already set by the caller is left untouched.
 */
class AuthorizationClientHttpRequestInterceptor(
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
            ?.cookies
            ?.firstOrNull { cookie -> cookie.name == ACCESS_TOKEN_COOKIE }
            ?.value
            ?.takeIf { it.isNotBlank() }
}
