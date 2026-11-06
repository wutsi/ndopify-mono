package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import org.springframework.stereotype.Service

/**
 * The signed-in agent, with its profile and its party (name, e-mail, photo, KYC status): ndopify-server resolves it
 * from the access token. Null when the caller has no party or isn't an agent: those must sign in again.
 */
@Service
class CurrentAgent(private val agentClient: NdAgentClient) {
    fun get(): Agent? = agentClient.me()
}
