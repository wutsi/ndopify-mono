package com.wutsi.ndopify.agent.server.event

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.event.KycCaseCompletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AgentKycEventListener(
    private val service: AgentService,
) {
    /**
     * Synchronous, inside the KYC transaction: `joinedAt` commits (or rolls back) with the VERIFIED status. Lets the
     * party domain stay unaware of agents.
     */
    @EventListener
    fun onKycCaseCompleted(event: KycCaseCompletedEvent) {
        if (event.status == KycStatus.VERIFIED) {
            service.onKycVerified(event.partyId)
        }
    }
}
