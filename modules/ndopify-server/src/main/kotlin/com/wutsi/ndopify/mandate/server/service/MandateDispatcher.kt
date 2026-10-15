package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.dto.KycStatus
import org.springframework.stereotype.Service

@Service
class MandateDispatcher(
    private val assignmentDao: AssignmentRepository,
    private val agentService: AgentService,
    private val customerService: CustomerService,
) {
    fun dispatch(mandate: MandateEntity): AgentEntity? {
        if (mandate.status != MandateStatus.PENDING_ASSIGNMENT) {
            return null
        }

        val candidates = findCandidates(mandate)
        if (candidates.isEmpty()) {
            return null
        }

        return null
    }

    private fun findCandidates(mandate: MandateEntity): List<AgentEntity> {
        // Exclude all agent that declined or ignored the mandate in the past
        val excludeIds = assignmentDao.findByMandateAndStatusIn(
            mandate,
            listOf(
                AssignmentStatus.DECLINED,
                AssignmentStatus.EXPIRED,
            ),
        ).map { assignment -> assignment.agentId }

        // Find candidates
        // - ALWAYS select verified agents in the same city as the mandate
        // - Exclude agents that declined or ignored the mandate in the past
        // - Prevent self-assignment
        val customer = customerService.findById(mandate.customerId)
        return agentService.search(
            SearchAgentRequest(
                kycStatus = listOf(KycStatus.VERIFIED),
                cityId = mandate.cityId,
                limit = 100,
            )
        ).filter { agent -> !excludeIds.contains(agent.id) && agent.party.id != customer.party.id }
    }
}
