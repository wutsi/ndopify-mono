package com.wutsi.ndopify.web.ui.match

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.web.core.service.security.JwtAuthentication
import org.springframework.security.core.context.SecurityContextHolder

object MatchFixtures {
    val MATCH = Match(id = 12, mandateId = 7, listingId = 123, status = MatchStatus.PROPOSED)

    fun signIn() {
        val principal = mock<JwtPrincipal> { on { name } doReturn "1" }
        SecurityContextHolder.getContext().authentication = JwtAuthentication(principal)
    }

    fun signOut() = SecurityContextHolder.clearContext()
}
