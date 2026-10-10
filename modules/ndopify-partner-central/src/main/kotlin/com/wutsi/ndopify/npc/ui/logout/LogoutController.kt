package com.wutsi.ndopify.npc.ui.logout

import com.wutsi.ndopify.npc.service.security.AccessTokenService
import com.wutsi.ndopify.npc.ui.login.LoginController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Signs the agent out: drops the access-token cookie set by [LoginController] and the HTTP session (which holds the
 * sign-in flash attributes), then sends them back to the sign-in page.
 *
 * ndopify-server has no token revocation endpoint, so the token itself stays valid until it expires; it is only
 * removed from this browser.
 */
@Controller
@RequestMapping("/logout")
class LogoutController(private val accessTokenHolder: AccessTokenService) {
    @GetMapping
    fun logout(request: HttpServletRequest, response: HttpServletResponse): String {
        request.getSession(false)?.invalidate()
        accessTokenHolder.remove()

        return "redirect:/login"
    }
}
