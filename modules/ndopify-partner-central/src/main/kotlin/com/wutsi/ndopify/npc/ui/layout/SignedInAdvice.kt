package com.wutsi.ndopify.npc.ui.layout

import com.wutsi.ndopify.npc.service.security.JwtAuthentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * Tells every page whether someone is signed in (model attribute `signedIn`): the header's account menu is only
 * rendered then, so it never shows on a public page, nor on a signed-in one whose token is gone.
 */
@ControllerAdvice
class SignedInAdvice {
    @ModelAttribute("signedIn")
    fun signedIn(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication is JwtAuthentication && authentication.isAuthenticated
    }
}
