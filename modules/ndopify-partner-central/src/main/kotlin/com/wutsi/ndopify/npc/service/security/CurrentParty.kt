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
        val principal = principal() ?: return null
        return userClient.get(principal.getUserId())?.party
    }

    /**
     * The party's id, from the token without any call; looked up like [get] for a token issued before it carried one.
     * Null when nobody is signed in, or the user has no party.
     */
    fun id(): Long? {
        val principal = principal() ?: return null
        return principal.getPartyId() ?: get()?.id
    }

    private fun principal(): JwtPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? JwtPrincipal
}
