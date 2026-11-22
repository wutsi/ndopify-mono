package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.ui.login.SignInTarget
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

/**
 * The login entry point: it remembers the local page an unauthenticated GET/HEAD asked for in the HTTP session, under
 * [SignInTarget.SESSION_ATTRIBUTE], then sends the agent to the login form. `LoginController.verify` sends the agent
 * back there once signed in. A POST is never remembered, so its body is never replayed.
 *
 * A GET that remembers a target answers a tiny interstitial page instead of the usual redirect, because a plain 302
 * would drop the deep link's `#match-7` fragment (fragments are never sent to the server). The interstitial's script
 * appends `location.hash` to the login URL before navigating, so the login page can lift it into its hidden field.
 * Without JavaScript the interstitial's meta refresh still reaches `/login`, and the agent lands at the top of the
 * right page — the accepted progressive-enhancement fallback. HEAD and POST keep the plain redirect.
 */
class SignInTargetEntryPoint(private val loginFormUrl: String = "/login") : LoginUrlAuthenticationEntryPoint(loginFormUrl) {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val target = if (request.method == "GET" || request.method == "HEAD") {
            SignInTarget.of(request.requestURI, request.queryString)?.also {
                request.getSession(true).setAttribute(SignInTarget.SESSION_ATTRIBUTE, it)
            }
        } else {
            null
        }
        if (request.method == "GET" && target != null) {
            writeInterstitial(response)
        } else {
            super.commence(request, response, authException)
        }
    }

    /** The page whose script carries the URL's fragment over to `/login`, where a 302 would lose it. */
    private fun writeInterstitial(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_OK
        response.contentType = "text/html;charset=UTF-8"
        response.writer.write(
            """
            <!doctype html>
            <html lang="fr">
            <head>
                <meta charset="utf-8">
                <meta http-equiv="refresh" content="0; url=$loginFormUrl">
                <title>Connexion</title>
                <script>location.replace('$loginFormUrl' + location.hash);</script>
            </head>
            <body></body>
            </html>
            """.trimIndent(),
        )
    }
}
