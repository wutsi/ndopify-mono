package com.wutsi.ndopify.npc.ui.logout

import com.wutsi.ndopify.npc.ui.login.LoginController
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Signs the agent out: drops the access-token cookie set by [LoginController] and the HTTP session (which holds the
 * sign-in flash attributes), then sends them back to the sign-in page. A POST, from the account menu's form, so that
 * another site can't sign the agent out with a link (the access-token cookie is `SameSite=Lax`, sent on a GET from
 * anywhere).
 *
 * ndopify-server has no token revocation endpoint, so the token itself stays valid until it expires; it is only
 * removed from this browser.
 */
@Controller
@RequestMapping("/logout")
class LogoutController(private val accessTokenHolder: AccessTokenService) {
    @PostMapping
    fun logout(request: HttpServletRequest): String {
        request.getSession(false)?.invalidate()
        accessTokenHolder.remove()

        return "redirect:/login"
    }
}
