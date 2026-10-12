package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.npc.client.ndopify.NdUserClient
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/** The signed-in agent's party, looked up from the user in the access-token cookie (see [JwtAuthenticationFilter]). */
@Service
class CurrentParty(private val userClient: NdUserClient) {
    /** Null when nobody is signed in, or the user is gone or has no party: those must sign in again. */
    fun get(): Party? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? JwtPrincipal
            ?: return null
        return userClient.get(principal.getUserId())?.party
    }
}
