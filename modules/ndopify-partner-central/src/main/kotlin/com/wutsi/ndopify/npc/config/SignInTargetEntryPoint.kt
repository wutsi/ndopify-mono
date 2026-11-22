package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.ui.login.SignInTarget
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

/**
 * The login entry point: it remembers the local page an unauthenticated GET/HEAD asked for in the HTTP session,
 * under [SignInTarget.SESSION_ATTRIBUTE], before redirecting to the login form. `LoginController.verify` sends the
 * agent back there once signed in. A POST is never remembered, so its body is never replayed.
 */
class SignInTargetEntryPoint(loginFormUrl: String = "/login") : LoginUrlAuthenticationEntryPoint(loginFormUrl) {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        if (request.method == "GET" || request.method == "HEAD") {
            SignInTarget.of(request.requestURI, request.queryString)?.let {
                request.getSession(true).setAttribute(SignInTarget.SESSION_ATTRIBUTE, it)
            }
        }
        super.commence(request, response, authException)
    }
}
