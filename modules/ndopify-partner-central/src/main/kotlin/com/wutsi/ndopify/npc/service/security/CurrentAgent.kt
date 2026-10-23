package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import org.springframework.stereotype.Service

/**
 * The signed-in agent, with its profile and its party (name, e-mail, photo, KYC status). Its ids always come from the
 * access token, never from a form or URL: ndopify-server doesn't check who updates an agent yet.
 */
@Service
class CurrentAgent(
    private val currentParty: CurrentParty,
    private val agentClient: NdAgentClient,
) {
    /** Null when nobody is signed in, or the user has no party or isn't an agent: those must sign in again. */
    fun get(): Agent? {
        val partyId = currentParty.id() ?: return null
        return agentClient.findByPartyId(partyId)
    }
}
